package com.noop.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Honest helpers for off-device backup via Android's Storage Access Framework.
 *
 * NOOP does **not** embed the Google Drive SDK or OAuth. Google Drive appears as a
 * DocumentsProvider in the system folder / create-document picker when the Drive app
 * is installed (typically via Google Play services). We only write `.noopbak` bytes
 * to a user-picked URI; Drive's own sync uploads them.
 */
object BackupCloudHints {

    /** Play Store package for the Google Drive app. */
    const val DRIVE_PACKAGE = "com.google.android.apps.docs"

    /** Primary Drive DocumentsProvider authority (current Drive app). */
    const val DRIVE_DOCS_AUTHORITY = "com.google.android.apps.docs.storage"

    /** Legacy Drive DocumentsProvider authority (older builds still seen in the wild). */
    const val DRIVE_DOCS_AUTHORITY_LEGACY = "com.google.android.apps.docs.storage.legacy"

    /** Play Store listing for Drive — opened only when the user asks from the help dialog. */
    const val DRIVE_PLAY_STORE_URI = "market://details?id=$DRIVE_PACKAGE"
    const val DRIVE_PLAY_STORE_WEB =
        "https://play.google.com/store/apps/details?id=$DRIVE_PACKAGE"

    /**
     * Pure authority check (unit-testable without Robolectric). True for Drive's
     * DocumentsProvider authorities, or any authority that embeds the Drive package id.
     */
    fun isDriveAuthority(authority: String?): Boolean {
        val auth = authority.orEmpty()
        if (auth == DRIVE_DOCS_AUTHORITY || auth == DRIVE_DOCS_AUTHORITY_LEGACY) return true
        return auth.contains("com.google.android.apps.docs")
    }

    /**
     * True when [uri] is a SAF tree (or document) rooted in Google Drive's provider.
     */
    fun isDriveUri(uri: Uri): Boolean {
        if (isDriveAuthority(uri.authority)) return true
        // Some tree URIs encode the provider in the path segment (rare, but cheap to catch).
        return (uri.path ?: "").lowercase().contains("com.google.android.apps.docs")
    }

    /** Hint URI so [OpenDocumentTree] opens near Drive's root when the provider is present. */
    fun drivePickerHintUri(): Uri =
        DocumentsContract.buildTreeDocumentUri(DRIVE_DOCS_AUTHORITY, "root")

    fun isDriveAppInstalled(context: Context): Boolean =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(DRIVE_PACKAGE, 0)
            true
        }.getOrDefault(false)

    /**
     * Pure label builder (unit-testable). [lastPathSegment] is typically the SAF tree's
     * `lastPathSegment` (often `primary:Folder` or an encoded Drive doc id).
     */
    fun folderDisplayLabel(authority: String?, lastPathSegment: String?): String {
        val drive = isDriveAuthority(authority)
        val seg = lastPathSegment ?: return if (drive) "Google Drive" else "selected folder"
        val path = seg.substringAfterLast(':').ifBlank { seg }
        return if (drive) {
            when {
                path.isBlank() || path.equals("root", ignoreCase = true) -> "Google Drive"
                path.equals("home", ignoreCase = true) -> "Google Drive · My Drive"
                else -> "Google Drive · $path"
            }
        } else {
            path.ifBlank { "selected folder" }
        }
    }

    /** Human label for a chosen backup-folder tree. */
    fun folderDisplayLabel(treeUri: Uri): String =
        folderDisplayLabel(treeUri.authority, treeUri.lastPathSegment)

    /** Opens the Play Store page for Drive, falling back to the HTTPS listing. */
    fun openDrivePlayStore(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse(DRIVE_PLAY_STORE_URI))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(DRIVE_PLAY_STORE_WEB))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(market) }.recoverCatching { context.startActivity(web) }
    }
}
