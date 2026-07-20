<#
.SYNOPSIS
  Always-on score research watcher for NOOP MAIN / DEBUG (Tailscale ADB).

.DESCRIPTION
  Pulls score-relevant Room rows + prefs from Fold (append-only, Preserve-IfPresent),
  then compares banked Effort / Charge / Rest / Stress / HRV against raw sample density.

  Never invents SpO2 %. Never deletes SpO2 / hist / fold-pull research banks.
  Writes under:
    <pairing-logs>/score-research-YYYYMMDD/<HHMMSS>/

.PARAMETER TailscaleHost
  Fold Tailscale IPv4 (default: 100.91.234.88).

.PARAMETER Port
  Wireless ADB port (default: 5555).

.PARAMETER Loop
  Run forever, sleeping -IntervalMinutes between passes.

.PARAMETER IntervalMinutes
  Loop sleep (default 30).

.PARAMETER RegisterTask
  Register Windows Scheduled Task NOOP-ScoreResearchWatch (every 30 min).

.PARAMETER UnregisterTask
  Remove that scheduled task and exit.

.EXAMPLE
  .\Tools\score_research_watch.ps1
  .\Tools\score_research_watch.ps1 -Loop -IntervalMinutes 30
  .\Tools\score_research_watch.ps1 -RegisterTask
#>
param(
    [string]$TailscaleHost = "100.91.234.88",
    [int]$Port = 5555,
    [string]$PairingLogsRoot = "",
    [switch]$Loop,
    [int]$IntervalMinutes = 30,
    [switch]$RegisterTask,
    [switch]$UnregisterTask,
    [switch]$SkipPullDownloads
)

$ErrorActionPreference = "Continue"
$TaskName = "NOOP-ScoreResearchWatch"
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
    else { throw "adb not found" }
}

$connectScript = Join-Path $PSScriptRoot "connect_fold_tailscale_adb.ps1"
$serial = "${TailscaleHost}:${Port}"
$analyzer = Join-Path $PSScriptRoot "analyze_score_research.py"

function Write-WatchLog([string]$msg, [string]$logFile = $null) {
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $msg
    Write-Host $line
    if ($logFile) { Add-Content -LiteralPath $logFile -Value $line -Encoding utf8 }
}

function Preserve-IfPresent([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) { return }
    $len = (Get-Item -LiteralPath $path).Length
    if ($len -lt 32) { return }
    $stamp = Get-Date -Format "HHmmss"
    $bak = "$path.prev.$stamp"
    $n = 0
    while (Test-Path -LiteralPath $bak) {
        $n++
        $bak = "$path.prev.$stamp.$n"
    }
    Copy-Item -LiteralPath $path -Destination $bak -Force
}

function Register-WatchTask {
    $ps = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"
    $scriptPath = Join-Path $PSScriptRoot "score_research_watch.ps1"
    $arg = "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`""
    $action = New-ScheduledTaskAction -Execute $ps -Argument $arg -WorkingDirectory $noopRoot
    $trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) `
        -RepetitionInterval (New-TimeSpan -Minutes 30) `
        -RepetitionDuration (New-TimeSpan -Days 3650)
    Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Force | Out-Null
    Write-Host "Registered Scheduled Task: $TaskName (every 30 min)"
}

function Unregister-WatchTask {
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
    Write-Host "Removed Scheduled Task: $TaskName (if it existed)"
}

function Ensure-FoldAdb {
    if (Test-Path $connectScript) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $connectScript -TailscaleHost $TailscaleHost -Port $Port | Out-Null
        if ($LASTEXITCODE -eq 0) { return $true }
    }
    & $adb connect $serial 2>&1 | Out-Null
    Start-Sleep -Seconds 2
    $online = & $adb devices | Select-Object -Skip 1 | Where-Object {
        $_ -match [regex]::Escape($serial) -and $_ -match "\tdevice$"
    }
    return [bool]$online
}

function Pull-DebugDb([string]$outFile) {
    Preserve-IfPresent $outFile
    $remoteTmp = "/sdcard/Download/noop_score_research_probe.db"
    & $adb -s $serial shell "run-as $DebugPkg cp databases/$DbName $remoteTmp" 2>$null | Out-Null
    & $adb -s $serial pull $remoteTmp $outFile 2>$null | Out-Null
    if ((Test-Path $outFile) -and (Get-Item $outFile).Length -gt 1024) { return $true }
    $proc = Start-Process -FilePath $adb -ArgumentList @(
        "-s", $serial, "exec-out", "run-as", $DebugPkg, "cat", "databases/$DbName"
    ) -NoNewWindow -Wait -PassThru -RedirectStandardOutput $outFile
    return ((Test-Path $outFile) -and (Get-Item $outFile).Length -gt 1024)
}

function Invoke-ScoreResearchPass {
    $day = Get-Date -Format "yyyyMMdd"
    $stamp = Get-Date -Format "HHmmss"
    $outDir = Join-Path $PairingLogsRoot ("score-research-{0}\{1}" -f $day, $stamp)
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $logFile = Join-Path $outDir "watch.log"
    Write-WatchLog ("Score research pass -> {0}" -f $outDir) $logFile

    if (-not (Ensure-FoldAdb)) {
        Write-WatchLog ("Fold ADB offline ({0}) - skip pull" -f $serial) $logFile
        @{ ok = $false; outDir = $outDir; reason = "adb_offline" } | ConvertTo-Json |
            Set-Content (Join-Path $outDir "manifest.json") -Encoding utf8
        return
    }

    $mainVer = (& $adb -s $serial shell "dumpsys package $MainPkg" 2>$null | Select-String "versionName=") |
        Select-Object -First 1
    $dbgVer = (& $adb -s $serial shell "dumpsys package $DebugPkg" 2>$null | Select-String "versionName=") |
        Select-Object -First 1
    Write-WatchLog ("MAIN {0} | DEBUG {1}" -f $mainVer, $dbgVer) $logFile

    foreach ($pkg in @($DebugPkg, $MainPkg)) {
        $safe = $pkg -replace '\.', '_'
        $prefsOut = Join-Path $outDir ("{0}_noop_prefs_snip.txt" -f $safe)
        Preserve-IfPresent $prefsOut
        $raw = & $adb -s $serial shell "run-as $pkg cat shared_prefs/noop_prefs.xml" 2>$null
        if ($raw) {
            $raw | Select-String -Pattern "effort|strain|hrv|continuous|rescore|recovery" |
                ForEach-Object { $_.Line } |
                Set-Content -LiteralPath $prefsOut -Encoding utf8
        }
        $scoresOut = Join-Path $outDir ("{0}_app_scores.xml" -f $safe)
        Preserve-IfPresent $scoresOut
        & $adb -s $serial shell "run-as $pkg cat shared_prefs/noop_whoop_app_scores.xml" 2>$null |
            Set-Content -LiteralPath $scoresOut -Encoding utf8
    }

    $dbOut = Join-Path $outDir "debug_noop_whoop.db"
    $pulled = Pull-DebugDb $dbOut
    $dbBytes = 0
    if (Test-Path $dbOut) { $dbBytes = (Get-Item $dbOut).Length }
    Write-WatchLog ("DEBUG DB pull={0} bytes={1}" -f $pulled, $dbBytes) $logFile

    $reportPath = Join-Path $outDir "score_anomaly_report.md"
    $summaryJson = Join-Path $outDir "summary.json"
    if ($pulled -and (Test-Path $analyzer)) {
        python $analyzer $dbOut $reportPath $summaryJson 2>&1 | Tee-Object -FilePath $logFile -Append
    } else {
        @(
            "# Score research - no DEBUG DB",
            "MAIN release cannot run-as; export backup or keep DEBUG lab."
        ) | Set-Content $reportPath -Encoding utf8
        '{ "anomaly_count": -1, "reason": "no_db" }' | Set-Content $summaryJson -Encoding utf8
    }

    if (-not $SkipPullDownloads) {
        $dlDir = Join-Path $outDir "downloads"
        New-Item -ItemType Directory -Force -Path $dlDir | Out-Null
        $names = & $adb -s $serial shell "ls /sdcard/Download/noop*daily* 2>/dev/null; ls /sdcard/Download/*noopbak* 2>/dev/null" 2>$null
        foreach ($line in ($names | Where-Object { $_ -and $_.Trim() })) {
            $leaf = Split-Path ($line.Trim()) -Leaf
            $dest = Join-Path $dlDir $leaf
            Preserve-IfPresent $dest
            & $adb -s $serial pull $line.Trim() $dest 2>$null | Out-Null
        }
    }

    $manifest = [ordered]@{
        ok                  = $true
        outDir              = $outDir
        serial              = $serial
        main                = "$mainVer"
        debug               = "$dbgVer"
        dbPulled            = $pulled
        appendOnly          = $true
        preserveIfPresent   = $true
        generatedAt         = (Get-Date).ToUniversalTime().ToString("o")
    }
    ($manifest | ConvertTo-Json -Depth 4) | Set-Content (Join-Path $outDir "manifest.json") -Encoding utf8
    Write-WatchLog ("Done. Report: {0}" -f $reportPath) $logFile
}

if ($UnregisterTask) { Unregister-WatchTask; return }
if ($RegisterTask) { Register-WatchTask; return }

do {
    Invoke-ScoreResearchPass
    if (-not $Loop) { break }
    Write-Host ("Sleeping {0}m..." -f $IntervalMinutes)
    Start-Sleep -Seconds ([Math]::Max(60, $IntervalMinutes * 60))
} while ($true)
