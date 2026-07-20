package com.noop.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.noop.BuildConfig
import com.noop.update.ApkInstaller
import com.noop.update.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Settings → About: browse GitHub Release APKs, download, install.
 * Downgrade (lower versionCode / older name) requires an Are you sure? confirm.
 * Copy stays GitHub-first — no Tailscale / AI Store branding.
 */
@Composable
fun VersionSelectSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var listing by remember { mutableStateOf(false) }
    var listResult by remember { mutableStateOf<UpdateCheck.ListResult?>(null) }
    var expanded by remember { mutableStateOf(false) }

    var downgradeConfirm by remember { mutableStateOf<UpdateCheck.ReleaseVersion?>(null) }

    var busyVersion by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusLine by remember { mutableStateOf<String?>(null) }

    fun relationOf(rel: UpdateCheck.ReleaseVersion): UpdateCheck.VersionRelation =
        UpdateCheck.relationToInstalled(
            candidateVersion = rel.version,
            candidateCode = rel.versionCode,
            currentVersion = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE,
        )

    fun startInstall(rel: UpdateCheck.ReleaseVersion) {
        if (busyVersion != null) return
        if (ApkInstaller.needsUnknownSourcesPermission(context)) {
            Toast.makeText(
                context,
                "Allow installs from NOOP, then tap Install again.",
                Toast.LENGTH_LONG,
            ).show()
            ApkInstaller.openUnknownSourcesSettings(context)
            return
        }
        busyVersion = rel.version
        progress = 0f
        statusLine = "Downloading ${rel.version}…"
        scope.launch {
            when (
                val dl = ApkInstaller.download(
                    context = context,
                    apkUrl = rel.apkUrl,
                    versionLabel = rel.version,
                    onProgress = { p ->
                        scope.launch(Dispatchers.Main.immediate) { progress = p }
                    },
                )
            ) {
                is ApkInstaller.DownloadResult.Ready -> {
                    val mismatch = ApkInstaller.packageMismatchReason(context, dl.file)
                    if (mismatch != null) {
                        statusLine = mismatch
                    } else {
                        statusLine = "Opening installer…"
                        val ok = ApkInstaller.install(context, dl.file)
                        statusLine = if (ok) {
                            "Installer opened for ${rel.version}."
                        } else {
                            "Couldn't open installer. Retry, or open the APK from GitHub Releases."
                        }
                    }
                }
                is ApkInstaller.DownloadResult.Failed -> {
                    statusLine = dl.reason
                }
            }
            busyVersion = null
        }
    }

    fun requestInstall(rel: UpdateCheck.ReleaseVersion) {
        when (relationOf(rel)) {
            UpdateCheck.VersionRelation.OLDER -> downgradeConfirm = rel
            UpdateCheck.VersionRelation.SAME,
            UpdateCheck.VersionRelation.NEWER,
            -> startInstall(rel)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    if (listing) return@OutlinedButton
                    if (expanded && listResult is UpdateCheck.ListResult.Ok) {
                        expanded = false
                        return@OutlinedButton
                    }
                    listing = true
                    statusLine = null
                    scope.launch {
                        listResult = UpdateCheck.listAvailableVersions(
                            applicationId = BuildConfig.APPLICATION_ID,
                        )
                        listing = false
                        expanded = listResult is UpdateCheck.ListResult.Ok
                        if (listResult is UpdateCheck.ListResult.Failed) {
                            statusLine =
                                "Couldn't list versions. Retry · or open GitHub Releases."
                        }
                    }
                },
                enabled = !listing && busyVersion == null,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.accent),
                modifier = Modifier.semantics {
                    contentDescription = "Browse available NOOP versions from GitHub"
                },
            ) {
                if (listing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(14.dp),
                        strokeWidth = 2.dp,
                        color = Palette.accent,
                    )
                    Text("Loading…", style = NoopType.captionNumber)
                } else if (expanded) {
                    Text("Hide versions", style = NoopType.captionNumber)
                } else {
                    Text("Choose version", style = NoopType.captionNumber)
                }
            }
            Text(
                "Installed ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = NoopType.footnote,
                color = Palette.textSecondary,
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        if (expanded) {
            when (val r = listResult) {
                is UpdateCheck.ListResult.Ok -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Palette.surfaceInset)
                            .border(1.dp, Palette.hairline, RoundedCornerShape(10.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                    ) {
                        r.releases.forEach { rel ->
                            VersionRow(
                                release = rel,
                                relation = relationOf(rel),
                                busy = busyVersion == rel.version,
                                enabled = busyVersion == null,
                                onInstall = { requestInstall(rel) },
                            )
                        }
                    }
                    Text(
                        "From GitHub Releases · Newbbsss/noop-public-release.",
                        style = NoopType.caption,
                        color = Palette.textTertiary,
                    )
                }
                else -> {}
            }
        }

        if (busyVersion != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(2.dp)),
                color = Palette.accent,
                trackColor = Palette.hairline,
            )
        }

        statusLine?.let { line ->
            Text(
                line,
                style = NoopType.footnote,
                color = if (
                    line.startsWith("Couldn't") ||
                    line.contains("failed", ignoreCase = true)
                ) {
                    Palette.statusWarning
                } else {
                    Palette.textSecondary
                },
            )
        }

        Text(
            "Pick any release to download and install. Older builds ask Are you sure? first.",
            style = NoopType.footnote,
            color = Palette.textTertiary,
        )
    }

    downgradeConfirm?.let { rel ->
        AlertDialog(
            onDismissRequest = { downgradeConfirm = null },
            containerColor = Palette.surfaceOverlay,
            title = {
                Text("Are you sure?", style = NoopType.title2, color = Palette.textPrimary)
            },
            text = {
                Text(
                    buildString {
                        append("Install ${rel.version}")
                        rel.versionCode?.let { append(" (versionCode $it)") }
                        append(" over ${BuildConfig.VERSION_NAME}")
                        append(" (${BuildConfig.VERSION_CODE})? ")
                        append(
                            "Downgrades can remove newer features and may need a clear-data " +
                                "if schemas differ.",
                        )
                    },
                    style = NoopType.subhead,
                    color = Palette.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        downgradeConfirm = null
                        startInstall(rel)
                    },
                ) {
                    Text("Install anyway", style = NoopType.body, color = Palette.statusWarning)
                }
            },
            dismissButton = {
                TextButton(onClick = { downgradeConfirm = null }) {
                    Text("Cancel", style = NoopType.body, color = Palette.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun VersionRow(
    release: UpdateCheck.ReleaseVersion,
    relation: UpdateCheck.VersionRelation,
    busy: Boolean,
    enabled: Boolean,
    onInstall: () -> Unit,
) {
    val badge = when (relation) {
        UpdateCheck.VersionRelation.NEWER -> "Newer"
        UpdateCheck.VersionRelation.SAME -> "Installed"
        UpdateCheck.VersionRelation.OLDER -> "Older"
    }
    val badgeColor = when (relation) {
        UpdateCheck.VersionRelation.NEWER -> Palette.statusPositive
        UpdateCheck.VersionRelation.SAME -> Palette.accent
        UpdateCheck.VersionRelation.OLDER -> Palette.statusWarning
    }
    val actionLabel = when {
        busy -> "…"
        relation == UpdateCheck.VersionRelation.SAME -> "Reinstall"
        relation == UpdateCheck.VersionRelation.OLDER -> "Downgrade"
        else -> "Install"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !busy, onClick = onInstall)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics {
                contentDescription = "Version ${release.version}, $badge. $actionLabel"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    release.version,
                    style = NoopType.subhead,
                    color = Palette.textPrimary,
                )
                Text(
                    badge,
                    style = NoopType.caption,
                    color = badgeColor,
                )
            }
            Text(
                buildString {
                    release.versionCode?.let { append("versionCode $it · ") }
                    append("GitHub APK")
                },
                style = NoopType.caption,
                color = Palette.textTertiary,
            )
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Palette.accent,
            )
        } else {
            TextButton(
                onClick = onInstall,
                enabled = enabled,
            ) {
                Text(
                    actionLabel,
                    style = NoopType.captionNumber,
                    color = if (relation == UpdateCheck.VersionRelation.OLDER) {
                        Palette.statusWarning
                    } else {
                        Palette.accent
                    },
                )
            }
        }
    }
}
