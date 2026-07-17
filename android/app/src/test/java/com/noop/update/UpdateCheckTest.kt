package com.noop.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins version comparison + GitHub Releases catalog parsing for Gilbert's update path.
 * Package ids: `com.noop.whoop` (MAIN) and `com.noop.whoop.debug` (DEBUG twin).
 */
class UpdateCheckTest {

    @Test
    fun newer() {
        assertTrue(UpdateCheck.isNewer("1.40", "1.39"))
        assertTrue(UpdateCheck.isNewer("1.10", "1.9"))
        assertTrue(UpdateCheck.isNewer("2.0", "1.39"))
        assertTrue(UpdateCheck.isNewer("1.39.1", "1.39"))
        assertTrue(UpdateCheck.isNewer("v1.40", "1.39"))
        assertTrue(UpdateCheck.isNewer("8.6.72-fable", "8.6.71-fable"))
    }

    @Test
    fun fableSuffixSameNumericNotNewer() {
        // Same numeric core â€” suffix alone must not look like an upgrade.
        assertFalse(UpdateCheck.isNewer("8.6.72-fable", "8.6.72-fable-debug"))
        assertFalse(UpdateCheck.isNewer("8.6.72-fable-debug", "8.6.72-fable"))
    }

    @Test
    fun notNewer() {
        assertFalse(UpdateCheck.isNewer("1.39", "1.39"))
        assertFalse(UpdateCheck.isNewer("1.38", "1.39"))
        assertFalse(UpdateCheck.isNewer("1.9", "1.10"))
        assertFalse(UpdateCheck.isNewer("1.39-demo", "1.39"))
        assertFalse(UpdateCheck.isNewer("garbage", "1.39"))
    }

    @Test
    fun storeCatalog_mainPackageNewerByVersionCode() {
        val body = """
            {"store":{"name":"AI Store","version":1},"apps":[
              {"id":"com.noop.whoop","versionName":"8.6.72-fable","versionCode":343,
               "apk":"apks/NOOP-v8.6.72-fable-main.apk","changelog":"sleep + stress tip"}
            ]}
        """.trimIndent()
        val r = UpdateCheck.parseStoreCatalog(
            body = body,
            applicationId = "com.noop.whoop",
            currentVersion = "8.6.70-fable",
            currentVersionCode = 341,
            catalogBaseUrl = "https://github.com/Newbbsss/noop-public-release/releases/latest",
        )
        assertTrue(r is UpdateCheck.Result.Available)
        val avail = r as UpdateCheck.Result.Available
        assertEquals("8.6.72-fable", avail.version)
        assertEquals(343, avail.versionCode)
        assertEquals("ai-store", avail.source)
        assertTrue(avail.url.contains("NOOP-v8.6.72-fable-main.apk"))
    }

    @Test
    fun storeCatalog_debugPackageSeparateFromMain() {
        val body = """
            {"apps":[
              {"id":"com.noop.whoop","versionName":"8.6.72-fable","versionCode":343,"apk":"apks/main.apk"},
              {"id":"com.noop.whoop.debug","versionName":"8.6.47-fable-debug","versionCode":316,
               "apk":"apks/debug.apk","changelog":"debug twin"}
            ]}
        """.trimIndent()
        val debug = UpdateCheck.parseStoreCatalog(
            body, "com.noop.whoop.debug", "8.6.40-fable-debug", 300,
            "https://github.com/Newbbsss/noop-public-release/releases/latest",
        )
        assertTrue(debug is UpdateCheck.Result.Available)
        assertEquals("8.6.47-fable-debug", (debug as UpdateCheck.Result.Available).version)

        val mainUpToDate = UpdateCheck.parseStoreCatalog(
            body, "com.noop.whoop", "8.6.72-fable", 343,
            "https://github.com/Newbbsss/noop-public-release/releases/latest",
        )
        assertTrue(mainUpToDate is UpdateCheck.Result.UpToDate)
    }

    @Test
    fun storeCatalog_missingPackageFails() {
        val body = """{"apps":[{"id":"com.other","versionName":"9.0","versionCode":1,"apk":"x.apk"}]}"""
        val r = UpdateCheck.parseStoreCatalog(
            body, "com.noop.whoop", "8.6.1", 1, "https://github.com/Newbbsss/noop-public-release/releases/latest",
        )
        assertTrue(r is UpdateCheck.Result.Failed)
    }

    @Test
    fun storeCatalog_olderStoreIsUpToDate() {
        val body = """
            {"apps":[{"id":"com.noop.whoop","versionName":"8.6.70-fable","versionCode":341,"apk":"a.apk"}]}
        """.trimIndent()
        val r = UpdateCheck.parseStoreCatalog(
            body, "com.noop.whoop", "8.6.72-fable", 343, "https://github.com/Newbbsss/noop-public-release/releases/latest",
        )
        assertTrue(r is UpdateCheck.Result.UpToDate)
    }

    @Test
    fun storeCatalog_prefersGithubReleaseUrl() {
        val gh =
            "https://github.com/Newbbsss/noop-public-release/releases/download/v8.6.75-fable/NOOP-v8.6.75-fable-main.apk"
        val body = """
            {"apps":[{"id":"com.noop.whoop","versionName":"8.6.75-fable","versionCode":345,
              "apk":"apks/local.apk","apk_github":"$gh","apk_lan":"apks/local.apk",
              "changelog":"github primary"}]}
        """.trimIndent()
        val r = UpdateCheck.parseStoreCatalog(
            body, "com.noop.whoop", "8.6.72-fable", 343, "https://github.com/Newbbsss/noop-public-release/releases/latest",
        )
        assertTrue(r is UpdateCheck.Result.Available)
        val avail = r as UpdateCheck.Result.Available
        assertEquals(gh, avail.url)
        assertEquals("ai-store+github", avail.source)
    }

    @Test
    fun storeCatalog_absoluteApkUrlPassthrough() {
        val url = "https://example.com/builds/NOOP.apk"
        val body = """
            {"apps":[{"id":"com.noop.whoop","versionName":"9.0.0","versionCode":400,"apk":"$url"}]}
        """.trimIndent()
        val r = UpdateCheck.parseStoreCatalog(
            body, "com.noop.whoop", "8.0.0", 1, "https://github.com/Newbbsss/noop-public-release/releases/latest",
        )
        assertTrue(r is UpdateCheck.Result.Available)
        assertEquals(url, (r as UpdateCheck.Result.Available).url)
    }

    @Test
    fun releasesList_parsesMainApkAndSkipsDraft() {
        val body = """
            [
              {"tag_name":"v8.6.153-fable","draft":false,"body":"versionCode: 423","published_at":"2026-07-16T00:00:00Z",
               "assets":[
                 {"name":"NOOP-v8.6.153-fable-main.apk",
                  "browser_download_url":"https://github.com/Newbbsss/noop-public-release/releases/download/v8.6.153-fable/NOOP-v8.6.153-fable-main.apk"},
                 {"name":"notes.txt","browser_download_url":"https://example.com/notes.txt"}
               ]},
              {"tag_name":"v8.6.152-fable","draft":true,"body":"versionCode: 422",
               "assets":[{"name":"NOOP-v8.6.152-fable-main.apk",
                 "browser_download_url":"https://github.com/x/y/NOOP.apk"}]},
              {"tag_name":"v8.6.150-fable","draft":false,"body":"versionCode: 420",
               "assets":[{"name":"NOOP-v8.6.150-fable-main.apk",
                 "browser_download_url":"https://github.com/Newbbsss/noop-public-release/releases/download/v8.6.150-fable/NOOP-v8.6.150-fable-main.apk"}]}
            ]
        """.trimIndent()
        val list = UpdateCheck.parseReleasesList(body, preferDebug = false)
        assertEquals(2, list.size)
        assertEquals("8.6.153-fable", list[0].version)
        assertEquals(423, list[0].versionCode)
        assertTrue(list[0].apkUrl.contains("-main.apk"))
        assertEquals("8.6.150-fable", list[1].version)
        assertEquals(420, list[1].versionCode)
    }

    @Test
    fun releasesList_prefersDebugApkForDebugTwin() {
        val body = """
            [{"tag_name":"v8.6.47-fable-debug","draft":false,"body":"versionCode: 316",
              "assets":[
                {"name":"NOOP-v8.6.47-fable-main.apk","browser_download_url":"https://ex/main.apk"},
                {"name":"NOOP-v8.6.47-fable-debug.apk","browser_download_url":"https://ex/debug.apk"}
              ]}]
        """.trimIndent()
        val list = UpdateCheck.parseReleasesList(body, preferDebug = true)
        assertEquals(1, list.size)
        assertEquals("https://ex/debug.apk", list[0].apkUrl)
    }

    @Test
    fun relation_and_downgrade() {
        assertEquals(
            UpdateCheck.VersionRelation.OLDER,
            UpdateCheck.relationToInstalled("8.6.150-fable", 420, "8.6.153-fable", 423),
        )
        assertTrue(UpdateCheck.isDowngrade("8.6.150-fable", 420, "8.6.153-fable", 423))
        assertFalse(UpdateCheck.isDowngrade("8.6.154-fable", 424, "8.6.153-fable", 423))
        assertEquals(
            UpdateCheck.VersionRelation.SAME,
            UpdateCheck.relationToInstalled("8.6.153-fable", 423, "8.6.153-fable", 423),
        )
        assertEquals(
            UpdateCheck.VersionRelation.OLDER,
            UpdateCheck.relationToInstalled("8.6.140-fable", null, "8.6.153-fable", 0),
        )
    }
}
