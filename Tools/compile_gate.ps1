param(
    # Optional ADB serial: after a green MAIN assemble, deploy via deploy_live_edit.ps1 -Main.
    [string]$Serial = "",
    [switch]$Clean
)

# Compile Guardian gate for MAIN (com.noop.whoop).
# Runs one :app:assembleFullRelease - specialists must not run parallel Gradle.
# Exit 0 = green (APK path printed for integrator reuse). Non-zero = red + error tail.
# Optional -Serial deploys only after green (no thrash rebuild beyond install task).
# ASCII-only (Windows PS safe).
#
# Dense-bank heaps: prefer android/gradle.properties at ~1024m gradle / ~1536m kotlin while parallel agents thrash RAM.
# Exclusive quiet gate (lintVital): use ~2048m/2048m or lint OOMs on 1 GiB.

$ErrorActionPreference = "Stop"

$workspace = "C:\Users\Gilbert\Documents\Ai app store"
$repoRoot = Split-Path -Parent $PSScriptRoot
$androidLong = Join-Path $repoRoot "android"
$sdkMapped = "N:\android-sdk-local"
$androidMapped = "N:\noop-v8.4.0-src\android"
$gradle = "C:\Users\Gilbert\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat"
$javaHome = "C:\Users\Gilbert\.jdks\jbr-17.0.14"
$logPath = Join-Path $androidLong "compile_gate.log"
$tailLines = 100
$exitCode = 1

function Write-Gate([string]$Message) {
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
    Write-Host $line
    Add-Content -LiteralPath $logPath -Value $line -Encoding UTF8
}

function Ensure-NDrive {
    if (Test-Path -LiteralPath $sdkMapped) { return }
    if (Test-Path -LiteralPath "N:\") {
        Write-Gate "Removing stale N: mapping..."
        subst N: /D | Out-Null
        Start-Sleep -Milliseconds 300
    }
    Write-Gate "Mapping N: -> $workspace"
    $null = subst N: $workspace
    if (-not (Test-Path -LiteralPath $sdkMapped)) {
        throw "Failed to map N: to workspace. Tried: subst N: `"$workspace`""
    }
}

function Show-ErrorTail {
    if (-not (Test-Path -LiteralPath $logPath)) { return }
    Write-Host ""
    Write-Host "=== compile_gate error tail (last $tailLines lines) ===" -ForegroundColor Red
    Get-Content -LiteralPath $logPath -Tail $tailLines | ForEach-Object { Write-Host $_ }
    Write-Host "=== end error tail (full log: $logPath) ===" -ForegroundColor Red
}

if (-not (Test-Path -LiteralPath $javaHome)) { throw "JAVA_HOME not found: $javaHome" }
if (-not (Test-Path -LiteralPath $gradle)) { throw "Gradle not found: $gradle" }

"" | Set-Content -LiteralPath $logPath -Encoding UTF8
Write-Gate "=== compile_gate start (MAIN assembleFullRelease) ==="
Write-Gate "Serial='$Serial' Clean=$Clean"

Ensure-NDrive

$env:JAVA_HOME = $javaHome
$env:ANDROID_SDK_ROOT = $sdkMapped
$env:ANDROID_HOME = $sdkMapped
$env:PATH = "$javaHome\bin;" + $env:PATH

$android = if (Test-Path -LiteralPath $androidMapped) { $androidMapped } else { $androidLong }

# Ensure dense-bank-safe heaps (no BOM). Leave them set - do not thrash-restore to 4g.
$propsPath = Join-Path $androidLong "gradle.properties"
if (Test-Path -LiteralPath $propsPath) {
    $text = [System.IO.File]::ReadAllText($propsPath)
    $need = $false
    if ($text -notmatch 'org\.gradle\.jvmargs=-Xmx2048m') { $need = $true }
    if ($text -notmatch 'kotlin\.daemon\.jvmargs=-Xmx2048m') { $need = $true }
    if ($need) {
        $text = [regex]::Replace($text, 'org\.gradle\.jvmargs=.*', 'org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8')
        $text = [regex]::Replace($text, 'kotlin\.daemon\.jvmargs=.*', 'kotlin.daemon.jvmargs=-Xmx2048m')
        $text = [regex]::Replace($text, 'org\.gradle\.parallel=.*', 'org.gradle.parallel=false')
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($propsPath, $text, $utf8NoBom)
        Write-Gate "Set gradle.properties heaps to 2048m/2048m (exclusive quiet / lintVital)."
        & $gradle --stop 2>&1 | ForEach-Object { Write-Gate "gradle --stop: $_" }
    }
}

$argLine = ""
if ($Clean) { $argLine += ":app:clean " }
$argLine += ":app:assembleFullRelease --no-daemon --stacktrace --max-workers=1"
Write-Gate "Running Gradle $argLine (via cmd)"

# /v:on = delayed expansion so !ERRORLEVEL! is captured after gradle finishes.
$cmd = "cd /d `"$android`" && `"$gradle`" $argLine > `"$logPath.build`" 2>&1 & echo EXITCODE=!ERRORLEVEL!>> `"$logPath.build`""
$p = Start-Process -FilePath "cmd.exe" -ArgumentList "/v:on", "/c", $cmd -PassThru -Wait -NoNewWindow
if (Test-Path -LiteralPath "$logPath.build") {
    Get-Content -LiteralPath "$logPath.build" | ForEach-Object {
        Write-Host $_
        Add-Content -LiteralPath $logPath -Value $_ -Encoding UTF8
    }
    $codeLine = Get-Content -LiteralPath "$logPath.build" | Where-Object { $_ -match '^EXITCODE=-?\d+' } | Select-Object -Last 1
    if ($codeLine -match '^EXITCODE=(-?\d+)') {
        $exitCode = [int]$Matches[1]
    } else {
        $exitCode = if ($null -ne $p.ExitCode) { $p.ExitCode } else { 1 }
        Write-Gate "WARN: EXITCODE marker missing; using process exit $exitCode"
    }
    if ($exitCode -lt 0) {
        Write-Gate "COMPILE RED - gradle/daemon died (exit $exitCode). Often RAM: stop parallel Gradle / free ~4GB then re-run."
    }
} else {
    $exitCode = if ($null -ne $p.ExitCode) { $p.ExitCode } else { 1 }
}

# Never claim green without the MAIN APK on disk.
$apkCandidates = @(
    (Join-Path $android "app\build\outputs\apk\full\release\app-full-release.apk"),
    (Join-Path $androidLong "app\build\outputs\apk\full\release\app-full-release.apk")
)
$apk = $apkCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1

if ($exitCode -ne 0) {
    Write-Gate "COMPILE RED - exit $exitCode. Guardian must fix or revert; never ship red."
    Show-ErrorTail
    exit $exitCode
}
if (-not $apk) {
    Write-Gate "COMPILE RED - gradle exit 0 but MAIN APK missing (false green guard)."
    Show-ErrorTail
    exit 1
}

Write-Gate "COMPILE GREEN - MAIN assembleFullRelease OK"
Write-Gate "APK: $apk"
Write-Host "INTEGRATOR TIP: Reuse this green assemble for publish/deploy; avoid a thrash rebuild unless sources changed. Prefer Tools\compile_gate.ps1 then publish-main-release.ps1 (or -SkipBuild if APK reused)."

if (-not [string]::IsNullOrWhiteSpace($Serial)) {
    Write-Gate "Deploying MAIN to $Serial after green gate..."
    $deploy = Join-Path $PSScriptRoot "deploy_live_edit.ps1"
    & powershell -NoProfile -ExecutionPolicy Bypass -File $deploy -Serial $Serial -Main
    if ($LASTEXITCODE -ne 0) {
        Write-Gate "Deploy failed after green assemble (exit $LASTEXITCODE)."
        exit $LASTEXITCODE
    }
    Write-Gate "Deploy OK on $Serial"
}

Write-Gate "=== compile_gate done ==="
exit 0
