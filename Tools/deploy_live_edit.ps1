param(
    [string]$Serial = "emulator-5554",
    [switch]$Clean,
    # MAIN = store-line fullRelease (com.noop.whoop). Default remains DEBUG fullDebug.
    [switch]$Main
)

# Rebuild + install NOOP onto emulator/device. ASCII-only (Windows PS safe).
# Default: fullDebug (com.noop.whoop.debug) for emulator live review.
# -Main: fullRelease (com.noop.whoop) for Gilbert's daily Fold MAIN APK — never overwrite with .debug.

$ErrorActionPreference = "Stop"

$projectDir = Join-Path (Split-Path -Parent $PSScriptRoot) "android"
$workspaceDir = (Resolve-Path (Join-Path $projectDir "..\..")).Path
$gradle = "C:\Users\Gilbert\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$activity = "com.noop.IconDefault"

if ($Main) {
    $pkg = "com.noop.whoop"
    $installTask = ":app:installFullRelease"
    $tierLabel = "MAIN fullRelease"
} else {
    $pkg = "com.noop.whoop.debug"
    $installTask = ":app:installFullDebug"
    $tierLabel = "DEBUG fullDebug"
}

if (-not (Test-Path $gradle)) { throw "Gradle not found at $gradle" }
if (-not (Test-Path $adb)) { throw "adb not found at $adb" }

$devices = @(& $adb devices | Select-String "\tdevice$" | ForEach-Object { ($_ -split "\s+")[0] })
if ($devices -notcontains $Serial) {
    throw "Device $Serial not connected. Connected: $($devices -join ', '). Start Tools\start_noop_live_review.ps1 first."
}

# Refuse installing DEBUG over MAIN on a physical device when -Main was expected.
if ($Main) {
    $debugInstalled = & $adb -s $Serial shell pm path com.noop.whoop.debug 2>$null
    $mainInstalled = & $adb -s $Serial shell pm path com.noop.whoop 2>$null
    Write-Host "Pre-deploy packages on $Serial :"
    Write-Host "  MAIN  com.noop.whoop       -> $(if ($mainInstalled) { $mainInstalled.Trim() } else { '(not installed)' })"
    Write-Host "  DEBUG com.noop.whoop.debug -> $(if ($debugInstalled) { $debugInstalled.Trim() } else { '(not installed)' })"
}

if (-not (Test-Path "N:\android-sdk-local")) {
    subst N: $workspaceDir | Out-Null
}

$env:JAVA_HOME = "C:\Users\Gilbert\.jdks\jbr-17.0.14"
$env:ANDROID_HOME = "N:\android-sdk-local"
$env:ANDROID_SDK_ROOT = "N:\android-sdk-local"

Push-Location "N:\noop-v8.4.0-src\android"
try {
    $gradleArgs = @($installTask, "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx1536m", "--max-workers=1")
    if ($Clean) { $gradleArgs = @(":app:clean") + $gradleArgs }
    Write-Host "Deploying $tierLabel to $Serial via $($gradleArgs -join ' ') ..."
    Write-Host "Package: $pkg"
    & $gradle @gradleArgs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
finally {
    Pop-Location
}

& $adb -s $Serial shell am force-stop $pkg | Out-Null
& $adb -s $Serial shell am start -n "$pkg/$activity" | Out-Null
$pidOnDevice = (& $adb -s $Serial shell pidof $pkg 2>$null)

# Post-deploy confirmation
$ver = (& $adb -s $Serial shell dumpsys package $pkg 2>$null | Select-String -Pattern "versionName=")
Write-Host "Live APK updated on $Serial ($tierLabel, pid=$pidOnDevice)."
Write-Host "Verified package: $pkg  $($ver -join ' ')"
if ($pkg -like "*.debug") {
    Write-Host "NOTE: This is DEBUG (.debug). Use -Main for store-line com.noop.whoop."
} else {
    Write-Host "Confirmed MAIN (no .debug suffix)."
}
Write-Host "Preview charging: Tools\preview_charging.ps1 -Serial $Serial"
