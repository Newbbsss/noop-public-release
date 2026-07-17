# build-ipa.ps1 - Windows stub. IPA packaging needs macOS + Xcode.
#
# Artifact (when built on Mac): dist/NOOP-v{VER}-ios.ipa
# Mac:   Tools/build-ipa.sh [version]
# CI:    .github/workflows/fork-release.yml ios job -> NOOP-ios-unsigned-v*.ipa
param(
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"
Write-Host "IPA build blocked on Windows - needs macOS + Xcode (xcodebuild)." -ForegroundColor Yellow
Write-Host ""
Write-Host "On a Mac (repo root):"
Write-Host "  brew install xcodegen"
if ($Version) {
    Write-Host "  Tools/build-ipa.sh $Version"
} else {
    Write-Host "  Tools/build-ipa.sh"
}
Write-Host ""
Write-Host "Or GitHub Actions (fork):"
Write-Host "  gh workflow run ios-ipa.yml -f version=8.6.151-fable -f release_tag=v8.6.151-fable --repo Newbbsss/noop-public-release"
Write-Host "  (or: gh workflow run fork-release.yml -f bump=none -f version=8.6.151-fable â€” rebuilds all platforms)"
Write-Host ""
Write-Host "Expected artifact:"
Write-Host "  dist/NOOP-vVER-ios.ipa"
Write-Host "  (CI name: NOOP-ios-unsigned-vVER.ipa on the release)"
exit 2
