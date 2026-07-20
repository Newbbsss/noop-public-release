<#
.SYNOPSIS
  Single-writer lock for android/ source edits (and aggregator ship).

.EXAMPLE
  .\Tools\source_write_lock.ps1 -Status
  .\Tools\source_write_lock.ps1 -Acquire -Role source -AgentId tick17 -Lane "Coach lacquer"
  .\Tools\source_write_lock.ps1 -Release -AgentId tick17
  .\Tools\source_write_lock.ps1 -Acquire -Role aggregator -AgentId ship-d038 -Lane "20:45Z ship"
#>
param(
  [switch]$Status,
  [switch]$Acquire,
  [switch]$Release,
  [switch]$Renew,
  [switch]$ForceClear,
  [ValidateSet('source', 'aggregator')]
  [string]$Role = 'source',
  [string]$AgentId = '',
  [string]$Lane = '',
  [string[]]$FilesHint = @(),
  [int]$TtlMinutes = 0
)

$ErrorActionPreference = 'Stop'
# Tools lives under noop-v8.4.0-src/Tools
$Repo = Split-Path $PSScriptRoot -Parent
$LockPath = Join-Path $Repo 'docs\agent\SOURCE_WRITE_LOCK.json'

function Read-Lock {
  if (-not (Test-Path $LockPath)) {
    return [pscustomobject]@{
      holder = 'none'; agentId = ''; lane = ''; filesHint = @()
      acquiredAt = ''; expiresAt = ''; note = ''
    }
  }
  return (Get-Content -Raw $LockPath | ConvertFrom-Json)
}

function Write-Lock($obj) {
  $json = $obj | ConvertTo-Json -Depth 5
  Set-Content -Path $LockPath -Value $json -Encoding UTF8
}

function Test-Expired($lock) {
  if (-not $lock.expiresAt) { return $true }
  try {
    $exp = [datetime]::Parse([string]$lock.expiresAt, $null, [System.Globalization.DateTimeStyles]::RoundtripKind)
    return ([datetime]::UtcNow -gt $exp.ToUniversalTime())
  } catch { return $true }
}

$lock = Read-Lock

if ($ForceClear) {
  Write-Lock ([pscustomobject]@{
    holder = 'none'; agentId = ''; lane = ''; filesHint = @()
    acquiredAt = ''; expiresAt = ''; note = 'Force-cleared'
  })
  Write-Output 'LOCK_CLEARED'
  exit 0
}

if ($Status -or (-not $Acquire -and -not $Release -and -not $Renew)) {
  $expired = Test-Expired $lock
  Write-Output ("HOLDER={0} AGENT={1} LANE={2} EXPIRED={3} EXPIRES={4}" -f $lock.holder, $lock.agentId, $lock.lane, $expired, $lock.expiresAt)
  if ($lock.holder -ne 'none' -and -not $expired) { exit 2 }
  exit 0
}

if ($Release) {
  if (-not $AgentId) { throw '-AgentId required for -Release' }
  if ($lock.holder -eq 'none') { Write-Output 'LOCK_ALREADY_FREE'; exit 0 }
  if ($lock.agentId -and $lock.agentId -ne $AgentId) {
    Write-Output ("LOCK_HELD_BY_OTHER agent={0} lane={1}" -f $lock.agentId, $lock.lane)
    exit 3
  }
  Write-Lock ([pscustomobject]@{
    holder = 'none'; agentId = ''; lane = ''; filesHint = @()
    acquiredAt = ''; expiresAt = ''; note = "Released by $AgentId"
  })
  Write-Output 'LOCK_RELEASED'
  exit 0
}

if ($Renew) {
  if (-not $AgentId) { throw '-AgentId required for -Renew' }
  if ($lock.holder -eq 'none' -or $lock.agentId -ne $AgentId) {
    Write-Output 'LOCK_RENEW_DENIED'
    exit 4
  }
  $ttl = if ($TtlMinutes -gt 0) { $TtlMinutes } elseif ($lock.holder -eq 'aggregator') { 90 } else { 45 }
  $lock.expiresAt = ([datetime]::UtcNow.AddMinutes($ttl)).ToString('o')
  Write-Lock $lock
  Write-Output ("LOCK_RENEWED until={0}" -f $lock.expiresAt)
  exit 0
}

if ($Acquire) {
  if (-not $AgentId) { throw '-AgentId required for -Acquire' }
  $expired = Test-Expired $lock
  $held = ($lock.holder -ne 'none' -and -not $expired)

  if ($held) {
    # Same agent renews
    if ($lock.agentId -eq $AgentId) {
      $ttl = if ($TtlMinutes -gt 0) { $TtlMinutes } elseif ($Role -eq 'aggregator') { 90 } else { 45 }
      $lock.expiresAt = ([datetime]::UtcNow.AddMinutes($ttl)).ToString('o')
      $lock.lane = if ($Lane) { $Lane } else { $lock.lane }
      $lock.holder = $Role
      Write-Lock $lock
      Write-Output ("LOCK_RENEWED holder={0} until={1}" -f $Role, $lock.expiresAt)
      exit 0
    }
    # Aggregator may take over expired-or-source only when source expired; never steal live aggregator
    if ($Role -eq 'aggregator' -and $lock.holder -eq 'source') {
      Write-Output ("LOCK_HELD_SOURCE agent={0} lane={1} -- specialist must Release first (or wait expiry)" -f $lock.agentId, $lock.lane)
      exit 2
    }
    Write-Output ("LOCK_HELD holder={0} agent={1} lane={2} expires={3}" -f $lock.holder, $lock.agentId, $lock.lane, $lock.expiresAt)
    exit 2
  }

  $ttl = if ($TtlMinutes -gt 0) { $TtlMinutes } elseif ($Role -eq 'aggregator') { 90 } else { 45 }
  $now = [datetime]::UtcNow
  Write-Lock ([pscustomobject]@{
    holder = $Role
    agentId = $AgentId
    lane = $Lane
    filesHint = @($FilesHint)
    acquiredAt = $now.ToString('o')
    expiresAt = $now.AddMinutes($ttl).ToString('o')
    note = ''
  })
  Write-Output ("LOCK_ACQUIRED role={0} agent={1} until={2}" -f $Role, $AgentId, $now.AddMinutes($ttl).ToString('o'))
  exit 0
}
