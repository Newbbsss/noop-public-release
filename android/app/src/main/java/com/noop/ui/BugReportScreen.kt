package com.noop.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.noop.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * In-app bug report (8.6.145): screenshots + one-tap diagnostics â†’ share sheet / GitHub Issues.
 * Reach Gilbert via GitHub (`user-bug`) or email share â€” never silent upload.
 */
@Composable
fun BugReportScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var whatHappens by remember { mutableStateOf("") }
    var expected by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val photos = remember { mutableStateListOf<Uri>() }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4),
    ) { uris ->
        photos.clear()
        photos.addAll(uris.take(4))
    }

    ScreenScaffold(
        title = "Bug report",
        subtitle = "Photos + diagnostics - GitHub Issues",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space12)) {
                Text(
                    "Describe what broke. Attach screenshots if you can. One tap packs a diagnostics " +
                        "zip and opens share - send to GitHub Issues (label user-bug) or email. " +
                        "Nothing uploads silently.",
                    style = NoopType.subhead,
                    color = Palette.textSecondary,
                )
                OutlinedTextField(
                    value = whatHappens,
                    onValueChange = { whatHappens = it.take(2_000) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .semantics { contentDescription = "What happens" },
                    label = { Text("What happens") },
                    placeholder = { Text("e.g. Sleep today/yesterday empty; older nights OK") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Palette.accent,
                        unfocusedBorderColor = Palette.hairline,
                        focusedLabelColor = Palette.accent,
                        cursorColor = Palette.accent,
                    ),
                )
                OutlinedTextField(
                    value = expected,
                    onValueChange = { expected = it.take(800) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "What you expected" },
                    label = { Text("What you expected (optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Palette.accent,
                        unfocusedBorderColor = Palette.hairline,
                        focusedLabelColor = Palette.accent,
                        cursorColor = Palette.accent,
                    ),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space8),
                ) {
                    Icon(
                        Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = Palette.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        if (photos.isEmpty()) "No screenshots yet - up to 4"
                        else "${photos.size} screenshot${if (photos.size == 1) "" else "s"} attached",
                        style = NoopType.footnote,
                        color = Palette.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
                NoopButton(
                    text = if (photos.isEmpty()) "Add screenshots" else "Change screenshots",
                    leadingIcon = Icons.Filled.PhotoLibrary,
                    kind = NoopButtonKind.Secondary,
                    fullWidth = true,
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
                NoopButton(
                    text = if (busy) "Packing..." else "Share report (zip + photos)",
                    leadingIcon = Icons.Filled.Upload,
                    kind = NoopButtonKind.Primary,
                    fullWidth = true,
                    enabled = !busy && whatHappens.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            val ok = runCatching {
                                shareBugReport(
                                    context = context,
                                    vm = vm,
                                    whatHappens = whatHappens.trim(),
                                    expected = expected.trim().ifBlank { null },
                                    photoUris = photos.toList(),
                                )
                            }.getOrDefault(false)
                            busy = false
                            if (!ok) {
                                Toast.makeText(
                                    context,
                                    "Couldn't build the report. Try Share strap log in Settings.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                )
                NoopButton(
                    text = "Open GitHub Issues (user-bug)",
                    leadingIcon = Icons.Filled.BugReport,
                    kind = NoopButtonKind.Secondary,
                    fullWidth = true,
                    onClick = {
                        val url = userBugIssueUrl(
                            whatHappens = whatHappens.ifBlank { "(describe after attaching zip)" },
                            expected = expected.ifBlank { null },
                        )
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }.onFailure {
                            Toast.makeText(context, "Open github.com/Newbbsss/noop-public-release/issues", Toast.LENGTH_LONG).show()
                        }
                    },
                )
                Text(
                    "Agents check GitHub issues labeled user-bug on Newbbsss/noop-public-release every few wakes.",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                    modifier = Modifier.padding(top = Metrics.space8),
                )
        }
    }
}

internal fun userBugIssueUrl(whatHappens: String, expected: String?): String {
    val title = "[user-bug] ${whatHappens.take(72).replace('\n', ' ')}"
    val body = buildString {
        appendLine("## What happens")
        appendLine(whatHappens.take(1_500))
        if (!expected.isNullOrBlank()) {
            appendLine()
            appendLine("## Expected")
            appendLine(expected.take(600))
        }
        appendLine()
        appendLine("## App")
        appendLine("- version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("- android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("- device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine()
        appendLine("_Attach the diagnostics zip + screenshots from in-app Bug report._")
    }
    val enc = { s: String ->
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
    return "https://github.com/Newbbsss/noop-public-release/issues/new?" +
        "labels=${enc("user-bug")}&title=${enc(title)}&body=${enc(body.take(3_500))}"
}

private suspend fun shareBugReport(
    context: android.content.Context,
    vm: AppViewModel,
    whatHappens: String,
    expected: String?,
    photoUris: List<Uri>,
): Boolean = withContext(Dispatchers.IO) {
    val stamp = LogExport.timestamp()
    val meta = JSONObject()
        .put("versionName", BuildConfig.VERSION_NAME)
        .put("versionCode", BuildConfig.VERSION_CODE)
        .put("android", Build.VERSION.RELEASE)
        .put("sdk", Build.VERSION.SDK_INT)
        .put("manufacturer", Build.MANUFACTURER)
        .put("model", Build.MODEL)
        .put("whatHappens", whatHappens)
        .put("expected", expected ?: JSONObject.NULL)
        .put("photoCount", photoUris.size)
        .toString(2)
    val readme = buildString {
        appendLine("NOOP user bug report")
        appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine()
        appendLine("WHAT HAPPENS")
        appendLine(whatHappens)
        if (!expected.isNullOrBlank()) {
            appendLine()
            appendLine("EXPECTED")
            appendLine(expected)
        }
        appendLine()
        appendLine("Attach this zip (+ screenshots) on a GitHub issue labeled user-bug:")
        appendLine("https://github.com/Newbbsss/noop-public-release/issues")
    }
    val strapLog = runCatching { vm.ble.exportLogText() }.getOrDefault("(no strap log)")
    val entries = listOf(
        "README.txt" to readme.toByteArray(Charsets.UTF_8),
        "diagnostics.json" to meta.toByteArray(Charsets.UTF_8),
        "strap-log.txt" to strapLog.toByteArray(Charsets.UTF_8),
    )
    val bytes = LogExport.zipEntries(entries) ?: return@withContext false
    val dir = File(context.cacheDir, "logs").apply { mkdirs() }
    val zipFile = File(dir, "noop-user-bug-$stamp.zip")
    zipFile.writeBytes(bytes)
    val authority = "${context.packageName}.fileprovider"
    val uris = ArrayList<Uri>()
    uris += FileProvider.getUriForFile(context, authority, zipFile)
    for ((i, src) in photoUris.withIndex()) {
        runCatching {
            val dest = File(dir, "bug-photo-$stamp-$i.jpg")
            context.contentResolver.openInputStream(src)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            if (dest.length() > 0L) {
                uris += FileProvider.getUriForFile(context, authority, dest)
            }
        }
    }
    withContext(Dispatchers.Main) {
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            putExtra(Intent.EXTRA_SUBJECT, "NOOP bug - ${BuildConfig.VERSION_NAME}")
            putExtra(
                Intent.EXTRA_TEXT,
                "NOOP bug report (${BuildConfig.VERSION_NAME}). Zip + photos attached. " +
                    "Please file at https://github.com/Newbbsss/noop-public-release/issues with label user-bug.\n\n" +
                    whatHappens.take(500),
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newPlainText("noop-bug", "bug report").also { clip ->
                uris.forEach { u ->
                    clip.addItem(ClipData.Item(u))
                }
            }
        }
        context.startActivity(Intent.createChooser(send, "Share bug report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    true
}
