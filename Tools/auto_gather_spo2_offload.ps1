<#
.SYNOPSIS
  Always-on Fold gather for SpO2 / sleep-offload research (Tailscale ADB).

.DESCRIPTION
  Ensures wireless ADB to Gilbert's Fold, optionally nudges DEBUG hist via SYNC_NOW + SignalHunt,
  pulls NOOP DB / exports / SpO2ReTrace-relevant logcat into:
    <pairing-logs>/auto-gather-YYYYMMDD/<HHMMSS>/

  Never invents SpO2 %. Raw aux82 / red/IR ADC / spo2re lines only.
  Grab+wipe of strap flash only happens when Backfiller runs a real hist offload (trim-ack) —
  this script cannot wipe the strap by itself.

  APPEND-ONLY pairing-logs policy (Gilbert 2026-07-19):
  - Each pass writes ONLY under auto-gather-YYYYMMDD/<HHMMSS>/ (new stamp every time).
  - NEVER delete, prune, or overwrite sibling research banks (fold-pull-*, fold-spo2-*,
    kimi*, signal-hunt-*, ble-re-*, or any non-auto-gather folder Kimi/agents produced).
  - If a pull target already exists with content inside THIS stamp dir, keep the prior bytes
    as *.prev.<stamp> before writing the new pull (never silent clobber).

.PARAMETER TailscaleHost
  Fold Tailscale IPv4 (default: 100.91.234.88).

.PARAMETER Port
  Wireless ADB port (default: 5555).

.PARAMETER PairingLogsRoot
  Destination root. Default: AI-store sibling pairing-logs.

.PARAMETER SkipTrigger
  Skip DEBUG wake / SYNC_NOW / SIGNAL_HUNT / hist wait (still full RAW pull).

.PARAMETER SkipPullDownloads
  Skip /sdcard/Download/noop* + whoop* pulls (DB + logcat still run).

.PARAMETER RegisterTask
  Register Windows Scheduled Task NOOP-AutoGather-SpO2:
  - every 20 min all day (10-year window)
  - plus dense late-morning window 09:00-14:00 every 15 min (Gilbert bedtime ~2am)

.PARAMETER UnregisterTask
  Remove that scheduled task and exit.

.PARAMETER Loop
  Run forever, sleeping -IntervalMinutes between passes (alternative to Task Scheduler).

.PARAMETER IntervalMinutes
  Loop sleep (default 20). Scheduled task uses 20 min.

.PARAMETER HistWaitSeconds
  After SYNC_NOW (+ bond-defer settle), wait this long for hist offload.
  Default 120s: DEBUG 8.6.205+ may defer SYNC_NOW ~30s for CLIENT_HELLO, then offload needs room.
  Official IPA hist caps: WhoopStrapDataUploadCount=50000 / OldestDataFetchCount=28000 (see IPA research doc).

.EXAMPLE
  .\Tools\auto_gather_spo2_offload.ps1
  .\Tools\auto_gather_spo2_offload.ps1 -RegisterTask
  .\Tools\auto_gather_spo2_offload.ps1 -UnregisterTask
  .\Tools\auto_gather_spo2_offload.ps1 -Loop -IntervalMinutes 20
#>
param(
    [string]$TailscaleHost = "100.91.234.88",
    [int]$Port = 5555,
    [string]$PairingLogsRoot = "",
    [switch]$SkipTrigger,
    [switch]$SkipPullDownloads,
    [switch]$RegisterTask,
    [switch]$UnregisterTask,
    [switch]$Loop,
    [int]$IntervalMinutes = 20,
    [int]$MaxDownloadFileMb = 80,
    [int]$DownloadMtimeHours = 72,
    [int]$HistWaitSeconds = 120
)

$ErrorActionPreference = "Continue"
$TaskName = "NOOP-AutoGather-SpO2"
$DebugPkg = "com.noop.whoop.debug"
$MainPkg = "com.noop.whoop"
$DbName = "noop_whoop.db"

$noopRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
if (-not $PairingLogsRoot) {
    $sibling = [IO.Path]::GetFullPath((Join-Path $noopRoot "..\pairing-logs"))
    $repoLocal = Join-Path $noopRoot "pairing-logs"
    if (Test-Path $sibling) { $PairingLogsRoot = $sibling }
    else { $PairingLogsRoot = $repoLocal }
}
$PairingLogsRoot = [IO.Path]::GetFullPath($PairingLogsRoot)

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $wingetAdb = Get-Command adb -ErrorAction SilentlyContinue
    if ($wingetAdb) { $adb = $wingetAdb.Source }
    else { throw "adb not found (expected under LOCALAPPDATA\Android\Sdk\platform-tools)" }
}

$connectScript = Join-Path $PSScriptRoot "connect_fold_tailscale_adb.ps1"
$serial = "${TailscaleHost}:${Port}"

function Write-GatherLog([string]$msg, [string]$logFile = $null) {
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $msg
    Write-Host $line
    if ($logFile) { Add-Content -LiteralPath $logFile -Value $line -Encoding utf8 }
}

function Register-GatherTask {
    $ps = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"
    $scriptPath = Join-Path $PSScriptRoot "auto_gather_spo2_offload.ps1"
    $arg = "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`""
    $action = New-ScheduledTaskAction -Execute $ps -Argument $arg -WorkingDirectory $noopRoot

    # 1) Always-on: every 20 min for ~10 years (Windows rejects TimeSpan.MaxValue).
    $tAllDay = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) `
        -RepetitionInterval (New-TimeSpan -Minutes 20) `
        -RepetitionDuration (New-TimeSpan -Days 3650)

    # 2) Post-sleep late morning: daily 09:00 with 15-min repeats for 5 hours (09:00-14:00).
    #    Gilbert bedtime often ~2 AM -> wake/sync later; dense window catches post-sleep hist.
    $tMorning = New-ScheduledTaskTrigger -Daily -At "09:00"
    $tMorningRep = New-ScheduledTaskTrigger -Once -At "09:00" `
        -RepetitionInterval (New-TimeSpan -Minutes 15) `
        -RepetitionDuration (New-TimeSpan -Hours 5)
    $tMorning.Repetition = $tMorningRep.Repetition

    $settings = New-ScheduledTaskSettingsSet `
        -AllowStartIfOnBatteries `
        -DontStopIfGoingOnBatteries `
        -StartWhenAvailable `
        -MultipleInstances IgnoreNew `
        -ExecutionTimeLimit (New-TimeSpan -Hours 2) `
        -WakeToRun
    $principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited

    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
    Register-ScheduledTask -TaskName $TaskName -Action $action `
        -Trigger @($tAllDay, $tMorning) -Settings $settings -Principal $principal -Force | Out-Null
    $ok = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    if (-not $ok) { throw "Failed to register Scheduled Task $TaskName" }
    Write-Host "Registered Scheduled Task: $TaskName"
    Write-Host "  Triggers: every 20 min all-day + daily 09:00-14:00 every 15 min (bedtime ~2am)"
    Write-Host "  Script:   $scriptPath"
    Write-Host "  Stop:     .\Tools\auto_gather_spo2_offload.ps1 -UnregisterTask"
    Write-Host "  Gilbert:  Wear MG overnight (~2am bedtime OK); Fold Tailscale up; desktop logged in"
    $ok | Format-List TaskName, State
    Get-ScheduledTaskInfo -TaskName $TaskName | Format-List LastRunTime, NextRunTime, LastTaskResult
    Write-Host "Triggers detail:"
    (Get-ScheduledTask -TaskName $TaskName).Triggers | ForEach-Object {
        Write-Host ("  StartBoundary={0} RepetitionInterval={1} RepetitionDuration={2}" -f `
            $_.StartBoundary, $_.Repetition.Interval, $_.Repetition.Duration)
    }
}

function Unregister-GatherTask {
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
    Write-Host "Removed Scheduled Task: $TaskName (if it existed)"
}

function Test-FoldAdbLive {
    # devices line alone can race (connect OK then drop). Require get-state + trivial shell.
    $st = (& $adb -s $serial get-state 2>$null | Out-String).Trim()
    if ($st -ne "device") { return $false }
    $echo = (& $adb -s $serial shell "echo noop_adb_ok" 2>$null | Out-String).Trim()
    return ($echo -match "noop_adb_ok")
}

function Ensure-FoldAdb {
    if (Test-Path $connectScript) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $connectScript -TailscaleHost $TailscaleHost -Port $Port
        $code = $LASTEXITCODE
        if ($code -eq 0 -and (Test-FoldAdbLive)) { return $true }
        if ($code -eq 0) {
            Write-Host "connect_fold reported OK but get-state/shell failed - treating as offline"
        } else {
            Write-Host "connect_fold_tailscale_adb.ps1 exit=$code - trying adb connect directly"
        }
    }
    & $adb connect $serial 2>&1 | Out-Host
    Start-Sleep -Seconds 2
    return [bool](Test-FoldAdbLive)
}

function Save-Text([string]$path, [string]$content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    Set-Content -LiteralPath $path -Value $content -Encoding utf8
}

# Preserve existing bytes before overwrite (append-only research banks — never silent clobber).
function Preserve-IfPresent([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) { return }
    $len = (Get-Item -LiteralPath $path).Length
    if ($len -lt 32) { return }
    $stamp = Get-Date -Format "HHmmss"
    $bak = "$path.prev.$stamp"
    # Never overwrite an existing .prev either — bump stamp if collision.
    $n = 0
    while (Test-Path -LiteralPath $bak) {
        $n++
        $bak = "$path.prev.$stamp.$n"
    }
    Copy-Item -LiteralPath $path -Destination $bak -Force
}

function Assert-AppendOnlyRoot([string]$outDir) {
    # Refuse to write outside an auto-gather stamp folder (protects Kimi / fold-pull banks).
    $leaf = Split-Path $outDir -Leaf
    $parent = Split-Path $outDir -Parent
    $dayLeaf = Split-Path $parent -Leaf
    if ($dayLeaf -notmatch '^auto-gather-\d{8}$' -or $leaf -notmatch '^\d{6}') {
        throw "APPEND-ONLY guard: refuse write outside auto-gather-YYYYMMDD/HHMMSS (got $outDir)"
    }
}

function Pull-RunAsFile([string]$pkg, [string]$remoteRel, [string]$outFile) {
    $dir = Split-Path $outFile -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    Preserve-IfPresent $outFile
    $tmpErr = Join-Path $env:TEMP ("noop_gather_err_{0}.txt" -f [guid]::NewGuid().ToString("N"))
    try {
        $proc = Start-Process -FilePath $adb -ArgumentList @("-s", $serial, "exec-out", "run-as", $pkg, "cat", $remoteRel) `
            -NoNewWindow -Wait -PassThru -RedirectStandardOutput $outFile -RedirectStandardError $tmpErr
        $len = 0
        if (Test-Path $outFile) { $len = (Get-Item -LiteralPath $outFile).Length }
        if ($proc.ExitCode -ne 0 -or $len -lt 32) {
            # Failed pull — restore prior bank if we just clobbered it with empty/error bytes.
            $prev = Get-ChildItem -LiteralPath $dir -Filter ((Split-Path $outFile -Leaf) + ".prev.*") -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending | Select-Object -First 1
            Remove-Item -LiteralPath $outFile -Force -ErrorAction SilentlyContinue
            if ($prev) {
                Copy-Item -LiteralPath $prev.FullName -Destination $outFile -Force -ErrorAction SilentlyContinue
            }
            return $false
        }
        # run-as errors sometimes land in stdout as tiny text files
        if ($len -lt 512 -and $remoteRel -match '\.db$') {
            $prev = Get-ChildItem -LiteralPath $dir -Filter ((Split-Path $outFile -Leaf) + ".prev.*") -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending | Select-Object -First 1
            Remove-Item -LiteralPath $outFile -Force -ErrorAction SilentlyContinue
            if ($prev) {
                Copy-Item -LiteralPath $prev.FullName -Destination $outFile -Force -ErrorAction SilentlyContinue
            }
            return $false
        }
        return $true
    } finally {
        Remove-Item -LiteralPath $tmpErr -Force -ErrorAction SilentlyContinue
    }
}

# Hunt exact pull (SPO2_REAL_SOLUTION_HUNT.md section 6): full exec-out cat via cmd redirect.
# NEVER use head -c / 8MB truncate (signal_hunt_rapid_fire session --pull does that).
function Pull-DebugBackfillFull([string]$outDir, [string]$runLog) {
    Assert-AppendOnlyRoot $outDir
    $ras = Join-Path $outDir "run-as-debug"
    New-Item -ItemType Directory -Path $ras -Force | Out-Null
    $files = @(
        "whoop5-backfill-capture.jsonl",
        "whoop5-backfill-capture.jsonl.1",
        "whoop5-events.jsonl"
    )
    $ok = @()
    foreach ($f in $files) {
        $dest = Join-Path $ras $f
        Preserve-IfPresent $dest
        # Exact hunt pattern: cmd /c "adb ... exec-out run-as ... cat files/$f > dest"
        # Use full adb path (SDK) so we never hit scrcpy's adb / PATH surprises.
        $cmdLine = "`"$adb`" -s $serial exec-out run-as $DebugPkg cat files/$f > `"$dest`""
        Write-GatherLog "FULL pull (hunt): $f" $runLog
        cmd /c $cmdLine
        $len = 0
        if (Test-Path -LiteralPath $dest) { $len = (Get-Item -LiteralPath $dest).Length }
        if ($len -lt 32) {
            Write-GatherLog "WARN: missing/empty run-as-debug/$f (len=$len) — prior bank preserved if any" $runLog
            $prev = Get-ChildItem -LiteralPath $ras -Filter ($f + ".prev.*") -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending | Select-Object -First 1
            Remove-Item -LiteralPath $dest -Force -ErrorAction SilentlyContinue
            if ($prev) {
                Copy-Item -LiteralPath $prev.FullName -Destination $dest -Force -ErrorAction SilentlyContinue
                Write-GatherLog "  restored $($prev.Name) -> $f" $runLog
            }
            continue
        }
        Write-GatherLog "  -> run-as-debug/$f ($($len) bytes) FULL" $runLog
        $ok += ("run-as-debug/{0}" -f $f)
    }
    return $ok
}

function Invoke-AdbTimed([string[]]$AdbArgs, [int]$TimeoutSec = 8) {
    # adb can hang indefinitely when the wireless device drops mid-command.
    $outFile = Join-Path $env:TEMP ("noop-adb-out-{0}.txt" -f [guid]::NewGuid().ToString("N"))
    $errFile = Join-Path $env:TEMP ("noop-adb-err-{0}.txt" -f [guid]::NewGuid().ToString("N"))
    try {
        $p = Start-Process -FilePath $adb -ArgumentList $AdbArgs -NoNewWindow -PassThru `
            -RedirectStandardOutput $outFile -RedirectStandardError $errFile
        if (-not $p.WaitForExit([math]::Max(1000, $TimeoutSec * 1000))) {
            try { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } catch {}
            return $null
        }
        return (Get-Content -LiteralPath $outFile -ErrorAction SilentlyContinue | Out-String)
    } finally {
        Remove-Item -LiteralPath $outFile, $errFile -Force -ErrorAction SilentlyContinue
    }
}
function Wait-HistOffload([int]$seconds, [string]$runLog) {
    Write-GatherLog "Waiting ${seconds}s for hist offload (watch HISTORICAL_DATA_RESULT / NoopSyncNow)..." $runLog
    $deadline = (Get-Date).AddSeconds([math]::Max(5, $seconds))
    $sawHist = $false
    $sawSync = $false
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-FoldAdbLive)) {
            Write-GatherLog "ADB dropped during hist wait - aborting wait early" $runLog
            break
        }
        $raw = Invoke-AdbTimed @("-s", $serial, "logcat", "-d", "-t", "120") -TimeoutSec 8
        if ($null -eq $raw) {
            Write-GatherLog "adb logcat timed out (device likely offline) - aborting wait early" $runLog
            break
        }
        $chunk = $raw | Select-String -Pattern "HISTORICAL_DATA_RESULT|SEND_HISTORICAL|GET_DATA_RANGE|NoopSyncNow|history.*banked|Backfill: session|SYNC_NOW"
        if ($chunk) {
            $hitLines = @($chunk | ForEach-Object { $_.Line })
            if (-not $sawSync -and ($hitLines | Where-Object { $_ -match "NoopSyncNow|SYNC_NOW" })) {
                $sawSync = $true
                Write-GatherLog "SYNC_NOW activity in logcat (defer or syncNow)" $runLog
            }
            if ($hitLines | Where-Object { $_ -match "HISTORICAL_DATA_RESULT|SEND_HISTORICAL|GET_DATA_RANGE|Backfill: session|history.*banked" }) {
                $sawHist = $true
                Write-GatherLog "Hist activity seen in logcat; continuing wait for settle..." $runLog
                break
            }
        }
        Start-Sleep -Seconds 5
    }
    $remain = [int][math]::Max(0, ($deadline - (Get-Date)).TotalSeconds)
    if ($remain -gt 0 -and (Test-FoldAdbLive)) {
        Start-Sleep -Seconds ([math]::Min($remain, 30))
    }
    if (-not $sawHist) {
        Write-GatherLog "No HISTORICAL_DATA_RESULT in wait window (strap offline, bond-defer race, or already synced) - still pulling full RAW" $runLog
    }
    return $sawHist
}

function Invoke-GatherOnce {
    $lockPath = Join-Path $env:TEMP "noop-auto-gather-spo2.lock"
    $lockStream = $null
    try {
        $lockStream = [System.IO.File]::Open($lockPath, [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    } catch {
        Write-Host "Gather already running (lock $lockPath) - skip this pass"
        return 0
    }
    try {
        return (Invoke-GatherOnceUnlocked)
    } finally {
        if ($lockStream) { $lockStream.Close(); $lockStream.Dispose() }
    }
}

function Invoke-GatherOnceUnlocked {
    $day = Get-Date -Format "yyyyMMdd"
    $stamp = Get-Date -Format "HHmmss"
    $dayDir = Join-Path $PairingLogsRoot ("auto-gather-{0}" -f $day)
    $outDir = Join-Path $dayDir $stamp
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
    Assert-AppendOnlyRoot $outDir
    $runLog = Join-Path $outDir "gather.log"
    $manifest = [ordered]@{
        host            = $env:COMPUTERNAME
        hostname_dns    = [System.Net.Dns]::GetHostName()
        startedUtc      = (Get-Date).ToUniversalTime().ToString("o")
        foldSerial      = $serial
        pairingOut      = $outDir
        adbOk           = $false
        trigger         = @{}
        pulled          = @()
        notes           = @()
        noFakeSpo2Pct   = $true
    }

    Write-GatherLog "=== auto_gather_spo2_offload start -> $outDir ===" $runLog
    Write-GatherLog "Host=$env:COMPUTERNAME TailscaleFold=$serial" $runLog

    $ts = Get-Command tailscale -ErrorAction SilentlyContinue
    if ($ts) {
        $status = & tailscale status 2>&1 | Out-String
        Save-Text (Join-Path $outDir "tailscale-status.txt") $status
        $foldLine = ($status -split "`n") | Where-Object { $_ -match [regex]::Escape($TailscaleHost) } | Select-Object -First 1
        Write-GatherLog "Tailscale Fold: $foldLine" $runLog
        if ("$foldLine" -match "offline") {
            $manifest.notes += "Fold Tailscale OFFLINE"
            Write-GatherLog "BLOCKED: Fold Tailscale offline - wake Fold / Tailscale app" $runLog
            $manifest | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $outDir "manifest.json") -Encoding utf8
            return 3
        }
    }

    if (-not (Ensure-FoldAdb)) {
        $manifest.notes += "ADB connect failed"
        Write-GatherLog "BLOCKED: adb connect $serial failed (tcpip 5555 may need USB once)" $runLog
        $manifest | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $outDir "manifest.json") -Encoding utf8
        return 2
    }
    $manifest.adbOk = $true
    Write-GatherLog "ADB OK: $serial" $runLog

    $model = (& $adb -s $serial shell getprop ro.product.model 2>$null | Out-String).Trim()
    $pkgDump = & $adb -s $serial shell "dumpsys package $DebugPkg; dumpsys package $MainPkg" 2>$null |
        Select-String -Pattern "versionName=|versionCode=|package:" | Select-Object -First 16
    $pkgText = (@("model=$model") + @($pkgDump | ForEach-Object { $_.Line })) -join "`n"
    Save-Text (Join-Path $outDir "packages.txt") $pkgText

    if (-not $SkipTrigger) {
        # Hunt section 6 + SYNC_NOW: launch DEBUG -> force gated hist sync -> wait -> full RAW pull
        # SYNC_NOW needs DEBUG APK that registers com.noop.debug.SYNC_NOW (WhoopConnectionService).
        # 8.6.205+ defers ~30s when connected&&!bonded (CLIENT_HELLO race). IPA hist path:
        # Hello -> StrapBacklog -> DataRange -> EnterHighFreqHistoricalMode -> SendHistorical...
        # Older DEBUG builds ignore SYNC_NOW; monkey/resume may still kick hist via ensureStrapSleepBanked.
        Write-GatherLog "Trigger: launch DEBUG, SYNC_NOW (plus bond settle), wait hist ~${HistWaitSeconds}s, SIGNAL_HUNT r22/all" $runLog
        & $adb -s $serial shell "monkey -p $DebugPkg -c android.intent.category.LAUNCHER 1" 2>&1 | Out-Null
        Start-Sleep -Seconds 5
        $syncNow = & $adb -s $serial shell "am broadcast -a com.noop.debug.SYNC_NOW -p $DebugPkg" 2>&1 | Out-String
        # Let DEBUG bond-defer (~30s) start hist before AFK SignalHunt steals the link.
        $bondSettle = 35
        Write-GatherLog "Bond/hello settle ${bondSettle}s after SYNC_NOW (DEBUG 205+ defer window)" $runLog
        Start-Sleep -Seconds $bondSettle
        $huntR22 = & $adb -s $serial shell "am broadcast -a com.noop.debug.SIGNAL_HUNT --es mode r22 -p $DebugPkg" 2>&1 | Out-String
        Start-Sleep -Seconds 2
        $huntAll = & $adb -s $serial shell "am broadcast -a com.noop.debug.SIGNAL_HUNT --es mode all -p $DebugPkg" 2>&1 | Out-String
        Save-Text (Join-Path $outDir "trigger-broadcast.txt") ("=== SYNC_NOW ===`n$syncNow`n=== bondSettleSec=$bondSettle ===`n=== r22 ===`n$huntR22`n=== all ===`n$huntAll")
        $sawHist = Wait-HistOffload -seconds $HistWaitSeconds -runLog $runLog
        $manifest.trigger = @{
            debugMonkey     = $true
            syncNowBroadcast = ($syncNow -match "Broadcast completed|result=0|data=")
            bondSettleSeconds = $bondSettle
            histWaitSeconds = $HistWaitSeconds
            histSeenInWait  = [bool]$sawHist
            signalHuntR22   = ($huntR22 -match "Broadcast completed|result=0|data=")
            signalHuntAll   = ($huntAll -match "Broadcast completed|result=0|data=")
            huntDoc         = "docs/agent/research/SPO2_REAL_SOLUTION_HUNT.md section 6"
            ipaHistDoc      = "docs/agent/research/WHOOP_IPA_MG_INTERCEPT_2026-07-18.md"
            grabAndWipeNote = "When hist offload runs, Backfiller persist+HISTORICAL_DATA_RESULT trim-ack is the wipe. Gather does NOT separately clear strap flash. Needs connected+bonded DEBUG (8.6.205+ defers SYNC_NOW ~30s for CLIENT_HELLO race; alongside-only never hist). Fold Tailscale up."
            histOffloadUi   = @(
                "adb SYNC_NOW -> WhoopBleClient.syncNow (MANUAL; same as UI Sync now; DEBUG defers if connected&&!bonded)",
                "DEBUG automatic hist on resume/connect (ensureStrapSleepBanked / SEND_HISTORICAL)",
                "UI Sync now: Health > Sync; Live; Settings; Test Centre",
                "IPA: Hello -> backlog/DataRange -> EnterHighFreqHistoricalMode -> SendHistorical + BurstResult",
                "Overnight expectation: asleep>0 and @82 nz in NEW whoop5-backfill-capture.jsonl (raw, never %)",
                "Full RAW via exec-out cat (NOT head -c 8MB truncate)"
            )
        }
        Write-GatherLog "DEBUG up; hist wait done. Pulling FULL backfill RAW next." $runLog
    } else {
        Write-GatherLog "SkipTrigger: still doing FULL backfill pull (hunt path)" $runLog
    }

        Write-GatherLog "Pulling logcat (spo2re / Whoop / SignalHunt / hist)" $runLog
        $logRaw = Invoke-AdbTimed @("-s", $serial, "logcat", "-d", "-v", "threadtime") -TimeoutSec 20
        if ($null -eq $logRaw) {
            Write-GatherLog "adb logcat pull timed out - writing empty logcat note" $runLog
            $logText = "(adb logcat timed out / device offline)"
        } else {
            $logcat = $logRaw | Select-String -Pattern "spo2|SpO2|SPO2|aux82|WhoopBle|SignalHunt|HISTORICAL|Backfill|SYNC_NOW|NoopSyncNow|R22|enable_r22|Deep-data"
            $logText = if ($logcat) { ($logcat | ForEach-Object { $_.Line }) -join [Environment]::NewLine } else { "(no matching lines in buffer)" }
        }
        Save-Text (Join-Path $outDir "logcat-spo2-hist.txt") $logText
        $manifest.pulled += "logcat-spo2-hist.txt"

    # Hunt exact next command: FULL whoop5-backfill-capture.jsonl (+.1, events) via exec-out cat
    $bf = @(Pull-DebugBackfillFull -outDir $outDir -runLog $runLog)
    foreach ($p in $bf) { $manifest.pulled += $p }
    if ($bf.Count -lt 1) {
        $manifest.notes += "FULL backfill pull empty - DEBUG may lack RAW files"
    }

    $dbOut = Join-Path $outDir "debug_noop_whoop.db"
    if (Pull-RunAsFile $DebugPkg "databases/$DbName" $dbOut) {
        Write-GatherLog "Pulled DEBUG db ($((Get-Item $dbOut).Length) bytes)" $runLog
        $manifest.pulled += "debug_noop_whoop.db"
        foreach ($sfx in @("-wal", "-shm")) {
            $side = Join-Path $outDir ("debug_noop_whoop.db{0}" -f $sfx)
            if (Pull-RunAsFile $DebugPkg ("databases/{0}{1}" -f $DbName, $sfx) $side) {
                $manifest.pulled += (Split-Path $side -Leaf)
            }
        }
    } else {
        Write-GatherLog "WARN: could not pull DEBUG databases/$DbName via run-as" $runLog
        $manifest.notes += "debug db pull failed"
    }

    # Method 40 (SPO2_REAL_SOLUTION_HUNT): nz-night auto-flag gate. One cheap Room query per gather;
    # only when aux82 nz-asleep > 0 do the correlation methods (30-32/18) fire. Raw research - never %.
    if (Test-Path $dbOut) {
        $nzPy = Join-Path $outDir "nz_flag.py"
        Save-Text $nzPy @'
import sqlite3, sys, json, datetime
db = sys.argv[1]
out = {"nzFlag": False}
try:
    con = sqlite3.connect("file:%s?mode=ro" % db, uri=True, timeout=15)
    cur = con.cursor()
    out["asleepRows"] = cur.execute("SELECT COUNT(*) FROM sleepStateSample WHERE state=2").fetchone()[0]
    out["nzAsleep"] = cur.execute("SELECT COUNT(*) FROM sleepStateSample WHERE state=2 AND aux82 IS NOT NULL AND aux82 != 0").fetchone()[0]
    out["nzAny"] = cur.execute("SELECT COUNT(*) FROM sleepStateSample WHERE aux82 IS NOT NULL AND aux82 != 0").fetchone()[0]
    mx = cur.execute("SELECT MAX(ts) FROM sleepStateSample WHERE state=2").fetchone()[0]
    out["latestAsleepTsRaw"] = mx
    if mx:
        sec = mx / 1000.0 if mx > 1e12 else float(mx)
        out["latestAsleepUtc"] = datetime.datetime.fromtimestamp(sec, datetime.UTC).isoformat()
    con.close()
except Exception as e:
    out["error"] = str(e)
out["nzFlag"] = bool(out.get("nzAsleep"))
print(json.dumps(out))
'@
        $py = Get-Command python -ErrorAction SilentlyContinue
        if ($py) {
            $nzJson = & python $nzPy $dbOut 2>&1 | Out-String
            Save-Text (Join-Path $outDir "nz_flag.txt") $nzJson.Trim()
            try {
                $nz = $nzJson.Trim() | ConvertFrom-Json
                $manifest.nzFlag = $nz
                Write-GatherLog "NZ-FLAG: aux82 nz-asleep=$($nz.nzAsleep) nz-any=$($nz.nzAny) asleep-rows=$($nz.asleepRows) latest=$($nz.latestAsleepUtc) flag=$($nz.nzFlag)" $runLog
                if ($nz.nzFlag) { Write-GatherLog "NZ-FLAG *** nz aux82 NIGHT DETECTED - fire methods 30-32/18 on this DB ***" $runLog }
            } catch {
                Write-GatherLog "NZ-FLAG: query parse failed ($nzJson)" $runLog
                $manifest.notes += "nz_flag parse failed"
            }
        } else {
            Write-GatherLog "NZ-FLAG: python not on PATH - gate skipped" $runLog
        }
    }

    # Methods 48/52-54 (SPO2_REAL_SOLUTION_HUNT): per-pass RAW frame census. Counts v18/v20/v21/v26 in
    # the freshly-pulled backfill JSONL so the R22-armed 1:1:1 question is scored EVERY pass without a
    # manual mine. Raw structural census only - never SpO2 %.
    $rawDir = Join-Path $outDir "run-as-debug"
    if ((Test-Path $rawDir) -and (Get-Command python -ErrorAction SilentlyContinue)) {
        $censusPy = Join-Path $outDir "frame_census.py"
        Save-Text $censusPy @'
import json, collections, sys, os
raw = sys.argv[1]
census = collections.Counter()
v20ts = []
v18last = 0
for fn in os.listdir(raw):
    if not fn.startswith("whoop5-backfill-capture.jsonl"):
        continue
    with open(os.path.join(raw, fn), "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            try:
                j = json.loads(line)
            except Exception:
                continue
            hx = j.get("hex")
            if not hx or len(hx) < 40:
                continue
            b = bytes.fromhex(hx)
            if len(b) < 20:
                continue
            inner, ver, size = b[8], b[9], len(b)
            if inner in (0x2F, 0x2B):
                census[ver] += 1
                census[(ver, size)] += 1
                if len(b) >= 19:
                    u = int.from_bytes(b[15:19], "little")
                    if 1_700_000_000 <= u <= 1_900_000_000:
                        if ver == 20 and size == 244:
                            v20ts.append(u)
                        if ver == 18 and u > v18last:
                            v18last = u
v20ts.sort()
mono = sum(1 for a, b in zip(v20ts, v20ts[1:]) if b >= a)
out = {
    "v18": census.get(18, 0), "v20": census.get(20, 0), "v21": census.get(21, 0), "v26": census.get(26, 0),
    "v20_244": census.get((20, 244), 0), "v20_2140": census.get((20, 2140), 0), "v21_1244": census.get((21, 1244), 0),
    "v20ts_n": len(v20ts), "v20ts_mono": mono,
    "v18_last_unix": v18last,
    "ratio_v20_per_v18": round(census.get(20, 0) / max(census.get(18, 0), 1), 3),
    "ratio_v21_per_v18": round(census.get(21, 0) / max(census.get(18, 0), 1), 3),
}
print(json.dumps(out))
'@
        $censusJson = & python $censusPy $rawDir 2>&1 | Out-String
        Save-Text (Join-Path $outDir "frame_census.txt") $censusJson.Trim()
        try {
            $fc = $censusJson.Trim() | ConvertFrom-Json
            $manifest.frameCensus = $fc
            Write-GatherLog "CENSUS: v18=$($fc.v18) v20=$($fc.v20) v21=$($fc.v21) v26=$($fc.v26) (244=$($fc.v20_244) 2140=$($fc.v20_2140) 1244=$($fc.v21_1244)) v20ts=$($fc.v20ts_mono)/$($fc.v20ts_n) ratio20=$($fc.ratio_v20_per_v18) ratio21=$($fc.ratio_v21_per_v18)" $runLog
            if ($fc.v21 -gt 0) { Write-GatherLog "CENSUS *** v21 IMU BANK PRESENT - score 1:1:1 + run Whoop5RawImu verify on this pull ***" $runLog }
        } catch {
            Write-GatherLog "CENSUS: parse failed ($censusJson)" $runLog
            $manifest.notes += "frame_census parse failed"
        }
    }

    $privateFiles = @(
        @{ Rel = "shared_prefs/noop_prefs.xml"; Out = "noop_prefs.xml" },
        @{ Rel = "shared_prefs/noop_whoop_app_scores.xml"; Out = "noop_whoop_app_scores.xml" },
        @{ Rel = "shared_prefs/noop_backfill_cursors.xml"; Out = "noop_backfill_cursors.xml" }
    )
    foreach ($f in $privateFiles) {
        $dest = Join-Path $outDir $f.Out
        if (Pull-RunAsFile $DebugPkg $f.Rel $dest) {
            Write-GatherLog "Pulled $($f.Out) ($((Get-Item $dest).Length) bytes)" $runLog
            $manifest.pulled += $f.Out
        }
    }

    $mainDb = Join-Path $outDir "main_noop_whoop.db"
    if (Pull-RunAsFile $MainPkg "databases/$DbName" $mainDb) {
        Write-GatherLog "Pulled MAIN db ($((Get-Item $mainDb).Length) bytes)" $runLog
        $manifest.pulled += "main_noop_whoop.db"
    } else {
        $manifest.notes += "MAIN run-as blocked (expected for release) - use Data sources Export backup / Download pulls"
    }

    if (-not $SkipPullDownloads) {
        $dlDir = Join-Path $outDir "Download"
        New-Item -ItemType Directory -Path $dlDir -Force | Out-Null
        Write-GatherLog "Listing /sdcard/Download noop*|whoop* ..." $runLog
        $listing = & $adb -s $serial shell "ls -la /sdcard/Download/noop* /sdcard/Download/whoop* /sdcard/Download/*noop* 2>/dev/null" 2>&1 | Out-String
        Save-Text (Join-Path $outDir "download-listing.txt") $listing

        # Prefer ls over find (Android sh chokes on escaped parens / odd filenames).
        $names = & $adb -s $serial shell "ls /sdcard/Download/noop* /sdcard/Download/whoop* 2>/dev/null" 2>$null
        $cutoff = (Get-Date).AddHours(-1 * [math]::Abs($DownloadMtimeHours))
        $maxBytes = [int64]$MaxDownloadFileMb * 1MB
        foreach ($line in @($names)) {
            $remote = ("$line").Trim()
            if (-not $remote -or $remote -notmatch "^/sdcard/") { continue }
            if ($remote -match '[()]') { continue }  # skip "noop-main (1).zip" etc.
            $leaf = Split-Path $remote -Leaf
            $stat = (& $adb -s $serial shell "stat -c '%Y %s' $remote 2>/dev/null" | Out-String).Trim()
            if ($stat -notmatch "^(\d+)\s+(\d+)$") { continue }
            $mtime = [DateTimeOffset]::FromUnixTimeSeconds([int64]$Matches[1]).LocalDateTime
            $size = [int64]$Matches[2]
            $core = $leaf -match '\.(db|db-wal|db-shm|jsonl|json|xml|csv)$' -or $leaf -match 'raw-capture|whoop5|noop_whoop|aux82'
            if (-not $core) {
                if ($mtime -lt $cutoff) { continue }
                if ($leaf -match '\.(zip|png)$' -and $mtime -lt $cutoff) { continue }
            }
            if ($size -gt $maxBytes) {
                Write-GatherLog "SKIP large $leaf ($([math]::Round($size/1MB,1)) MB > $MaxDownloadFileMb MB)" $runLog
                continue
            }
            # Screenshots only if newer than cutoff (avoid PNG spam every 20 min)
            if ($leaf -match '\.png$' -and $mtime -lt $cutoff) { continue }
            $local = Join-Path $dlDir $leaf
            Write-GatherLog "adb pull $remote" $runLog
            & $adb -s $serial pull $remote $local 2>&1 | Out-Null
            if (Test-Path $local) { $manifest.pulled += ("Download/{0}" -f $leaf) }
        }
    }

    $manifest.finishedUtc = (Get-Date).ToUniversalTime().ToString("o")
    $manifest | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $outDir "manifest.json") -Encoding utf8
    Write-GatherLog "DONE. Artifacts: $outDir" $runLog
    Write-GatherLog "Reminder: aux82 / spo2re / red-IR are raw research - never SpO2 %." $runLog
    return 0
}

if ($UnregisterTask) { Unregister-GatherTask; exit 0 }
if ($RegisterTask) { Register-GatherTask; exit 0 }

if ($Loop) {
    Write-Host "Looping every $IntervalMinutes min (Ctrl+C to stop). Task alternative: -RegisterTask"
    while ($true) {
        try { [void](Invoke-GatherOnce) } catch { Write-Host "Gather error: $_" }
        Start-Sleep -Seconds ([math]::Max(60, $IntervalMinutes * 60))
    }
} else {
    exit (Invoke-GatherOnce)
}
