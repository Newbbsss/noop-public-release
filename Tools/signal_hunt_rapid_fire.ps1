# WHOOP signal-hunt / BLE RE Lane 1 (Tools-first; no Gradle; no DEBUG=MAIN).
#
# Usage:
#   Tools\signal_hunt_rapid_fire.ps1 -CatalogOnly
#   Tools\signal_hunt_rapid_fire.ps1 -Lane1 -Serial RFCX70E8RCD -Pull
#   Tools\signal_hunt_rapid_fire.ps1 -Serial RFCX70E8RCD -Pull   # legacy signal-hunt-YYYYMMDD

param(
    [string]$Serial = "RFCX70E8RCD",
    [switch]$Pull,
    [switch]$CatalogOnly,
    [switch]$Lane1,
    [int]$Seed = 20260717,
    [int]$RandomN = 160
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $Root "Tools\signal_hunt_rapid_fire.py"))) {
    $Root = $PSScriptRoot + "\.."
}
$Py = Join-Path $Root "Tools\signal_hunt_rapid_fire.py"

function Find-Python {
    foreach ($c in @("python", "py", "python3")) {
        try {
            $v = & $c --version 2>&1
            if ($LASTEXITCODE -eq 0 -or $v -match "Python") { return $c }
        } catch {}
    }
    throw "Python not found on PATH"
}

$python = Find-Python

if ($CatalogOnly) {
    & $python $Py catalog
    exit $LASTEXITCODE
}

if ($Lane1) {
    $argsList = @("lane1", "--seed", "$Seed", "--random-n", "$RandomN")
    if ($Pull) { $argsList += @("--serial", $Serial, "--pull") }
    elseif ($Serial) { $argsList += @("--serial", $Serial) }
    Write-Host "signal_hunt_rapid_fire: $($argsList -join ' ')"
    & $python $Py @argsList
    exit $LASTEXITCODE
}

$argsList = @("session")
if ($Pull) {
    $argsList += @("--serial", $Serial, "--pull")
} elseif ($Serial) {
    $argsList += @("--serial", $Serial)
}

Write-Host "signal_hunt_rapid_fire: $($argsList -join ' ')"
& $python $Py @argsList
exit $LASTEXITCODE
