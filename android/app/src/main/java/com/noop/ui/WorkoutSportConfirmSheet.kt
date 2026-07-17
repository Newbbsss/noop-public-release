package com.noop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noop.analytics.Sport
import com.noop.analytics.WorkoutSport

/**
 * Post-workout ask: "What was this workout?" so sport-ID / ML labels improve over time.
 * Shown after a live session ends (or when a detected bout needs confirmation).
 */
@Composable
fun WorkoutSportConfirmSheet(
    suggested: String?,
    onConfirm: (Sport) -> Unit,
    onDismiss: () -> Unit,
    /** SHIP #173 — quiet 0–1 classifier confidence when known. */
    suggestionConfidence: Float? = null,
) {
    var query by remember { mutableStateOf("") }
    val initial = remember(suggested) {
        WorkoutSport.all.firstOrNull { it.name.equals(suggested, ignoreCase = true) }
            ?: WorkoutSport.default
    }
    var selected by remember { mutableStateOf(initial) }
    // SHIP #172 — suggested first, then A–Z (frequency-sorted callers pass suggested from history).
    val filtered = remember(query, suggested) {
        val base = WorkoutSport.all.filter { it.name.contains(query, ignoreCase = true) }
        val tip = suggested?.trim().orEmpty()
        if (tip.isEmpty()) base.sortedBy { it.name.lowercase() }
        else base.sortedWith(
            compareByDescending<Sport> { it.name.equals(tip, ignoreCase = true) }
                .thenBy { it.name.lowercase() },
        )
    }
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.surfaceRaised,
        title = {
            Text("What was this workout?", style = NoopType.headline, color = Palette.textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (!suggested.isNullOrBlank()) {
                        val conf = suggestionConfidence?.coerceIn(0f, 1f)?.let {
                            " · phone guess ~${(it * 100).toInt()}%"
                        }.orEmpty()
                        "Suggested: $suggested$conf. Confirm or pick the real sport — trains on-device sport ID."
                    } else {
                        "Pick the sport so future predictions get better. Labels stay on this phone."
                    },
                    style = NoopType.footnote,
                    color = Palette.textSecondary,
                )
                // SHIP #145/#148 — IMU / motion disclosure (not magical).
                Text(
                    "Phone motion sensors may hint a sport. They are approximate — never override what you felt.",
                    style = NoopType.caption,
                    color = Palette.textTertiary,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search sports", color = Palette.textTertiary) },
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    filtered.forEach { sport ->
                        val on = sport.name == selected.name
                        Text(
                            sport.name,
                            style = if (on) NoopType.subhead else NoopType.body,
                            color = if (on) Palette.accent else Palette.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = sport }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.effortColor,
                    contentColor = Palette.surfaceBase,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("Save label", style = NoopType.subhead) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Skip", style = NoopType.footnote, color = Palette.textSecondary)
            }
        },
    )
}
