<#
.SYNOPSIS
  Build + publish NOOP fullDebug (com.noop.whoop.debug) aligned to MAIN version bank.

.DESCRIPTION
  Assembles :app:assembleFullDebug from current defaultConfig (same versionCode /
  versionName stem as MAIN, plus -debug suffix). Keeps debug capabilities.
  Copies APK into local AI store, uploads private GitHub Release, opens catalog PR.

.PARAMETER SkipBuild
  Reuse existing app-full-debug.apk (metadata must match gradle versions).

.PARAMETER SkipCatalogPr
  Update local apps.json + GitHub release only.

.PARAMETER RefreshDependencies
  Pass --refresh-dependencies to Gradle.
#>
param(
    [switch]$SkipBuild,
    [switch]$SkipCatalogPr,
    [switch]$RefreshDependencies
)

$ErrorActionPreference = "Stop"

$workspace = "C:\Users\Gilbert\Documents\Ai app store"
$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $projectDir
$storeDir = "C:\Users\Gilbert\ai-appstore"
$gradle = "C:\Users\Gilbert\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat"
$javaHome = "C:\Users\Gilbert\.jdks\jbr-17.0.14"
$sdkMapped = "N:\android-sdk-local"
$androidMapped = "N:\noop-v8.4.0-src\android"
$logPath = Join-Path $projectDir "publish-full-debug.log"
$githubRepo = "Newbbsss/noop-public-release"
$expectedCertSha256 = "4511d9037513f582e52a82ac03d8f928655d29e86a21245dce59a133a2fee3ab"

function Write-Log([string]$Message) {
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
    Write-Host $line
    Add-Content -LiteralPath $logPath -Value $line -Encoding UTF8
}

function Ensure-NDrive {
    if (Test-Path -LiteralPath $sdkMapped) { return }
    if (Test-Path -LiteralPath "N:\") {
        subst N: /D | Out-Null
        Start-Sleep -Milliseconds 300
    }
    $null = subst N: $workspace
    if (-not (Test-Path -LiteralPath $sdkMapped)) {
        throw "Failed to map N: to workspace"
    }
}

function Get-ApkCertSha256([string]$ApkPath) {
    $apksignerBat = Join-Path $env:ANDROID_SDK_ROOT "build-tools\36.1.0\apksigner.bat"
    if (-not (Test-Path -LiteralPath $apksignerBat)) {
        $apksignerBat = Get-ChildItem -Path (Join-Path $env:ANDROID_SDK_ROOT "build-tools") -Recurse -Filter "apksigner.bat" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $apksignerBat) { throw "apksigner.bat not found" }
    $out = & $apksignerBat verify --print-certs $ApkPath 2>&1 | Out-String
    if ($out -match "SHA-256 digest:\s*([0-9a-fA-F]{64})") { return $Matches[1].ToLowerInvariant() }
    throw "Could not parse APK cert for $ApkPath"
}

"" | Set-Content -LiteralPath $logPath -Encoding UTF8
Write-Log "=== publish-full-debug start ==="

if (-not (Test-Path -LiteralPath $javaHome)) { throw "JAVA_HOME missing: $javaHome" }
if (-not (Test-Path -LiteralPath $gradle)) { throw "Gradle missing: $gradle" }
if (-not (Test-Path -LiteralPath $storeDir)) { throw "AI Store missing: $storeDir" }

Ensure-NDrive
$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdkMapped
$env:ANDROID_SDK_ROOT = $sdkMapped
$env:PATH = "$javaHome\bin;" + $env:PATH

$android = if (Test-Path -LiteralPath $androidMapped) { $androidMapped } else { $projectDir }
$apkPath = Join-Path $projectDir "app\build\outputs\apk\full\debug\app-full-debug.apk"
$metadataPath = Join-Path $projectDir "app\build\outputs\apk\full\debug\output-metadata.json"

if (-not $SkipBuild) {
    # Private Gradle home so sibling agents' `gradle --stop` (default home) cannot kill this gate.
    $debugGradleHome = Join-Path $env:USERPROFILE ".gradle-noop-debug-gate"
    if (-not (Test-Path -LiteralPath $debugGradleHome)) {
        New-Item -ItemType Directory -Path $debugGradleHome | Out-Null
    }
    # Reuse wrapper dists from the main home (avoid re-download).
    $mainDists = Join-Path $env:USERPROFILE ".gradle\wrapper\dists"
    $privDists = Join-Path $debugGradleHome "wrapper\dists"
    if ((Test-Path -LiteralPath $mainDists) -and -not (Test-Path -LiteralPath $privDists)) {
        New-Item -ItemType Directory -Path (Split-Path $privDists) -Force | Out-Null
        cmd /c mklink /J "$privDists" "$mainDists" | Out-Null
    }
    $env:GRADLE_USER_HOME = $debugGradleHome
    Write-Log "GRADLE_USER_HOME=$debugGradleHome (isolated from sibling --stop)"

    Write-Log "assembleFullDebug..."
    Push-Location $android
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $gradleArgs = @(
            ":app:assembleFullDebug",
            "--no-daemon",
            "--no-build-cache",
            "--stacktrace",
            "--max-workers=1",
            "-g", $debugGradleHome,
            "-Dorg.gradle.jvmargs=-Xmx3072m"
        )
        if ($RefreshDependencies) { $gradleArgs += "--refresh-dependencies" }
        & $gradle @gradleArgs 2>&1 | ForEach-Object {
            $text = "$_"
            Write-Host $text
            Add-Content -LiteralPath $logPath -Value $text -Encoding UTF8
        }
        if ($LASTEXITCODE -ne 0) { throw "Gradle assembleFullDebug failed ($LASTEXITCODE). See $logPath" }
    } finally {
        $ErrorActionPreference = $prevEap
        Pop-Location
    }
}

if (-not (Test-Path -LiteralPath $apkPath)) { throw "DEBUG APK missing: $apkPath" }
if (-not (Test-Path -LiteralPath $metadataPath)) { throw "DEBUG metadata missing: $metadataPath" }

$metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
$element = $metadata.elements[0]
$versionName = [string]$element.versionName
$versionCode = [int]$element.versionCode
$applicationId = [string]$element.applicationId
if ([string]::IsNullOrWhiteSpace($applicationId)) { $applicationId = "com.noop.whoop.debug" }

Write-Log "Built $applicationId $versionName ($versionCode)"

$cert = Get-ApkCertSha256 $apkPath
Write-Log "APK cert SHA-256: $cert"
if ($cert -ne $expectedCertSha256) {
    Write-Log "WARN: unexpected signing cert (expected fork-debug $expectedCertSha256)"
}

$storeApkName = "NOOP-v$versionName.apk"
$storeApkRel = "apks/$storeApkName"
$apksDir = Join-Path $storeDir "apks"
if (-not (Test-Path -LiteralPath $apksDir)) { New-Item -ItemType Directory -Path $apksDir | Out-Null }
$storeApkPath = Join-Path $apksDir $storeApkName
Copy-Item -LiteralPath $apkPath -Destination $storeApkPath -Force
Write-Log "Copied APK -> $storeApkPath"

$githubTag = "v$versionName"
$apkGithubUrl = "https://github.com/$githubRepo/releases/download/$githubTag/$storeApkName"

$appsPath = Join-Path $storeDir "apps.json"
$json = Get-Content -LiteralPath $appsPath -Raw | ConvertFrom-Json
$json.apps = @($json.apps | Where-Object { $_.id -ne "com.noop.whoop.demo.debug" })
$app = $json.apps | Where-Object id -eq "com.noop.whoop.debug" | Select-Object -First 1
if (-not $app) {
    $app = [pscustomobject]@{
        id = "com.noop.whoop.debug"
        name = ""
        icon = "N"
        tagline = ""
        description = ""
        versionName = ""
        versionCode = 0
        apk = ""
        changelog = ""
    }
    $json.apps += $app
}

$safeTagline = "DEBUG fullDebug — Test Centre, SignalHunt 105-108 writer, RAW capture"
$safeDescription = "Side-by-side DEBUG package ($applicationId). Same feature bank as MAIN $versionName stem / code $versionCode, plus debuggable tooling: Test Centre, review pins, SignalHunt research writer (cmds 105-108 / GET_FF 128), deep-data send, RAW writers. Not for daily MAIN use."
$safeChangelog = "DEBUG $versionName ($versionCode): aligned to MAIN 8.6.204 bank. Adds adb SYNC_NOW (com.noop.debug.SYNC_NOW) for auto-gather hist nudge + Test Centre/SignalHunt. Denylist DFU/firmware intact. ACK != stream; no invented SpO2%."

$app.name = "NOOP DEBUG $versionName"
$app.icon = "N"
$app.tagline = $safeTagline
$app.description = $safeDescription
$app.versionName = $versionName
$app.versionCode = $versionCode
$app.apk = $storeApkRel
$app.changelog = $safeChangelog
$app | Add-Member -NotePropertyName apk_lan -NotePropertyValue $storeApkRel -Force
$app | Add-Member -NotePropertyName apk_github -NotePropertyValue $apkGithubUrl -Force
$app | Add-Member -NotePropertyName release_repo -NotePropertyValue $githubRepo -Force
$app | Add-Member -NotePropertyName package_id -NotePropertyValue $applicationId -Force

# Keep MAIN first; DEBUG next among noop entries; leave other apps alone.
$main = @($json.apps | Where-Object { $_.id -eq "com.noop.whoop" }) | Select-Object -First 1
$others = @($json.apps | Where-Object {
    $_.id -ne "com.noop.whoop" -and $_.id -ne "com.noop.whoop.debug" -and
    $_.id -ne "com.noop.whoop.demo" -and $_.id -ne "com.noop.whoop.demo.debug"
})
if ($main) {
    $json.apps = @($main, $app) + $others
} else {
    $json.apps = @($app) + $others
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$text = $json | ConvertTo-Json -Depth 12 -Compress:$false
if ($text.Length -gt 200KB) { throw "apps.json too large ($($text.Length))" }
[System.IO.File]::WriteAllText($appsPath, $text, $utf8NoBom)
Write-Log "Updated $appsPath"
Write-Log "apk_github=$apkGithubUrl"

# Private GitHub Release for DEBUG
$gh = Get-Command gh -ErrorAction SilentlyContinue
if ($gh) {
    $notesFile = Join-Path $env:TEMP ("noop-debug-notes-" + [guid]::NewGuid().ToString("n") + ".md")
    @(
        "## NOOP $versionName ($versionCode) DEBUG"
        ""
        $safeChangelog
        ""
        "**Package:** ``$applicationId``"
        "**APK:** ``$storeApkName`` (fork-debug signed)"
        "**Catalog:** ``apk_github`` = ``$apkGithubUrl``"
        ""
        "Keeps Test Centre / SignalHunt research writer. Do not install over MAIN."
    ) -join "`n" | Set-Content -LiteralPath $notesFile -Encoding UTF8

    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $null = & gh release view $githubTag --repo $githubRepo 2>&1
        $viewOk = ($LASTEXITCODE -eq 0)
    } finally {
        $ErrorActionPreference = $prevEap
    }
    if ($viewOk) {
        Write-Log "GitHub release $githubTag exists — uploading DEBUG APK (clobber)..."
        $ErrorActionPreference = "Continue"
        try {
            & gh release upload $githubTag $storeApkPath --repo $githubRepo --clobber 2>&1 | ForEach-Object { Write-Log "$_" }
        } finally {
            $ErrorActionPreference = $prevEap
        }
    } else {
        Write-Log "Creating private GitHub release $githubTag ..."
        $ErrorActionPreference = "Continue"
        try {
            & gh release create $githubTag $storeApkPath --repo $githubRepo --title "NOOP $versionName ($versionCode) DEBUG" --notes-file $notesFile 2>&1 |
                ForEach-Object { Write-Log "$_" }
            if ($LASTEXITCODE -ne 0) { throw "gh release create failed ($LASTEXITCODE)" }
        } finally {
            $ErrorActionPreference = $prevEap
        }
    }
    Remove-Item -LiteralPath $notesFile -Force -ErrorAction SilentlyContinue
} else {
    Write-Log "WARN: gh CLI missing — skipped GitHub Release"
}

$catalogUrl = ""
if (-not $SkipCatalogPr) {
    $catalogScript = Join-Path $workspace "Tools\publish-catalog-pr.ps1"
    if (-not (Test-Path -LiteralPath $catalogScript)) {
        throw "Catalog PR script missing: $catalogScript"
    }
    $safeVer = ($versionName -replace '[^a-zA-Z0-9._-]', '-')
    $branch = "catalog/noop-debug-$safeVer-$versionCode"
    $title = "catalog: NOOP DEBUG $versionName ($versionCode)"
    Write-Log "Opening catalog PR branch=$branch"
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $out = & powershell -NoProfile -ExecutionPolicy Bypass -File $catalogScript `
            -ManifestPath $appsPath `
            -BranchName $branch `
            -Title $title 2>&1
        $out | ForEach-Object { Write-Log "$_" }
        $catalogUrl = ($out | Where-Object { $_ -match 'https://github.com/.+/pull/\d+' } | Select-Object -Last 1)
        if (-not $catalogUrl) {
            $catalogUrl = ($out | Select-Object -Last 1)
        }
    } finally {
        $ErrorActionPreference = $prevEap
    }
}

Write-Log "Published DEBUG $versionName ($versionCode) package=$applicationId"
Write-Host "APK: $storeApkPath"
Write-Host "GitHub: $apkGithubUrl"
Write-Host "Catalog: $catalogUrl"
Write-Output @{
    versionName = $versionName
    versionCode = $versionCode
    packageId = $applicationId
    apkGithub = $apkGithubUrl
    catalog = "$catalogUrl"
}
