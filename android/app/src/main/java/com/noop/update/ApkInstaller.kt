package com.noop.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Download a GitHub Release APK into cache and hand it to the system package installer.
 * Prefer [UpdateCheck] GitHub `apk_github` / release asset URLs â€” not LAN/Tailscale.
 */
object ApkInstaller {

    private const val CACHE_DIR = "updates"

    sealed interface DownloadResult {
        data class Ready(val file: File) : DownloadResult
        data class Failed(val reason: String) : DownloadResult
    }

    fun needsUnknownSourcesPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return !context.packageManager.canRequestPackageInstalls()
    }

    fun openUnknownSourcesSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Stream [apkUrl] into `cache/updates/`. [onProgress] is 0fâ€¦1f when Content-Length is known.
     */
    suspend fun download(
        context: Context,
        apkUrl: String,
        versionLabel: String,
        onProgress: (Float) -> Unit = {},
    ): DownloadResult = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, CACHE_DIR).also { it.mkdirs() }
            val safe = versionLabel.replace(Regex("""[^\w.\-]+"""), "_").take(64)
            val dest = File(dir, "NOOP-$safe.apk")
            if (dest.exists()) dest.delete()

            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 120_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream, */*")
                setRequestProperty("User-Agent", "NOOP-ApkInstaller")
            }
            try {
                if (conn.responseCode !in 200..299) {
                    return@runCatching DownloadResult.Failed("Download failed (${conn.responseCode})")
                }
                val total = conn.contentLengthLong.takeIf { it > 0L } ?: -1L
                conn.inputStream.use { input ->
                    dest.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var readTotal = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            readTotal += n
                            if (total > 0L) {
                                onProgress((readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                        output.flush()
                    }
                }
                if (dest.length() < 1024L) {
                    dest.delete()
                    return@runCatching DownloadResult.Failed("APK too small â€” check the release asset")
                }
                onProgress(1f)
                DownloadResult.Ready(dest)
            } finally {
                conn.disconnect()
            }
        }.getOrElse { e ->
            DownloadResult.Failed(e.message?.take(120) ?: "Download failed")
        }
    }

    /** Launch the system installer for a previously downloaded APK. */
    fun install(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() < 1024L) return false
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
