$ErrorActionPreference = "Continue"
Set-Location "C:\Users\Gilbert\Documents\Ai app store\noop-v8.4.0-src\android"
$log = "C:\Users\Gilbert\Documents\Ai app store\noop-v8.4.0-src\android\publish-138-console.log"
"START $(Get-Date -Format o)" | Set-Content $log -Encoding UTF8
try {
  & ".\publish-main-release.ps1" -VersionName "8.6.138-fable" -VersionCode 408 -ForceUpdate *>> $log
  "EXIT=$LASTEXITCODE $(Get-Date -Format o)" | Add-Content $log -Encoding UTF8
} catch {
  "THROW: $($_.Exception.Message)" | Add-Content $log -Encoding UTF8
  "EXIT=1 $(Get-Date -Format o)" | Add-Content $log -Encoding UTF8
  exit 1
}
