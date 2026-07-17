package com.noop.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * User-initiated + quiet launch "Check for updates".
 *
 * Gilbert's daily-driver builds (`com.noop.whoop` / `com.noop.whoop.debug`) update from
 * GitHub (`Newbbsss/noop-public-release` + `Newbbsss/noop-public-release` Releases). Optional LAN catalog
 * hosts remain as fallbacks for on-network installs â€” not user-facing "AI store" copy.
 * Upstream `ryanbr/noop` releases are a separate lineage and must not drive this fork's prompts.
 *
 * Sources (first hit wins):
 *  1. GitHub apps catalog (`apk_github` preferred in parse) â€” matches [applicationId]
 *  2. Optional LAN catalog hosts (same Wiâ€‘Fi / private net fallback)
 *  3. GitHub Releases latest on `Newbbsss/noop-public-release`
 *
 * Version select ([listAvailableVersions]) lists Releases APKs for install/downgrade.
 *
 * Runs only when asked (Settings tap) or once per quiet window on launch. Nothing about the user
 * is sent â€” the client only GETs a catalog or the releases JSON.
 *
 * Friends / Tailscale invite copy lives in [friendsNetworkShareText] â€” see Friends network UI.
 */
object UpdateCheck {

    /**
     * Catalog URLs. GitHub first so Fold/away updates don't depend on LAN.
     * LAN hosts stay as fallback for on-network installs (not user-facing store branding).
     */
    val STORE_CATALOGS: List<String> = listOf(
        // Public release: GitHub Releases only (no private LAN / Tailscale catalogs).
    ). Private raw needs network auth;
        // unauthenticated clients fall through to Releases latest below.
        "",
        "",
    )

    const val GITHUB_APPS_CATALOG_URL =
    /** Private fork releases (Newbbsss). Backup when catalog GETs fail. */
    const val GITHUB_RELEASES_LATEST =
        "https://api.github.com/repos/Newbbsss/noop-public-release/releases/latest"

    /**
     * GitHub Releases list endpoints for in-app version select.
     * Primary: Gilbert APK home. Optional public mirror listed second (merged, primary wins on tag).
     */
    val GITHUB_RELEASES_LIST: List<String> = listOf(
        "https://api.github.com/repos/Newbbsss/noop-public-release/releases?per_page=40",
        "https://api.github.com/repos/Newbbsss/noop/releases?per_page=20",
    )

    const val PROJECT_HOME_URL = "https://github.com/Newbbsss/noop-public-release"
    const val UPSTREAM_HOME_URL = "https://github.com/ryanbr/noop"

    /** Private-net install hosts for Friends network invite text only (not Settings updates). */
    const val FRIENDS_LAN_INSTALL_URL = "https://github.com/Newbbsss/noop-public-release/releases/latest"
    const val FRIENDS_TAILSCALE_INSTALL_URL = "https://github.com/Newbbsss/noop-public-release/releases/latest"

    /** @deprecated Use [FRIENDS_LAN_INSTALL_URL] â€” kept for older call sites. */
    const val AI_STORE_BROWSER_URL = FRIENDS_LAN_INSTALL_URL
    /** @deprecated Use [FRIENDS_TAILSCALE_INSTALL_URL] â€” Friends network only. */
    const val AI_STORE_TAILSCALE_URL = FRIENDS_TAILSCALE_INSTALL_URL

    /**
     * Upstream AltStore / SideStore source was ryanbr. Gilbert ships from the private fork â€”
     * Friends install help uses Newbbsss/noop (match Android version when IPA is published).
     */
    const val ALTSTORE_SOURCE_URL =
        "https://raw.githubusercontent.com/Newbbsss/noop/main/altstore-source.json"

    /**
     * Plain-text invite for Friends network (LAN / Tailscale private pipe).
     * Not the app-update path â€” Settings â†’ Check for updates uses GitHub.
     */
    fun friendsNetworkShareText(inviteCode: String? = null): String = buildString {
        appendLine("NOOP â€” Friends network invite (private pipe only).")
        appendLine()
        appendLine("1. Join the same home Wi-Fi â€” or a Tailscale tailnet â€” as your friend.")
        appendLine("2. Android: install from GitHub Releases:")
        appendLine("   https://github.com/Newbbsss/noop-public-release/releases/latest")
        appendLine("3. iPhone: add this AltStore / SideStore source, then install NOOP:")
        appendLine("   $ALTSTORE_SOURCE_URL")
        appendLine("   (AltStore: https://altstore.io â€” free Apple ID, re-signs every 7 days.)")
        val code = inviteCode?.trim().orEmpty()
        if (code.isNotBlank()) {
            appendLine()
            appendLine("4. In NOOP â†’ Friends network, enter invite code: $code")
        }
        appendLine()
        appendLine("Opt-in Charge / Effort day shares stay on the private pipe. No cloud accounts.")
        appendLine("App updates for you still come from GitHub â€” Friends is not the update channel.")
        appendLine("Fully offline once installed. No WHOOP cloud. Not affiliated with WHOOP.")
    }

    /** @deprecated Prefer [friendsNetworkShareText]. */
    fun friendsTailnetShareText(): String = friendsNetworkShareText()

    sealed interface Result {
        data class UpToDate(val version: String) : Result
        data class Available(
            val version: String,
            val url: String,
            val notes: String,
            val versionCode: Int? = null,
            val source: String = "unknown",
        ) : Result
        object Failed : Result
    }

    /** One installable GitHub Release APK for version select. */
    data class ReleaseVersion(
        val version: String,
        val apkUrl: String,
        val notes: String = "",
        val versionCode: Int? = null,
        val publishedAt: String? = null,
        val source: String = "github",
    )

    sealed interface ListResult {
        data class Ok(val releases: List<ReleaseVersion>) : ListResult
        object Failed : ListResult
    }

    /** Relative to the installed build: newer, same, or older (downgrade). */
    enum class VersionRelation { NEWER, SAME, OLDER }

    /**
     * Fetch the newest build for [applicationId] and classify it against the installed
     * [currentVersion] / [currentVersionCode]. Never throws.
     */
    suspend fun check(
        currentVersion: String,
        currentVersionCode: Int = 0,
        applicationId: String = "com.noop.whoop",
    ): Result = withContext(Dispatchers.IO) {
        val fromStore = checkStoreCatalogs(applicationId, currentVersion, currentVersionCode)
        if (fromStore !is Result.Failed) return@withContext fromStore
        checkGitHubRelease(currentVersion, currentVersionCode)
    }

    /**
     * List installable APK versions from GitHub Releases (apk asset URLs).
     * Prefer `-main.apk` for MAIN / `-debug.apk` for the debug twin. Never throws.
     */
    suspend fun listAvailableVersions(
        applicationId: String = "com.noop.whoop",
    ): ListResult = withContext(Dispatchers.IO) {
        val preferDebug = applicationId.endsWith(".debug")
        val seen = linkedMapOf<String, ReleaseVersion>()
        for (url in GITHUB_RELEASES_LIST) {
            val body = httpGet(url) ?: continue
            val parsed = parseReleasesList(body, preferDebug = preferDebug)
            for (r in parsed) {
                seen.putIfAbsent(r.version.lowercase(), r)
            }
        }
        if (seen.isEmpty()) ListResult.Failed else ListResult.Ok(seen.values.toList())
    }

    /** Parse GitHub Releases API array JSON. Pure + unit-tested. */
    fun parseReleasesList(body: String, preferDebug: Boolean = false): List<ReleaseVersion> {
        val root = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        val out = ArrayList<ReleaseVersion>(root.length())
        for (i in 0 until root.length()) {
            val json = root.optJSONObject(i) ?: continue
            if (json.optBoolean("draft", false)) continue
            val tag = json.optString("tag_name", "").removePrefix("v").trim()
            if (tag.isBlank()) continue
            val notes = cleanNotes(json.optString("body", ""))
            val apkUrl = pickPreferredApkAssetUrl(json.optJSONArray("assets"), preferDebug)
                ?: continue
            val versionCode = versionCodeFromTagNotes(tag, notes)
            out += ReleaseVersion(
                version = tag,
                apkUrl = apkUrl,
                notes = notes,
                versionCode = versionCode,
                publishedAt = json.optString("published_at", "").ifBlank { null },
                source = "github",
            )
        }
        return out
    }

    /**
     * Classify [candidateVersion]/[candidateCode] vs the installed build.
     * versionCode wins when both sides are known; otherwise numeric name segments.
     */
    fun relationToInstalled(
        candidateVersion: String,
        candidateCode: Int?,
        currentVersion: String,
        currentVersionCode: Int,
    ): VersionRelation {
        if (candidateCode != null && candidateCode > 0 && currentVersionCode > 0) {
            return when {
                candidateCode > currentVersionCode -> VersionRelation.NEWER
                candidateCode < currentVersionCode -> VersionRelation.OLDER
                else -> VersionRelation.SAME
            }
        }
        return when {
            isNewer(candidateVersion, currentVersion) -> VersionRelation.NEWER
            isNewer(currentVersion, candidateVersion) -> VersionRelation.OLDER
            else -> VersionRelation.SAME
        }
    }

    /** True when installing [candidate] would lower versionCode / name vs installed. */
    fun isDowngrade(
        candidateVersion: String,
        candidateCode: Int?,
        currentVersion: String,
        currentVersionCode: Int,
    ): Boolean = relationToInstalled(
        candidateVersion, candidateCode, currentVersion, currentVersionCode,
    ) == VersionRelation.OLDER

    /** Parse one apps-catalog body. Pure + unit-tested. */
    fun parseStoreCatalog(
        body: String,
        applicationId: String,
        currentVersion: String,
        currentVersionCode: Int,
        catalogBaseUrl: String,
    ): Result {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return Result.Failed
        val apps = root.optJSONArray("apps") ?: return Result.Failed
        val app = findApp(apps, applicationId) ?: return Result.Failed
        val versionName = app.optString("versionName", "").ifBlank {
            app.optString("version", "")
        }
        val versionCode = app.optInt("versionCode", 0).takeIf { it > 0 }
        val apkRel = app.optString("apk", "")
        val apkGithub = app.optString("apk_github", "")
        val apkLan = app.optString("apk_lan", "")
        val changelog = app.optString("changelog", "")
        // Prefer Newbbsss GitHub release URL when catalog publishes one; else absolute/relative apk;
        // apk_lan is private-net fallback metadata (resolved relative to catalog host).
        val url = resolvePreferredApkUrl(catalogBaseUrl, apkGithub, apkRel, apkLan)
            ?: catalogBaseUrl.trimEnd('/') + "/"
        // Internal source tokens kept for tests; Settings/UpdateNotifier map them to GitHub copy.
        val source = when {
            isAbsoluteHttp(apkGithub) ||
                (isAbsoluteHttp(apkRel) && apkRel.contains("github.com", ignoreCase = true)) ->
                "ai-store+github"
            else -> "ai-store"
        }
        if (versionName.isBlank() && versionCode == null) return Result.Failed
        val newerByCode = versionCode != null && currentVersionCode > 0 && versionCode > currentVersionCode
        val newerByName = versionName.isNotBlank() && isNewer(versionName, currentVersion)
        return when {
            newerByCode || (versionCode == null && newerByName) ||
                (currentVersionCode <= 0 && newerByName) ->
                Result.Available(
                    version = versionName.ifBlank { "code-$versionCode" },
                    url = url,
                    notes = cleanNotes(changelog),
                    versionCode = versionCode,
                    source = source,
                )
            else -> Result.UpToDate(versionName.ifBlank { currentVersion })
        }
    }

    /**
     * True iff [latest] is a strictly newer version than [current]. Compares dot-separated numeric
     * segments left to right â€” so `1.40 > 1.39` and `1.9 < 1.10`. Tolerant of a leading "v" and any
     * non-numeric suffix (e.g. `-fable`, `-debug`). Pure + unit-tested.
     */
    fun isNewer(latest: String, current: String): Boolean {
        val a = segments(latest)
        val b = segments(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Turn release / changelog body into a short inline preview. */
    fun cleanNotes(body: String): String {
        var s = body.substringBefore("Downloads")
        for (marker in listOf("**", "## ", "# ")) s = s.replace(marker, "")
        s = s.trim()
        return if (s.length > 700) s.take(700).trim() + "â€¦" else s
    }

    private fun checkStoreCatalogs(
        applicationId: String,
        currentVersion: String,
        currentVersionCode: Int,
    ): Result {
        for (catalog in STORE_CATALOGS) {
            val body = httpGet(catalog) ?: continue
            val result = parseStoreCatalog(
                body = body,
                applicationId = applicationId,
                currentVersion = currentVersion,
                currentVersionCode = currentVersionCode,
                catalogBaseUrl = catalog.removeSuffix("apps.json"),
            )
            if (result !is Result.Failed) return result
        }
        return Result.Failed
    }

    private fun checkGitHubRelease(
        currentVersion: String,
        currentVersionCode: Int,
    ): Result = runCatching {
        val body = httpGet(GITHUB_RELEASES_LATEST) ?: return Result.Failed
        val json = JSONObject(body)
        val latest = json.getString("tag_name").removePrefix("v")
        val htmlUrl = json.getString("html_url")
        val notes = cleanNotes(json.optString("body", ""))
        val assetUrl = pickApkAssetUrl(json.optJSONArray("assets")) ?: htmlUrl
        val tagCode = versionCodeFromTagNotes(latest, notes)
        val newerByCode = tagCode != null && currentVersionCode > 0 && tagCode > currentVersionCode
        val newerByName = isNewer(latest, currentVersion)
        when {
            newerByCode || newerByName ->
                Result.Available(
                    version = latest,
                    url = assetUrl,
                    notes = notes,
                    versionCode = tagCode,
                    source = "github",
                )
            else -> Result.UpToDate(latest)
        }
    }.getOrDefault(Result.Failed)

    private fun findApp(apps: JSONArray, applicationId: String): JSONObject? {
        for (i in 0 until apps.length()) {
            val o = apps.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (id == applicationId) return o
        }
        return null
    }

    private fun resolvePreferredApkUrl(
        catalogBaseUrl: String,
        apkGithub: String,
        apk: String,
        apkLan: String,
    ): String? {
        if (isAbsoluteHttp(apkGithub)) return apkGithub.trim()
        resolveApkUrl(catalogBaseUrl, apk)?.let { return it }
        return resolveApkUrl(catalogBaseUrl, apkLan)
    }

    private fun resolveApkUrl(catalogBaseUrl: String, apkRel: String): String? {
        if (apkRel.isBlank()) return null
        if (isAbsoluteHttp(apkRel)) return apkRel.trim()
        val base = catalogBaseUrl.trimEnd('/') + "/"
        return base + apkRel.trimStart('/')
    }

    private fun isAbsoluteHttp(path: String): Boolean {
        val p = path.trim()
        return p.startsWith("http://", ignoreCase = true) ||
            p.startsWith("https://", ignoreCase = true)
    }

    private fun pickApkAssetUrl(assets: JSONArray?): String? =
        pickPreferredApkAssetUrl(assets, preferDebug = false)

    /**
     * Prefer channel-matching APK: MAIN â†’ `*-main.apk`, debug twin â†’ `*-debug.apk`.
     * Falls back to any `.apk` asset. Pure helper used by latest + list parsers.
     */
    fun pickPreferredApkAssetUrl(assets: JSONArray?, preferDebug: Boolean): String? {
        if (assets == null) return null
        var fallback: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name", "")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val url = a.optString("browser_download_url", "").trim()
            if (url.isBlank()) continue
            val lower = name.lowercase()
            val isDebug = lower.contains("-debug") || lower.contains("_debug")
            when {
                preferDebug && isDebug -> return url
                !preferDebug && lower.contains("-main") -> return url
                !preferDebug && !isDebug && fallback == null -> fallback = url
                preferDebug && !isDebug && fallback == null -> fallback = url
                fallback == null -> fallback = url
            }
        }
        return fallback
    }

    /** Optional `versionCode: N` hint in release notes for fable builds. */
    fun versionCodeFromTagNotes(tag: String, notes: String): Int? {
        val fromNotes = Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(notes)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (fromNotes != null) return fromNotes
        return Regex("""-(\d{2,})(?:-|$|\s)""").find(tag)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun segments(s: String): List<Int> =
        s.trim().removePrefix("v").removePrefix("V")
            .takeWhile { it.isDigit() || it == '.' }
            .split(".")
            .mapNotNull { it.toIntOrNull() }

    private fun httpGet(url: String): String? {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json, application/vnd.github+json, */*")
                setRequestProperty("User-Agent", "NOOP-Public-UpdateCheck")
            }
            try {
                if (conn.responseCode !in 200..299) return null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
