<#
.SYNOPSIS
  Keep Fold wireless ADB (:5555) alive over Tailscale.

.DESCRIPTION
  If USB (RFCX70E8RCD) is present, re-arms tcpip 5555 (survives reboot only when
  USB is plugged once). Always tries adb connect to Tailscale. Registered as
  scheduled task NOOP-Fold-ADB-KeepAlive every 30 min so overnight SpO2 gather
  does not die after wireless ADB drops.
#>
$ErrorActionPreference = "Continue"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$UsbSerial = "RFCX70E8RCD"
$TailscaleHost = "100.91.234.88"
$Port = 5555
$target = "${TailscaleHost}:${Port}"

$devs = & $adb devices 2>$null | Out-String
if ($devs -match "$UsbSerial\s+device") {
    & $adb -s $UsbSerial tcpip $Port 2>$null | Out-Null
    Start-Sleep -Seconds 2
}
& $adb connect $target 2>$null | Out-Null
