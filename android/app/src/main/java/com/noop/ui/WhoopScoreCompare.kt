package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.noop.analytics.WhoopNoopAlignment
import com.noop.analytics.HcNoopAlign
import com.noop.data.DailyMetric
import kotlin.math.roundToInt

/**
 * Side-by-side **NOOP (open BLE + our algo)** vs **WHOOP mobile app scores** + pass score.
 *
 * WHOOP column = official **app** Recovery / Day Strain (0–21) from Data Export or manual log —
 * NEVER open BLE, NEVER NOOP-computed rows mislabeled as WHOOP.
 * Pass score from [WhoopNoopAlignment] on a shared 0–100 scale (Strain 0–21 → ×100/21 for math only).
 */
data class ScoreCompareRow(
    val label: String,
    val noop: Double?,
    val whoop: Double?,
    val higherIsBetter: Boolean = true,
    val unit: String = "",
)

@Composable
fun WhoopScoreCompareCard(
    dayLabel: String,
    rows: List<ScoreCompareRow>,
    whoopSourceNote: String,
    alignment: WhoopNoopAlignment.DayAlignment,
    evolutions: List<WhoopNoopAlignment.EvolutionEntry>,
    onOpenHealthConnect: (() -> Unit)? = null,
    /** Fable 200 #21 / 300 #72 — tap a compare head → that metric's home screen. */
    onOpenMetricHome: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val passColor = when (alignment.grade) {
        WhoopNoopAlignment.Grade.STRONG -> Palette.statusPositive
        WhoopNoopAlignment.Grade.PASS -> Palette.accent
        WhoopNoopAlignment.Grade.BUILDING -> Palette.metricAmber
        WhoopNoopAlignment.Grade.FAIL -> Palette.metricRose
        WhoopNoopAlignment.Grade.AWAITING -> Color.White.copy(0.45f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surfaceRaised)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    // TODAY_STYLE #15 — domain overline before dual-scale compare hero.
                    Text("COMPARE", style = NoopType.overline, color = Palette.accent)
                    Text("NOOP algo vs WHOOP app", style = NoopType.headline, color = Color.White)
                    // SHIP #260/#257 — brand columns + HC import path named once.
                    Text(
                        "Gold = NOOP on-device · Blue = WHOOP app (export / HC / Log) — not open BLE.",
                        style = NoopType.caption,
                        color = Color.White.copy(0.55f),
                    )
                    // SHIP #28 — purpose before day/model chrome.
                    Text(
                        LifeChapterLacquer.COMPARE_PURPOSE,
                        style = NoopType.footnote,
                        color = Color.White.copy(0.65f),
                    )
                    // SHIP #41 — past days never silently borrow today's WHOOP labels.
                    if (dayLabel.isNotBlank() && dayLabel != logicalDayKeyNow()) {
                        Text(
                            "Past day · WHOOP column only if this wake-day was imported — not today's labels.",
                            style = NoopType.caption,
                            color = Palette.metricAmber.copy(alpha = 0.9f),
                        )
                    }
                    Text(
                        "$dayLabel · model ${alignment.modelVersion}",
                        style = NoopType.footnote,
                        color = Color.White.copy(0.55f),
                    )
                }
                StatePill(
                    when (alignment.grade) {
                        WhoopNoopAlignment.Grade.AWAITING -> "Need WHOOP app labels"
                        else -> alignment.grade.label
                    },
                    tone = when (alignment.grade) {
                        WhoopNoopAlignment.Grade.STRONG, WhoopNoopAlignment.Grade.PASS -> StrandTone.Positive
                        WhoopNoopAlignment.Grade.BUILDING -> StrandTone.Accent
                        else -> StrandTone.Neutral
                    },
                    showsDot = true,
                )
            }

            // Pass score — flat overline row (SHIP #39: no nested brick inside the compare card).
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Match vs WHOOP app", style = NoopType.footnote, color = Color.White.copy(0.55f))
                    Text(
                        alignment.passScore?.let { "${it.roundToInt()} / 100" } ?: "— / 100",
                        style = NoopType.title2,
                        color = passColor,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${alignment.passedHeads}/${alignment.pairedHeads} heads in band",
                        style = NoopType.footnote,
                        color = Color.White.copy(0.75f),
                    )
                    Text(
                        alignment.summary,
                        style = NoopType.caption,
                        color = Color.White.copy(0.5f),
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }

            // SHIP #29 — TrainingStatusStrip demoted from default Today compare path.
            // SHIP #28 — short LEFT/RIGHT purpose (long dual-scale note lives on Effort track).
            Text(
                LifeChapterLacquer.COMPARE_FOOTNOTE_SHORT,
                style = NoopType.footnote,
                color = Color.White.copy(0.55f),
            )
            // Scale cheat-sheet so 14/21 is never misread as /100
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF5B9DFF).copy(0.12f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("WHOOP Strain scale", style = NoopType.footnote, color = Color(0xFF8EC0FF))
                Text("0 – 21 (×100/21 → % for pass only)", style = NoopType.footnote, color = Color.White.copy(0.7f))
            }

            // Fable 200 #20 / #82 — default to Charge + Effort; expand for Rest / Stress.
            // Empty WHOOP side: wait copy, not four dashes alone.
            // SHIP #37 — visible order always Charge → Effort → Rest → Stress (vessel order; Stress last).
            var showMoreHeads by remember { mutableStateOf(false) }
            val orderedHeads = remember(alignment.heads) {
                compareHeadsInVesselOrder(alignment.heads)
            }
            val primaryHeads = orderedHeads.filter {
                it.name.startsWith("Charge") || it.name.startsWith("Effort")
            }
            val moreHeads = orderedHeads.filter {
                !it.name.startsWith("Charge") && !it.name.startsWith("Effort")
            }
            val visibleHeads = if (showMoreHeads) orderedHeads else primaryHeads.ifEmpty { orderedHeads.take(2) }
            if (alignment.pairedHeads == 0 && alignment.heads.all { it.whoop == null }) {
                Text(
                    "Waiting on WHOOP app labels via Health Connect (or export / Log).",
                    style = NoopType.footnote,
                    color = Palette.metricAmber.copy(alpha = 0.9f),
                )
            }
            visibleHeads.forEach { h ->
                val delta = if (h.noop != null && h.whoop != null) h.noop - h.whoop else null
                // SHIP #29 — emptyHints stay 3–6 words (calm lowercase).
                val emptyHint = when {
                    h.noop == null && h.whoop == null && h.name.startsWith("Charge") ->
                        "Wear nights · import Recovery"
                    h.noop == null && h.whoop == null && h.name.startsWith("Effort") ->
                        "Wear today · import Strain"
                    h.noop == null && h.whoop == null && h.name.startsWith("Rest") ->
                        "Need Rest · import sleep"
                    h.noop == null && h.whoop == null && h.name.startsWith("Stress") ->
                        "Needs HRV baseline"
                    h.whoop == null ->
                        "No WHOOP app label this day"
                    h.noop == null ->
                        "NOOP still learning · wear"
                    else -> null
                }
                val isEffortHead = h.name.startsWith("Effort")
                val isStressHead = h.name.startsWith("Stress")
                val isRestHead = h.name.startsWith("Rest")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.04f))
                        .then(
                            if (onOpenMetricHome != null) {
                                Modifier.clickable(
                                    onClickLabel = "Open ${h.name} home",
                                    onClick = { onOpenMetricHome.invoke(h.name) },
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(h.name, style = NoopType.subhead, color = Color.White.copy(0.9f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(
                            when (h.withinBand) {
                                true -> "in band ✓"
                                false -> "gap"
                                null -> "unpaired"
                            },
                            style = NoopType.footnote,
                            color = when (h.withinBand) {
                                true -> Palette.statusPositive
                                false -> Palette.metricAmber
                                null -> Color.White.copy(0.35f)
                            },
                        )
                    }
                    // SHIP #40 — high-contrast scale chip whenever Effort has either side present.
                    if (isEffortHead && (h.noop != null || h.whoop != null)) {
                        Text(
                            LifeChapterLacquer.COMPARE_NOT_SAME_SCALE,
                            style = NoopType.caption,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF5B9DFF).copy(0.28f))
                                .border(1.dp, Color(0xFF8EC0FF).copy(0.65f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier
                                .weight(1f)
                                .then(
                                    // TODAY_STYLE #17 — empty cells get the same inset depth as scored cells.
                                    if (h.noop == null) {
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(0.06f))
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            Text("NOOP algo", style = NoopType.footnote, color = Palette.accent)
                            Text(h.noopDisplay, style = NoopType.number(16f), color = Color.White)
                        }
                        // TODAY_STYLE #16 — gold | blue column hairline (not flat white).
                        Box(
                            Modifier
                                .padding(horizontal = 6.dp)
                                .width(1.dp)
                                .height(36.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Palette.accent.copy(alpha = 0.55f),
                                            Color(0xFF5B9DFF).copy(alpha = 0.55f),
                                        ),
                                    ),
                                ),
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .then(
                                    if (h.whoop == null) {
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(0.06f))
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            Text("WHOOP app", style = NoopType.footnote, color = Color(0xFF5B9DFF))
                            Text(h.whoopDisplay, style = NoopType.number(16f), color = Color(0xFF8EC0FF))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Δ %", style = NoopType.footnote, color = Color.White.copy(0.45f))
                            Text(
                                delta?.let { d -> "${if (d > 0) "+" else ""}${d.roundToInt()}" } ?: "—",
                                style = NoopType.number(16f),
                                color = Color.White.copy(0.7f),
                            )
                        }
                    }
                    // Fable 200 #22: dual-scale track ALWAYS under Effort (even unpaired), 2dp hairline.
                    // Markers/fill only when values exist — empty track still teaches the dual scale.
                    if (isEffortHead) {
                        DualScaleCompareTrack(
                            noopPct = h.noop,
                            whoopPct = h.whoop,
                            band = h.band,
                        )
                    }
                    h.scaleNote?.let {
                        Text(it, style = NoopType.footnote, color = Color.White.copy(0.4f))
                    }
                    // SHIP #30 — Stress has no Today vessel twin.
                    if (isStressHead) {
                        Text(
                            LifeChapterLacquer.COMPARE_STRESS_NO_VESSEL,
                            style = NoopType.footnote,
                            color = Color.White.copy(0.5f),
                        )
                    }
                    // SHIP #31 — Rest % vs Sleep tab hours/stages.
                    if (isRestHead) {
                        Text(
                            LifeChapterLacquer.COMPARE_REST_VS_SLEEP,
                            style = NoopType.footnote,
                            color = Color.White.copy(0.5f),
                        )
                    }
                    emptyHint?.let {
                        Text(it, style = NoopType.footnote, color = Palette.metricAmber.copy(alpha = 0.85f))
                    }
                }
            }
            if (moreHeads.isNotEmpty()) {
                Text(
                    if (showMoreHeads) "Hide Rest / Stress / Sleep" else "Show Rest / Stress / Sleep",
                    style = NoopType.footnote,
                    color = Palette.accent,
                    modifier = Modifier
                        .clickable { showMoreHeads = !showMoreHeads }
                        .padding(vertical = 4.dp),
                )
                // SHIP #30 — tip while Stress head is still collapsed.
                if (!showMoreHeads) {
                    Text(
                        LifeChapterLacquer.COMPARE_STRESS_NO_VESSEL,
                        style = NoopType.caption,
                        color = Color.White.copy(0.4f),
                    )
                }
            }

            // SHIP #29 — TrainingStatusStrip + Model evolutions only after expand (not default Today path).
            if (showMoreHeads) {
                TrainingStatusStrip()
                if (evolutions.isNotEmpty()) {
                    Text("Model evolutions", style = NoopType.footnote, color = Color.White.copy(0.45f))
                    evolutions.takeLast(6).forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "v${e.version}",
                                style = NoopType.caption,
                                color = if (e.version == alignment.modelVersion) {
                                    Palette.accent
                                } else {
                                    Color.White.copy(0.55f)
                                },
                            )
                            Text(
                                e.passScore?.let { "pass ${it.roundToInt()}" } ?: "—",
                                style = NoopType.caption,
                                color = Color.White.copy(0.45f),
                            )
                        }
                        Text(e.notes, style = NoopType.footnote, color = Color.White.copy(0.35f))
                    }
                }
            }

            Text(whoopSourceNote, style = NoopType.footnote, color = Color.White.copy(0.4f))
            if (onOpenHealthConnect != null && alignment.pairedHeads == 0) {
                WetBounceButton(
                    label = "Import WHOOP app export (Data Management)",
                    modifier = Modifier.fillMaxWidth(),
                    tint = Color(0xFF5B9DFF),
                    onClick = onOpenHealthConnect,
                )
            }
        }
    }
}

/**
 * Build WHOOP **app** side from export daily row (device whoop-app, strain on 0–21) and/or manual store.
 * Returns nulls when we have no official app labels — never fill from NOOP days[].
 */
fun whoopAppMetricFromSources(
    exportDay: DailyMetric?,
    manual: com.noop.data.WhoopAppScoreStore.DayScores?,
): Pair<DailyMetric?, Double?> {
    val recovery = manual?.recoveryPct ?: exportDay?.recovery
    val strain021 = manual?.dayStrain021 ?: exportDay?.strain
    if (recovery == null && strain021 == null) return null to null
    val metric = DailyMetric(
        deviceId = com.noop.data.WhoopAppScoreStore.DEVICE_ID,
        day = exportDay?.day ?: manual?.day ?: "",
        recovery = recovery,
        strain = strain021, // keep 0–21 native for dual-scale display
        totalSleepMin = exportDay?.totalSleepMin,
        avgHrv = exportDay?.avgHrv,
        restingHr = exportDay?.restingHr,
    )
    return metric to null // stress from app not in open export usually
}

/** User types Recovery % and Day Strain 0–21 as shown in the official WHOOP app UI. */
@Composable
fun WhoopAppScoreLogDialog(
    day: String,
    initial: com.noop.data.WhoopAppScoreStore.DayScores?,
    onDismiss: () -> Unit,
    onSave: (com.noop.data.WhoopAppScoreStore.DayScores) -> Unit,
) {
    var rec by remember {
        mutableStateOf(initial?.recoveryPct?.let { String.format("%.0f", it) } ?: "")
    }
    var strain by remember {
        mutableStateOf(initial?.dayStrain021?.let { String.format("%.1f", it) } ?: "")
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WHOOP app scores", style = NoopType.headline, color = Palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Open the official WHOOP app and type Recovery % and Day Strain (0–21) for $day. " +
                        "This is how we compare our algo to the app — not to bracelet raw.",
                    style = NoopType.footnote,
                    color = Palette.textSecondary,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = rec,
                    onValueChange = { rec = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Recovery % (app)") },
                    singleLine = true,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = strain,
                    onValueChange = { strain = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Day Strain 0–21 (app)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    val r = rec.toDoubleOrNull()?.coerceIn(0.0, 100.0)
                    val s = strain.toDoubleOrNull()?.coerceIn(0.0, 21.0)
                    onSave(
                        com.noop.data.WhoopAppScoreStore.DayScores(
                            day = day,
                            recoveryPct = r,
                            dayStrain021 = s,
                            source = "manual",
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun fmt(v: Double?): String {
    if (v == null) return "—"
    return if (kotlin.math.abs(v) >= 10) v.roundToInt().toString() else String.format("%.1f", v)
}

/**
 * On-device training honesty strip. Reads bundled assets when present; never claims accuracy_valid
 * without enough labeled days (Impeccable: no fake 100% pass).
 */
@Composable
private fun TrainingStatusStrip() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val status = remember {
        runCatching {
            context.assets.open("ml_engine_status.json").bufferedReader().use { it.readText() }
        }.getOrNull()?.let { raw ->
            runCatching {
                val o = org.json.JSONObject(raw)
                val nLab = o.optInt("n_label_rows", 0)
                val nFeat = o.optInt("n_feature_days", 0)
                val nSamples = o.optInt("n_ml_samples_ingested", 0)
                // SHIP #388 — never claim accuracy_valid when labeled N is sparse (<3).
                val validClaim = o.optBoolean("accuracy_valid", false)
                val valid = validClaim && nLab >= 3
                "ML: ${nSamples} samples · ${nFeat} feature days · ${nLab} WHOOP app labels · " +
                    if (valid) "accuracy valid" else "need ≥3 labeled days (underfit)"
            }.getOrNull()
        } ?: "ML: open collect active · accuracy needs ≥3 distinct WHOOP app Strain days"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(0.05f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            status,
            style = NoopType.footnote,
            color = Color.White.copy(0.65f),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Shared 0–100 track with NOOP (gold) and WHOOP-app (blue) markers.
 * [whoopPct] must already be normalized (Strain 0–21 → ×100/21 via alignment).
 * Fable 200 #22: always 2dp hairline under Effort — committed lacquer rail, not a fat card bar.
 * SHIP #35/#36 — stronger overline + concrete 14.7/21 example inside the block.
 */
@Composable
private fun DualScaleCompareTrack(
    noopPct: Double?,
    whoopPct: Double?,
    band: Double,
) {
    // TODAY_STYLE #15 — slightly taller lacquer rail so dual markers read under Effort.
    val trackH = 3.dp
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            LifeChapterLacquer.COMPARE_DUAL_SCALE_OVERLINE,
            style = NoopType.caption,
            color = Color.White.copy(0.78f),
        )
        Text(
            LifeChapterLacquer.COMPARE_DUAL_SCALE_EXAMPLE,
            style = NoopType.footnote,
            color = Color(0xFF8EC0FF).copy(alpha = 0.95f),
        )
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val wPx = maxWidth
            // Lacquer rail
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackH)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(0.12f))
                    .align(Alignment.CenterStart),
            )
            // Band corridor around WHOOP marker (quiet wash on the rail only).
            whoopPct?.let { w ->
                val lo = ((w - band) / 100.0).coerceIn(0.0, 1.0)
                val hi = ((w + band) / 100.0).coerceIn(0.0, 1.0)
                Box(
                    Modifier
                        .offset(x = wPx * lo.toFloat())
                        .width(wPx * (hi - lo).toFloat().coerceAtLeast(0.02f))
                        .height(trackH)
                        .align(Alignment.CenterStart)
                        .background(Color(0xFF5B9DFF).copy(0.28f)),
                )
            }
            // NOOP gold fill along the rail
            noopPct?.let { n ->
                val f = (n / 100.0).coerceIn(0.0, 1.0).toFloat()
                Box(
                    Modifier
                        .width(wPx * f.coerceAtLeast(0.02f))
                        .height(trackH)
                        .align(Alignment.CenterStart)
                        .background(Palette.accent.copy(0.55f)),
                )
            }
            // WHOOP marker (blue tick)
            whoopPct?.let { w ->
                val f = (w / 100.0).coerceIn(0.0, 1.0).toFloat()
                Box(
                    Modifier
                        .offset(x = (wPx * f) - 3.dp)
                        .width(2.dp)
                        .height(12.dp)
                        .align(Alignment.CenterStart)
                        .background(Color(0xFF8EC0FF)),
                )
            }
            // NOOP marker (gold tick)
            noopPct?.let { n ->
                val f = (n / 100.0).coerceIn(0.0, 1.0).toFloat()
                Box(
                    Modifier
                        .offset(x = (wPx * f) - 3.dp)
                        .width(2.dp)
                        .height(12.dp)
                        .align(Alignment.CenterStart)
                        .background(Palette.accent),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0", style = NoopType.caption, color = Color.White.copy(0.35f))
            Text("gold NOOP algo · blue WHOOP app (not open BLE)", style = NoopType.caption, color = Color.White.copy(0.4f))
            Text("100", style = NoopType.caption, color = Color.White.copy(0.35f))
        }
    }
}

/** SHIP #37 — Charge → Effort → Rest → Stress (Stress last), matching ScoreHeroRow vessels. */
private fun compareHeadRank(name: String): Int = when {
    name.startsWith("Charge") -> 0
    name.startsWith("Effort") -> 1
    name.startsWith("Rest") -> 2
    name.startsWith("Stress") -> 3
    else -> 4
}

private fun compareHeadsInVesselOrder(
    heads: List<WhoopNoopAlignment.HeadResult>,
): List<WhoopNoopAlignment.HeadResult> = heads.sortedBy { compareHeadRank(it.name) }

fun buildCompareRows(
    noopRecovery: Double?,
    noopStrain: Double?,
    noopSleepPerf: Double?,
    noopStress: Double?,
    whoop: DailyMetric?,
    whoopStress: Double?,
    /** HC / WHOOP-app sleep duration minutes when official Sleep Performance isn't labeled. */
    hcSleepMin: Double? = null,
): List<ScoreCompareRow> {
    val whoopSleepPerf = whoop?.let { d ->
        // Prefer an explicit sleep_performance-like field if ever present on the export row via recovery-adjacent;
        // otherwise duration-vs-8h need is an honest proxy (not invented clinical %).
        HcNoopAlign.durationAsSleepPerf(d.totalSleepMin)
            ?: HcNoopAlign.durationAsSleepPerf(hcSleepMin)
    } ?: HcNoopAlign.durationAsSleepPerf(hcSleepMin)
    // SHIP #37 — vessel order: Charge → Effort → Rest → Stress (Stress last).
    return listOf(
        ScoreCompareRow("Charge / Recovery", noopRecovery, whoop?.recovery, higherIsBetter = true),
        ScoreCompareRow("Effort / Strain", noopStrain, whoop?.strain, higherIsBetter = false),
        ScoreCompareRow(
            "Rest / Sleep %",
            noopSleepPerf,
            whoopSleepPerf,
            higherIsBetter = true,
        ),
        ScoreCompareRow("Stress (0–3 band)", noopStress, whoopStress, higherIsBetter = false),
    )
}

fun alignmentFromRows(
    day: String,
    rows: List<ScoreCompareRow>,
): WhoopNoopAlignment.DayAlignment {
    fun row(labelPrefix: String) = rows.firstOrNull { it.label.startsWith(labelPrefix) }
    val rec = row("Charge")
    val str = row("Effort")
    val slp = row("Rest")
    val st = row("Stress")
    return WhoopNoopAlignment.evaluateDay(
        day = day,
        noopRecovery = rec?.noop,
        noopStrain = str?.noop,
        noopSleep = slp?.noop,
        noopStressPct = st?.noop,
        whoopRecovery = rec?.whoop,
        whoopStrain = str?.whoop,
        whoopSleep = slp?.whoop,
        whoopStressPct = st?.whoop,
    )
}
