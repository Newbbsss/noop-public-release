package com.noop.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noop.data.DataBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backup & Sync — folder via SAF. Apple mirror of `BackupSyncView`: pick a folder (including a
 * Google Drive folder from the system picker), turn on the opt-in daily auto-backup, back up now,
 * or restore. Snapshots are the existing `.noopbak` whole-DB format ([DataBackup]). No Drive SDK /
 * OAuth — Drive only shows when the Google Drive app (and usually Play services) is on the phone.
 *
 * Must-fixes baked in here:
 *  1. Restore lists the snapshots in the CHOSEN folder (newest-first) and lets the user pick one,
 *     rather than re-prompting with an unrelated document picker. A tightened file fallback exists
 *     only for folders we can't enumerate / legacy files.
 *  2. An explicit in-app confirm dialog fires before any destructive restore call.
 *  3. The file-fallback picker is tightened off the all-files wildcard to the backup MIME types, and
 *     the live [DataBackup.importFrom] now also rejects a foreign-but-valid SQLite (Mac/GRDB or other-app DB).
 */
@Composable
fun BackupSyncScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var treeUri by remember { mutableStateOf(BackupSyncPrefs.treeUri(context)) }
    var auto by remember { mutableStateOf(BackupSyncPrefs.autoEnabled(context)) }
    var autoRestore by remember { mutableStateOf(BackupSyncPrefs.autoRestoreEnabled(context)) }
    var lastMs by remember { mutableStateOf(BackupSyncPrefs.lastBackupMs(context)) }
    var busy by remember { mutableStateOf(false) }
    // How many dated snapshots to keep; pruning deletes the oldest beyond this (BackupSync.snapshotsToPrune).
    var keep by remember { mutableStateOf(BackupSyncPrefs.keepCount(context)) }
    var keepMenu by remember { mutableStateOf(false) }
    // Time-of-day the daily backup runs (minutes since midnight); default 01:00, user-adjustable.
    var backupMinute by remember { mutableStateOf(BackupSyncPrefs.backupMinute(context)) }

    // Restore-from-folder sheet state: the listed snapshots, and the one pending confirmation.
    var snapshots by remember { mutableStateOf<List<BackupSync.SnapshotDoc>>(emptyList()) }
    var showSnapshotPicker by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Pair<String, Uri>?>(null) }
    // Honest Drive help when the Drive app isn't installed (or the user wants the explanation).
    var showDriveHelp by remember { mutableStateOf(false) }

    val driveLinked = treeUri?.let { BackupCloudHints.isDriveUri(it) } == true

    // Runs the actual destructive restore for a chosen backup Uri, off the main thread.
    fun runRestore(uri: Uri) {
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { DataBackup.importFrom(context, uri) }
            busy = false
            when (r) {
                is DataBackup.ImportResult.NeedsRestart -> {
                    // #57: the restore CLOSED and swapped the database file. The long-lived WhoopRepository +
                    // BLE client still hold a DAO on the OLD (now-closed) connection, so any strap sync would
                    // fail with "connection pool has been closed" — and, worse, empty/metadata history ENDs
                    // would still ack and trim the strap PAST records we can't store, discarding real history.
                    // Relaunching the process re-opens Room against the restored file. Do it automatically
                    // rather than trust the user to read a toast (which is exactly how #57 happened).
                    Toast.makeText(context, "Backup restored — restarting NOOP…", Toast.LENGTH_LONG).show()
                    // NonCancellable: this coroutine runs in the screen's scope, which is cancelled the
                    // instant the user navigates away. The restart is a data-safety guarantee (the DB is
                    // already swapped), so it must complete even if the composition leaves — otherwise the
                    // user could keep syncing into the closed DB, the very bug we're fixing.
                    withContext(NonCancellable) {
                        delay(800)   // let the toast render before the process dies
                        DataBackup.relaunchProcessAfterRestore(context)
                    }
                }
                is DataBackup.ImportResult.Failed -> {
                    Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
                    if (r.mustRelaunch) {
                        withContext(NonCancellable) {
                            delay(1200)
                            DataBackup.relaunchProcessAfterRestore(context)
                        }
                    }
                }
            }
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val persistOk = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            true
        }.getOrDefault(false)
        BackupSyncPrefs.setTreeUri(context, uri)
        treeUri = uri
        runCatching { BackupSync.reschedule(context) }
        when {
            !persistOk -> Toast.makeText(
                context,
                "Folder linked, but Android didn't grant lasting access. Daily auto-backup may " +
                    "fail after a restart — re-pick the folder if that happens. Some Drive builds do this.",
                Toast.LENGTH_LONG,
            ).show()
            BackupCloudHints.isDriveUri(uri) -> Toast.makeText(
                context,
                "Google Drive folder linked. NOOP writes backups here; Drive syncs them with your account.",
                Toast.LENGTH_LONG,
            ).show()
            else -> Unit
        }
    }

    fun launchFolderPicker(preferDrive: Boolean) {
        if (preferDrive && !BackupCloudHints.isDriveAppInstalled(context)) {
            showDriveHelp = true
            return
        }
        // Prefer Drive's DocumentsProvider root when asked; otherwise the system default.
        pickFolder.launch(if (preferDrive) BackupCloudHints.drivePickerHintUri() else null)
    }

    // Must-fix #1 + #3: the FILE fallback is tightened to the backup MIME types (was `*/*`). Used only
    // when the chosen folder holds no snapshots, or to restore a one-off file from elsewhere. The chosen
    // file still passes through importFrom's full validation (magic + Room/GRDB-origin) and the same
    // confirm dialog before it overwrites anything.
    val pickRestoreFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingRestore = "the selected file" to uri
    }

    LazyScreenScaffold(
        title = "Backup & Sync",
        subtitle = "Save full .noopbak snapshots to a folder you choose — including Google Drive via Android's file picker (no in-app Drive login).",
    ) {
        // 1 · Destination folder
        item {
            NoopCard(padding = 20.dp, tint = if (driveLinked) Palette.accent else null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Backup folder", style = NoopType.headline, color = Palette.textPrimary)
                    Text(
                        treeUri?.let {
                            if (driveLinked) {
                                "Linked: ${BackupCloudHints.folderDisplayLabel(it)}"
                            } else {
                                "Saving to: ${BackupCloudHints.folderDisplayLabel(it)}"
                            }
                        } ?: "No folder chosen yet. Use Google Drive for off-device copies, or any local folder.",
                        style = NoopType.footnote, color = Palette.textTertiary,
                    )
                    Text(
                        "NOOP never signs into Drive. In the system picker, open Google Drive and " +
                            "choose (or create) a folder. Needs the Google Drive app — and Google Play " +
                            "services on most phones. Without them, Drive won't appear; pick Downloads " +
                            "or another folder instead.",
                        style = NoopType.caption, color = Palette.accent,
                    )
                    NoopButton(
                        text = if (driveLinked) "Change Google Drive folder" else "Use Google Drive…",
                        leadingIcon = Icons.Outlined.Cloud,
                        kind = NoopButtonKind.Primary,
                        enabled = !busy,
                        fullWidth = true,
                        onClick = { launchFolderPicker(preferDrive = true) },
                    )
                    NoopButton(
                        text = if (treeUri == null) "Choose any folder" else "Change to another folder",
                        leadingIcon = Icons.Filled.FolderOpen,
                        kind = NoopButtonKind.Secondary,
                        enabled = !busy,
                        fullWidth = true,
                        onClick = { launchFolderPicker(preferDrive = false) },
                    )
                }
            }
        }

        // 2 · Auto-backup + back up now
        item {
            NoopCard(padding = 20.dp, tint = if (auto && treeUri != null) Palette.accent else null) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Daily auto-backup", style = NoopType.body, color = Palette.textPrimary)
                            Text(
                                "Writes a fresh dated backup to your folder once a day at the time below, keeping " +
                                    "the latest $keep. Off by default - flip it on if you want it.",
                                style = NoopType.footnote, color = Palette.textTertiary,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Switch(
                            checked = auto,
                            enabled = treeUri != null && !busy,
                            onCheckedChange = {
                                auto = it
                                BackupSyncPrefs.setAutoEnabled(context, it)
                                runCatching { BackupSync.reschedule(context) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Palette.surfaceBase,
                                checkedTrackColor = Palette.accent,
                                uncheckedThumbColor = Palette.textSecondary,
                                uncheckedTrackColor = Palette.surfaceInset,
                                uncheckedBorderColor = Palette.hairline,
                            ),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Auto-restore on open", style = NoopType.body, color = Palette.textPrimary)
                            Text(
                                "When this phone has no NOOP data yet, restore the newest good backup from " +
                                    "your folder on open. Never overwrites a phone that already has data. On by default.",
                                style = NoopType.footnote, color = Palette.textTertiary,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Switch(
                            checked = autoRestore,
                            enabled = treeUri != null && !busy,
                            onCheckedChange = {
                                autoRestore = it
                                BackupSyncPrefs.setAutoRestoreEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Palette.surfaceBase,
                                checkedTrackColor = Palette.accent,
                                uncheckedThumbColor = Palette.textSecondary,
                                uncheckedTrackColor = Palette.surfaceInset,
                                uncheckedBorderColor = Palette.hairline,
                            ),
                        )
                    }
                    // Retention: how many dated snapshots to keep. Wired to the existing setKeepCount; the
                    // next backup (auto or "Back up now") prunes the oldest beyond this count.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Keep last snapshots", style = NoopType.body, color = Palette.textPrimary)
                            Text(
                                "Older backups beyond this many are pruned, oldest first (≈ that many days of " +
                                    "daily backups). For recovery: if data ever corrupts, grab the newest snapshot.",
                                style = NoopType.footnote, color = Palette.textTertiary,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Box {
                            TextButton(
                                enabled = treeUri != null && !busy,
                                onClick = { keepMenu = true },
                            ) {
                                Text("$keep", style = NoopType.body, color = Palette.accent)
                            }
                            DropdownMenu(
                                expanded = keepMenu,
                                onDismissRequest = { keepMenu = false },
                            ) {
                                KEEP_OPTIONS.forEach { n ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "$n",
                                                style = NoopType.body,
                                                color = if (n == keep) Palette.accent else Palette.textPrimary,
                                            )
                                        },
                                        onClick = {
                                            keep = n
                                            BackupSyncPrefs.setKeepCount(context, n)
                                            keepMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    // Backup time-of-day. Picking a new time re-anchors the schedule immediately
                    // (BackupSync.applyTimeChange); WorkManager isn't exact so it's best-effort.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Backup time", style = NoopType.body, color = Palette.textPrimary)
                            Text(
                                "Roughly when the daily backup runs (best-effort — the system may slide it a little).",
                                style = NoopType.footnote, color = Palette.textTertiary,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        TimeChip(
                            minutes = backupMinute,
                            accessibilityLabel = "Daily backup time",
                            onPicked = { m ->
                                backupMinute = m
                                BackupSyncPrefs.setBackupMinute(context, m)
                                runCatching { BackupSync.applyTimeChange(context) }
                            },
                        )
                    }
                    Text(
                        if (lastMs > 0L) {
                            "Last backup: ${DateUtils.getRelativeTimeSpanString(lastMs)}"
                        } else {
                            "No backup yet."
                        },
                        style = NoopType.caption, color = Palette.textTertiary,
                    )
                    NoopButton(
                        text = if (busy) "Working…" else "Back up now",
                        leadingIcon = Icons.Filled.CloudUpload,
                        fullWidth = true,
                        enabled = treeUri != null && !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { BackupSync.backupNow(context) }
                                lastMs = BackupSyncPrefs.lastBackupMs(context)
                                busy = false
                                Toast.makeText(
                                    context,
                                    if (ok) {
                                        if (driveLinked) {
                                            "Backed up to Google Drive."
                                        } else {
                                            "Backed up to your folder."
                                        }
                                    } else {
                                        "Backup failed — re-pick the folder (or Google Drive) and try again."
                                    },
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                    )
                }
            }
        }

        // 3 · Restore (must-fix #1: from the chosen folder, newest-first)
        item {
            NoopCard(padding = 20.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Restore", style = NoopType.headline, color = Palette.textPrimary)
                    Text(
                        "Replace this device's data with one of your backups. This overwrites current data, " +
                            "so back up first if unsure.",
                        style = NoopType.footnote, color = Palette.textTertiary,
                    )
                    NoopButton(
                        text = "Restore from a backup…",
                        leadingIcon = Icons.Filled.Restore,
                        kind = NoopButtonKind.Secondary,
                        enabled = !busy,
                        onClick = {
                            val tree = treeUri
                            if (tree == null) {
                                // No folder set: fall back to the tightened file picker.
                                pickRestoreFile.launch(RESTORE_MIME_TYPES)
                            } else {
                                scope.launch {
                                    val found = withContext(Dispatchers.IO) {
                                        runCatching { BackupSync.listSnapshotDocs(context, tree) }
                                            .getOrDefault(emptyList())
                                    }
                                    if (found.isEmpty()) {
                                        // Folder has no snapshots we wrote - point at a file instead.
                                        pickRestoreFile.launch(RESTORE_MIME_TYPES)
                                    } else {
                                        snapshots = found
                                        showSnapshotPicker = true
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    // Must-fix #1: the snapshot picker - the folder's backups, newest-first.
    if (showSnapshotPicker) {
        AlertDialog(
            onDismissRequest = { showSnapshotPicker = false },
            containerColor = Palette.surfaceOverlay,
            title = {
                Text("Choose a backup", style = NoopType.title2, color = Palette.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Newest first. Restoring replaces this device's data.",
                        style = NoopType.footnote, color = Palette.textSecondary,
                    )
                    snapshots.forEach { snap ->
                        // Label + confirmation come from the resolved timeMs carried through from
                        // listSnapshotDocs, so a hand-named / date-only backup still shows a friendly date
                        // (its file-modification date) instead of the raw filename - parity with Swift. Only
                        // when the date is genuinely unknown (timeMs == 0) do we fall back to the name.
                        val whenLabel = if (snap.timeMs > 0L) {
                            DateUtils.getRelativeTimeSpanString(snap.timeMs).toString()
                        } else {
                            snap.name
                        }
                        Text(
                            text = whenLabel,
                            style = NoopType.body,
                            color = Palette.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSnapshotPicker = false
                                    pendingRestore = if (snap.timeMs > 0L) {
                                        "the backup from $whenLabel"
                                    } else {
                                        snap.name
                                    } to snap.uri
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSnapshotPicker = false }) {
                    Text("Cancel", style = NoopType.body, color = Palette.textSecondary)
                }
            },
        )
    }

    // Must-fix #2: explicit in-app confirm BEFORE any destructive restore call, on every restore path.
    pendingRestore?.let { (label, uri) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            containerColor = Palette.surfaceOverlay,
            title = {
                Text("Replace all current data?", style = NoopType.title2, color = Palette.textPrimary)
            },
            text = {
                Text(
                    "Replace all current data with $label? This cannot be undone.",
                    style = NoopType.subhead, color = Palette.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    runRestore(uri)
                }) {
                    Text("Replace", style = NoopType.body, color = Palette.statusCritical)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text("Cancel", style = NoopType.body, color = Palette.textSecondary)
                }
            },
        )
    }

    if (showDriveHelp) {
        AlertDialog(
            onDismissRequest = { showDriveHelp = false },
            containerColor = Palette.surfaceOverlay,
            title = {
                Text("Google Drive", style = NoopType.title2, color = Palette.textPrimary)
            },
            text = {
                Text(
                    "The Google Drive app isn't on this phone (or isn't visible to the file picker). " +
                        "NOOP doesn't sign into Drive itself — it uses Android's folder picker, so Drive " +
                        "only appears when the Drive app is installed. On most phones that also means " +
                        "Google Play services. Without them, pick any local folder instead, or install Drive.",
                    style = NoopType.subhead,
                    color = Palette.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDriveHelp = false
                    BackupCloudHints.openDrivePlayStore(context)
                }) {
                    Text("Get Drive", style = NoopType.body, color = Palette.accent)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showDriveHelp = false
                        // Let them try the system picker anyway — some ROMs list Drive without the package check.
                        pickFolder.launch(BackupCloudHints.drivePickerHintUri())
                    }) {
                        Text("Try picker", style = NoopType.body, color = Palette.textSecondary)
                    }
                    TextButton(onClick = { showDriveHelp = false }) {
                        Text("Cancel", style = NoopType.body, color = Palette.textSecondary)
                    }
                }
            },
        )
    }
}

/**
 * Must-fix #3: the restore file fallback is tightened off the all-files wildcard to the backup
 * container MIME types: the .noopbak ZIP (octet-stream / zip) and a legacy plain SQLite. Anything that
 * slips through still meets importFrom's magic-byte + Room/GRDB-origin validation before it can touch
 * the live DB.
 */
/** Retention choices for the "Keep last snapshots" menu. Each snapshot is a dated .noopbak; the daily
 *  job keeps this many and prunes the oldest. Kept modest — a few days of rollback without hoarding. */
private val KEEP_OPTIONS = listOf(1, 3, 5, 7, 10, 14)

private val RESTORE_MIME_TYPES = arrayOf(
    "application/octet-stream",
    "application/zip",
    "application/x-sqlite3",
)
