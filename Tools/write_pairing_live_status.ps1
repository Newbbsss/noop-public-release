# Write machine-readable pairing-logs/live-status.json (SHIP #394).
# Never append prose notes into this file — agents/scripts read JSON only.
param(
    [string]$State = "idle",
    [string]$Note = "",
    [string]$Serial = "",
    [string]$ApkVersion = ""
)

$ErrorActionPreference = "Stop"
$pairing = Join-Path $PSScriptRoot "..\..\pairing-logs"
if (-not (Test-Path $pairing)) {
    $pairing = Join-Path $PSScriptRoot "..\pairing-logs"
}
if (-not (Test-Path $pairing)) {
    New-Item -ItemType Directory -Path $pairing | Out-Null
}

$path = Join-Path $pairing "live-status.json"
$payload = [ordered]@{
    schema      = 1
    updatedAt   = (Get-Date).ToUniversalTime().ToString("o")
    state       = $State
    serial      = $Serial
    apkVersion  = $ApkVersion
    note        = $Note
}
($payload | ConvertTo-Json -Depth 4) + "`n" | Set-Content -Path $path -Encoding utf8NoBOM
Write-Host "Wrote $path"
