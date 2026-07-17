package com.noop.update

import android.content.Context
import com.noop.BuildConfig
import com.noop.ui.UpdateItem
import com.noop.ui.UpdateKind
import com.noop.ui.UpdateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Quiet launch-time update probe for Gilbert's builds.
 *
 * Posts at most one inbox row per discovered version, and at most one network check per
 * [MIN_INTERVAL_MS]. Failures are silent — Settings → Check for updates remains the explicit path.
 */
object UpdateNotifier {

    private const val PREFS = "noop_update_notifier"
    private const val KEY_LAST_CHECK_MS = "lastCheckMs"
    private const val KEY_LAST_NOTIFIED_VERSION = "lastNotifiedVersion"
    private const val MIN_INTERVAL_MS = 6L * 60L * 60L * 1000L // 6 hours

    /**
     * Probe GitHub catalog / Releases for a newer [BuildConfig.APPLICATION_ID] build and, when
     * found, post an Updates-inbox notification. Never throws.
     */
    suspend fun checkAndNotify(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val last = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
            if (last > 0L && now - last < MIN_INTERVAL_MS) return@runCatching
            prefs.edit().putLong(KEY_LAST_CHECK_MS, now).apply()

            val result = UpdateCheck.check(
                currentVersion = BuildConfig.VERSION_NAME,
                currentVersionCode = BuildConfig.VERSION_CODE,
                applicationId = BuildConfig.APPLICATION_ID,
            )
            val avail = result as? UpdateCheck.Result.Available ?: return@runCatching
            val already = prefs.getString(KEY_LAST_NOTIFIED_VERSION, null)
            if (already == avail.version) return@runCatching

            val where = when (avail.source) {
                "ai-store+github", "github" -> "GitHub Releases"
                "ai-store" -> "GitHub catalog"
                else -> "update catalog"
            }
            UpdateStore.from(app).post(
                UpdateItem(
                    kind = UpdateKind.WHATS_NEW,
                    title = "Update available: ${avail.version}",
                    message = buildString {
                        append("A newer build is on $where")
                        avail.versionCode?.let { append(" (versionCode $it)") }
                        append(". Open Settings → About → Check for updates to download.")
                        if (avail.notes.isNotBlank()) {
                            append(' ')
                            append(avail.notes.take(240))
                        }
                    },
                ),
            )
            prefs.edit().putString(KEY_LAST_NOTIFIED_VERSION, avail.version).apply()
        }
        Unit
    }
}
