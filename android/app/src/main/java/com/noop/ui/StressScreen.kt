package com.noop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.analytics.DaytimeStress
import com.noop.analytics.HrvFreqDomain
import com.noop.analytics.StressIndex
import com.noop.data.DailyMetric
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

// MARK: - Stress Monitor (ported from Strand/Screens/StressView.swift)
//
// A Whoop-style "Stress Monitor": one 0–3 number, a band (LOW/MEDIUM/HIGH), and a
// single plain-English line on *why*. The score is a transparent proxy for autonomic
// load, DERIVED from how today's resting HR / HRV sit against a personal 30-day
// baseline (a stored "stress" series, if present, takes priority):
//
//   zRHR = (todayRHR − meanRHR) / sdRHR        // positive when RHR is UP
//   zHRV = (meanHRV − todayHRV) / sdHRV        // positive when HRV is DOWN
//   raw  = zRHR + zHRV                          // combined autonomic load
//   stress = 3 / (1 + e^(−(raw − calmAnchor))) // 0 calm · ~0.36–0.5 floor · 3 high
//
// Bands: 0–1 LOW · 1–2 MEDIUM · 2–3 HIGH. Daily load uses resting HR/HRV z-scores;
// intraday “Now” uses DaytimeStress (5-min buckets, night bias, calm floor). See the
// "How this is computed" card + docs/STRESS_FACTORS_AND_LITERATURE.md.
//
// Source priority for today's value:
//   1. A persisted daily `stress` value from the metricSeries store ("my-whoop").
//   2. Otherwise the z-score derivation above.
// Both the hero number and the full trend line share ONE baseline so the line is
// internally comparable.

@Composable
fun StressScreen(vm: AppViewModel, onBreathe: () -> Unit = {}) {
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()

    // #698: the liquid day-of-sky backdrop is gated on the same "Day-cycle background" setting as Today,
    // so turning it off falls back to the flat theme canvas on every liquid screen alike.
    val context = LocalContext.current
    val showDayCycleBackground = remember { NoopPrefs.showDayCycleBackground(context) }

    // Stored daily "stress" values (0–3), keyed by day. Loaded once per device; the
    // metricSeries store is the Android analogue of the macOS `repo.series(key:source:)`.
    // We pull a wide range so the whole history is covered.
    var stored by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var storedLoaded by remember { mutableStateOf(false) }
    var daytime by remember { mutableStateOf<DaytimeStress.Result?>(null) }
    var stressIndex by remember { mutableStateOf<StressIndex.Components?>(null) }
    var freqHrv by remember { mutableStateOf<HrvFreqDomain.Bands?>(null) }
    var lastLiveRefreshAt by remember { mutableStateOf<Long?>(null) }
    // Fable #34/#45: debounced Now tip (stable 2.5 min unless tip jumps); freeze while charging.
    var displayedNow by remember { mutableStateOf<Double?>(null) }
    var displayedNowAt by remember { mutableStateOf(0L) }

    // Live poll: refresh banked stress series + intraday HR/R-R math while this screen is open.
    // Connected strap → ~12s (new beats land often); idle → ~45s. Never invents numbers — each
    // pass re-reads Room and re-runs the same engines. Keyed on connection only so HR ticks do
    // not thrash Room; the interval already tracks banking while the strap is live.
    androidx.compose.runtime.LaunchedEffect(live.connected, live.charging) {
        while (true) {
            // Hot-fix: union classic `stress` + daytime tips across active∪canonical∪noop —
            // never gate the whole screen on a live BLE link when history exists.
            stored = runCatching { loadStressStoredSeries(vm) }.getOrDefault(emptyMap())
            storedLoaded = true
            val charging = live.charging == true
            // Charging: keep last daytime readout (freeze tip); still refresh stored daily series.
            if (!charging || daytime == null || daytime?.scored.isNullOrEmpty()) {
                val read = runCatching { loadDaytimeStress(vm, context) }
                    .getOrDefault(DaytimeReadout(DaytimeStress.Result.EMPTY, null, null))
                daytime = read.daytime
                stressIndex = read.stressIndex
                freqHrv = read.freqHrv
                lastLiveRefreshAt = System.currentTimeMillis()
            }
            val wakeStart = NoopPrefs.stressWakingStartHour(context)
            val wakeEnd = NoopPrefs.stressWakingEndHour(context)
            val tip = daytime?.nowTip(wakeStart, wakeEnd)
            val nowMs = System.currentTimeMillis()
            val holdMs = 150_000L
            when {
                tip == null -> { /* keep prior displayedNow */ }
                displayedNow == null -> {
                    displayedNow = tip
                    displayedNowAt = nowMs
                }
                kotlin.math.abs(tip - (displayedNow ?: tip)) >= 0.25 -> {
                    displayedNow = tip
                    displayedNowAt = nowMs
                }
                nowMs - displayedNowAt >= holdMs -> {
                    displayedNow = tip
                    displayedNowAt = nowMs
                }
            }
            kotlinx.coroutines.delay(if (live.connected) 12_000L else 45_000L)
        }
    }

    // In-session Now tip fills today's stored gap before the next DB persist (same 0–3 scale).
    val storedForModel = remember(stored, days, displayedNow, daytime) {
        val tip = displayedNow ?: daytime?.nowTip()
        val todayKey = days.lastOrNull()?.day
        if (tip != null && todayKey != null && stored[todayKey] == null) {
            stored + (todayKey to tip.coerceIn(0.0, 3.0))
        } else {
            stored
        }
    }

    // Rebuild the model only when the inputs (days, stored) actually change — the
    // derivation is O(n) over the full history, so we memoize on the inputs.
    val model = remember(days, storedForModel) { StressModel.build(days, storedForModel) }
    val hasHistory = days.isNotEmpty() || stored.isNotEmpty() ||
        !(daytime?.scored.isNullOrEmpty())
    var stressPage by remember { mutableStateOf(StressInnerPage.Today) }

    LazyScreenScaffold(
        title = "Stress",
        // CONTINUED 2026-07-12 stress-export: one subtitle — drop duplicate "Live · recalculating"
        // when the hero already carries Updated HH:mm.
        subtitle = "Autonomic load from today’s beats and resting HRV",
        topBackground = if (showDayCycleBackground) { { LiquidScreenSky() } } else null,
    ) {
        item(key = "stress_page_pills") {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                SegmentedPillControl(
                    items = StressInnerPage.entries,
                    selection = stressPage,
                    label = { it.label },
                    onSelect = { stressPage = it },
                )
            }
        }
        when {
            model != null -> StressContent(
                model,
                daytime,
                stressIndex,
                freqHrv,
                onBreathe,
                page = stressPage,
                stored = stored,
                lastLiveRefreshAt = lastLiveRefreshAt,
                liveConnected = live.connected,
                displayedNow = displayedNow,
                tipFrozenCharging = live.charging == true,
            )
            !storedLoaded -> item { StressLoading() }
            else -> item {
                StressEmpty(
                    liveConnected = live.connected,
                    hasHistory = hasHistory,
                    bankingHrSamples = daytime?.bankingHrSamples ?: 0,
                )
            }
        }
    }
}

/** Stress tab inner pages — Today (live tip) vs History (last night + trend + method). */
private enum class StressInnerPage(val label: String) {
    Today("Today"),
    History("History"),
}

/**
 * The daytime timeline result plus the two additive, on-demand HRV readouts, all derived from the
 * SAME day's R-R. The readouts are null when their engine's gate is not met (Baevsky needs >= 20
 * clean beats; freq-HRV needs >= 60 s span) or when the day had no usable intraday HR. None of this
 * touches the 0..3 score.
 */
private data class DaytimeReadout(
    val daytime: DaytimeStress.Result,
    val stressIndex: StressIndex.Components?,
    val freqHrv: HrvFreqDomain.Bands?,
)

/**
 * Read TODAY's banked HR + R-R and build the intraday stress timeline. Local-day window
 * [midnight, now]; [DaytimeStress] buckets it into waking hours and reuses the daily
 * score's math, so this is the same proxy at a finer grain (never a new score). The SAME `rr` is
 * then fed to the two additive HRV engines (no extra fetch, no DB / schema change).
 */
private suspend fun loadDaytimeStress(vm: AppViewModel, context: android.content.Context): DaytimeReadout {
    val daytime = loadDaytimeStressShared(vm, context)
    if (daytime.scored.isEmpty() && daytime.hours.isEmpty()) {
        // Preserve bankingHrSamples / priorCalmDayCount — do not wipe to bare EMPTY (#167).
        return DaytimeReadout(daytime, null, null)
    }
    // Advanced lenses still need today's R-R (same window as shared loader).
    val nowSeconds = System.currentTimeMillis() / 1000L
    val tzOffsetSeconds = java.util.TimeZone.getDefault().getOffset(nowSeconds * 1_000L) / 1_000L
    val localNow = nowSeconds + tzOffsetSeconds
    val from = (localNow - Math.floorMod(localNow, 86_400L)) - tzOffsetSeconds
    val rr = vm.repo.rrIntervalsUnion(vm.activeStrapId, from, nowSeconds, limit = 200_000)
    val si = StressIndex.components(rr)
    val freq = HrvFreqDomain.freqDomain(rr)
    return DaytimeReadout(daytime, si, freq)
}

// MARK: - Loaded content

// PERF (#scroll-jank): a [LazyListScope] extension so the 5 sections build lazily under
// [LazyScreenScaffold] (only the rows on screen are composed). Same order, the SAME
// `staggeredAppear(0..4)` indices, and the inter-section spacing is the scaffold's 20dp
// `Arrangement.spacedBy` — identical to the eager ColumnScope it replaced.
private fun androidx.compose.foundation.lazy.LazyListScope.StressContent(
    model: StressModel,
    daytime: DaytimeStress.Result?,
    stressIndex: StressIndex.Components?,
    freqHrv: HrvFreqDomain.Bands?,
    onBreathe: () -> Unit,
    page: StressInnerPage,
    stored: Map<String, Double>,
    lastLiveRefreshAt: Long? = null,
    liveConnected: Boolean = false,
    displayedNow: Double? = null,
    tipFrozenCharging: Boolean = false,
) {
    // Hero prefers debounced Now tip when available; else waking-aware tip (defaults to inSleepBandNow).
    val nowLevel = displayedNow ?: daytime?.nowTip()
    val calmBucketPct = daytime?.calmBucketPct()
    val inSleepBand = daytime?.inSleepBandNow == true
    val lastNightMean = daytime?.lastNightMean
    val yesterdayKey = java.time.LocalDate.now().minusDays(1).toString()
    val yesterdayStored = stored[yesterdayKey]

    when (page) {
        StressInnerPage.Today -> {
            item(key = "stress_plain_def") {
                Text(
                    stringResource(R.string.stress_plain_definition),
                    style = NoopType.subhead,
                    color = Palette.textSecondary,
                    modifier = Modifier.staggeredAppear(0),
                )
            }
            item(key = "stress_hero") {
                StressHeroCard(
                    model = model,
                    nowLevel = nowLevel,
                    inSleepBand = inSleepBand,
                    lastLiveRefreshAt = lastLiveRefreshAt,
                    liveConnected = liveConnected,
                    tipFrozenCharging = tipFrozenCharging,
                    modifier = Modifier.staggeredAppear(0),
                )
            }

            if (hasAdvancedReadouts(stressIndex, freqHrv)) {
                item(key = "stress_advanced") {
                    StressAdvancedCard(stressIndex, freqHrv, modifier = Modifier.staggeredAppear(1))
                }
            }

            item(key = "stress_markers") {
                Column(
                    modifier = Modifier.staggeredAppear(1),
                    verticalArrangement = Arrangement.spacedBy(Metrics.gap),
                ) {
                    SectionHeader("Drivers", overline = "Today", trailing = "vs 30-day baseline")
                    StressTiles(
                        model,
                        nowLevel = nowLevel,
                        calmBucketPct = calmBucketPct,
                        calmBucketCount = daytime?.scored?.size,
                    )
                }
            }

            // #167 + #207: daytime EMPTY + live → banking progress; disconnected Wear CTA stays in StressEmpty.
            if (liveConnected && (daytime == null || daytime.scored.isEmpty())) {
                item(key = "stress_banking") {
                    StressBankingProgressNote(
                        samples = daytime?.bankingHrSamples ?: 0,
                        modifier = Modifier.staggeredAppear(2),
                    )
                }
            }

            if (daytime != null && daytime.scored.isNotEmpty()) {
                item(key = "stress_daytime") {
                    StressDaytimeSection(daytime, onBreathe, modifier = Modifier.staggeredAppear(2))
                }
            }
        }
        StressInnerPage.History -> {
            item(key = "stress_last_night") {
                StressLastNightCard(
                    yesterdayStored = yesterdayStored,
                    overnightMean = lastNightMean,
                    modifier = Modifier.staggeredAppear(0),
                )
            }
            item(key = "stress_trend") {
                StressTrendSection(model, modifier = Modifier.staggeredAppear(1))
            }
            item(key = "stress_method") {
                StressMethodologyCard(model, modifier = Modifier.staggeredAppear(2))
            }
        }
    }
}

// MARK: - Liquid hero tokens (the liquid restyle)
//
// The hero card the stress vessel floats on, ported from the iOS liquid heroCard. `LIQUID_HERO_FILL` is a
// translucent near-black (mock rgba(13,14,20,.80)) so it floats over the day-of-sky; the vessel + white
// count-up number read crisp on it. Radius 26 + a white@0.11 hairline give the frosted-glass edge. Same
// numbers as the Today pilot's hero card.
private val LIQUID_HERO_RADIUS = LiquidHeroRadius

// MARK: - 1 · Hero — the liquid stress VESSEL (the flat PipBar is gone)
//
// The liquid restyle: the headline 0–3 read is now a band-tinted [LiquidVessel] filling to score/3, with
// the count-up value rolled up over it in white (the same HeroScoreVessel idiom as the liquid Today). The
// band word + StatePill + the one plain-English line ride beside / under it. The score, band, tints
// (StressRamp: calm blue → steady green → tense amber) and the explanation are UNCHANGED — only the
// presentation moved from the flat PipBar to the sloshing vessel. The card wrapper is the liquid frosted
// translucent-black hero surface so the vessel + white number stay crisp over the day-of-sky.

@Composable
private fun StressHeroCard(
    model: StressModel,
    nowLevel: Double? = null,
    inSleepBand: Boolean = false,
    lastLiveRefreshAt: Long? = null,
    liveConnected: Boolean = false,
    tipFrozenCharging: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // CONTINUED 2026-07-12 stress-export: prefer "Now" (latest daytime hour) as the hero number
    // when the timeline has scored — WHOOP Stress Monitor shows the live tip, not only the
    // daily RHR/HRV load. Daily load stays as a quiet secondary line.
    val context = LocalContext.current
    val workContextActive = NoopPrefs.isWorkContextActive(context)
    val workLabel = NoopPrefs.workLabel(context)
    val displayScore = nowLevel ?: model.score
    val displayBand = StressBand.forScore(displayScore)
    val bandColor = StressRamp.color(displayScore)
    val fraction = (displayScore / 3.0).coerceIn(0.0, 1.0)
    var vesselAnimated by remember { mutableStateOf(true) }
    var lastVesselScore by remember { mutableStateOf<Double?>(null) }
    androidx.compose.runtime.LaunchedEffect(displayScore) {
        val prev = lastVesselScore
        if (prev != null && kotlin.math.abs(prev - displayScore) < 0.05) {
            vesselAnimated = false
        } else {
            vesselAnimated = true
            lastVesselScore = displayScore
            kotlinx.coroutines.delay(750)
            vesselAnimated = false
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LIQUID_HERO_RADIUS))
            .background(liquidHeroFillColor(CardAppearance.opacity))
            .border(1.dp, liquidHeroBorderColor(CardAppearance.opacity), RoundedCornerShape(LIQUID_HERO_RADIUS))
            .padding(Metrics.cardPadding),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Overline(
                    text = when {
                        nowLevel != null && inSleepBand -> "This hour · asleep band"
                        nowLevel != null && workContextActive -> {
                            val label = workLabel?.takeIf { it.isNotBlank() }
                            if (label != null) "This hour · at $label" else "This hour · at work"
                        }
                        nowLevel != null -> "This hour"
                        model.scoreDayKey == java.time.LocalDate.now().toString() -> "Today’s load"
                        else -> "Latest · ${model.scoreDayKey}"
                    },
                    modifier = Modifier.weight(1f),
                )
                // One band cue only (drop duplicate MEDIUM beside the big word).
                StatePill(displayBand.title, tone = displayBand.tone, showsDot = true)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .semantics {
                            contentDescription =
                                "Stress ${String.format(Locale.US, "%.1f", displayScore)} of 3, ${displayBand.title}"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    LiquidVessel(
                        value = fraction,
                        tint = bandColor,
                        animated = vesselAnimated,
                        modifier = Modifier.size(112.dp),
                    )
                    CountUpText(
                        value = displayScore,
                        format = { String.format(Locale.US, "%.1f", it) },
                        style = NoopType.number(30f, weight = FontWeight.Bold)
                            .copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), offset = Offset(0f, 1f), blurRadius = 6f)),
                        color = Color.White,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "of 3 · ${displayBand.title.lowercase()}",
                        style = NoopType.title2,
                        color = bandColor,
                    )
                    Text(
                        when {
                            nowLevel != null && inSleepBand ->
                                "This hour · asleep band"
                            nowLevel != null -> {
                                val hour = java.util.Calendar.getInstance()
                                    .get(java.util.Calendar.HOUR_OF_DAY)
                                if (hour < DaytimeStress.wakingStartHour ||
                                    hour >= DaytimeStress.wakingEndHour
                                ) {
                                    "This hour · night floor tip"
                                } else {
                                    "This hour · banked quiet beats"
                                }
                            }
                            else -> "RHR + HRV vs your 30-day baseline"
                        },
                        style = NoopType.footnote,
                        color = if (inSleepBand) Palette.restColor else Palette.textTertiary,
                    )
                    // #199: when showing This hour, always clarify Today’s load (not only when Δ≥0.15).
                    if (nowLevel != null) {
                        Text(
                            "Today’s load ${String.format(Locale.US, "%.1f", model.score)} (all-day resting picture)",
                            style = NoopType.footnote,
                            color = Palette.textTertiary,
                        )
                    }
                }
            }

            Text(
                when {
                    nowLevel != null && inSleepBand ->
                        "You’re in a recorded sleep window — stress stays near the night floor."
                    nowLevel != null -> explanationForNow(displayBand)
                    else -> model.explanation
                },
                style = NoopType.subhead,
                color = Palette.textSecondary,
            )

            if (lastLiveRefreshAt != null) {
                val is24 = NoopPrefs.use24HourClock(LocalContext.current)
                val clock = remember(lastLiveRefreshAt, is24) {
                    val pattern = if (is24) "HH:mm" else "h:mm a"
                    java.text.SimpleDateFormat(pattern, Locale.getDefault())
                        .format(java.util.Date(lastLiveRefreshAt))
                }
                Text(
                    when {
                        tipFrozenCharging -> "Tip held while charging · last $clock"
                        liveConnected -> stringResource(R.string.stress_updated_prefix, clock)
                        else -> "Idle · last $clock"
                    },
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
        }
    }
}

private fun explanationForNow(band: StressBand): String = when (band) {
    StressBand.Low -> "This hour sits near your calm end for today."
    StressBand.Medium -> "This hour is moderately activated vs your quieter hours today."
    StressBand.High -> "This hour is elevated vs your quieter hours today. Ease off if you can."
}

// MARK: - 1b · Advanced HRV readouts (additive, on-demand)
//
// Two extra, clearly-labelled lenses on the SAME day's R-R the timeline already reads, surfaced in
// their own card so they are visibly separate from the 0..3 monitor. Each tile is shown only when
// its engine produced a value (the engines self-gate on clean-beat count / record span), and the
// whole card is gated by [hasAdvancedReadouts]. Nothing here feeds the score. Faithful twin of iOS.

/**
 * True when at least one advanced readout is presentable (an SI value, or an LF/HF ratio, or at
 * least the HF power). Drives whether the advanced card is shown at all.
 */
private fun hasAdvancedReadouts(
    stressIndex: StressIndex.Components?,
    freqHrv: HrvFreqDomain.Bands?,
): Boolean {
    if (stressIndex != null) return true
    if (freqHrv != null && (freqHrv.lfhf != null || freqHrv.hf > 0)) return true
    return false
}

@Composable
private fun StressAdvancedCard(
    stressIndex: StressIndex.Components?,
    freqHrv: HrvFreqDomain.Bands?,
    modifier: Modifier = Modifier,
) {
    NoopCard(tint = Palette.stressColor, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Overline("Advanced HRV", modifier = Modifier.weight(1f))
                // SHIP #196 — cadence matches StressScreen LaunchedEffect (12s live / 45s idle).
                Text(
                    "every 12s live · 45s idle",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }

            // The advanced tiles, two-up, mirroring the Today markers grid layout.
            val tiles = ArrayList<@Composable (Modifier) -> Unit>()
            // Baevsky Stress Index, a whole number; higher means a more rigid, stressed rhythm.
            if (stressIndex != null) {
                tiles.add { m ->
                    StatTile(
                        modifier = m,
                        label = "Baevsky SI",
                        value = "${stressIndex.si.roundToInt()}",
                        caption = "Higher = more rigid rhythm",
                        accent = StressRamp.TENSE,
                    )
                }
            }
            // Frequency-domain HRV: prefer the LF/HF ratio; if the span was too short for LF
            // (lfhf null) fall back to the HF (rest) band power so the lens still reads.
            if (freqHrv != null) {
                val ratio = freqHrv.lfhf
                if (ratio != null) {
                    tiles.add { m ->
                        StatTile(
                            modifier = m,
                            label = "LF/HF",
                            value = String.format(Locale.US, "%.1f", ratio),
                            caption = "Lens only · not sympathovagal certainty · not the 0–3 score",
                            accent = StressRamp.STEADY,
                        )
                    }
                } else if (freqHrv.hf > 0) {
                    tiles.add { m ->
                        StatTile(
                            modifier = m,
                            label = "HF power",
                            value = "${freqHrv.hf.roundToInt()}",
                            caption = "Parasympathetic (rest) band of your HRV.",
                            accent = StressRamp.STEADY,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
                tiles.chunked(2).forEach { rowTiles ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.gap)) {
                        rowTiles.forEach { tile -> tile(Modifier.weight(1f)) }
                        if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Text(
                "Extra R-R lenses · same 12s/45s poll · do not change the score",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
        }
    }
}

// MARK: - 3 · Daytime timeline (intraday, same 0–3 proxy)

@Composable
private fun StressDaytimeSection(
    day: DaytimeStress.Result,
    onBreathe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        SectionHeader(
            "Today's Timeline",
            overline = "Intraday · 5-min steps",
            trailing = timelineTrailing(day),
        )

        NoopCard(tint = Palette.stressColor) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Overline("Stress through the day", modifier = Modifier.weight(1f))
                    val peak = day.peak
                    val peakLevel = peak?.level
                    val avg = day.dayMean
                    val is24 = NoopPrefs.use24HourClock(LocalContext.current)
                    Column(horizontalAlignment = Alignment.End) {
                        if (peak != null && peakLevel != null) {
                            Text(
                                "peak ${String.format(Locale.US, "%.1f", peakLevel)} · ${bucketClockLabel(peak.startTs, is24)}",
                                style = NoopType.captionNumber,
                                color = StressRamp.color(peakLevel),
                            )
                        }
                        if (avg != null) {
                            Text(
                                "avg ${String.format(Locale.US, "%.1f", avg)}",
                                style = NoopType.footnote,
                                color = Palette.textTertiary,
                            )
                        }
                    }
                }

                // Autonomic-load LINE for the day, drawn in the same blue→green→amber WHOOP
                // ramp as the hero PipBar (README screen 9 "day autonomic-load line").
                DaytimeStressLine(day.hours)

                // Fable Stress #37 — tip-bucket drivers (quiet HR / RMSSD / motion), display only.
                val tip = day.scored.lastOrNull {
                    it.hour >= DaytimeStress.wakingStartHour && it.hour < DaytimeStress.wakingEndHour && !it.asleep
                } ?: day.scored.lastOrNull()
                if (tip != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = stressYAxisWidth),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        tip.meanHr?.let {
                            Text(
                                "Quiet ${it.roundToInt()} bpm",
                                style = NoopType.footnote,
                                color = Palette.textTertiary,
                            )
                        }
                        tip.rmssd?.let {
                            Text(
                                rrRmssdMsCaption(it),
                                style = NoopType.footnote,
                                color = Palette.textTertiary,
                            )
                        }
                        Text(
                            when {
                                tip.asleep -> "Asleep"
                                tip.motionBusy -> "Motion busy"
                                else -> "Still"
                            },
                            style = NoopType.footnote,
                            color = when {
                                tip.asleep -> Palette.restColor
                                tip.motionBusy -> Palette.statusWarning
                                else -> Palette.textTertiary
                            },
                        )
                    }
                }

                // Hour ruler under the line (first / midday / last covered hour).
                // start padding matches stressYAxisWidth so labels align with the chart area.
                val lo = day.hours.firstOrNull()?.hour
                val hi = day.hours.lastOrNull()?.hour
                if (lo != null && hi != null) {
                    Row(modifier = Modifier.fillMaxWidth().padding(start = stressYAxisWidth)) {
                        Text(hourLabel(lo), style = NoopType.footnote, color = Palette.textTertiary)
                        Spacer(Modifier.weight(1f))
                        Text(hourLabel((lo + hi) / 2), style = NoopType.footnote, color = Palette.textTertiary)
                        Spacer(Modifier.weight(1f))
                        Text(hourLabel(hi), style = NoopType.footnote, color = Palette.textTertiary)
                    }
                }

                // SHIP #190 — skin unit follows Settings °C/°F (stored delta is always °C).
                val tempUnit = UnitPrefs.temperature(LocalContext.current)
                val skinUnit = UnitFormatter.temperatureUnit(tempUnit)
                Text(
                    "The line traces autonomic load in ~${DaytimeStress.bucketMinutes}-minute steps " +
                        "(dense WHOOP-like timeline from banked beats — no invented points). " +
                        "Sleep windows wash in Rest teal. Walk/run and gravity motion damp false highs " +
                        "(busy peaks get a small glyph — scrub to read · busy). " +
                        "Skin temp ($skinUnit) / resp bumps can lift a tip when elevated vs your baseline; they do not invent clinical vitals. " +
                        "Sedentary stretches pull toward calm. Buckets without enough data stay as honest gaps.",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )

                // Fable Stress #50 — WHOOP-style "Wear N more nights" of 4 (display only; tip still lives).
                val nightsLeft = day.calibrationNightsRemaining
                if (nightsLeft > 0) {
                    Text(
                        if (nightsLeft == 1) {
                            "Wear 1 more night of ${DaytimeStress.calibrationNightsTarget} to personalize calm"
                        } else {
                            "Wear $nightsLeft more nights of ${DaytimeStress.calibrationNightsTarget} to personalize calm"
                        },
                        style = NoopType.footnote,
                        color = Palette.textSecondary,
                    )
                }
            }
        }

        // Totals bar — how the scored hours split across Calm (blue) / Moderate (green) /
        // High (amber), mirroring the README screen-9 split.
        StressTotalsBar(day)

        HighZoneMinutesCaption(day)

        // Sustained-high suggestion — only when the recent run stays in the HIGH band.
        if (day.sustainedHigh) SustainedBreatheCard(day, onBreathe)
    }
}

// MARK: - Daytime autonomic-load line (gradient, same scale as the gauge)
//
// Interactive Canvas line over the scored waking hours. Tap or drag to scrub across hours
// and see the stress level at a specific time in a tooltip pill. The Y-axis shows the 0–3
// scale with hairline grid lines. Unscored hours break the line (honest gap, no interpolation).

// Width reserved for the Y-axis labels. Matches the start padding on the X-axis ruler row.
private val stressYAxisWidth = 32.dp

@Composable
private fun DaytimeStressLine(hours: List<DaytimeStress.HourPoint>) {
    // SHIP #195 — animate / redraw geometry only when hour levels change, not every poll frame.
    val levels = remember(hours) { hours.map { it.level } }
    if (levels.size < 2) return

    val reduced = rememberReduceMotion()
    var scrubFrac by remember { mutableStateOf<Float?>(null) }
    // Same blue→green→amber WHOOP ramp as the hero PipBar / totals bar (no gold).
    val gradient = remember { Brush.horizontalGradient(*StressRamp.stops.toTypedArray()) }

    // Capture Compose colors at composition time — DrawScope lambdas run on the render thread.
    val hairline = Palette.hairline
    val textTertiary = Palette.textTertiary
    val textPrimary = Palette.textPrimary
    val stressColor = Palette.stressColor
    // Clock-night wash stays subtle; asleep windows get Rest indigo at ~0.22.
    val nightWash = Palette.surfaceInset.copy(alpha = 0.35f)
    val sleepWash = Palette.restColor.copy(alpha = 0.22f)
    val yAxisPx = with(LocalDensity.current) { stressYAxisWidth.toPx() }

    // PERF (#scroll-jank — drawing-bound): this chart is scrubbable, so a finger drag re-records the
    // whole Canvas at ~60fps. Previously every frame rebuilt the gradient line + fill Paths and the
    // axis Paint from scratch, even though only the crosshair moves. Split the chart into two layers:
    //   • a STATIC base — the axis grid/labels and the gradient line+fill — drawn via `drawWithCache`,
    //     which rebuilds the Paths + label Paint ONLY when `levels`/size change (NOT per scrub frame);
    //   • a thin DYNAMIC overlay Canvas — just the crosshair, dot and tooltip — that reads `scrubFrac`.
    // The geometry (yAxisPx, 8dp top/bot pad, yFor, stepX) is byte-identical to the old single Canvas,
    // and the two layers share the same Box bounds, so the rendered pixels are unchanged. Hoisted Paints.
    val labelPaint = remember(textTertiary) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 22f
            textAlign = android.graphics.Paint.Align.RIGHT
            color = textTertiary.toArgb()
        }
    }
    val tooltipPaint = remember(textPrimary) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 26f
            color = textPrimary.toArgb()
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    fun DrawScope.yFor(level: Double, topPad: Float, usable: Float): Float =
        topPad + (1f - (level / 3.0).coerceIn(0.0, 1.0).toFloat()) * usable

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(RoundedCornerShape(Metrics.cornerSm))
            .semantics { contentDescription = daytimeLineDescription(hours) }
            .then(
                if (reduced) Modifier else Modifier.pointerInput(hours) {
                    // Single gesture handler: first touch shows the crosshair; dragging scrubs
                    // across hours; lifting the finger clears it. Off under Reduce Motion (#69).
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val chartW = (size.width - yAxisPx).coerceAtLeast(1f)
                        scrubFrac = ((down.position.x - yAxisPx) / chartW).coerceIn(0f, 1f)
                        var ptr = down
                        while (ptr.pressed) {
                            val event = awaitPointerEvent()
                            ptr = event.changes.firstOrNull() ?: break
                            if (ptr.pressed) {
                                ptr.consume()
                                scrubFrac = ((ptr.position.x - yAxisPx) / chartW).coerceIn(0f, 1f)
                            }
                        }
                        scrubFrac = null
                    }
                },
            ),
    ) {
        // STATIC base layer — axis + gradient line/fill. `drawWithCache` rebuilds the cached Paths only
        // when the chart's size or `levels` change (the cache block reads neither `scrubFrac`), so a
        // scrub drag never re-walks the run-builder or re-allocates Paths. Same draw order as before.
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val topPad = 8.dp.toPx()
                    val botPad = 8.dp.toPx()
                    val usable = (h - topPad - botPad).coerceAtLeast(1f)
                    val chartLeft = yAxisPx
                    val chartW = (w - chartLeft).coerceAtLeast(1f)
                    val stepX = if (levels.size > 1) chartW / (levels.size - 1) else chartW
                    fun yForC(level: Double): Float =
                        topPad + (1f - (level / 3.0).coerceIn(0.0, 1.0).toFloat()) * usable

                    // Pre-build the gradient line + fill Paths for each contiguous run (null breaks).
                    data class Run(val fill: Path?, val line: Path?, val dot: Offset?, val dotColor: Color?)
                    val runs = ArrayList<Run>()
                    var i = 0
                    while (i < levels.size) {
                        if (levels[i] == null) { i++; continue }
                        var j = i
                        val pts = ArrayList<Offset>()
                        while (j < levels.size && levels[j] != null) {
                            pts.add(Offset(chartLeft + j * stepX, yForC(levels[j]!!)))
                            j++
                        }
                        if (pts.size >= 2) {
                            val fill = Path().apply {
                                moveTo(pts.first().x, h - botPad)
                                pts.forEach { lineTo(it.x, it.y) }
                                lineTo(pts.last().x, h - botPad)
                                close()
                            }
                            val line = Path().apply {
                                moveTo(pts.first().x, pts.first().y)
                                for (k in 1 until pts.size) lineTo(pts[k].x, pts[k].y)
                            }
                            runs.add(Run(fill, line, null, null))
                        } else if (pts.size == 1) {
                            runs.add(Run(null, null, pts.first(), StressRamp.color(levels[i]!!)))
                        }
                        i = j
                    }
                    val strokeW = 3.dp.toPx()
                    val dotR = 2.5.dp.toPx()

                    onDrawBehind {
                        if (w <= 0f || h <= 0f) return@onDrawBehind
                        // Sleep / night wash: prefer asleep (Rest indigo); clock-night only when !asleep.
                        val half = stepX / 2f
                        hours.forEachIndexed { idx, pt ->
                            val clockNight = pt.hour < DaytimeStress.wakingStartHour ||
                                pt.hour >= DaytimeStress.wakingEndHour
                            if (!pt.asleep && !clockNight) return@forEachIndexed
                            val cx = chartLeft + idx * stepX
                            drawRect(
                                color = if (pt.asleep) sleepWash else nightWash,
                                topLeft = Offset((cx - half).coerceAtLeast(chartLeft), topPad),
                                size = Size(
                                    (half * 2f).coerceAtMost(w - chartLeft),
                                    usable,
                                ),
                            )
                        }
                        // Y-axis: hairline grid lines + scale labels 0 / 1 / 2 / 3.
                        listOf(0.0, 1.0, 2.0, 3.0).forEach { lvl ->
                            val y = yForC(lvl)
                            drawLine(color = hairline, start = Offset(chartLeft, y), end = Offset(w, y), strokeWidth = 1f)
                            drawContext.canvas.nativeCanvas.drawText(lvl.toInt().toString(), chartLeft - 6f, y + 8f, labelPaint)
                        }
                        // Gradient line + fill — contiguous runs (null levels break the line).
                        runs.forEach { r ->
                            if (r.fill != null) drawPath(r.fill, brush = gradient, alpha = StrandAlpha.chartFillSoftResolved() + 0.10f)
                            if (r.line != null) drawPath(r.line, brush = gradient, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            if (r.dot != null && r.dotColor != null) drawCircle(color = r.dotColor, radius = dotR, center = r.dot)
                        }
                        // Activity glyphs above busy peaks (WHOOP motion cue — Fable #14/#49).
                        hours.forEachIndexed { idx, pt ->
                            val lvl = pt.level ?: return@forEachIndexed
                            if (!pt.motionBusy || lvl < 1.6) return@forEachIndexed
                            val cx = chartLeft + idx * stepX
                            val cy = yForC(lvl) - 7.dp.toPx()
                            val path = Path().apply {
                                moveTo(cx, cy - 5.dp.toPx())
                                lineTo(cx + 4.dp.toPx(), cy + 3.dp.toPx())
                                lineTo(cx - 4.dp.toPx(), cy + 3.dp.toPx())
                                close()
                            }
                            drawPath(path, color = textTertiary.copy(alpha = 0.75f))
                        }
                        // Sleep-region crescent marks: one glyph centered on each contiguous asleep
                        // wash run so the moon band reads as sleep, not just a tint.
                        var si = 0
                        while (si < hours.size) {
                            if (!hours[si].asleep) { si += 1; continue }
                            var ej = si
                            while (ej + 1 < hours.size && hours[ej + 1].asleep) ej += 1
                            val mid = (si + ej) / 2
                            val cx = chartLeft + mid * stepX
                            val r = 3.5.dp.toPx()
                            drawCircle(
                                color = Palette.restColor.copy(alpha = 0.85f),
                                radius = r,
                                center = Offset(cx, topPad + r + 1.dp.toPx()),
                            )
                            drawCircle(
                                color = sleepWash.copy(alpha = 1f),
                                radius = r * 0.72f,
                                center = Offset(cx + r * 0.35f, topPad + r + 1.dp.toPx()),
                            )
                            si = ej + 1
                        }
                    }
                },
        )

        // DYNAMIC overlay — crosshair + dot + tooltip pill, drawn only while the finger is down. A thin
        // Canvas that re-records on each scrub frame; the heavy static Paths above are untouched.
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            val frac = scrubFrac ?: return@Canvas

            val topPad = 8.dp.toPx()
            val botPad = 8.dp.toPx()
            val usable = (h - topPad - botPad).coerceAtLeast(1f)
            val chartLeft = yAxisPx
            val chartW = (w - chartLeft).coerceAtLeast(1f)
            val stepX = if (levels.size > 1) chartW / (levels.size - 1) else chartW

            val scrubIdx = (frac * (levels.size - 1)).roundToInt().coerceIn(0, levels.size - 1)
            val scrubX = chartLeft + scrubIdx * stepX
            val pt = hours[scrubIdx]

            // Dashed vertical crosshair at the selected hour.
            drawLine(
                color = textTertiary,
                start = Offset(scrubX, topPad),
                end = Offset(scrubX, h - botPad),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
            )

            val lvl = pt.level
            if (lvl != null) {
                val dotY = yFor(lvl, topPad, usable)
                // Ring dot at the scrubbed point.
                drawCircle(color = stressColor, radius = 5.dp.toPx(), center = Offset(scrubX, dotY))
                drawCircle(color = Palette.tipCore, radius = 2.5.dp.toPx(), center = Offset(scrubX, dotY))

                // Tooltip pill: "9 am · 1.4 · 5m" — Fable #433: stairs vs WHOOP continuous.
                val tenths = (lvl * 10).roundToInt().coerceIn(0, 30)
                val asleepBit = if (pt.asleep) " · asleep" else ""
                val busyBit = if (pt.motionBusy && !pt.asleep) " · busy" else ""
                val label = "${hourLabel(pt.hour)} · ${tenths / 10}.${tenths % 10} · ${DaytimeStress.bucketMinutes}m$asleepBit$busyBit"
                val textW = tooltipPaint.measureText(label)
                val pillPad = 12f
                val pillW = textW + pillPad * 2
                val pillH = 40f
                val pillX = (scrubX - pillW / 2f).coerceIn(chartLeft, w - pillW)
                val pillY = (dotY - pillH - 12.dp.toPx()).coerceAtLeast(topPad)
                drawRoundRect(
                    color = stressColor.copy(alpha = 0.22f),
                    topLeft = Offset(pillX, pillY),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(pillH / 2),
                )
                // Magnify tip (#47): larger type while scrubbing.
                tooltipPaint.textSize = 30f
                drawContext.canvas.nativeCanvas.drawText(label, pillX + pillW / 2f, pillY + pillH * 0.68f, tooltipPaint)
            }
        }
    }
}

private fun daytimeLineDescription(hours: List<DaytimeStress.HourPoint>): String {
    val scored = hours.mapNotNull { p -> p.level?.let { p to it } }
    if (scored.isEmpty()) return "No intraday stress data yet today."
    val highMin = DaytimeStress.Result.minutesForBuckets(
        scored.count { it.second >= DaytimeStress.highBandFloor },
    )
    val tip = scored.lastOrNull()?.second
    val tipBit = tip?.let { " Latest ${String.format(Locale.US, "%.1f", it)}." } ?: ""
    val highBit = if (highMin > 0) {
        " High zone ${DaytimeStress.Result.formatZoneDuration(highMin)}."
    } else ""
    return "Intraday stress, ${scored.size} scored ${DaytimeStress.bucketMinutes}-minute windows.$tipBit$highBit"
}

// MARK: - Time-in-band — Calm / Moderate / High split of the scored waking hours (liquid tubes)
//
// The liquid restyle of the README screen-9 totals split: instead of one stacked proportional bar, each
// band gets its OWN [LiquidTube] row filled to that band's SHARE of the day's scored hours (a genuine
// single-value 0..1 fraction per row — Calm hours / total, Moderate / total, High / total), tinted by the
// SAME StressRamp band colour (blue → green → amber). `animated = false` so the three tubes pose once and
// cost nothing per frame. The swatch + label sit on the left, the hour count on the right; the shares still
// sum to the day.

@Composable
private fun StressTotalsBar(day: DaytimeStress.Result) {
    val scored = day.scored
    if (scored.isEmpty()) return

    val calmMin = day.calmZoneMinutes
    val highMin = day.highZoneMinutes
    val moderateMin = day.moderateZoneMinutes
    val totalMin = (calmMin + moderateMin + highMin).coerceAtLeast(1)

    NoopCard(tint = Palette.stressColor) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Overline("Time in band")

            TimeInBandRow(
                StressTotalsBand.Calm,
                DaytimeStress.Result.formatZoneCompact(calmMin),
                calmMin.toDouble() / totalMin,
            )
            TimeInBandRow(
                StressTotalsBand.Moderate,
                DaytimeStress.Result.formatZoneCompact(moderateMin),
                moderateMin.toDouble() / totalMin,
            )
            TimeInBandRow(
                StressTotalsBand.High,
                DaytimeStress.Result.formatZoneCompact(highMin),
                highMin.toDouble() / totalMin,
            )
            Text(
                "Minutes from scored ${DaytimeStress.bucketMinutes}-min windows (not rounded hours).",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
        }
    }
}

/** WHOOP-style high-zone minutes under the totals (Fable Stress-24/25/61). */
@Composable
private fun HighZoneMinutesCaption(day: DaytimeStress.Result) {
    val highMin = day.highZoneMinutes
    if (highMin <= 0) return
    val body = "You spent ${DaytimeStress.Result.formatZoneDuration(highMin)} in the high stress zone today."
    // Sparse coverage: WHOOP continuous PPG can still differ; stay honest about the sample base.
    val sparse = day.scoredHoursApprox < 8.0
    val note = if (sparse) {
        " From ${day.scored.size} scored windows (~${String.format(Locale.US, "%.1f", day.scoredHoursApprox)} h)."
    } else ""
    Text(
        body + note,
        style = NoopType.footnote,
        color = Palette.textTertiary,
    )
}

private enum class StressTotalsBand(val title: String, val color: Color) {
    Calm("Calm", StressRamp.CALM),         // blue — low stress
    Moderate("Moderate", StressRamp.STEADY), // green — balanced
    High("High", StressRamp.TENSE),        // amber — high
}

/** One band's share as a liquid tube; [durationLabel] is compact minutes (e.g. 2h 8m). */
@Composable
private fun TimeInBandRow(band: StressTotalsBand, durationLabel: String, share: Double) {
    val frac = share.coerceIn(0.0, 1.0)
    val has = durationLabel != "—"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "${band.title} $durationLabel" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(Metrics.legendSwatch)
                .clip(CircleShape)
                .background(if (has) band.color else Palette.surfaceInset),
        )
        Text(
            band.title,
            style = NoopType.captionNumber,
            color = if (has) band.color else Palette.textTertiary,
            modifier = Modifier.width(72.dp),
        )
        LiquidTube(
            frac = frac,
            tint = band.color,
            height = Metrics.progressHeight,
            animated = false,
            modifier = Modifier.weight(1f),
        )
        Text(
            durationLabel,
            style = NoopType.captionNumber,
            color = if (has) Palette.textSecondary else Palette.textTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
            maxLines = 1,
        )
    }
}

/** "avg 1.4 · 9h" summary — scored coverage in clock-hours (5-min buckets). */
private fun timelineTrailing(day: DaytimeStress.Result): String {
    val hrs = day.scoredHoursApprox
    val mean = day.dayMean ?: return String.format(Locale.US, "%.0fh", hrs)
    return "avg " + String.format(Locale.US, "%.1f", mean) +
        " · " + String.format(Locale.US, "%.0fh", hrs)
}

/**
 * A passive, in-app nudge to run a Breathe session after a sustained high-stress run. No
 * notification — just a card with a CTA that opens the existing trainer.
 */
@Composable
private fun SustainedBreatheCard(day: DaytimeStress.Result, onBreathe: () -> Unit) {
    NoopCard(tint = Palette.stressColor) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Overline("Sustained high stress", modifier = Modifier.weight(1f))
                val elevH = (day.sustainedRun.toDouble() / DaytimeStress.bucketsPerHour)
                    .coerceAtLeast(0.25)
                StatePill(
                    String.format(Locale.US, "%.0fh elevated", elevH),
                    tone = StrandTone.Warning,
                    showsDot = true,
                )
            }
            val elevHours = (day.sustainedRun.toDouble() / DaytimeStress.bucketsPerHour)
                .coerceAtLeast(0.25)
            Text(
                "Your last ${String.format(Locale.US, "%.0f", elevHours)} hours have stayed in the high band. A few minutes " +
                    "of paced breathing can help downshift your nervous system.",
                style = NoopType.subhead,
                color = Palette.textSecondary,
            )
            NoopButton(
                text = "Start a Breathe session",
                leadingIcon = Icons.Filled.Air,
                kind = NoopButtonKind.Primary,
                fullWidth = true,
                onClick = onBreathe,
            )
        }
    }
}

/** "6 am" / "2 pm" style hour-of-day label. */
private fun hourLabel(hour: Int): String {
    val h = ((hour % 24) + 24) % 24
    val ampm = if (h < 12) "am" else "pm"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12 $ampm"
}

/** Clock label for a 5-min stress bucket start (unix seconds, device local). */
private fun bucketClockLabel(startTs: Long, use24: Boolean): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = startTs * 1000L }
    val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val m = cal.get(java.util.Calendar.MINUTE)
    return if (use24) {
        String.format(Locale.US, "%02d:%02d", h, m)
    } else {
        val ampm = if (h < 12) "am" else "pm"
        val h12 = when (h % 12) { 0 -> 12; else -> h % 12 }
        if (m == 0) "$h12 $ampm" else String.format(Locale.US, "%d:%02d %s", h12, m, ampm)
    }
}


// MARK: - 2 · Today's tiles (uniform grid)

@Composable
private fun StressTiles(
    model: StressModel,
    nowLevel: Double? = null,
    calmBucketPct: Int? = null,
    calmBucketCount: Int? = null,
) {
    // Drop duplicate Stress tile (hero owns Now). Prefer Calm today from DaytimeStress buckets
    // when scored — same quiet-HR lane as the tip; fall back to 30-day low-stress day %.
    val tiles = ArrayList<@Composable (Modifier) -> Unit>(4)
    if (nowLevel != null) {
        tiles.add { m ->
            StatTile(
                modifier = m,
                label = "Today’s load",
                value = String.format(Locale.US, "%.1f", model.score),
                caption = "of 3 · RHR/HRV day",
                accent = StressRamp.color(model.score),
            )
        }
    }
    tiles.add { m ->
        MarkerTile(
            modifier = m,
            label = "Resting HR",
            value = model.rhrToday?.toString() ?: "—",
            unit = "bpm",
            delta = model.rhrDelta,
            accent = Palette.metricRose,
            higherIsStress = true,
        )
    }
    tiles.add { m ->
        MarkerTile(
            modifier = m,
            label = "HRV",
            value = model.hrvToday?.roundToInt()?.toString() ?: "—",
            unit = "ms",
            delta = model.hrvDelta,
            accent = Palette.metricPurple,
            higherIsStress = false,
        )
    }
    tiles.add { m ->
        StatTile(
            modifier = m,
            label = if (calmBucketPct != null) "Calm today" else "Calm days",
            value = calmBucketPct?.let { "$it%" } ?: model.calmTimeValue,
            caption = when {
                calmBucketPct != null && calmBucketCount != null ->
                    "of scored · ${calmBucketCount}×${DaytimeStress.bucketMinutes}m"
                else -> model.calmTimeCaption
            },
            accent = StressRamp.CALM,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(Metrics.gap)) {
                rowTiles.forEach { tile -> tile(Modifier.weight(1f)) }
                if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * A vs-baseline marker as a fixed-height [StatTile]. The delta is tinted by whether
 * the move is toward stress (warning) or recovery (positive). Mirrors macOS markerTile.
 */
@Composable
private fun MarkerTile(
    label: String,
    value: String,
    unit: String,
    delta: Double?,
    accent: Color,
    higherIsStress: Boolean,
    modifier: Modifier = Modifier,
) {
    val deltaText: String
    val deltaColor: Color
    if (delta != null && kotlin.math.abs(delta) >= 0.5) {
        val up = delta > 0
        val isStressful = (up == higherIsStress)
        deltaText = "${if (up) "+" else "−"}${kotlin.math.abs(delta).roundToInt()}"
        deltaColor = if (isStressful) Palette.statusWarning else Palette.statusPositive
    } else {
        deltaText = "base"
        deltaColor = Palette.textTertiary
    }
    StatTile(
        modifier = modifier,
        label = label,
        value = value,
        caption = unit,
        accent = accent,
        delta = deltaText,
        deltaColor = deltaColor,
        compactDelta = true,
    )
}

// MARK: - 3 · Trend (range-controlled)

@Composable
private fun StressLastNightCard(
    yesterdayStored: Double?,
    overnightMean: Double?,
    modifier: Modifier = Modifier,
) {
    if (yesterdayStored == null && overnightMean == null) {
        NoopCard(tint = Palette.restColor, modifier = modifier) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Overline("Last night")
                Text(
                    "No stored stress for yesterday yet, and no asleep buckets on today’s timeline.",
                    style = NoopType.subhead,
                    color = Palette.textTertiary,
                )
            }
        }
        return
    }
    NoopCard(tint = Palette.restColor, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Overline("Last night")
            yesterdayStored?.let { y ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            String.format(Locale.US, "%.1f", y),
                            style = NoopType.number(28f),
                            color = StressRamp.color(y),
                        )
                        Text(
                            "Yesterday’s banked stress",
                            style = NoopType.footnote,
                            color = Palette.textTertiary,
                        )
                    }
                    StatePill(
                        StressBand.forScore(y).title,
                        tone = StressBand.forScore(y).tone,
                        showsDot = true,
                    )
                }
            }
            overnightMean?.let { night ->
                Text(
                    "Overnight mean ${String.format(Locale.US, "%.1f", night)} · asleep buckets on today’s timeline",
                    style = NoopType.subhead,
                    color = Palette.restColor,
                )
            }
        }
    }
}

@Composable
private fun StressTrendSection(
    model: StressModel,
    modifier: Modifier = Modifier,
) {
    var range by remember { mutableStateOf(StressRange.Month) }
    val points = remember(model, range) { model.windowedTrend(range) }
    val historyStrip = remember(model, range) {
        model.windowedTrendPoints(range).takeLast(7)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        SectionHeader("Stress history", overline = "Prior days", trailing = range.label)
        if (points.size >= 2) {
            val avg = points.average()
            NoopCard(tint = Palette.stressColor) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Overline("Daily stress · ${range.label}")
                            Text(
                                "Banked 0–3 tips · oldest → newest",
                                style = NoopType.footnote,
                                color = Palette.textTertiary,
                            )
                        }
                        Text(
                            "avg " + String.format(Locale.US, "%.1f", avg),
                            style = NoopType.captionNumber,
                            color = Palette.textSecondary,
                        )
                    }
                    // Compact prior-day strip so history is readable without scrubbing the chart.
                    if (historyStrip.size >= 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            historyStrip.forEach { pt ->
                                val band = StressBand.forScore(pt.value)
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(StressRamp.color(pt.value).copy(alpha = 0.28f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            String.format(Locale.US, "%.1f", pt.value),
                                            style = NoopType.captionNumber,
                                            color = StressRamp.color(pt.value),
                                        )
                                    }
                                    Text(
                                        pt.day.takeLast(5),
                                        style = NoopType.footnote,
                                        color = Palette.textTertiary,
                                        maxLines = 1,
                                    )
                                    Text(
                                        band.title.take(1),
                                        style = NoopType.footnote,
                                        color = Palette.textTertiary,
                                    )
                                }
                            }
                        }
                    }
                    LineChart(
                        values = points,
                        modifier = Modifier.height(Metrics.chartHeight),
                        color = StressRamp.STEADY,
                        fill = true,
                        selectionEnabled = true,
                        formatValue = { String.format(Locale.US, "%.1f / 3", it) },
                        yAxisUnit = "/3",
                    )
                    HorizontalDivider(color = Palette.hairline)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TrendFooterItem("Today", String.format(Locale.US, "%.1f", model.score))
                        TrendFooterItem("Average", String.format(Locale.US, "%.1f", avg))
                        TrendFooterItem("Days", points.size.toString())
                    }
                }
            }
            // The one segmented control — full width, right-aligned.
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                SegmentedPillControl(
                    items = StressRange.entries,
                    selection = range,
                    label = { it.label },
                    onSelect = { range = it },
                )
            }
        } else {
            NoopCard(tint = Palette.stressColor) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Not enough recent days to chart stress history yet. Keep wearing your strap to populate it.",
                        style = NoopType.subhead,
                        color = Palette.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TrendFooterItem(label: String, value: String) {
    Column(modifier = Modifier.weight(1f)) {
        Overline(label)
        Text(value, style = NoopType.number(18f), color = Palette.textPrimary)
    }
}

// MARK: - 4 · Methodology (transparency)

@Composable
private fun StressMethodologyCard(model: StressModel, modifier: Modifier = Modifier) {
    NoopCard(tint = Palette.stressColor, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Overline("How this is computed")
            Text(
                if (model.usingStored) {
                    "Today's value is your recorded daily stress score (0-3)."
                } else {
                    "Stress is derived from two autonomic signals."
                },
                style = NoopType.body,
                color = Palette.textPrimary,
            )
            Text(
                "Daily load compares today's resting heart rate and HRV to your own 30-day " +
                    "baseline (logistic mid ≈ 1.5 when both sit at average). Intraday “Now” " +
                    "(the hero when banked) maps quiet HR + RMSSD onto 0–3 with a WHOOP-like " +
                    "calm floor near 0.5 — not 1.5. Same evening tip can still differ from the " +
                    "WHOOP app (different calm baselines — not a bug).",
                style = NoopType.subhead,
                color = Palette.textSecondary,
            )
            HorizontalDivider(color = Palette.hairline)
            Row(modifier = Modifier.fillMaxWidth()) {
                BandLegend("0-1", "LOW", StressRamp.CALM)
                BandLegend("1-2", "MEDIUM", StressRamp.STEADY)
                BandLegend("2-3", "HIGH", StressRamp.TENSE)
            }
            // Fable Stress-46 / Stress-38 — CONTINUED 2026-07-12 WHOOP-match.
            Text(
                "Intraday uses ~${DaytimeStress.bucketMinutes}-minute quiet-HR buckets, step still/walk/run, gravity motion, " +
                    "and sedentary bouts. Sleep windows tint the chart in Rest teal. Baevsky and LF/HF are lenses only — they do not change " +
                    "this 0–3 score. Factors: docs/STRESS_FACTORS_AND_LITERATURE.md.",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BandLegend(range: String, label: String, color: Color) {
    Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = NoopType.captionNumber, color = Palette.textPrimary)
            Text(range, style = NoopType.footnote, color = Palette.textTertiary)
        }
    }
}

// MARK: - Empty / loading states

@Composable
private fun StressLoading() {
    NoopCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Reading your heart-rate variability and resting heart rate…",
                style = NoopType.subhead,
                color = Palette.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StressBankingProgressNote(
    samples: Int,
    modifier: Modifier = Modifier,
) {
    // #167 + #207 — live + EMPTY scored: progress toward first tip, never invent stress.
    NoopCard(modifier = modifier, tint = Palette.stressColor) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stressBankingQuietHrProgress(samples, DaytimeStress.minHourHrSamples),
                style = NoopType.headline,
                color = Palette.textPrimary,
            )
            Text(
                "Connected — quiet heart-rate samples are banking toward your first daytime tip. " +
                    "No stress number until that gate clears.",
                style = NoopType.subhead,
                color = Palette.textSecondary,
            )
        }
    }
}

@Composable
private fun StressEmpty(
    liveConnected: Boolean = false,
    hasHistory: Boolean = false,
    bankingHrSamples: Int = 0,
) {
    when {
        // #167: live + EMPTY → banking progress; Wear CTA only when disconnected (#207).
        liveConnected -> StressBankingProgressNote(samples = bankingHrSamples)
        // Hot-fix: do NOT ask to pair/connect when beats or prior days already exist — a momentary
        // disconnect must not look like a fresh install.
        hasHistory -> DataPendingNote(
            title = "Waiting on today’s beats",
            body = "Stress uses banked heart rate. Wear your strap through the day — history stays " +
                "visible even when the strap isn’t connected right now.",
        )
        else -> DataPendingNote(
            title = "No stress history yet",
            body = "Wear through the day so heart rate can bank, or import a WHOOP export in Data Sources. " +
                "WHOOP app Stress labels are sparse here — this screen stays honestly empty until NOOP banks quiet HR. " +
                "NOOP never invents a stress day to fill this screen.",
        )
    }
}

// MARK: - Stress band

internal enum class StressBand(val title: String, val tone: StrandTone) {
    Low("LOW", StrandTone.Positive),
    Medium("MEDIUM", StrandTone.Warning),
    High("HIGH", StrandTone.Critical);

    companion object {
        fun forScore(score: Double): StressBand = when {
            score < 1.0 -> Low
            score < 2.0 -> Medium
            else -> High
        }
    }
}

// MARK: - Stress ramp (the WHOOP Stress sweep: blue → green → amber)
//
// The Stress screen's one ramp, matching the iOS StressRamp exactly. WHOOP has NO gold:
// calm reads as the link blue, a balanced day as positive green, and a high-stress day as
// warning amber. The PipBar tint, the day autonomic-load line, the Calm/Moderate/High totals
// bar and the trend all sample this SAME ramp, so the colour language is identical across the
// screen. Never the gold or red→green recovery ramp.

private object StressRamp {
    val CALM = Palette.accent           // calm WHOOP blue — low
    val STEADY = Palette.statusPositive // balanced WHOOP green — baseline
    val TENSE = Palette.statusWarning   // high WHOOP amber — high

    /** The 3-stop ramp, evenly spaced (blue → green → amber). */
    val stops: List<Pair<Float, Color>> = listOf(
        0.00f to CALM,
        0.50f to STEADY,
        1.00f to TENSE,
    )

    /** Sample the ramp at a 0–3 stress score. */
    fun color(score: Double): Color = Palette.sample(stops, (score / 3.0).toFloat())
}

// MARK: - Trend range (the W/M/3M/6M/1Y/ALL window, mirroring ExploreRange)

internal enum class StressRange(val label: String, val days: Int?) {
    Week("W", 7),
    Month("M", 30),
    Quarter("3M", 90),
    Half("6M", 180),
    Year("1Y", 365),
    All("ALL", null),
}

// MARK: - Stress model (transparent: stored value OR z-score derivation)

// #753: `internal` (was file-private) so Today's pinned Stress card can build the SAME model the detail
// screen shows and read `model.score`, instead of taking the stress series' last banked row. The pinned card
// and the detail page then derive today's score identically (stored row preferred, else live RHR/HRV
// baseline) and refresh on the same data, so the pinned card never lags the detail page (e.g. a stale "2").
// The constructor stays private; only the companion `build` factory is exposed.
internal class StressModel private constructor(
    val score: Double,            // 0–3 (today)
    val band: StressBand,
    val explanation: String,
    val rhrToday: Int?,
    val hrvToday: Double?,
    val rhrDelta: Double?,        // today − baseline mean (bpm)
    val hrvDelta: Double?,        // today − baseline mean (ms)
    val fullTrend: List<TrendPoint>, // entire daily proxy history, oldest → newest
    val calmTimeValue: String,
    val calmTimeCaption: String,
    val usingStored: Boolean,     // true when today's value came from the stored series
    /** Calendar day the [score] was scored against (may lag LocalDate when trailing shells). */
    val scoreDayKey: String,
) {
    data class TrendPoint(val day: String, val value: Double)

    /** The full daily proxy trend, sliced to the selected trailing window (count-based,
     *  matching the day budget). Falls back to ALL when the trailing slice has < 2 points. */
    fun windowedTrend(range: StressRange): List<Double> =
        windowedTrendPoints(range).map { it.value }

    /** Same window as [windowedTrend] but keeps day keys for the history strip. */
    fun windowedTrendPoints(range: StressRange): List<TrendPoint> {
        val days = range.days ?: return fullTrend
        val slice = fullTrend.takeLast(days)
        return if (slice.size >= 2) slice else fullTrend
    }

    companion object {
        /** Build from oldest→newest daily metrics plus any stored "stress" series.
         *  Returns null only when there is no usable signal at all.
         *
         *  Hot-fix 2026-07-13: skip trailing days that have neither a stored tip nor
         *  derivable RHR/HRV (common mid-day / post-midnight before overnight vitals land).
         *  Previously `days.lastOrNull()` with null RHR/HRV blanked the whole Stress screen
         *  even when prior days and daytime tips existed — and the empty CTA said "pair".
         */
        fun build(days: List<DailyMetric>, stored: Map<String, Double>): StressModel? {
            if (days.isEmpty()) return null

            // Walk back from newest until we find a day we can score (stored tip or RHR/HRV
            // against a prior baseline). Dropping empty trailing shells keeps history on screen.
            var end = days.lastIndex
            while (end >= 0) {
                val candidate = days[end]
                val prior = if (end > 0) days.subList(0, end) else emptyList()
                val base = prior.takeLast(30)
                val meanR = mean(base.mapNotNull { it.restingHr?.toDouble() })
                val meanH = mean(base.mapNotNull { it.avgHrv })
                val canDerive =
                    (candidate.restingHr != null && meanR != null) ||
                        (candidate.avgHrv != null && meanH != null)
                if (stored[candidate.day] != null || canDerive) break
                end--
            }
            if (end < 0) return null

            val scoredDays = days.subList(0, end + 1)
            val today = scoredDays.last()

            // Baseline window: up to 30 days ending the day BEFORE today, so "today" is
            // measured against its own recent past rather than itself.
            val history = if (scoredDays.size > 1) scoredDays.dropLast(1) else emptyList()
            val baseline = history.takeLast(30)

            val rhrBase = baseline.mapNotNull { it.restingHr?.toDouble() }
            val hrvBase = baseline.mapNotNull { it.avgHrv }

            val meanRHR = mean(rhrBase)
            val sdRHR = std(rhrBase, meanRHR)
            val meanHRV = mean(hrvBase)
            val sdHRV = std(hrvBase, meanHRV)

            // Markers: tip/score day may be an empty mid-day shell with only a daytime tip —
            // Fold 2026-07-16 / #208: still surface the freshest prior RHR/HRV (never invent).
            val markerDay = when {
                today.restingHr != null || today.avgHrv != null -> today
                else -> scoredDays.asReversed().firstOrNull {
                    it.restingHr != null || it.avgHrv != null
                } ?: today
            }

            val rhrT = markerDay.restingHr?.toDouble()
            val hrvT = markerDay.avgHrv

            val derivedAvailable =
                (today.restingHr != null && meanRHR != null) ||
                    (today.avgHrv != null && meanHRV != null)
            val storedToday = stored[today.day]
            if (storedToday == null && !derivedAvailable) return null

            val derivedToday: Double? = if (derivedAvailable) {
                squash(
                    rawScore(
                        today.restingHr?.toDouble(), meanRHR, sdRHR,
                        today.avgHrv, meanHRV, sdHRV,
                    ),
                )
            } else {
                null
            }

            val s = storedToday ?: derivedToday ?: 1.5
            val usingStored = storedToday != null
            val band = StressBand.forScore(s)
            val rhrDelta = if (rhrT != null && meanRHR != null) rhrT - meanRHR else null
            val hrvDelta = if (hrvT != null && meanHRV != null) hrvT - meanHRV else null
            val explanation = explanation(band, rhrDelta, hrvDelta)

            // Full daily proxy history: stored value if present for the day, else the
            // z-score derivation against the SAME baseline so the line is comparable.
            // Include trailing empty days when they have a stored tip (e.g. daytime Now).
            val pts = ArrayList<TrendPoint>()
            for (d in days) {
                val v = stored[d.day]
                if (v != null) {
                    pts.add(TrendPoint(d.day, v.coerceIn(0.0, 3.0)))
                    continue
                }
                val dRHR = d.restingHr?.toDouble()
                val dHRV = d.avgHrv
                if ((dRHR == null || meanRHR == null) && (dHRV == null || meanHRV == null)) continue
                pts.add(TrendPoint(d.day, squash(rawScore(dRHR, meanRHR, sdRHR, dHRV, meanHRV, sdHRV))))
            }

            // "Calm time": share of the last 30 charted days that sat in the LOW band.
            val recent = pts.takeLast(30)
            val calmValue: String
            val calmCaption: String
            if (recent.isEmpty()) {
                calmValue = "—"
                calmCaption = "needs history"
            } else {
                val calm = recent.count { it.value < 1.0 }
                val pct = (calm.toDouble() / recent.size * 100).roundToInt()
                calmValue = "$pct%"
                calmCaption = "low-stress days · ${recent.size}d"
            }

            return StressModel(
                score = s,
                band = band,
                explanation = explanation,
                rhrToday = markerDay.restingHr,
                hrvToday = hrvT,
                rhrDelta = rhrDelta,
                hrvDelta = hrvDelta,
                fullTrend = pts,
                calmTimeValue = calmValue,
                calmTimeCaption = calmCaption,
                usingStored = usingStored,
                scoreDayKey = today.day,
            )
        }

        // MARK: Stress math (pure helpers, ported from StressMath)

        private fun mean(xs: List<Double>): Double? =
            if (xs.isEmpty()) null else xs.sum() / xs.size

        /** Population standard deviation; 0 when there's no spread. */
        private fun std(xs: List<Double>, m: Double?): Double {
            if (m == null || xs.size <= 1) return 0.0
            val v = xs.sumOf { (it - m) * (it - m) } / xs.size
            return sqrt(v)
        }

        /** Combined autonomic z-score. RHR-up and HRV-down both push it positive. */
        private fun rawScore(
            rhrToday: Double?, meanRHR: Double?, sdRHR: Double,
            hrvToday: Double?, meanHRV: Double?, sdHRV: Double,
        ): Double {
            var sum = 0.0
            if (rhrToday != null && meanRHR != null && sdRHR > 0.0001) {
                sum += (rhrToday - meanRHR) / sdRHR        // up = stress
            }
            if (hrvToday != null && meanHRV != null && sdHRV > 0.0001) {
                sum += (meanHRV - hrvToday) / sdHRV        // down = stress
            }
            return sum
        }

        /** Logistic squash of the raw z-sum onto 0–3 (baseline 0 → 1.5). */
        private fun squash(raw: Double): Double =
            (3.0 / (1.0 + exp(-raw))).coerceIn(0.0, 3.0)

        private fun explanation(band: StressBand, rhrDelta: Double?, hrvDelta: Double?): String {
            val rhrUp = (rhrDelta ?: 0.0) > 1.0
            val hrvDn = (hrvDelta ?: 0.0) < -1.0
            val hrvUp = (hrvDelta ?: 0.0) > 1.0
            val rhrDn = (rhrDelta ?: 0.0) < -1.0
            return when (band) {
                StressBand.High -> when {
                    rhrUp && hrvDn -> "Resting HR is elevated and HRV is below your baseline, both classic signs of high activation. Prioritise rest, hydration and an easy day."
                    hrvDn -> "HRV has dropped well below your baseline, pointing to elevated stress or fatigue. Ease off and give your body time to recover."
                    rhrUp -> "Resting heart rate is running high versus your norm. Your body is under load today. Keep effort light."
                    else -> "Your autonomic markers are skewed toward stress today. Treat it as a recovery-focused day."
                }
                StressBand.Medium -> when {
                    rhrUp || hrvDn -> "Slightly off baseline (${if (rhrUp) "resting HR is a touch high" else "HRV is a little low"}), so you're moderately activated. Nothing alarming; just don't overreach."
                    else -> "You're sitting around your typical autonomic baseline: moderate stress, a normal, balanced day."
                }
                StressBand.Low -> when {
                    rhrDn && hrvUp -> "Resting heart rate is low and HRV is up. Your nervous system looks well-recovered and calm. A great day to push if you want to."
                    hrvUp -> "HRV is above baseline, a sign of a relaxed, well-recovered nervous system. Stress is low."
                    else -> "Resting heart rate and HRV are sitting at or below baseline: low physiological stress. You're in a calm, recovered state."
                }
            }
        }
    }
}
