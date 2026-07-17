param(
    [string]$VersionName = "8.5.3-mg",
    [int]$VersionCode = 0,
    [switch]$RefreshDependencies,
    [switch]$ForceUpdate,
    [switch]$SkipServerStart,
    [switch]$SkipBuild,
    [switch]$SkipCatalogPr
)

$ErrorActionPreference = "Stop"

$workspace = "C:\Users\Gilbert\Documents\Ai app store"
$store = "C:\Users\Gilbert\ai-appstore"
$logPath = Join-Path $workspace "noop-v8.4.0-src\android\publish-main-release.log"
$gradle = "C:\Users\Gilbert\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat"
$javaHome = "C:\Users\Gilbert\.jdks\jbr-17.0.14"
$sdkMapped = "N:\android-sdk-local"
$androidMapped = "N:\noop-v8.4.0-src\android"
$androidLong = Join-Path $workspace "noop-v8.4.0-src\android"

function Write-Log([string]$Message) {
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
    Write-Host $line
    Add-Content -LiteralPath $logPath -Value $line -Encoding UTF8
}

function Ensure-NDrive {
    if (Test-Path -LiteralPath $sdkMapped) {
        Write-Log "N: drive already mapped to workspace SDK."
        return
    }

    # If N: exists but is wrong/broken, drop it and remap.
    if (Test-Path -LiteralPath "N:\") {
        Write-Log "Removing stale N: mapping..."
        subst N: /D | Out-Null
        Start-Sleep -Milliseconds 300
    }

    Write-Log "Mapping N: -> $workspace"
    $null = subst N: $workspace
    if (-not (Test-Path -LiteralPath $sdkMapped)) {
        throw "Failed to map N: to workspace. Build paths with spaces will fail. Tried: subst N: `"$workspace`""
    }
}

function Test-StoreUrl([string]$Url) {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
        return ($r.StatusCode -ge 200 -and $r.StatusCode -lt 300)
    } catch {
        return $false
    }
}

function Start-StoreServer {
    if (Test-StoreUrl "http://127.0.0.1:8090/apps.json") {
        Write-Log "AI Store server already running."
        return
    }

    $pythonw = "C:\Python314\pythonw.exe"
    $python = if (Test-Path -LiteralPath $pythonw) { $pythonw } else { "python" }
    Write-Log "Starting AI Store server with $python ..."
    Start-Process -FilePath $python -ArgumentList "server.py" -WorkingDirectory $store -WindowStyle Hidden
    Start-Sleep -Seconds 2

    if (-not (Test-StoreUrl "http://127.0.0.1:8090/apps.json")) {
        throw "AI Store server did not start on http://127.0.0.1:8090/apps.json"
    }
}

function Invoke-NoopGradle([switch]$WithRefresh) {
    $gradleArgs = @(":app:assembleFullRelease", "--no-daemon", "--no-build-cache", "--stacktrace", "--max-workers=1", "-Dorg.gradle.jvmargs=-Xmx2048m")
    if ($WithRefresh) { $gradleArgs += "--refresh-dependencies" }
    Write-Log "Running Gradle $($gradleArgs -join ' ')"
    # Gradle writes "e: ..." compile errors to stderr; with $ErrorActionPreference=Stop,
    # piping NativeCommandError through ForEach-Object aborts the script mid-build.
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $gradle @gradleArgs 2>&1 | ForEach-Object {
            $text = "$_"
            Write-Host $text
            Add-Content -LiteralPath $logPath -Value $text -Encoding UTF8
        }
        $script:NoopGradleExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevEap
    }
}

# Fresh log for this run
"" | Set-Content -LiteralPath $logPath -Encoding UTF8
Write-Log "=== publish-main-release start ==="
Write-Log "VersionName=$VersionName VersionCode=$VersionCode ForceUpdate=$ForceUpdate RefreshDependencies=$RefreshDependencies SkipBuild=$SkipBuild SkipCatalogPr=$SkipCatalogPr"

if (-not (Test-Path -LiteralPath $javaHome)) {
    throw "JAVA_HOME not found: $javaHome"
}
if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Gradle not found: $gradle"
}
if (-not (Test-Path -LiteralPath $store)) {
    throw "AI Store folder not found: $store"
}

Ensure-NDrive

$env:JAVA_HOME = $javaHome
$env:ANDROID_SDK_ROOT = $sdkMapped
$env:ANDROID_HOME = $sdkMapped
$env:PATH = "$javaHome\bin;" + $env:PATH

$android = if (Test-Path -LiteralPath $androidMapped) { $androidMapped } else { $androidLong }
$manifestPath = Join-Path $store "apps.json"
if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "Store manifest missing: $manifestPath"
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$noop = $manifest.apps | Where-Object { $_.id -eq "com.noop.whoop" } | Select-Object -First 1
if ($null -eq $noop) { throw "Could not find com.noop.whoop in $manifestPath" }

$storeCode = [int]$noop.versionCode
Write-Log "Current store listing: $($noop.versionName) ($storeCode) apk=$($noop.apk)"

# Always publish a higher versionCode than the store so the phone sees an update.
if ($ForceUpdate -or $VersionCode -le 0) {
    $nextCode = $storeCode + 1
    if ($VersionCode -gt $nextCode) { $nextCode = $VersionCode }
    $VersionCode = $nextCode
}
if ($VersionCode -le $storeCode) {
    throw "versionCode $VersionCode must be greater than current store versionCode $storeCode. Pass -ForceUpdate or a higher -VersionCode."
}

$apkName = "NOOP-v$VersionName-main.apk"
$apkNameVersioned = "NOOP-v$VersionName-$VersionCode-main.apk"
$buildFile = Join-Path $android "app\build.gradle.kts"
if (-not (Test-Path -LiteralPath $buildFile)) {
    throw "build.gradle.kts not found at $buildFile"
}

# Line-scoped version bump only â€” never whole-file [^"]+ replace (mojibake comments can
# explode a greedy quote match into multi-MB corruption).
$buildLines = [System.Collections.Generic.List[string]]::new()
$buildLines.AddRange([string[]](Get-Content -LiteralPath $buildFile))
if ($buildLines.Count -gt 400 -or ((Get-Item -LiteralPath $buildFile).Length -gt 200KB)) {
    throw "build.gradle.kts looks corrupted (lines=$($buildLines.Count) bytes=$((Get-Item -LiteralPath $buildFile).Length)). Restore before publishing."
}
$vcHits = 0; $vnHits = 0
for ($i = 0; $i -lt $buildLines.Count; $i++) {
    $line = $buildLines[$i]
    if ($line -match '^\s*versionCode\s*=') {
        $buildLines[$i] = ($line -replace 'versionCode\s*=\s*\d+', "versionCode = $VersionCode")
        $vcHits++
        if ($vcHits -eq 1) { continue }
    }
    if ($line -match '^\s*versionName\s*=\s*"') {
        $buildLines[$i] = ($line -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$VersionName`"")
        $vnHits++
        if ($vnHits -eq 1) { continue }
    }
}
if ($vcHits -lt 1 -or $vnHits -lt 1) {
    throw "Failed to patch versionCode/versionName in build.gradle.kts (vc=$vcHits vn=$vnHits)."
}
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($buildFile, $buildLines.ToArray(), $utf8NoBom)
Write-Log "Build version set to $VersionName ($VersionCode)."

$src = Join-Path $android "app\build\outputs\apk\full\release\app-full-release.apk"
$meta = Join-Path $android "app\build\outputs\apk\full\release\output-metadata.json"

if (-not $SkipBuild) {
    Set-Location -LiteralPath $android
    $script:NoopGradleExitCode = 0
    Invoke-NoopGradle -WithRefresh:$RefreshDependencies
    $exit = $script:NoopGradleExitCode
    if ($exit -ne 0 -and -not $RefreshDependencies) {
        Write-Log "Gradle failed (exit $exit). Retrying once with --refresh-dependencies..."
        Invoke-NoopGradle -WithRefresh
        $exit = $script:NoopGradleExitCode
    }
    if ($exit -ne 0) {
        throw "Gradle build failed with exit code $exit. See log: $logPath"
    }
}

if (-not (Test-Path -LiteralPath $src)) {
    throw "Built APK not found at $src"
}

# Verify the APK metadata matches the intended version before publishing.
if (Test-Path -LiteralPath $meta) {
    $out = Get-Content -LiteralPath $meta -Raw | ConvertFrom-Json
    $builtCode = [int]$out.elements[0].versionCode
    $builtName = [string]$out.elements[0].versionName
    Write-Log "Built APK metadata: $builtName ($builtCode)"
    if ($builtCode -ne $VersionCode -or $builtName -ne $VersionName) {
        throw "Built APK version mismatch. Expected $VersionName ($VersionCode), got $builtName ($builtCode). Re-run without -SkipBuild."
    }
}

$apksDir = Join-Path $store "apks"
if (-not (Test-Path -LiteralPath $apksDir)) {
    New-Item -ItemType Directory -Path $apksDir | Out-Null
}

$dst = Join-Path $apksDir $apkName
$dstVersioned = Join-Path $apksDir $apkNameVersioned
Copy-Item -LiteralPath $src -Destination $dst -Force
Copy-Item -LiteralPath $src -Destination $dstVersioned -Force
Write-Log "Copied APK -> $dst"
Write-Log "Copied APK -> $dstVersioned"
# Enforce fork-debug signing (device cert SHA-256 4511d903...). Wrong-signed store APKs break OTA.
$expectedCertSha256 = "4511d9037513f582e52a82ac03d8f928655d29e86a21245dce59a133a2fee3ab"
$forkKeystore = Join-Path $androidLong "fork-debug.keystore"
if (-not (Test-Path -LiteralPath $forkKeystore)) {
    $forkKeystore = Join-Path $android "fork-debug.keystore"
}
function Get-ApkCertSha256([string]$ApkPath) {
    $apksignerBat = Join-Path $env:ANDROID_SDK_ROOT "build-tools\36.1.0\apksigner.bat"
    if (-not (Test-Path -LiteralPath $apksignerBat)) {
        $apksignerBat = Get-ChildItem -Path (Join-Path $env:ANDROID_SDK_ROOT "build-tools") -Recurse -Filter "apksigner.bat" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $apksignerBat) { throw "apksigner.bat not found under ANDROID_SDK_ROOT=$env:ANDROID_SDK_ROOT" }
    $out = & $apksignerBat verify --print-certs $ApkPath 2>&1 | Out-String
    if ($out -match "SHA-256 digest:\s*([0-9a-fA-F]{64})") { return $Matches[1].ToLowerInvariant() }
    throw "Could not parse APK cert from apksigner output for $ApkPath`n$out"
}
function Ensure-ForkDebugSigned([string]$ApkPath) {
    $sha = Get-ApkCertSha256 $ApkPath
    Write-Log "APK cert SHA-256: $sha ($ApkPath)"
    if ($sha -eq $expectedCertSha256) { return }
    Write-Log "WARN: wrong signing cert ($sha). Re-signing with fork-debug.keystore..."
    if (-not (Test-Path -LiteralPath $forkKeystore)) { throw "fork-debug.keystore missing at $forkKeystore" }
    $apksignerBat = Join-Path $env:ANDROID_SDK_ROOT "build-tools\36.1.0\apksigner.bat"
    if (-not (Test-Path -LiteralPath $apksignerBat)) {
        $apksignerBat = Get-ChildItem -Path (Join-Path $env:ANDROID_SDK_ROOT "build-tools") -Recurse -Filter "apksigner.bat" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
    $zipalign = Join-Path (Split-Path $apksignerBat) "zipalign.exe"
    $work = Join-Path $env:TEMP ("noop-resign-" + [guid]::NewGuid().ToString("n") + ".apk")
    $aligned = Join-Path $env:TEMP ("noop-align-" + [guid]::NewGuid().ToString("n") + ".apk")
    $signed = Join-Path $env:TEMP ("noop-signed-" + [guid]::NewGuid().ToString("n") + ".apk")
    try {
        Copy-Item -LiteralPath $ApkPath -Destination $work -Force
        # Strip existing signatures then re-sign (apksigner sign --overwrite after zipalign).
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [System.IO.Compression.ZipFile]::Open($work, [System.IO.Compression.ZipArchiveMode]::Update)
        $toRemove = @($zip.Entries | Where-Object { $_.FullName -like "META-INF/*" -and ($_.FullName -match '\.(SF|RSA|DSA|EC)$' -or $_.FullName -eq "META-INF/MANIFEST.MF") })
        foreach ($e in $toRemove) { $e.Delete() }
        $zip.Dispose()
        if (Test-Path -LiteralPath $aligned) { Remove-Item -LiteralPath $aligned -Force }
        & $zipalign -f -p 4 $work $aligned
        if ($LASTEXITCODE -ne 0) { throw "zipalign failed ($LASTEXITCODE)" }
        & $apksignerBat sign --ks $forkKeystore --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out $signed $aligned
        if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed ($LASTEXITCODE)" }
        Copy-Item -LiteralPath $signed -Destination $ApkPath -Force
        $sha2 = Get-ApkCertSha256 $ApkPath
        Write-Log "Re-signed APK cert SHA-256: $sha2"
        if ($sha2 -ne $expectedCertSha256) {
            throw "Re-sign produced unexpected cert $sha2 (expected $expectedCertSha256)"
        }
    } finally {
        Remove-Item -LiteralPath $work,$aligned,$signed -Force -ErrorAction SilentlyContinue
    }
}
Ensure-ForkDebugSigned $dst
Ensure-ForkDebugSigned $dstVersioned

# Never rewrite long marketing fields from a dirty in-memory object â€” that once blew
# apps.json into a multi-MB mojibake tagline and broke the GitHub Releases catalog.
$safeTagline = "WHOOP 3/4/5/MG health hub with fixed alarms and MG buzz"
$safeDescription = "Your fork of NOOP: pairs with WHOOP straps over Bluetooth, keeps WHOOP MG live HR stable, supports WHOOP 3/4/MG strap buzz, and receives Apple Watch pushes over Tailscale without seeded demo data."
$safeChangelog = @"
Update ${VersionName} (${VersionCode}): Sleep today/yesterday P0 — barren HC mono-light stagesJSON no longer kills recent charts (usableTimedStages / isBarrenTimedStages). Bug report in More/Settings — screenshots + diagnostics zip → GitHub user-bug (no Tailscale). Live Session lead icon matches Health heart slot (TrackChanges + todaySiblingLeadIconDp). Dense bank merge: vessel fill/Rest captions · compare purpose · exact-alarm Sleep context. Agents: check user-bug issues every few wakes. iOS parity after Android solid. P0 buzz hardware + Mac IPA still open.
"@.Trim()
$existingTagline = [string]$noop.tagline
$existingDescription = [string]$noop.description
if ($existingTagline.Length -gt 0 -and $existingTagline.Length -le 200 -and $existingTagline -notmatch '[\uFFFD]') {
    $safeTagline = $existingTagline
}
if ($existingDescription.Length -gt 0 -and $existingDescription.Length -le 2000 -and $existingDescription -notmatch '[\uFFFD]') {
    $safeDescription = $existingDescription
}
if ($safeChangelog.Length -gt 2000) {
    throw "Changelog too long ($($safeChangelog.Length) chars). Cap at 2000 before publishing."
}

# Stable GitHub Releases URL — AI Store prefers apk_github so Fold does not need Tailscale for APKs.
$githubRepo = "Newbbsss/noop-public-release"
$githubTag = "v$VersionName"
$apkGithubUrl = "https://github.com/$githubRepo/releases/download/$githubTag/$apkName"

$noop.versionName = $VersionName
$noop.versionCode = $VersionCode
$noop.apk = "apks/$apkName"
$noop | Add-Member -NotePropertyName apk_lan -NotePropertyValue "apks/$apkName" -Force
$noop | Add-Member -NotePropertyName apk_github -NotePropertyValue $apkGithubUrl -Force
$noop | Add-Member -NotePropertyName release_repo -NotePropertyValue $githubRepo -Force
$noop.name = "NOOP Health Hub"
$noop.icon = "N"
$noop.tagline = $safeTagline
$noop.description = $safeDescription
$noop.changelog = $safeChangelog

# Keep the main NOOP entry first, drop demo listings, leave other apps alone.
$others = @($manifest.apps | Where-Object {
    $_.id -ne "com.noop.whoop" -and $_.id -ne "com.noop.whoop.demo" -and $_.id -ne "com.noop.whoop.demo.debug"
})
$manifest.apps = @($noop) + $others

$text = $manifest | ConvertTo-Json -Depth 10 -Compress:$false
if ($text.Length -gt 200KB) {
    throw "Refusing to write apps.json: serialized size $($text.Length) bytes looks corrupted (cap 200KB)."
}
if ($text -match '"tagline"\s*:\s*"[^"]{500,}') {
    throw "Refusing to write apps.json: tagline exceeds 500 chars (mojibake guard)."
}
[System.IO.File]::WriteAllText($manifestPath, $text, $utf8NoBom)
Write-Log "Updated manifest $manifestPath (taglineLen=$($safeTagline.Length) descLen=$($safeDescription.Length) changelogLen=$($safeChangelog.Length))"
Write-Log "apk_github=$apkGithubUrl"

# Attach APK to GitHub Release so AI Store / in-app update can download without Tailscale.
function Publish-GithubReleaseApk {
    param(
        [string]$Repo,
        [string]$Tag,
        [string]$VersionName,
        [int]$VersionCode,
        [string]$ApkPath,
        [string]$Changelog
    )
    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if (-not $gh) {
        Write-Log "WARN: gh CLI not found — skipped GitHub Release upload. Set apk_github manually after uploading $ApkPath"
        return $false
    }
    $title = "NOOP $VersionName ($VersionCode) MAIN"
    $notesFile = Join-Path $env:TEMP ("noop-release-notes-" + [guid]::NewGuid().ToString("n") + ".md")
    try {
        @(
            "## NOOP $VersionName ($VersionCode) MAIN"
            ""
            $Changelog
            ""
            "**APK:** ``$(Split-Path $ApkPath -Leaf)`` (fork-debug signed)"
            "**Catalog field:** ``apk_github`` = ``https://github.com/$Repo/releases/download/$Tag/$(Split-Path $ApkPath -Leaf)``"
        ) -join "`n" | Set-Content -LiteralPath $notesFile -Encoding UTF8

        # Native gh stderr ("release not found") must not trip $ErrorActionPreference=Stop.
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $null = & gh release view $Tag --repo $Repo 2>&1
            $viewOk = ($LASTEXITCODE -eq 0)
        } finally {
            $ErrorActionPreference = $prevEap
        }
        if ($viewOk) {
            Write-Log "GitHub release $Tag exists — uploading APK (clobber)..."
            $ErrorActionPreference = "Continue"
            try {
                & gh release upload $Tag $ApkPath --repo $Repo --clobber 2>&1 | ForEach-Object { Write-Log "$_" }
                if ($LASTEXITCODE -ne 0) { throw "gh release upload failed ($LASTEXITCODE)" }
            } finally {
                $ErrorActionPreference = $prevEap
            }
        } else {
            Write-Log "Creating GitHub release $Tag ..."
            $ErrorActionPreference = "Continue"
            try {
                & gh release create $Tag $ApkPath --repo $Repo --title $title --notes-file $notesFile 2>&1 | ForEach-Object { Write-Log "$_" }
                if ($LASTEXITCODE -ne 0) { throw "gh release create failed ($LASTEXITCODE)" }
            } finally {
                $ErrorActionPreference = $prevEap
            }
        }
        Write-Log "GitHub Release OK: https://github.com/$Repo/releases/tag/$Tag"
        return $true
    } finally {
        Remove-Item -LiteralPath $notesFile -Force -ErrorAction SilentlyContinue
    }
}

$githubOk = $false
try {
    $githubOk = Publish-GithubReleaseApk -Repo $githubRepo -Tag $githubTag -VersionName $VersionName -VersionCode $VersionCode -ApkPath $dst -Changelog $safeChangelog
} catch {
    Write-Log "WARN: GitHub Release upload failed: $_"
    Write-Log "Local store still published. Fix gh auth / retry upload, or leave apk_github pointing at a missing asset."
}

# Sync personal GitHub apps catalog via PR (Newbbsss/noop-public-release). Additive — never blocks local publish.
$catalogPrUrl = ""
if (-not $SkipCatalogPr) {
    $catalogPrScript = Join-Path $workspace "Tools\publish-catalog-pr.ps1"
    if (-not (Test-Path -LiteralPath $catalogPrScript)) {
        Write-Log "WARN: catalog PR script missing: $catalogPrScript"
    } else {
        try {
            Write-Log "Opening catalog PR against Newbbsss/noop-public-release ..."
            $prevEapCatalog = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            $catalogOut = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $catalogPrScript -ManifestPath $manifestPath 2>&1
            $catalogExit = $LASTEXITCODE
            $ErrorActionPreference = $prevEapCatalog
            foreach ($line in @($catalogOut)) { Write-Log "catalog-pr: $line" }
            if ($catalogExit -eq 0) {
                $catalogPrUrl = (@($catalogOut) | Where-Object { $_ -match '^https://github\.com/.+/pull/\d+' } | Select-Object -Last 1)
                if (-not $catalogPrUrl) {
                    $catalogPrUrl = (@($catalogOut) | Where-Object { $_ -match '^https://github\.com/' } | Select-Object -Last 1)
                }
                Write-Log "Catalog PR: $catalogPrUrl"
            } else {
                Write-Log "WARN: catalog PR script exit $catalogExit — local + Release still OK."
            }
        } catch {
            Write-Log "WARN: catalog PR failed: $_"
        }
    }
} else {
    Write-Log "SkipCatalogPr set — not opening Newbbsss/noop-public-release PR."
}

if (-not $SkipServerStart) { Start-StoreServer }

$localOk = Test-StoreUrl "http://127.0.0.1:8090/apps.json"
$tailOk = Test-StoreUrl "https://github.com/Newbbsss/noop-public-release/releases/latestapps.json"
$apkOk = Test-StoreUrl "http://127.0.0.1:8090/apks/$apkName"
Write-Log "Published $VersionName ($VersionCode)"
Write-Log "APK: $dst"
Write-Log "apk_github: $apkGithubUrl"
Write-Log "GitHub upload: $githubOk"
Write-Log "Catalog PR: $catalogPrUrl"
Write-Log "Manifest local: $localOk"
Write-Log "Manifest Tailscale: $tailOk"
Write-Log "APK local: $apkOk"
Write-Log "=== publish-main-release done ==="

if (-not $localOk) {
    throw "Publish finished but local store URL is not reachable."
}

