<#
.SYNOPSIS
  Gate for AGENT_LOOP_TICK_science_lang10 — skip if a writer or ship is active.

  Exit 0 + print TICK_OK  → parent may spawn ONE source specialist
  Exit 2 + SKIP_*        → do not spawn a writer
#>
param(
  [string]$RequestsPath = ''
)

$ErrorActionPreference = 'Stop'
$Repo = Split-Path $PSScriptRoot -Parent
$LockScript = Join-Path $PSScriptRoot 'source_write_lock.ps1'
if (-not $RequestsPath) {
  $RequestsPath = Join-Path $Repo 'docs\agent\KIMI_SUBAGENT_REQUESTS.md'
}

& $LockScript -Status | Out-Host
$lockExit = $LASTEXITCODE
if ($lockExit -eq 2) {
  Write-Output 'SKIP_LOCK_HELD'
  exit 2
}

# Open REQUESTs without matching FULFILLED → ship in flight / pending; don't add writers
if (Test-Path $RequestsPath) {
  $text = Get-Content -Raw $RequestsPath
  $reqMatches = [regex]::Matches($text, '(?m)^## REQUEST (\S+)')
  $fulMatches = [regex]::Matches($text, '(?m)^## FULFILLED (\S+)')
  $fulfilled = @{}
  foreach ($m in $fulMatches) { $fulfilled[$m.Groups[1].Value] = $true }
  $open = @()
  foreach ($m in $reqMatches) {
    $id = $m.Groups[1].Value
    if (-not $fulfilled.ContainsKey($id)) { $open += $id }
  }
  # Allow at most 2 open REQUEST banks before pausing new lacquer writers
  # (aggregator should drain; more than 2 means writers are outrunning ship)
  if ($open.Count -ge 2) {
    Write-Output ("SKIP_SHIP_BACKLOG open={0}" -f ($open -join ','))
    exit 2
  }
}

Write-Output 'TICK_OK'
exit 0
