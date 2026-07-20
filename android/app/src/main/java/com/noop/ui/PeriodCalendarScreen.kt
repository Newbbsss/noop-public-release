package com.noop.ui

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.analytics.CyclePhaseEngine
import com.noop.analytics.PeriodCalendar
import com.noop.data.PeriodCalendarStore
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Cycle calendar — designed first for people who bleed: full month grid, symptoms,
 * next-period windows, one-tap import. Strap temp/HRV/RHR may corroborate phase only.
 * Logs remain truth; never invents bleed days. Awareness only, not contraception.
 */
@Composable
fun PeriodCalendarScreen(
    vm: AppViewModel,
    onOpenSettingsPeriodTracking: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { PeriodCalendarStore.from(context) }
    var events by remember { mutableStateOf(store.loadEvents()) }
    var prefs by remember { mutableStateOf(store.loadPrefs()) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf(LocalDate.now().toString()) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    val v5 by vm.v5Signals.collectAsStateWithLifecycle()
    val cycleEnabled by vm.cycleTrackingEnabled.collectAsStateWithLifecycle()
    val recentDays by vm.recentDays.collectAsStateWithLifecycle()

    fun hasPeriodStarts(list: List<PeriodCalendar.Event> = events): Boolean =
        list.any { it.kind == PeriodCalendar.EventKind.PERIOD_START }

    // Keep the calendar store usable under More when the Cycle *tab* is hidden. Never force-re-enable
    // VM cycleTracking (that fought Settings "Hide tab" and bounced the bottom-bar tab back on).
    // Never force-disable the store when a log already exists — that hid an imported .pc.
    LaunchedEffect(cycleEnabled) {
        if (cycleEnabled) {
            if (!prefs.enabled) {
                prefs = prefs.copy(enabled = true)
                store.savePrefs(prefs)
            }
        } else if (!hasPeriodStarts(store.loadEvents())) {
            if (prefs.enabled) {
                prefs = prefs.copy(enabled = false)
                store.savePrefs(prefs)
            }
        } else if (!prefs.enabled) {
            // Logs exist, tab/awareness off — keep calendar logging alive for More → Cycle.
            prefs = prefs.copy(enabled = true)
            store.savePrefs(prefs)
        }
    }

    // Do NOT auto-complete onboarding when period starts already exist — Settings → Replay Cycle
    // setup sets onboardingComplete=false on purpose; a LaunchedEffect that re-armed complete
    // made "cycle reset / replay" a no-op. Import / finish paths set onboardingComplete themselves.

    // WHOOP learning: merge solid shift markers into the calendar (markers only — never invent bleed).
    LaunchedEffect(v5, prefs.whoopLearningEnabled, prefs.enabled) {
        if (prefs.enabled && prefs.whoopLearningEnabled) {
            val markers = PeriodCalendar.whoopSuggestedEvents(v5?.cycle)
            if (markers.isNotEmpty()) {
                store.mergeWhoopSignals(markers)
                events = store.loadEvents()
            }
        }
    }

    fun refresh() {
        events = store.loadEvents()
        prefs = store.loadPrefs()
    }

    // #237/#249 — soft Saved check in the day panel for ~1.2s after a successful log/toggle.
    var savedFlash by remember { mutableStateOf(false) }
    val saveFlashScope = rememberCoroutineScope()
    fun flashSaved() {
        savedFlash = true
        saveFlashScope.launch {
            delay(1_200L)
            savedFlash = false
        }
    }

    val snap = remember(events, prefs, v5) {
        PeriodCalendar.evaluate(
            today = LocalDate.now(),
            events = events,
            prefs = prefs,
            engine = v5?.cycle,
        ).also { s ->
            android.util.Log.i(
                "NoopCycle",
                "forecast enabled=${s.enabled} starts=${s.loggedStartCount} avg=${s.avgCycleLength} " +
                    "next=${s.nextPeriodLikely} windows=${s.forecastWindows.size} " +
                    "aug=${s.forecastWindows.any { it.likelyDay.startsWith("2026-08") || it.earliestDay.startsWith("2026-08") || it.latestDay.startsWith("2026-08") }} " +
                    "sample=${s.forecastWindows.take(3).joinToString { it.likelyDay }}",
            )
        }
    }

    val grid = remember(month, events, snap) {
        PeriodCalendar.monthGrid(month, LocalDate.now(), events, snap)
    }

    val dayEvents = remember(selectedDay, events) {
        events.filter { it.day == selectedDay }.sortedBy { it.createdAtMs }
    }

    // Measured WHOOP vitals for selected day (never invented).
    val dayMetric = remember(selectedDay, recentDays) {
        recentDays.firstOrNull { it.day == selectedDay }
    }

    val importScope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val name = runCatching {
                        context.contentResolver.query(
                            uri,
                            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                            null, null, null,
                        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    }.getOrNull().orEmpty()
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: ByteArray(0)
                    val result = com.noop.ingest.PcCalendarImport.parse(bytes, name)
                    result to name
                }
            }
            outcome.fold(
                onSuccess = { (result, _) ->
                    if (result.events.isEmpty()) {
                        importStatus = result.message
                        return@fold
                    }
                    store.mergeImport(result.events)
                    vm.setCycleTrackingEnabled(true)
                    store.savePrefs(store.loadPrefs().copy(enabled = true, onboardingComplete = true))
                    vm.refreshCycleFromPeriodLog()
                    refresh()
                    importStatus = result.message +
                        " · Tip: tap a day → Log period start · Soft rose = forecast only."
                },
                onFailure = {
                    importStatus =
                        "Could not read that file. Use UTF-8 CSV (date,type,note) or a My Calendar .pc backup."
                },
            )
        }
    }

    // Auto-import newest My Calendar .pc from Downloads when the log is empty —
    // SAF pickers are easy to miss on Fold; this is the path that actually gets tested.
    // If starts already exist, just make sure tracking is on so the calendar isn't gated.
    LaunchedEffect(Unit) {
        val existing = store.loadEvents()
        if (hasPeriodStarts(existing)) {
            // Collapse noisy .pc near-starts once so forecasts stay quiet.
            val dropped = store.refineNearPeriodStarts()
            if (!prefs.enabled || !cycleEnabled) {
                prefs = store.loadPrefs().copy(enabled = true)
                store.savePrefs(prefs)
                vm.setCycleTrackingEnabled(true)
                vm.refreshCycleFromPeriodLog()
                refresh()
                importStatus = "Opened your existing cycle log (${existing.count { it.kind == PeriodCalendar.EventKind.PERIOD_START }} starts)."
            } else if (dropped > 0) {
                refresh()
                importStatus = "Cleaned $dropped near-duplicate period starts from import noise."
            }
            return@LaunchedEffect
        }
        val pc = com.noop.data.DownloadsScanner.scan(context).pcFiles.firstOrNull() ?: return@LaunchedEffect
        runCatching {
            val result = com.noop.ingest.PcCalendarImport.parse(pc.readBytes(), pc.name)
            if (result.events.isEmpty()) {
                importStatus = result.message
                return@runCatching
            }
            store.mergeImport(result.events)
            prefs = store.loadPrefs().copy(enabled = true, onboardingComplete = true)
            store.savePrefs(prefs)
            vm.setCycleTrackingEnabled(true)
            vm.refreshCycleFromPeriodLog()
            refresh()
            importStatus = "${result.message} (auto from Downloads/${pc.name})"
        }.onFailure {
            importStatus = "Found ${pc.name} in Downloads but could not decode it. Tap Import."
        }
    }

    // Land on the upcoming predicted month when it's not this month (Aug blank was easy to miss).
    LaunchedEffect(snap.nextPeriodLikely, prefs.enabled) {
        if (!prefs.enabled) return@LaunchedEffect
        val next = snap.nextPeriodLikely ?: return@LaunchedEffect
        runCatching {
            val d = LocalDate.parse(next)
            if (!d.isBefore(LocalDate.now())) {
                month = YearMonth.from(d)
            }
        }
    }
    LazyScreenScaffold(
        title = null,
        topPadding = 12.dp,
        rowSpacing = 14.dp,
        topBackground = { LiquidScreenSky(fillHeight = true) },
        fullBleedBackground = true,
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Cycle",
                    style = NoopType.number(28f, weight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = Palette.textPrimary,
                )
                // SHIP #253 — phase sentence under title when known.
                val phaseLine = when (snap.phase) {
                    PeriodCalendar.CalendarPhase.UNKNOWN,
                    PeriodCalendar.CalendarPhase.LEARNING -> null
                    else -> "Now · ${snap.phase.label}"
                }
                Text(
                    phaseLine
                        ?: if (!prefs.onboardingComplete && !hasPeriodStarts()) {
                            "First-run setup"
                        } else {
                            "Your log · optional strap clues"
                        },
                    style = NoopType.caption,
                    color = Palette.textTertiary,
                )
                // SHIP #221/#236 — privacy + medical disclaimer near calendar predictions.
                Text(
                    "On this phone only · not shared with a partner unless you export. " +
                        "Awareness only — not contraception or medical advice.",
                    style = NoopType.caption,
                    color = Palette.textTertiary,
                )
            }
        }
        importStatus?.let { message ->
            item {
                Text(
                    message,
                    style = NoopType.subhead,
                    color = if (message.startsWith("Imported") || message.startsWith("Opened")) {
                        Palette.statusPositive
                    } else {
                        Palette.statusWarning
                    },
                )
            }
        }

        // First-run OR Settings replay (onboardingComplete=false). Allow with existing logs —
        // replay must not be blocked by hasPeriodStarts().
        if (!prefs.onboardingComplete) {
            item {
                CycleOnboardingFlow(
                    onFinished = { lastStart, cycleLen, periodLen, enableWhoop ->
                        if (lastStart != null) {
                            val already = store.loadEvents().any {
                                it.day == lastStart && it.kind == PeriodCalendar.EventKind.PERIOD_START
                            }
                            if (!already) {
                                store.addEvent(
                                    PeriodCalendar.Event(
                                        day = lastStart,
                                        kind = PeriodCalendar.EventKind.PERIOD_START,
                                    ),
                                )
                            }
                        }
                        prefs = prefs.copy(
                            enabled = true,
                            onboardingComplete = true,
                            avgCycleLengthOverride = cycleLen,
                            avgPeriodLengthOverride = periodLen,
                            whoopLearningEnabled = enableWhoop,
                        )
                        store.savePrefs(prefs)
                        vm.setCycleTrackingEnabled(true)
                        vm.refreshCycleFromPeriodLog()
                        refresh()
                    },
                    onImport = {
                        importLauncher.launch(
                            arrayOf(
                                "text/*",
                                "text/csv",
                                "application/csv",
                                "application/octet-stream",
                                "*/*",
                            ),
                        )
                    },
                    onSkipForNow = {
                        // Replay skip: keep tracking on when a log already exists.
                        prefs = prefs.copy(
                            onboardingComplete = true,
                            enabled = hasPeriodStarts() || prefs.enabled,
                        )
                        store.savePrefs(prefs)
                        refresh()
                    },
                )
            }
            return@LazyScreenScaffold
        }

        if (!prefs.enabled && !hasPeriodStarts()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cycle is off", style = NoopType.headline, color = Palette.textPrimary)
                    Text(
                        "Turn tracking on in Settings → Health & wellness → Period tracking. " +
                            "Then log starts, see phase, and unlock forecast windows. " +
                            "Predictions exist so you can plan pads ahead — not contraception.",
                        style = NoopType.subhead,
                        color = Palette.textSecondary,
                    )
                    WetBounceButton(
                        label = "Open Settings",
                        modifier = Modifier.fillMaxWidth(),
                        tint = Palette.accent,
                        onClick = onOpenSettingsPeriodTracking,
                    )
                }
            }
            return@LazyScreenScaffold
        }

        // Strap clues only — master Period tracking on/off lives in Settings (Gilbert P0).
        if (prefs.enabled) {
            item {
                StrapCluesRow(
                    whoopLearn = prefs.whoopLearningEnabled,
                    onWhoopLearn = { on ->
                        prefs = prefs.copy(whoopLearningEnabled = on)
                        store.savePrefs(prefs)
                        refresh()
                    },
                )
            }
        }

        // SHIP #222/#228 — empty before first start: clear CTA + sample log affordance.
        if (!hasPeriodStarts()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "No period starts logged yet",
                        style = NoopType.headline,
                        color = Palette.textPrimary,
                    )
                    Text(
                        "Pick a day on the calendar below and tap Log period start — or import Flo/PC CSV. " +
                            "Forecasts stay blank until you have at least one start.",
                        style = NoopType.subhead,
                        color = Palette.textSecondary,
                    )
                    WetBounceButton(
                        label = "Log today’s start",
                        modifier = Modifier.fillMaxWidth(),
                        tint = Palette.accent,
                        onClick = {
                            val today = LocalDate.now().toString()
                            selectedDay = today
                            month = YearMonth.now()
                            val already = store.loadEvents().any {
                                it.day == today && it.kind == PeriodCalendar.EventKind.PERIOD_START
                            }
                            if (!already) {
                                store.addEvent(
                                    PeriodCalendar.Event(
                                        day = today,
                                        kind = PeriodCalendar.EventKind.PERIOD_START,
                                    ),
                                )
                                flashSaved()
                                refresh()
                                vm.refreshCycleFromPeriodLog()
                            }
                        },
                    )
                }
            }
        }
        item { PhaseHeroCard(snap) }
        item {
            PredictionCard(
                snap = snap,
                onJumpToNext = {
                    val next = snap.nextPeriodLikely ?: return@PredictionCard
                    runCatching {
                        val d = LocalDate.parse(next)
                        month = YearMonth.from(d)
                        selectedDay = next
                    }
                },
                onJumpToday = {
                    month = YearMonth.now()
                    selectedDay = LocalDate.now().toString()
                },
            )
        }
        // Onboarding: import sits early when planning is still locked (<2 starts).
        if (snap.loggedStartCount < 2) {
            item {
                ImportCard(
                    onImport = {
                        importLauncher.launch(
                            arrayOf(
                                "text/*",
                                "text/csv",
                                "text/csv",
                                "application/csv",
                                "application/octet-stream",
                                "application/zip",
                                "*/*",
                            ),
                        )
                    },
                    highlightOnboarding = true,
                )
            }
        }
        item {
            MonthCalendarCard(
                month = month,
                grid = grid,
                selectedDay = selectedDay,
                snap = snap,
                onPrev = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
                onSelect = { selectedDay = it },
                onLongSelect = { day ->
                    selectedDay = day
                    store.togglePeriodStart(day)
                    vm.refreshCycleFromPeriodLog()
                    refresh()
                    flashSaved()
                },
            )
        }
        // SHIP #49/#229 — month chrome vs day panel; swipe month only when clearly horizontal.
        item {
            Text(
                "Swipe the month sideways (or use arrows) · day panel below stays on the selected day.",
                style = NoopType.caption,
                color = Palette.textTertiary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        item {
            DayDetailCard(
                day = selectedDay,
                events = dayEvents,
                dayMetric = dayMetric,
                predictedPhase = grid.firstOrNull { it.day == selectedDay }?.phase,
                isPredictedWindow = grid.firstOrNull { it.day == selectedDay }?.isPredictedWindow == true,
                isPredictedPeriod = grid.firstOrNull { it.day == selectedDay }?.isPredictedPeriod == true,
                savedFlash = savedFlash,
                onLog = { kind ->
                    store.addEvent(PeriodCalendar.Event(day = selectedDay, kind = kind))
                    if (kind == PeriodCalendar.EventKind.PERIOD_START) vm.refreshCycleFromPeriodLog()
                    refresh()
                    flashSaved()
                },
                onTogglePeriodStart = {
                    store.togglePeriodStart(selectedDay)
                    vm.refreshCycleFromPeriodLog()
                    refresh()
                    flashSaved()
                },
                onRemoveEvent = { e ->
                    store.removeEvent(e.day, e.kind, e.createdAtMs)
                    if (e.kind == PeriodCalendar.EventKind.PERIOD_START) vm.refreshCycleFromPeriodLog()
                    refresh()
                },
                onClearDay = {
                    store.removeAllForDay(selectedDay)
                    vm.refreshCycleFromPeriodLog()
                    refresh()
                },
            )
        }
        item { QuickLogRow(store = store, selectedDay = selectedDay, onChanged = {
            refresh()
            vm.refreshCycleFromPeriodLog()
            flashSaved()
        }) }
        item { PhaseContextCard(snap) }
        item {
            ReminderCard(prefs = prefs, onChange = {
                prefs = it
                store.savePrefs(it)
                refresh()
            })
        }
        // SHIP #219/#246 — replay first-run without wiping logs (also in Settings).
        item {
            TextButton(
                onClick = {
                    prefs = prefs.copy(onboardingComplete = false)
                    store.savePrefs(prefs)
                    refresh()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Replay Cycle setup",
                    style = NoopType.caption,
                    color = Palette.textSecondary,
                )
            }
        }
        if (snap.loggedStartCount >= 2) {
            item {
                ImportCard(onImport = {
                    // Include octet-stream so iOS My Calendar .pc backups appear in the picker.
                    importLauncher.launch(
                        arrayOf(
                            "text/*",
                            "text/csv",
                            "application/csv",
                            "application/octet-stream",
                            "application/zip",
                            "*/*",
                        ),
                    )
                })
            }
        }
        item {
            Text(
                snap.scienceNote + "\n" + PeriodCalendar.awarenessLine,
                style = NoopType.footnote,
                color = Palette.textTertiary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StrapCluesRow(
    whoopLearn: Boolean,
    onWhoopLearn: (Boolean) -> Unit,
) {
    // Flat row — no nested card chrome on the sky. Master Period tracking lives in Settings only.
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Strap clues", style = NoopType.subhead, color = Palette.textPrimary)
            Text("Temp · RHR · HRV hints only", style = NoopType.footnote, color = Palette.textTertiary)
        }
        Switch(checked = whoopLearn, onCheckedChange = onWhoopLearn)
    }
}

@Composable
private fun PhaseHeroCard(snap: PeriodCalendar.Snapshot) {
    // Quiet phase read — no radial glow orb (bolted AI chrome).
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Where you are", style = NoopType.overline, color = Palette.textTertiary)
        // #222 — before first period start, concrete CTA (not vague Unknown / Learning).
        if (snap.loggedStartCount == 0) {
            Text(
                "Mark your last period start on the calendar",
                style = NoopType.title1,
                color = Palette.textPrimary,
            )
        } else {
            Text(snap.phase.label, style = NoopType.title1, color = Palette.textPrimary)
            snap.cycleDay?.let {
                Text("Day $it of this cycle", style = NoopType.subhead, color = Palette.textSecondary)
            }
        }
        // Fable 200 #39 — one training note sentence, no nested card.
        val trainingNote = snap.note.lineSequence().firstOrNull()?.trim().orEmpty()
            .ifBlank { snap.note }
            .let { if (it.length > 96) it.take(93).trimEnd() + "…" else it }
        if (trainingNote.isNotBlank() && snap.loggedStartCount > 0) {
            Text("Training note", style = NoopType.overline, color = Palette.textTertiary)
            Text(
                trainingNote,
                style = NoopType.body,
                color = Palette.textSecondary,
                maxLines = 1,
            )
        }
        StatePill(
            when (snap.whoopConfidence) {
                CyclePhaseEngine.Confidence.SOLID -> "WHOOP solid"
                CyclePhaseEngine.Confidence.BUILDING -> "WHOOP building"
                else -> if (snap.lastPeriodStart != null) "Logs" else "Learning"
            },
            tone = StrandTone.Accent,
            showsDot = false,
        )
    }
}

@Composable
private fun PredictionCard(
    snap: PeriodCalendar.Snapshot,
    onJumpToNext: () -> Unit,
    onJumpToday: () -> Unit,
) {
    val longRangeReady = snap.forecastWindows.isNotEmpty()
    // Flat forecast read — no amber nested card wash.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Next period", style = NoopType.overline, color = Palette.textTertiary)
            StatePill(
                // Fable 200 #38 — window confidence, not an oracle.
                if (longRangeReady) "Window estimate" else "Learning pattern",
                tone = if (longRangeReady) StrandTone.Positive else StrandTone.Neutral,
                showsDot = false,
            )
        }
        val nextLikely = snap.nextPeriodLikely?.let { iso ->
            runCatching { LocalDate.parse(iso) }.getOrNull()
                ?.takeUnless { it.isBefore(LocalDate.now()) }
                ?.toString()
        }
        if (nextLikely != null) {
            Text(
                "Around ${prettyIso(nextLikely)}",
                style = NoopType.title1,
                color = Palette.textPrimary,
            )
            // SHIP #218 — never imply certainty; window estimate only.
            Text(
                "Estimate only · based on your logged starts (±4 day window). Not a prediction you should treat as sure.",
                style = NoopType.caption,
                color = Palette.textTertiary,
            )
            if (snap.nextPeriodEarliest != null && snap.nextPeriodLatest != null) {
                Text(
                    "Likely window ${prettyIso(snap.nextPeriodEarliest)} – ${prettyIso(snap.nextPeriodLatest)} " +
                        "(not a fixed day)",
                    style = NoopType.subhead,
                    color = Palette.textSecondary,
                )
                // Fable 200 #94 — quiet confidence band for the likely window (not an oracle mark).
                val earliest = runCatching { LocalDate.parse(snap.nextPeriodEarliest!!) }.getOrNull()
                val latest = runCatching { LocalDate.parse(snap.nextPeriodLatest!!) }.getOrNull()
                val likely = runCatching { LocalDate.parse(nextLikely) }.getOrNull()
                if (earliest != null && latest != null && !latest.isBefore(earliest)) {
                    val span = java.time.temporal.ChronoUnit.DAYS.between(earliest, latest).toInt().coerceAtLeast(1)
                    val peak = likely?.let {
                        java.time.temporal.ChronoUnit.DAYS.between(earliest, it).toInt().coerceIn(0, span)
                    } ?: (span / 2)
                    val left = peak
                    val right = (span - peak).coerceAtLeast(0)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    ) {
                        if (left > 0) {
                            Box(
                                Modifier
                                    .weight(left.toFloat())
                                    .fillMaxHeight()
                                    .background(Palette.metricAmber.copy(alpha = 0.18f)),
                            )
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Palette.metricAmber.copy(alpha = 0.55f)),
                        )
                        if (right > 0) {
                            Box(
                                Modifier
                                    .weight(right.toFloat())
                                    .fillMaxHeight()
                                    .background(Palette.metricAmber.copy(alpha = 0.18f)),
                            )
                        }
                    }
                }
            }
            snap.daysUntilLikely?.let { d ->
                Text(
                    when {
                        d > 1 -> "About $d days away"
                        d == 1 -> "Tomorrow"
                        else -> "Around today"
                    },
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
            // Fable 200 #43/#122 — medical disclaimer adjacent to predictions (also in About).
            Text(
                "Awareness only, not contraception or medical advice. Talk to a clinician for health decisions.",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onJumpToNext) {
                    Text("Show that month", style = NoopType.footnote)
                }
                TextButton(onClick = onJumpToday) {
                    Text("This month", style = NoopType.footnote)
                }
            }
        } else {
            Text(
                "Log a period start (or import .pc) to unlock next-period dates and window shading.",
                style = NoopType.body,
                color = Palette.textSecondary,
            )
        }
        if (!longRangeReady && snap.loggedStartCount < 2) {
            Text(
                "Log ${maxOf(0, 2 - snap.loggedStartCount)} more start" +
                    (if (snap.loggedStartCount == 1) "" else "s") +
                    " for long-range planning.",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
        }
        // Last logged start is history — never labeled "next" (SHIP #46).
        Row(Modifier.fillMaxWidth()) {
            MiniStat("Avg cycle", snap.avgCycleLength?.let { "$it d" } ?: "—", Modifier.weight(1f))
            MiniStat("Avg period", snap.avgPeriodLength?.let { "$it d" } ?: "—", Modifier.weight(1f))
            MiniStat("Last logged", snap.lastPeriodStart?.let { prettyIso(it) } ?: "—", Modifier.weight(1f))
        }
        Text(
            "Last logged = history. Next likely (below) is forecast only — never the same row.",
            style = NoopType.caption,
            color = Palette.textTertiary,
        )
        // SHIP #50 — ±4 day forecast cap is intentional (not empty far months broken).
        if (snap.forecastWindows.isNotEmpty()) {
            Text(
                "Forecast windows stay within ±4 days of likely start so one noisy import cannot wash the month.",
                style = NoopType.caption,
                color = Palette.textTertiary,
            )
        }
    }
}

@Composable
private fun MonthCalendarCard(
    month: YearMonth,
    grid: List<PeriodCalendar.DayCell>,
    selectedDay: String,
    snap: PeriodCalendar.Snapshot,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (String) -> Unit,
    onLongSelect: (String) -> Unit = {},
) {
    // Fable 200 #44 — month header weight matches Today date (title2), not dense headline.
    val title = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    val hasFuturePred = snap.forecastWindows.any {
        runCatching { LocalDate.parse(it.latestDay).isAfter(LocalDate.now()) }.getOrDefault(false)
    } || (snap.nextPeriodLikely != null &&
        runCatching { LocalDate.parse(snap.nextPeriodLikely!!).isAfter(LocalDate.now()) }.getOrDefault(false))
    // Flat on sky — no nested GlowCard (nested cards = ugly / heavy).
    // SHIP #229 — month swipe is axis-locked so LazyColumn vertical scroll wins on diagonal/vertical.
    val forecastFloodScale = remember(grid) {
        val predictedInMonth = grid.count {
            it.inMonth && (it.isPredictedPeriod || it.isPredictedWindow)
        }
        when {
            predictedInMonth >= 18 -> 0.35f
            predictedInMonth >= 12 -> 0.55f
            predictedInMonth >= 8 -> 0.75f
            else -> 1f
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var totalX = 0f
                var totalY = 0f
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (change.changedToUp()) break
                    val d = change.positionChange()
                    totalX += d.x
                    totalY += d.y
                    if (abs(totalY) > abs(totalX) && abs(totalY) > 28f) {
                        return@awaitEachGesture
                    }
                    if (abs(totalX) > abs(totalY) * 1.5f && abs(totalX) > 16f) {
                        change.consume()
                    }
                } while (event.changes.any { it.pressed })
                if (abs(totalX) < 96f || abs(totalX) < abs(totalY) * 1.8f) return@awaitEachGesture
                if (totalX < 0f) onNext() else onPrev()
            }
        },
    ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
                }
                Text(
                    title,
                    style = NoopType.title2,
                    color = Palette.textPrimary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month (future ok)")
                }
            }
            if (hasFuturePred) {
                Text(
                    "Soft rose = forecast likely · amber = window · solid marks = logged starts (not the same wash).",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            } else {
                Text(
                    "Hold a day to log or clear period start · tap to select",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                    Text(
                        d,
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            grid.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { cell ->
                        DayCellView(
                            cell = cell,
                            selected = cell.day == selectedDay,
                            // SHIP #359 — when import noise paints many forecast days, dial washes way down.
                            floodScale = forecastFloodScale,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(cell.day) },
                            onLongClick = { onLongSelect(cell.day) },
                        )
                    }
                }
            }
            if (forecastFloodScale < 1f) {
                Text(
                    "Forecast soft · many estimated days this month (not a logged orange flood).",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendDot(Palette.metricRose, "Period")
                LegendDot(Palette.metricAmber, "Window")
                LegendDot(Palette.accent, "WHOOP")
                LegendDot(Palette.metricPurple, "Note")
            }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = NoopType.footnote, color = Palette.textTertiary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCellView(
    cell: PeriodCalendar.DayCell,
    selected: Boolean,
    modifier: Modifier = Modifier,
    /** 1f = normal; lower when the month is forecast-heavy (#359). */
    floodScale: Float = 1f,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val dayNum = cell.day.takeLast(2).trimStart('0').ifEmpty { "0" }
    // Quiet calendar: logged period is clear; forecasts are hairline washes, not orange flood.
    // No phase letters on every cell — that read as "period-aware chrome" across the month.
    val predScale = floodScale.coerceIn(0.25f, 1f)
    val bg = when {
        cell.hasPeriod -> Palette.metricRose.copy(alpha = 0.38f)
        cell.isPredictedPeriod -> Palette.metricRose.copy(alpha = 0.10f * predScale)
        cell.isPredictedWindow -> Palette.metricAmber.copy(alpha = 0.07f * predScale)
        cell.hasWhoopMarker -> Palette.accent.copy(alpha = 0.10f)
        selected -> Palette.surfaceInset
        else -> Color.Transparent
    }
    val textColor = when {
        !cell.inMonth -> Palette.textTertiary.copy(alpha = 0.45f)
        cell.isToday -> Palette.accent
        cell.hasPeriod -> Palette.metricRose
        else -> Palette.textPrimary
    }
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .then(if (selected) Modifier.border(1.5.dp, Palette.accent, RoundedCornerShape(10.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(2.dp)
            .semantics {
                contentDescription = buildString {
                    append("Day ${cell.day}")
                    cell.phase?.let { append(", ${it.label}") }
                    if (cell.isPredictedWindow) append(", predicted window")
                    if (cell.isPredictedPeriod) append(", predicted period")
                    append(". Hold to toggle period start.")
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(dayNum, style = NoopType.footnote, color = textColor)
        // Tiny forecast tick (not a wash) — one quiet signal under the day number.
        if (cell.inMonth && (cell.isPredictedPeriod || cell.isPredictedWindow) && !cell.hasPeriod) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(width = 10.dp, height = 2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (cell.isPredictedPeriod) Palette.metricRose.copy(alpha = 0.55f)
                        else Palette.metricAmber.copy(alpha = 0.45f),
                    ),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (cell.hasSpotting) Box(Modifier.size(4.dp).clip(CircleShape).background(Palette.metricRose.copy(0.7f)))
            if (cell.hasSex) Box(Modifier.size(4.dp).clip(CircleShape).background(Palette.metricPurple))
            if (cell.hasSymptom) Box(Modifier.size(4.dp).clip(CircleShape).background(Palette.metricAmber))
            if (cell.hasWhoopMarker) Box(Modifier.size(4.dp).clip(CircleShape).background(Palette.accent))
        }
    }
}

@Composable
private fun DayDetailCard(
    day: String,
    events: List<PeriodCalendar.Event>,
    dayMetric: com.noop.data.DailyMetric?,
    predictedPhase: PeriodCalendar.CalendarPhase?,
    isPredictedWindow: Boolean,
    isPredictedPeriod: Boolean,
    savedFlash: Boolean = false,
    onLog: (PeriodCalendar.EventKind) -> Unit,
    onTogglePeriodStart: () -> Unit,
    onRemoveEvent: (PeriodCalendar.Event) -> Unit,
    onClearDay: () -> Unit,
) {
    val hasPeriodStart = events.any { it.kind == PeriodCalendar.EventKind.PERIOD_START }
    // Flat day panel — GlowCard here stacked on calendar = nested chrome.
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.surfaceRaised.copy(alpha = 0.55f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("This day", style = NoopType.footnote, color = Palette.textSecondary)
            // #237/#249 — soft check + Saved for ~1.2s after a successful log/toggle.
            if (savedFlash) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Palette.statusPositive,
                        modifier = Modifier.size(14.dp),
                    )
                    Text("Saved", style = NoopType.caption, color = Palette.statusPositive)
                }
            }
        }
        Text(prettyIso(day), style = NoopType.headline, color = Palette.textPrimary)
            // Future / phase readout for the selected cell (works into next months).
            when {
                isPredictedPeriod -> Text(
                    "Predicted period day (window) · not logged yet",
                    style = NoopType.subhead,
                    color = Palette.metricRose,
                )
                isPredictedWindow -> Text(
                    "Inside next-period window · awareness only",
                    style = NoopType.subhead,
                    color = Palette.metricAmber,
                )
            }
            predictedPhase?.let { ph ->
                Text("Phase if cycle continues: ${ph.label}", style = NoopType.subhead, color = Palette.textSecondary)
            }
            // Measured strap vitals for that day only — blank if not banked.
            if (dayMetric != null) {
                Text("From your strap that day · blank if missing", style = NoopType.footnote, color = Palette.textTertiary)
                Row(Modifier.fillMaxWidth()) {
                    MiniStat("RHR", dayMetric.restingHr?.toString() ?: "—", Modifier.weight(1f))
                    MiniStat(
                        "HRV",
                        dayMetric.avgHrv?.let { v -> kotlin.math.round(v).toInt().toString() } ?: "—",
                        Modifier.weight(1f),
                    )
                    MiniStat(
                        "Skin Δ",
                        dayMetric.skinTempDevC?.let { String.format(Locale.US, "%+.2f", it) } ?: "—",
                        Modifier.weight(1f),
                    )
                    MiniStat(
                        "SpO₂%",
                        dayMetric.spo2Pct?.let { String.format(Locale.US, "%.0f", it) } ?: "—",
                        Modifier.weight(1f),
                    )
                }
                if (dayMetric.spo2Pct == null && (dayMetric.spo2Red != null || dayMetric.spo2Ir != null)) {
                    Text(
                        "Raw SpO₂ ADC banked; no calibrated % (would be fake without WHOOP curve).",
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                }
            } else {
                Text("No banked WHOOP day metrics for this date yet.", style = NoopType.footnote, color = Palette.textTertiary)
            }
            if (events.isEmpty()) {
                Text("No logs this day — use chips below, or mark period start.", style = NoopType.subhead, color = Palette.textSecondary)
            } else {
                events.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "• ${e.kind.label}${if (e.note.isNotBlank()) " — ${e.note}" else ""} (${e.source})",
                            style = NoopType.subhead,
                            color = Palette.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onRemoveEvent(e) }) {
                            Text("Remove", style = NoopType.footnote, color = Palette.metricRose)
                        }
                    }
                }
                TextButton(onClick = onClearDay) {
                    Text("Clear all logs on this day", style = NoopType.footnote, color = Palette.metricRose)
                }
            }
            // Primary: Log period start. Secondary symptoms live in chips below (SHIP #47/#245).
            TextButton(onClick = onTogglePeriodStart) {
                Text(
                    if (hasPeriodStart) "Remove period start from this day"
                    else "Log period start",
                    style = NoopType.subhead,
                )
            }
            Text(
                "Symptoms & notes: chips below · expand More in this panel for secondary logs",
                style = NoopType.caption,
                color = Palette.textTertiary,
            )
    }
}

@Composable
private fun QuickLogRow(
    store: PeriodCalendarStore,
    selectedDay: String,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    fun pickDay(onDay: (String) -> Unit) {
        val cal = Calendar.getInstance()
        // Prefill selected day
        PeriodCalendar.parse(selectedDay)?.let {
            cal.set(it.year, it.monthValue - 1, it.dayOfMonth)
        }
        DatePickerDialog(
            context,
            { _, y, m, d -> onDay(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)) },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    val chips = listOf(
        "Period start" to PeriodCalendar.EventKind.PERIOD_START,
        "Flow" to PeriodCalendar.EventKind.PERIOD_DAY,
        "Period end" to PeriodCalendar.EventKind.PERIOD_END,
        "Spotting" to PeriodCalendar.EventKind.SPOTTING,
        "Notes" to PeriodCalendar.EventKind.NOTE,
        "Sex" to PeriodCalendar.EventKind.SEX,
        "Light" to PeriodCalendar.EventKind.FLOW_LIGHT,
        "Medium" to PeriodCalendar.EventKind.FLOW_MEDIUM,
        "Heavy" to PeriodCalendar.EventKind.FLOW_HEAVY,
        "Cramps" to PeriodCalendar.EventKind.CRAMPS,
        "Headache" to PeriodCalendar.EventKind.HEADACHE,
        "Low mood" to PeriodCalendar.EventKind.MOOD_LOW,
        "High mood" to PeriodCalendar.EventKind.MOOD_HIGH,
        "Low energy" to PeriodCalendar.EventKind.ENERGY_LOW,
        "High energy" to PeriodCalendar.EventKind.ENERGY_HIGH,
        "Poor sleep" to PeriodCalendar.EventKind.SLEEP_POOR,
        "Bloating" to PeriodCalendar.EventKind.BLOATING,
        "Pad" to PeriodCalendar.EventKind.PAD_CHANGE,
    )

    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        // SHIP #48 — teachable order: start → flow → end → symptoms/notes.
        SectionHeader(title = "Log", overline = "Start · flow · end · then symptoms · notes")
        chips.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, kind) ->
                    QuickChip(
                        label = label,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // One tap logs on the calendar-selected day (including future).
                            store.addEvent(PeriodCalendar.Event(day = selectedDay, kind = kind))
                            onChanged()
                        },
                        onLongClick = {
                            pickDay { day ->
                                store.addEvent(PeriodCalendar.Event(day = day, kind = kind))
                                onChanged()
                            }
                        },
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.surfaceRaised)
            .border(1.dp, Palette.hairline, RoundedCornerShape(14.dp))
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = NoopType.footnote, color = Palette.textPrimary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PhaseContextCard(snap: PeriodCalendar.Snapshot) {
    val m = snap.modifiers
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Phase · training", style = NoopType.overline, color = Palette.textTertiary)
        Text("Does not rewrite Charge / Effort / Rest — awareness only for how you might feel training.", style = NoopType.footnote, color = Palette.textTertiary)
        Text(m.recoveryNote, style = NoopType.subhead, color = Palette.textSecondary)
        Text(m.strainNote, style = NoopType.subhead, color = Palette.textSecondary)
        Text(m.bmrNote, style = NoopType.subhead, color = Palette.textSecondary)
        if (m.scienceCite.isNotBlank()) {
            Text(m.scienceCite, style = NoopType.footnote, color = Palette.textTertiary)
        }
    }
}

@Composable
private fun ReminderCard(prefs: PeriodCalendar.Prefs, onChange: (PeriodCalendar.Prefs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cycle reminders", style = NoopType.overline, color = Palette.textTertiary)
        Text(
            "Period heads-ups only — not Sleep alarms or system Notifications.",
            style = NoopType.caption,
            color = Palette.textTertiary,
        )
        ToggleRow("Reminders", prefs.remindersEnabled) { onChange(prefs.copy(remindersEnabled = it)) }
        ToggleRow("Night before", prefs.nightBeforeReminder) { onChange(prefs.copy(nightBeforeReminder = it)) }
        ToggleRow("Morning of", prefs.morningOfReminder) { onChange(prefs.copy(morningOfReminder = it)) }
        // SHIP #220 — typical length editor not buried after onboarding only.
        Text("Typical lengths", style = NoopType.overline, color = Palette.textTertiary)
        Text(
            "Overrides used for forecast when set · leave blank to use your logged average.",
            style = NoopType.caption,
            color = Palette.textTertiary,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Cycle days", style = NoopType.body, color = Palette.textPrimary, modifier = Modifier.weight(1f))
            val cycle = prefs.avgCycleLengthOverride ?: 28
            TextButton(
                onClick = {
                    onChange(prefs.copy(avgCycleLengthOverride = (cycle - 1).coerceAtLeast(21)))
                },
            ) { Text("−", color = Palette.textSecondary) }
            Text("$cycle", style = NoopType.bodyNumber, color = Palette.textPrimary)
            TextButton(
                onClick = {
                    onChange(prefs.copy(avgCycleLengthOverride = (cycle + 1).coerceAtMost(45)))
                },
            ) { Text("+", color = Palette.textSecondary) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Period days", style = NoopType.body, color = Palette.textPrimary, modifier = Modifier.weight(1f))
            val period = prefs.avgPeriodLengthOverride ?: 5
            TextButton(
                onClick = {
                    onChange(prefs.copy(avgPeriodLengthOverride = (period - 1).coerceAtLeast(2)))
                },
            ) { Text("−", color = Palette.textSecondary) }
            Text("$period", style = NoopType.bodyNumber, color = Palette.textPrimary)
            TextButton(
                onClick = {
                    onChange(prefs.copy(avgPeriodLengthOverride = (period + 1).coerceAtMost(10)))
                },
            ) { Text("+", color = Palette.textSecondary) }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = NoopType.body, color = Palette.textPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ImportCard(onImport: () -> Unit, highlightOnboarding: Boolean = false) {
    // Quiet import — no desktop-tool dead ends. Onboarding copy when planning is still locked.
    NoopCard(tint = null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (highlightOnboarding) "Unlock 12-month plan" else "Import calendar",
                    style = NoopType.headline,
                    color = Palette.textPrimary,
                )
                StatePill("On this phone", tone = StrandTone.Positive, showsDot = true)
            }
            Text(
                if (highlightOnboarding) {
                    "Import My Calendar .pc (Downloads) or log 2 period starts. Forecast unlocks after two starts."
                } else {
                    "My Calendar .pc from Downloads, or CSV (date,type,note). Decodes here — review starts after import."
                },
                style = NoopType.subhead,
                color = Palette.textSecondary,
            )
            WetBounceButton(
                label = "Import .pc or CSV",
                modifier = Modifier.fillMaxWidth(),
                tint = if (highlightOnboarding) Palette.accent else Palette.metricCyan,
                onClick = onImport,
            )
        }
    }
}

private fun prettyIso(iso: String): String = runCatching {
    val d = LocalDate.parse(iso)
    "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
}.getOrDefault(iso)

/** Soft glass glow card — continuous radial fade, no hard block patches. */
@Composable
fun GlowCard(
    tint: Color,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .frostedCardSurface(tint = tint, cornerRadius = 18.dp, washStrength = 1.15f)
            .padding(16.dp),
    ) {
        content()
    }
}
