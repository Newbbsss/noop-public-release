$log = 'N:\noop-v8.4.0-src\android\compile_gate.log'
$deadline = (Get-Date).AddMinutes(35)
while ((Get-Date) -lt $deadline) {
  if (Test-Path $log) {
    $tail = Get-Content $log -Tail 15
    $joined = $tail -join "`n"
    if ($joined -match 'COMPILE GREEN|COMPILE RED|Live APK updated|versionName=') {
      Write-Host $joined
      if ($joined -match 'COMPILE RED') { exit 1 }
      if ($joined -match 'COMPILE GREEN|Live APK updated|versionName=') { exit 0 }
    }
  }
  Start-Sleep -Seconds 25
}
Write-Host 'TIMEOUT'
if (Test-Path $log) { Get-Content $log -Tail 40 }
exit 1
