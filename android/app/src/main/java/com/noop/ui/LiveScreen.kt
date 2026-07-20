package com.noop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import com.noop.R
import com.noop.analytics.BatteryEstimator
import com.noop.analytics.HrZones
import com.noop.analytics.HrvAnalyzer
import com.noop.analytics.SpotHrvReading
import com.noop.analytics.Sport
import com.noop.analytics.WorkoutSport
import com.noop.ble.LiveState
import com.noop.ble.WhoopModel
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Live — the real-time strap view + hardware-test surface. A big smoothed HR number,
 * a connection pill, a battery/last-event status grid, and connect/disconnect/buzz
 * controls. Ports LiveView.swift to Compose. Toggles the strap's real-time HR stream
 * on/off as the screen enters/leaves composition.
 */

// MARK: - Liquid hero tokens (the liquid Live restyle)
//
// Soft frost radius matches Alarm/Today glass — BodyConsole uses frostedCardSurface, not a hard slab.
private val LIVE_HERO_RADIUS: Dp = 26.dp

@Composable
fun LiveScreen(viewModel: AppViewModel, onManageDevices: () -> Unit = {}) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val bpm by viewModel.bpm.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    // Active band name (MW-6) — names the band whose live data the console shows; falls back to "WHOOP".
    val activeDeviceName by viewModel.activeDeviceName.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var wornMgCandidate by remember { mutableStateOf<com.noop.data.PairedDeviceRow?>(null) }
    LaunchedEffect(Unit) {
        wornMgCandidate = runCatching { viewModel.pairedDevices() }.getOrDefault(emptyList())
            .firstOrNull { isWornMgDeviceCandidate(it) }
    }
    val liveLooksLike5AmSibling =
        com.noop.ble.WhoopBleClient.isStaleUnwornSiblingName(live.advertisingName.orEmpty())
    val activeWorkout by viewModel.activeWorkout.collectAsStateWithLifecycle()
    val lastWorkout by viewModel.lastWorkout.collectAsStateWithLifecycle()

    // Imperial/Metric display preference (D#103). Live distance/pace are computed from metres + sec/km
    // and re-labelled here. Display-only.
    val context = LocalContext.current
    val strapBatt = remember(
        live.connected, live.batteryPct, live.charging,
        live.batteryFreshCount, live.linkUpAtMs,
    ) {
        resolveStrapBatteryDisplay(context, live)
    }
    val unitSystem = UnitPrefs.system(context)
    // Effort display scale (#268) — routes the live + saved workout Effort read-outs. Display-only.
    val effortScale = UnitPrefs.effortScale(context)
    // Same day-cycle gate as the liquid Today (LiquidScreenSky.kt): the time-of-day sky settles behind the
    // top content when the user hasn't opted out; otherwise the scaffold paints the plain dark canvas.
    val showDayCycleBackground = remember { NoopPrefs.showDayCycleBackground(context) }

    // The runtime Bluetooth permission gates scanning. If it isn't granted, the Connect button
    // REQUESTS it (rather than silently doing nothing), then connects once allowed. Shared with
    // Settings → Re-scan via rememberRequestScan so no entry point can forget the gate (issue #1).
    val requestConnect = rememberRequestScan { viewModel.connect() }
    // Ding + full-screen charge UI are owned by StrapChargingHost in AppRoot (any tab).
    // Live still shows a large hero card that can re-open the full-screen presentation.

    // Keep the realtime HR stream on while this screen is visible (ref-counted in the ViewModel, so
    // navigating to Health Monitor — which also wants it — doesn't stop it). Refresh battery on bond.
    DisposableEffect(Unit) {
        viewModel.requestRealtimeHr()
        onDispose { viewModel.releaseRealtimeHr() }
    }
    LaunchedEffect(live.bonded) {
        if (live.bonded) viewModel.getBattery()
    }

    // Usable live link: full bond OR streaming HR (WHOOP 3 / MG HR-only paths).
    val activeConnection = live.connected && (live.bonded || live.streamingLiveHR || live.heartRate != null)

    // Live HR zone for the focal readout's colour world (presentation only — same shared HrZones model
    // the live-workout screen uses). 0 = below Zone 1 / no HR yet.
    val profile = remember { ProfileStore.from(context.applicationContext) }
    val zoneSet = remember(profile.hrMax) { HrZones.zones(maxHR = profile.hrMax.toDouble()) }
    val liveZone = bpm?.let { zoneSet.zoneNumber(it.toDouble()) } ?: 0

    // HR-zone coaching state, shown read-only here; the toggles live in Automations.
    val zoneCoaching by viewModel.zoneCoaching.collectAsStateWithLifecycle()
    val zone5Bpm = zoneSet.zones.firstOrNull { it.number == 5 }?.lower?.roundToInt() ?: 0

    // PERF (#707): the eager ScreenScaffold built (and accessibility-walked) every section up front; on a
    // live-ticking console that long column is what the Compose semantics copy hits each scroll frame.
    // Hoisting these two presentation-only sheet toggles out of the (now-lazy) content lambda — they were
    // shared across sibling sections — and rendering the sheets at body level (an overlay either way, so
    // appearance/behaviour-identical) lets the body migrate to LazyScreenScaffold below. Each former
    // top-level child becomes one `item { }` in the SAME order/spacing, so only on-screen sections compose
    // and semanticize. The live/bpm body reads are intentionally LEFT as-is (this screen's whole purpose is
    // the live readout); see the report note.
    var showSportPicker by remember { mutableStateOf(false) }
    var showHrvSnapshot by remember { mutableStateOf(false) }
    // Live workout mode (#238): the full-screen in-exercise overlay. Normally opened at workout START
    // (StartWorkoutSheet); this lets the Today "workout in progress" indicator re-open it for a session
    // already in flight by consuming the ViewModel's one-shot on appear (iOS parity:
    // LiveView.consumeActiveWorkoutRequest). Closing just hides it; the workout keeps recording.
    var showLiveWorkout by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // consumeActiveWorkoutRequest() returns true exactly once per raise, and only while a workout is
        // active, so a stale flag can never open an empty overlay.
        if (viewModel.consumeActiveWorkoutRequest()) showLiveWorkout = true
    }

    // GPS workout sport picker — the shared sheet (also used on the Workouts screen, #115). Rendered at
    // body level so it floats over Live as an overlay regardless of list position (unchanged behaviour).
    if (showSportPicker) {
        StartWorkoutSheet(vm = viewModel, onDismiss = { showSportPicker = false })
    }

    // Manual HRV snapshot (#127) — a still, seated 60s R-R reading. A plain full-screen Dialog so it floats
    // over Live; gated on a bonded connection (the reading needs the live R-R stream).
    if (showHrvSnapshot) {
        Dialog(
            onDismissRequest = { showHrvSnapshot = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            // Tell the reading where its R-R is coming from so the caveat is honest: a WHOOP 5/MG derives
            // R-R from the optical pulse signal (noisier) while a WHOOP 4 / chest strap is electrical R-R.
            // Driven off the picked strap model.
            val hrvSource = when (selectedModel) {
                WhoopModel.WHOOP5_MG -> SpotHrvReading.Source.OPTICAL_PPG
                // WHOOP 3.0 and 4.0 both stream optical wrist HR over the standard profile here.
                WhoopModel.WHOOP3 -> SpotHrvReading.Source.OPTICAL_PPG
                WhoopModel.WHOOP4 -> SpotHrvReading.Source.OPTICAL_PPG
            }
            HrvSnapshotScreen(
                viewModel = viewModel,
                source = hrvSource,
                onClose = { showHrvSnapshot = false },
            )
        }
    }

    // The full-screen live-workout overlay (#238). A plain full-screen Dialog so it floats over Live, the
    // same idiom WorkoutStartSection uses; opened by the Today indicator's one-shot above (or a future
    // in-screen re-open). Guarded on an active workout so it never shows an empty overlay. Dismiss hides it;
    // End (inside) stops the workout.
    if (showLiveWorkout && activeWorkout != null) {
        Dialog(
            onDismissRequest = { showLiveWorkout = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            LiveWorkoutScreen(vm = viewModel, onClose = { showLiveWorkout = false })
        }
    }

    LazyScreenScaffold(
        // SHIP #161 — speak "Live" (BLE stream), not "Live HR" / Monitor twin.
        title = stringResource(R.string.nav_live),
        subtitle = stringResource(R.string.live_subtitle),
        topBackground = if (showDayCycleBackground) { { LiquidScreenSky(fillHeight = true) } } else null,
        fullBleedBackground = showDayCycleBackground,
    ) {

        // Active band row (MW-6) — names the band the console is reading, with a "Manage devices"
        // affordance that opens the Devices screen. Additive; the connect/disconnect controls below are
        // untouched. Mirrors the iOS Live screen's active-band header + Manage-devices link.
        item {
        ActiveBandRow(
            name = activeDeviceName ?: "WHOOP",
            liveBleName = live.advertisingName?.takeIf { it.isNotBlank() },
            liveConnected = live.connected,
            liveLooksLike5AmSibling = liveLooksLike5AmSibling,
            wornMgLabel = wornMgCandidate?.let { displayName(it) },
            onManageDevices = onManageDevices,
            onUseWornMg = wornMgCandidate?.let { mg ->
                {
                    scope.launch {
                        viewModel.preferWornMgDevice(mg)
                        wornMgCandidate = runCatching { viewModel.pairedDevices() }.getOrDefault(emptyList())
                            .firstOrNull { isWornMgDeviceCandidate(it) }
                    }
                }
            },
        )
        }

        // Console header — the pill + a connection-mode badge (+ a live SYNCING badge during a history
        // offload), with battery / worn / last-sync stats. Mirrors the macOS consoleHeader.
        item {
        ConsoleHeader(live = live, activeConnection = activeConnection)
        }

        // Primary Connect affordance, surfaced ABOVE the fold whenever there's no link — the real
        // Connect control otherwise lives far below, past the Signal Trust grid, so an offline user
        // saw only inert copy up top. Gated purely on `!live.connected`, so it disappears the instant
        // the radio connects. Mirrors the macOS offlineConnectCallout.
        if (!live.connected) {
            item {
            OfflineConnectCallout(
                scanning = live.scanning,
                onConnect = { requestConnect() },
            )
            }
        }

        // Why it's in this state and what to try (permission, strap busy, not found…).
        live.statusNote?.let { note ->
            item {
            Text(
                note,
                style = NoopType.footnote,
                color = Palette.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            }
        }

        // Strap wiped its Bluetooth bond (firmware reset / official WHOOP app re-bond): show the forget+
        // re-pair steps in-app instead of looping a dead reconnect — parity with the macOS v1.73 banner.
        live.reconnectGuide?.let { guide ->
            item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.surfaceRaised, RoundedCornerShape(12.dp))
                    .border(1.dp, Palette.statusWarning.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "Can't connect - your strap's pairing was reset",
                    style = NoopType.subhead,
                    color = Palette.textPrimary,
                )
                Text(guide, style = NoopType.footnote, color = Palette.textSecondary)
            }
            }
        }

        // Honest sync outcome for a cloud-free app. While offloading, say so plainly — the brief
        // "· syncing" pill suffix is easy to miss (#91/#93). Otherwise: a non-silent error if the
        // last offload stalled, else a relative "history synced N ago". (PR #85; sync-visibility v1.70)
        // The item is gated on this block actually having something to render — in the old eager Column an
        // empty branch produced NO child (no spacing gap); an unconditional lazy `item {}` would instead
        // insert a 0-height row that `spacedBy(20.dp)` flanks, so the guard preserves the exact spacing.
        if (live.backfilling || live.lastSyncError != null || live.lastSyncAt != null) {
        item {
        if (live.backfilling) {
            // INDETERMINATE on purpose: the strap never tells us how many records remain, so a percent
            // would be a lie. A small spinner + the live acked-chunk count is the honest "it's working"
            // signal. The chunk count only appears once the first chunk lands (0 reads as "starting"). (#93)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 2.dp,
                    color = Palette.accent,
                )
                Text(
                    if (live.syncChunksThisSession > 0)
                        "Syncing your strap history… ${live.syncChunksThisSession} chunks pulled"
                    else "Syncing your strap history…",
                    style = NoopType.footnote,
                    color = Palette.textSecondary,
                )
            }
        } else {
            val syncError = live.lastSyncError
            if (syncError != null) {
                Text(
                    syncError,
                    style = NoopType.footnote,
                    color = Palette.statusWarning,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                live.lastSyncAt?.let { at ->
                    Text(
                        "History synced ${relativeAgo(at)}",
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        }
        }

        // AirPods-style charge hero when charging — tap re-opens full-screen overlay.
        if (live.charging == true && live.connected) {
            item {
                ChargingHeroCard(
                    pct = live.batteryPct ?: strapBatt?.pct,
                    model = selectedModel,
                    viewModel = viewModel,
                )
            }
        } else if (strapBatt != null) {
            // Always show a compact battery strip — predicted while offline / until live is trusted.
            item {
                BatteryStatusStrip(
                    pct = strapBatt.pct,
                    charging = strapBatt.charging,
                    model = selectedModel,
                    viewModel = viewModel,
                )
            }
        }

        // Body console — focal live HR VESSEL + live physiology (R-R thread, rolling RMSSD, frame/event).
        item {
        BodyConsole(live = live, bpm = bpm, activeConnection = activeConnection, zone = liveZone, hrMax = profile.hrMax)
        }

        // Signal Trust rail — one tile per signal that has to be current for the console to be trusted.
        item {
        SignalTrustRail(live = live, bpm = bpm, activeConnection = activeConnection)
        }

        // Honest live datastream panel — what the strap is actually sending right now (no invented metrics).
        item {
        LiveDatastreamCard(
            live = live,
            bpm = bpm,
            model = selectedModel,
            deviceName = activeDeviceName,
        )
        }

        // Live MG step counter (raw motion ticks + calibrated estimate when k is set).
        if (selectedModel == WhoopModel.WHOOP5_MG || selectedModel == WhoopModel.WHOOP4) {
            item {
                LiveStepsCard(viewModel = viewModel, model = selectedModel, connected = live.connected)
            }
        }

        // Honest live vitals board: only measured or clearly labeled estimates.
        item {
            HonestLiveVitalsCard(viewModel = viewModel, bpm = bpm, live = live)
        }

        // Max HR + the top-zone entry threshold (read-only; manage coaching in Automations).
        item {
        MaxHrZoneCard(hrMax = profile.hrMax, zone5Bpm = zone5Bpm, coachingOn = zoneCoaching)
        }

        // (The Start-workout sheet + HRV-snapshot Dialog were hoisted to the body above — they're overlays
        // that float regardless of list position, so this is appearance/behaviour-identical and keeps the
        // composable-only `remember`/Dialog out of the LazyListScope lambda.)

        // Session console — record or inspect the current stream.
        item {
        SectionHeader(
            title = stringResource(R.string.live_session_title),
            overline = stringResource(R.string.live_session_overline),
        )
        }

        // Manual workout — start/stop a session yourself; records HR + strain until you end it.
        // This block emits MULTIPLE siblings in its `else` branch (the actions Row, the last-workout note,
        // the HRV button), which in the old eager scaffold were spaced by the column's `spacedBy(20.dp)`.
        // Wrapping the whole block in one lazy item, they'd lose that inter-child spacing — so an explicit
        // `Column(spacedBy(20.dp))` inside the item reproduces the exact gaps (the if-branch's single card
        // is unaffected). Spacing to the neighbouring items is the LazyColumn's own `spacedBy(20.dp)`.
        item {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        val w = activeWorkout
        if (w != null) {
            var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
            var confirmEnd by remember { mutableStateOf(false) }
            LaunchedEffect(w.startMs) {
                while (true) { nowMs = System.currentTimeMillis(); delay(1000) }
            }
            val elapsedS = ((nowMs - w.startMs) / 1000).coerceAtLeast(0)
            NoopCard(tint = Palette.effortColor) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("● ${w.sport.name.uppercase()}", style = NoopType.overline, color = Palette.statusCritical)
                        Spacer(Modifier.weight(1f))
                        Text(
                            // Shared clock: M:SS up to an hour, H:MM:SS past it (so a long session reads
                            // "1:30:00", not "90:00"), the same format the Today indicator uses.
                            elapsedClock(elapsedS),
                            style = NoopType.number(22f), color = Palette.textPrimary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.gap)) {
                        StatTile(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.live_stat_hr),
                            value = bpm?.toString() ?: "—",
                            accent = if (bpm == null) Palette.textPrimary else Palette.metricRose,
                        )
                        StatTile(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.live_stat_avg),
                            value = if (w.avgHr > 0) "${w.avgHr}" else "—",
                        )
                        StatTile(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.live_stat_peak),
                            value = if (w.peakHr > 0) "${w.peakHr}" else "—",
                        )
                        StatTile(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.domain_effort),
                            value = UnitFormatter.effortDisplayOrEmpty(w.liveStrain, effortScale),
                            accent = w.liveStrain.takeIf { it > 0.0 }?.let { Palette.strainColor(it) }
                                ?: Palette.textPrimary,
                        )
                    }
                    if (w.gpsEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.gap)) {
                            StatTile(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.live_stat_distance),
                                value = liveDistance(w.distanceM, unitSystem),
                            )
                            StatTile(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.live_stat_pace),
                                value = w.paceSecPerKm?.let { livePace(it, unitSystem) } ?: "—",
                            )
                        }
                    }
                    Button(
                        onClick = { confirmEnd = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Palette.statusCritical, contentColor = Palette.surfaceBase,
                        ),
                    ) {
                        Text(stringResource(R.string.live_end_workout), style = NoopType.captionNumber)
                    }
                }
            }
            ConfirmEndWorkout(
                visible = confirmEnd,
                onConfirm = { confirmEnd = false; viewModel.endWorkout() },
                onDismiss = { confirmEnd = false },
            )
        } else {
            // Start-workout + a Refresh-battery action, gated on a live link (parity with the macOS
            // sessionActions). The Refresh button re-reads strap battery / connection on demand.
            Row(horizontalArrangement = Arrangement.spacedBy(Metrics.gap), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { showSportPicker = true },
                    modifier = Modifier.weight(1f),
                    enabled = activeConnection,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Palette.accent, contentColor = Palette.surfaceBase,
                    ),
                ) {
                    Text(
                        "Start workout", style = NoopType.captionNumber,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.getBattery() },
                    modifier = Modifier.weight(1f),
                    enabled = activeConnection,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.accent),
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).padding(end = 4.dp),
                    )
                    Text(
                        "Refresh", style = NoopType.captionNumber,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                    )
                }
            }
            lastWorkout?.let { row ->
                val mins = ((row.durationS ?: 0.0) / 60).toInt()
                val parts = listOfNotNull(
                    "$mins min",
                    row.distanceM?.let { liveDistance(it, unitSystem) },
                    row.avgHr?.let { "$it avg bpm" },
                    row.strain?.takeIf { it > 0.0 }?.let {
                        "strain ${UnitFormatter.effortDisplay(it, effortScale)}"
                    },
                )
                Text(
                    "✓ ${row.sport} saved · ${parts.joinToString(" · ")}",
                    style = NoopType.footnote, color = Palette.textSecondary,
                )
                row.routePolyline?.let { RouteCanvas(it, modifier = Modifier.padding(top = 8.dp)) }
            }

            // Manual HRV snapshot (#127) — a still, seated 60s R-R reading. Needs the live R-R stream,
            // so it's gated on a bonded connection just like the workout/refresh actions above.
            OutlinedButton(
                onClick = { showHrvSnapshot = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = activeConnection,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.restBright),
            ) {
                Icon(
                    Icons.Filled.MonitorHeart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).padding(end = 4.dp),
                )
                Text(
                    stringResource(R.string.hrv_take_reading), style = NoopType.captionNumber,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                )
            }
        }
        }
        }

        // Strap picker — choose the model before scanning so we look for exactly one device family.
        // Shown whenever we're not actively streaming, so a user with both a WHOOP 4 and a 5/MG can
        // switch between them (it used to hide once `bonded`, which stuck after the first pairing).
        if (!(live.connected && live.bonded)) {
            // Two siblings (picker Row + optional 5/MG guidance) that the eager column spaced by 20dp —
            // an inner `Column(spacedBy(20.dp))` reproduces that gap inside the single lazy item.
            item {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Metrics.gap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.live_strap_label), style = NoopType.footnote, color = Palette.textSecondary)
                SegmentedPillControl(
                    items = WhoopModel.entries.toList(),
                    selection = selectedModel,
                    label = { it.displayName },
                    onSelect = { viewModel.setSelectedModel(it) },
                )
            }
            // Proactive 5/MG guidance (#130): the strap bonds to one host at a time, so a scan finds
            // nothing while it's still paired in the official WHOOP app. Shown the moment 5/MG is picked.
            if (selectedModel == WhoopModel.WHOOP5_MG) {
                Text(
                    stringResource(R.string.live_mg_one_app_note),
                    style = NoopType.footnote,
                    color = Palette.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
            }
            }
        }

        // Controls.
        item {
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.gap), modifier = Modifier.fillMaxWidth()) {
            // Compact, single-line labels: with three weight(1f) buttons in a row, the default
            // body style + icon could wrap "Re-scan"/"Searching…" to two lines on narrow phones,
            // making one button taller than the others. captionNumber + maxLines=1 keeps the row
            // even. Connect disables while a scan is in flight so it can't be re-tapped mid-search.
            Button(
                onClick = { requestConnect() },
                modifier = Modifier.weight(1f),
                enabled = !live.scanning,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.accent,
                    contentColor = Palette.surfaceBase,
                ),
            ) {
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 4.dp),
                )
                Text(
                    when {
                        live.scanning -> "Searching…"
                        live.connected -> "Re-scan"
                        else -> "Connect"
                    },
                    style = NoopType.captionNumber,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }

            OutlinedButton(
                // #921: the confirmed one-shot sequence (pattern + RUN_ALARM where the family gate
                // allows it, acked). A bare pattern write here matched the iOS silent no-buzz path.
                onClick = { viewModel.buzzStrapOnce() },
                modifier = Modifier.weight(1f),
                enabled = live.bonded,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.accent),
            ) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 4.dp),
                )
                Text(
                    "Buzz",
                    style = NoopType.captionNumber,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }

            OutlinedButton(
                onClick = { viewModel.disconnect() },
                modifier = Modifier.weight(1f),
                enabled = live.connected,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.statusCritical),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 4.dp),
                )
                Text(
                    "End",
                    style = NoopType.captionNumber,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        }

        // Manual "Sync now" — kick a historical offload on demand instead of waiting for the 15-min
        // periodic timer (#93). Only meaningful once bonded (the offload needs the command channel), and
        // disabled mid-session so a double-tap can't fight the in-flight offload — viewModel.syncNow()
        // also no-ops in that case, this is just the matching UI state. While syncing, the button shows
        // an INDETERMINATE spinner (NEVER a percent — total pending records are unknowable from the
        // protocol); the "Syncing your strap history… N chunks pulled" line above carries the live count.
        // #295 — Alongside: encrypted history stays with WHOOP; show blocked honesty instead of Sync CTA.
        if (live.alongsideMode) {
            item {
                Text(
                    "History stays with WHOOP app · turn Alongside off for full offload",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (live.bonded) {
            item {
            OutlinedButton(
                onClick = { viewModel.syncNow() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !live.backfilling,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.accent),
            ) {
                if (live.backfilling) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 4.dp),
                        strokeWidth = 2.dp,
                        color = Palette.accent,
                    )
                } else {
                    Icon(
                        Icons.Filled.Sync,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 4.dp),
                    )
                }
                Text(
                    if (live.backfilling) "Syncing…" else "Sync now",
                    style = NoopType.captionNumber,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
            }
        }

        // Foolproof connection walkthrough — detects each blocker (WHOOP app, Bluetooth,
        // permission) and offers a one-tap fix. Hidden once the strap is bonded.
        if (!live.bonded) {
            item {
            ConnectionHelp(viewModel, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// MARK: - Console header

/**
 * Read-only Max-HR + top-zone card. Max HR is the age-based value from Settings; the Zone 5 entry
 * (≥ 90% of max) is where HR-zone coaching buzzes. Managing coaching lives in Automations.
 * Reimplemented from @cbarrado's PR #350.
 */
@Composable
private fun MaxHrZoneCard(hrMax: Int, zone5Bpm: Int, coachingOn: Boolean) {
    NoopCard {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap), modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Metrics.gap)) {
                StatTile(
                    modifier = Modifier.weight(1f),
                    label = "Max HR",
                    value = "$hrMax bpm",
                    accent = Palette.textPrimary,
                )
                StatTile(
                    modifier = Modifier.weight(1f),
                    label = "Top zone",
                    value = "≥ $zone5Bpm bpm",
                    accent = if (coachingOn) Palette.accent else Palette.textTertiary,
                )
            }
            Text(
                if (coachingOn)
                    "Strap buzzes when you climb into Zone 5 (≥ $zone5Bpm bpm). Manage it in Automations → Haptic coaching."
                else
                    "Turn on HR-zone coaching in Automations for a wrist buzz when you reach Zone 5 (≥ $zone5Bpm bpm).",
                style = NoopType.footnote,
                color = Palette.textTertiary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Active band row (MW-6): names the band whose live data the console is showing, with a "Manage devices"
 * affordance that opens the Devices screen. Additive — it sits above the console header and never touches
 * the connect/disconnect controls. Mirrors the iOS Live screen's active-band header + Manage-devices link.
 * Multi-bond: when live LE looks like an unworn 5AM sibling, offers Use worn MG (same as Devices/Settings).
 */
@Composable
private fun ActiveBandRow(
    name: String,
    liveBleName: String? = null,
    liveConnected: Boolean = false,
    liveLooksLike5AmSibling: Boolean = false,
    wornMgLabel: String? = null,
    onManageDevices: () -> Unit,
    onUseWornMg: (() -> Unit)? = null,
) {
    NoopCard(padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Filled.Watch,
                    contentDescription = null,
                    tint = Palette.accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Overline("Active band")
                    Text(name, style = NoopType.headline, color = Palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (liveConnected && liveBleName != null &&
                        !liveBleName.equals(name, ignoreCase = true)
                    ) {
                        Text(
                            "Live Bluetooth name · $liveBleName",
                            style = NoopType.footnote,
                            color = Palette.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (liveLooksLike5AmSibling) {
                        Text(
                            stringResource(R.string.live_5am_sibling_note),
                            style = NoopType.footnote,
                            color = Palette.textTertiary,
                        )
                    }
                }
                val manageInteraction = remember { MutableInteractionSource() }
                val manageLabel = stringResource(R.string.live_manage_devices)
                val manageA11y = stringResource(R.string.live_manage_devices_a11y)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .liquidPress(manageInteraction)
                        .clickable(
                            interactionSource = manageInteraction,
                            indication = null,
                            onClick = onManageDevices,
                        )
                        .semantics { contentDescription = manageA11y }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(manageLabel, style = NoopType.subhead, color = Palette.accent)
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Palette.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (liveLooksLike5AmSibling && wornMgLabel != null && onUseWornMg != null) {
                val useWornLabel = stringResource(R.string.live_use_worn_mg, wornMgLabel)
                val useWornA11y = stringResource(R.string.live_use_worn_mg_a11y, wornMgLabel)
                TextButton(
                    onClick = onUseWornMg,
                    modifier = Modifier.semantics { contentDescription = useWornA11y },
                ) {
                    Text(
                        useWornLabel,
                        style = NoopType.subhead,
                        color = Palette.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsoleHeader(live: LiveState, activeConnection: Boolean) {
    val context = LocalContext.current
    val strapBatt = remember(
        live.connected, live.batteryPct, live.charging,
        live.batteryFreshCount, live.linkUpAtMs,
    ) {
        resolveStrapBatteryDisplay(context, live)
    }
    NoopCard(padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Badges row — pill + connection-mode badge + a live SYNCING badge during an offload.
            val (label, tone) = when {
                live.encryptedBond && live.backfilling ->
                    context.getString(R.string.live_link_bonded_syncing) to StrandTone.Accent
                live.encryptedBond -> context.getString(R.string.live_link_bonded) to StrandTone.Positive
                live.bonded -> context.getString(R.string.live_link_hr_not_fully_paired) to StrandTone.Warning
                ringStreaming(live) ->
                    context.getString(R.string.live_link_streaming) to StrandTone.Positive   // #56: trusted non-WHOOP stream
                live.connected -> context.getString(R.string.live_link_connected) to StrandTone.Warning
                live.scanning -> context.getString(R.string.live_link_searching) to StrandTone.Warning
                else -> context.getString(R.string.live_link_disconnected) to StrandTone.Critical
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatePill(label, tone = tone, pulsing = live.bonded || live.scanning)
                // Suppress the redundant rose "OFFLINE" badge while fully offline — the pill already
                // reads "Disconnected" in critical/rose. Keep it for every informative state (FULL BOND
                // / LIVE HR ONLY / CONNECTING / PAIRED). Gate matches exactly the "OFFLINE" branch.
                if (showsModeBadge(live, activeConnection)) {
                    SourceBadge(connectionModeBadge(live, activeConnection), tint = connectionModeColor(live, activeConnection))
                }
                if (live.backfilling) {
                    SourceBadge("SYNCING ${live.syncChunksThisSession}", tint = Palette.metricCyan)
                }
            }
            // Stats row — battery / worn / last-sync. Worn is only trustworthy on a live link.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                // Charging bolt next to the battery % when the strap reports it's charging (PR #568 reimpl).
                HeaderStat(
                    "Battery",
                    strapBatt?.let { "${it.pctInt}%" } ?: "—",
                    charging = strapBatt?.charging == true,
                )
                HeaderStat("Worn", if (activeConnection) (if (live.worn) "Yes" else "No") else "—")
                HeaderStat("Last sync", lastSyncLabel(live))
            }
            // #267 — quiet reconnect policy near connection status.
            Text(
                LifeChapterLacquer.SETTINGS_RECONNECT_POLICY,
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
        }
    }
}

/**
 * In-list AirPods-style charge hero. Tap opens full-screen (ding + remaining time live there).
 * App-wide full-screen is [StrapChargingHost] in AppRoot.
 */
@Composable
private fun ChargingHeroCard(
    pct: Double?,
    model: WhoopModel,
    viewModel: AppViewModel,
) {
    val p = (pct ?: 0.0).coerceIn(0.0, 100.0)
    val infinite = rememberInfiniteTransition(label = "chargeHero")
    val pulse by infinite.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heroPulse",
    )
    var timeToFull by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pct, model) {
        val remain = 100.0 - p
        timeToFull = if (remain <= 0.5) "Full"
        else {
            val hours = remain / 40.0
            if (hours < 1.0) String.format("~%.0f min to full", hours * 60)
            else String.format("~%.1f h to full", hours)
        }
    }
    // Local re-open of full screen (same dialog as host).
    var localFull by remember { mutableStateOf(false) }
    if (localFull) {
        FullScreenChargingDialog(
            pct = pct,
            model = model,
            viewModel = viewModel,
            onDismiss = { localFull = false },
        )
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            // Opaque lacquer — translucent GlowCard let Live cards bleed through mint copy (unreadable).
            .background(
                Palette.surfaceRaised.copy(alpha = if (Palette.isLight) 0.98f else 0.94f),
            )
            .border(1.dp, StrapChargeMint.copy(alpha = 0.50f), RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { localFull = true }
            .semantics { contentDescription = "Charging ${p.roundToInt()} percent. Tap for full screen." }
            .padding(16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Charging · tap for full screen",
                style = NoopType.footnote,
                color = Palette.textPrimary,
            )
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 14.dp.toPx()
                    val pad = stroke / 2
                    drawArc(
                        color = Palette.hairline.copy(alpha = 0.35f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(pad, pad),
                        size = Size(size.minDimension - stroke, size.minDimension - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = StrapChargeMint,
                        startAngle = -90f,
                        sweepAngle = (360f * (p / 100f).toFloat()).coerceIn(0f, 360f),
                        useCenter = false,
                        topLeft = Offset(pad, pad),
                        size = Size(size.minDimension - stroke, size.minDimension - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ChargeBoltMark(
                        size = 32.dp,
                        tint = StrapChargeMint.copy(alpha = 0.70f + 0.30f * pulse),
                    )
                    Text(
                        "${p.roundToInt()}%",
                        style = NoopType.display(48f),
                        color = Palette.textPrimary,
                    )
                    Text(
                        if (p >= 99.5) "Full" else "Charging",
                        style = NoopType.footnote,
                        color = Palette.textSecondary,
                    )
                }
            }
            timeToFull?.let {
                Text(it, style = NoopType.headline, color = StrapChargeMint)
            }
            Text(
                "Ding plays when charging starts · charge limit not on open BLE",
                style = NoopType.footnote,
                color = Palette.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BatteryStatusStrip(
    pct: Double?,
    charging: Boolean,
    model: WhoopModel,
    viewModel: AppViewModel,
) {
    var daysLeft by remember { mutableStateOf("—") }
    LaunchedEffect(pct, model) {
        val rated = BatteryEstimator.ratedLifeHours(model.isWhoop5Family)
        val now = System.currentTimeMillis() / 1000L
        val samples = withContext(Dispatchers.IO) {
            runCatching {
                viewModel.repo.batterySamples("my-whoop", from = now - 14L * 86400L, to = now, limit = 400)
                    .mapNotNull { r -> r.soc?.let { r.ts to it } }
            }.getOrDefault(emptyList())
        }
        val est = BatteryEstimator.estimate(samples, rated)
        daysLeft = est?.let {
            if (it.daysRemaining >= 1) String.format("%.1fd left", it.daysRemaining)
            else String.format("%.0fh left", it.remainingHours)
        } ?: "—"
    }
    NoopCard(padding = 12.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Battery ${pct?.roundToInt() ?: "—"}%",
                style = NoopType.subhead,
                color = if (charging) StrapChargeMint else Palette.textPrimary,
            )
            Text(daysLeft, style = NoopType.subhead, color = Palette.textSecondary)
            Text(
                if (charging) "Charging" else "On wrist",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
        }
        // SHIP #265 — overnight charge reminder when SOC is low and not charging.
        if (!charging && pct != null && pct < 35.0) {
            Text(
                "Charge overnight so sleep banking doesn’t miss a low pack.",
                style = NoopType.caption,
                color = Palette.statusWarning,
            )
        }
    }
}

@Composable
private fun HeaderStat(title: String, value: String, charging: Boolean = false) {
    // AirPods-style concentric soft rings + bolt while charging (or SoC rising on MG).
    val infinite = rememberInfiniteTransition(label = "charge")
    val pulse by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chargePulse",
    )
    val ring by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "chargeRing",
    )
    val ring2 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = 350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "chargeRing2",
    )
    Column(horizontalAlignment = Alignment.Start) {
        Text(title.uppercase(), style = NoopType.footnote, color = Palette.textTertiary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                value,
                style = NoopType.captionNumber,
                color = if (charging) StrapChargeMint else Palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (charging) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                    // Two expanding soft rings (AirPods case energy feel)
                    Box(
                        modifier = Modifier
                            .size((22 + 16 * ring).dp)
                            .clip(CircleShape)
                            .background(StrapChargeMint.copy(alpha = 0.14f * (1f - ring))),
                    )
                    Box(
                        modifier = Modifier
                            .size((20 + 14 * ring2).dp)
                            .clip(CircleShape)
                            .background(StrapChargeMint.copy(alpha = 0.12f * (1f - ring2))),
                    )
                    Box(
                        modifier = Modifier
                            .size((18 + 8 * pulse).dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        StrapChargeMint.copy(alpha = 0.40f * pulse),
                                        StrapChargeMint.copy(alpha = 0.10f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                    ChargeBoltMark(
                        size = 18.dp,
                        tint = StrapChargeMint.copy(alpha = 0.70f + 0.30f * pulse),
                    )
                }
            }
        }
    }
}

// MARK: - Offline connect callout

/**
 * The above-the-fold primary Connect affordance, shown only while disconnected. Promotes the formerly-
 * inert "Scan and connect…" caption into an accent NoopCard with a real, full-width Connect button (the
 * same scan action the controls row uses below), so the offline state has an obvious action up top
 * instead of burying it past the Signal Trust grid. Mirrors the macOS offlineConnectCallout.
 */
@Composable
private fun OfflineConnectCallout(scanning: Boolean, onConnect: () -> Unit) {
    NoopCard(tint = Palette.accent) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = Palette.accent,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.live_start_stream_title),
                        style = NoopType.headline,
                        color = Palette.textPrimary,
                    )
                    Text(
                        stringResource(R.string.live_start_stream_body),
                        style = NoopType.subhead,
                        color = Palette.textSecondary,
                    )
                }
            }
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !scanning,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.accent,
                    contentColor = Palette.surfaceBase,
                ),
            ) {
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).padding(end = 4.dp),
                )
                Text(
                    if (scanning) {
                        stringResource(R.string.live_searching)
                    } else {
                        stringResource(R.string.live_scan_connect)
                    },
                    style = NoopType.captionNumber,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/** #56: a non-WHOOP live source (the Oura ring, and on Android any external HR source that drives
 *  [LiveState.streamingLiveHR]) that is connected and actively streaming live HR. It streams without a
 *  WHOOP encrypted bond, so `bonded`/`activeConnection` never trip — which left the console reading
 *  "stream not yet trusted" for a perfectly good stream. The status copy treats this as a trusted stream;
 *  the bond-only feature gates (buzz, alarm, HRV snapshot) keep keying off `activeConnection`. Twin of the
 *  iOS LiveView.ringStreaming. */
private fun ringStreaming(live: LiveState): Boolean = live.connected && live.streamingLiveHR

private fun connectionModeBadge(live: LiveState, activeConnection: Boolean): String = when {
    live.alongsideMode && (activeConnection || live.connected || live.streamingLiveHR) -> "ALONGSIDE WHOOP"
    activeConnection && live.encryptedBond -> "FULL BOND"
    activeConnection -> "LIVE HR ONLY"
    ringStreaming(live) -> LifeChapterLacquer.HEALTH_STREAMING_STATE
    live.connected -> "CONNECTING"
    live.encryptedBond -> "PAIRED"
    else -> "OFFLINE"
}

/** Whether to render the connection-mode badge. False exactly when the badge would read "OFFLINE" —
 *  the pill already says "Disconnected", so the duplicate rose badge is pure redundancy. */
private fun showsModeBadge(live: LiveState, activeConnection: Boolean): Boolean =
    !(!activeConnection && !live.connected && !live.encryptedBond)

private fun connectionModeColor(live: LiveState, activeConnection: Boolean): Color = when {
    live.alongsideMode && (activeConnection || live.connected || live.streamingLiveHR) -> Palette.accent
    (activeConnection && live.encryptedBond) || ringStreaming(live) -> Palette.accent
    activeConnection || live.connected -> Palette.statusWarning
    else -> Palette.metricRose
}

private fun lastSyncLabel(live: LiveState): String =
    live.lastSyncAt?.let { relativeAgo(it) } ?: "Never"

// MARK: - Body console (focal HR ring + live physiology)

@Composable
private fun BodyConsole(live: LiveState, bpm: Int?, activeConnection: Boolean, zone: Int, hrMax: Int) {
    // Soft frost hero — Effort-tinted glass over day-of-sky (Alarm Arm / Health vitals twin).
    // No hard LIVE_HERO_FILL slab; hairline comes from frostedCardSurface.
    val shape = RoundedCornerShape(LIVE_HERO_RADIUS)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .frostedCardSurface(
                tint = Palette.effortColor,
                cornerRadius = LIVE_HERO_RADIUS,
                washStrength = LifeChapterLacquer.LIVE_BODY_WASH,
            )
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            HeartReadout(
                live = live,
                bpm = bpm,
                activeConnection = activeConnection,
                zone = zone,
                hrMax = hrMax,
                charging = live.charging == true,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Palette.hairline),
            )
            PhysiologyStack(live = live, activeConnection = activeConnection)
        }
    }
}

@Composable
private fun HeartReadout(
    live: LiveState,
    bpm: Int?,
    activeConnection: Boolean,
    zone: Int,
    hrMax: Int,
    charging: Boolean = false,
) {
    // Tint by the live HR zone when streaming, the Effort world otherwise — the workouts/live colour world
    // (UNCHANGED from the hand-drawn ring this replaced: same zone→colour math, same value-sampled tint).
    val tint = when {
        bpm == null -> Palette.textSecondary
        zone >= 1 -> Palette.hrZoneColor(zone)
        else -> Palette.effortColor
    }
    // The vessel fill: current bpm as a fraction of the age-based max HR (the same hrMax the zone model
    // above uses). Null bpm → empty vessel. Clamped 0..1 by LiquidVessel at the draw call.
    val fraction = bpm?.let { (it.toDouble() / hrMax.toDouble()) } ?: 0.0

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Overline(liveHrOverline(charging))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            // AirPods-style soft energy rings when the pack is charging (or SoC rising on MG).
            if (charging) {
                val infinite = rememberInfiniteTransition(label = "hrCharge")
                val r1 by infinite.animateFloat(
                    0f, 1f,
                    infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart),
                    label = "hrR1",
                )
                val r2 by infinite.animateFloat(
                    0f, 1f,
                    infiniteRepeatable(tween(1600, delayMillis = 400, easing = FastOutSlowInEasing), RepeatMode.Restart),
                    label = "hrR2",
                )
                Box(
                    Modifier
                        .fillMaxSize(0.55f + 0.45f * r1)
                        .clip(CircleShape)
                        .background(StrapChargeMint.copy(alpha = 0.12f * (1f - r1))),
                )
                Box(
                    Modifier
                        .fillMaxSize(0.50f + 0.40f * r2)
                        .clip(CircleShape)
                        .background(StrapChargeMint.copy(alpha = 0.10f * (1f - r2))),
                )
            }
            // The live HR GAUGE as a liquid VESSEL — fills to bpm/hrMax in the zone tint, sloshing live once
            // a real HR is streaming (animated only when bpm != null, so an idle console poses static and
            // doesn't churn an empty canvas). Mirrors the liquid Today HeroScoreVessel idiom.
            LiquidVessel(
                value = fraction,
                tint = if (charging) StrapChargeMint else tint,
                animated = bpm != null || charging,
                modifier = Modifier.fillMaxSize(0.88f),
            )
            // The bpm number rolled up over the vessel — white, tabular, a soft shadow for legibility, and
            // hit-transparent (clearAndSetSemantics + no clickable) so the tap falls THROUGH to the vessel,
            // which owns its own tap→splash+haptic. Mirrors HeroScoreVessel's count-up-over-vessel number.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (bpm != null) {
                    CountUpText(
                        value = bpm.toDouble(),
                        format = { it.roundToInt().toString() },
                        style = NoopType.number(64f, weight = FontWeight.Bold)
                            .copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), offset = Offset(0f, 1f), blurRadius = 6f)),
                        color = Color.White,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                } else {
                    Text(
                        text = "—",
                        style = NoopType.number(64f, weight = FontWeight.Bold),
                        color = Palette.textSecondary,
                    )
                }
                Text(LifeChapterLacquer.HRV_BPM_CAPTION, style = NoopType.subhead, color = Palette.textSecondary)
                if (zone >= 1) {
                    Text(
                        stringResource(R.string.live_zone, zone),
                        style = NoopType.overline,
                        color = tint,
                    )
                }
            }
        }
        Text(
            signalTrustSummary(LocalContext.current, live, activeConnection),
            style = NoopType.footnote,
            color = Palette.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PhysiologyStack(live: LiveState, activeConnection: Boolean) {
    val rmssd = HrvAnalyzer.feelRmssdMs(live.rrRecent)
    val awaitingOptical = activeConnection && live.streamingLiveHR && live.rrRecent.isEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Overline(stringResource(R.string.live_physiology_overline))
                Text(connectionModeDetail(LocalContext.current, live, activeConnection), style = NoopType.headline, color = Palette.textPrimary)
            }
            if (rmssd != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(LifeChapterLacquer.RMSSD_CHIP_LABEL, style = NoopType.footnote, color = Palette.textTertiary)
                    Text(liveRmssdMsValue(rmssd.roundToInt()), style = NoopType.number(24f), color = Palette.metricCyan)
                }
            } else if (awaitingOptical && live.type40RrLockPct != null) {
                val lockPct = live.type40RrLockPct ?: 0
                val reduced = rememberReduceMotion()
                val lockFrac by animateFloatAsState(
                    targetValue = lockPct.coerceIn(0, 100) / 100f,
                    animationSpec = if (reduced) {
                        tween(0)
                    } else {
                        tween(LifeChapterLacquer.OPTICAL_LOCK_SETTLE_MS, easing = FastOutSlowInEasing)
                    },
                    label = "physLockClimb",
                )
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .graphicsLayer {
                            val s = 0.94f + 0.06f * lockFrac
                            scaleX = s
                            scaleY = s
                            alpha = 0.78f + 0.22f * lockFrac
                        }
                        .semantics {
                            contentDescription = opticalLockChipA11y(lockPct)
                        },
                ) {
                    Text(
                        LifeChapterLacquer.OPTICAL_LOCK_CHIP_LABEL,
                        style = NoopType.footnote,
                        color = Palette.metricCyan.copy(alpha = 0.85f),
                    )
                    Text(
                        opticalLockChipPct(lockPct),
                        style = NoopType.number(24f, weight = FontWeight.Bold),
                        color = Palette.metricCyan,
                    )
                }
            }
        }
        RRStrip(
            rrRecent = live.rrRecent,
            lockPct = live.type40RrLockPct.takeIf { awaitingOptical },
            type40Frames = live.type40FramesThisSession.takeIf { awaitingOptical },
            type40WithRr = live.type40WithRrThisSession.takeIf { awaitingOptical },
            // Parent owns optical caption + hairline; strip keeps Waiting / feel climb only.
            ownOpticalCaption = false,
        )
        // Client-side type-40 honesty: HR can stream before optical R-R lock (MG often 30–60s).
        if (awaitingOptical) {
            OpticalLockHairline(
                lockPct = live.type40RrLockPct,
                accent = Palette.metricCyan,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            )
            Text(
                opticalLockCaption(
                    context = LocalContext.current,
                    type40Frames = live.type40FramesThisSession,
                    type40WithRr = live.type40WithRrThisSession,
                    lockPct = live.type40RrLockPct,
                ),
                style = NoopType.caption,
                color = Palette.textTertiary,
            )
        }
        // Feel climb hairline lives in RRStrip only (no duplicate under physiology).
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.gap), modifier = Modifier.fillMaxWidth()) {
            // Offline: show a muted "Offline" word (dimmed to textTertiary) instead of bare accent-
            // coloured em-dashes that read as broken live readouts. Real values + accents return on a
            // stream. Mirrors the macOS liveProofMetric(offline:).
            val offlineLabel = stringResource(R.string.live_trust_offline)
            val rrProof = when {
                !activeConnection -> offlineLabel
                live.rr.lastOrNull() != null -> liveRrIntervalMsValue(live.rr.last()!!)
                live.rrRecent.isNotEmpty() -> {
                    val mean = live.rrRecent.takeLast(5).average().roundToInt()
                    rrProofMeanCaption(mean)
                }
                else -> opticalLockPctLabel(live.type40RrLockPct, awaitingOptical) ?: "—"
            }
            LiveProofMetric(
                Modifier.weight(1f), LifeChapterLacquer.RR_PROOF_LABEL,
                rrProof,
                Palette.metricCyan, offline = !activeConnection,
            )
            LiveProofMetric(
                Modifier.weight(1f), stringResource(R.string.live_proof_event),
                if (activeConnection) (live.lastEvent ?: "—") else offlineLabel,
                Palette.statusWarning, offline = !activeConnection,
            )
        }
    }
}

/** The recent R-R buffer as a live liquid THREAD — the beat-by-beat trace with a travelling glint +
 *  endpoint pulse (a single HR number can look frozen; a flowing thread can't). R-R intervals ARE the
 *  time between heartbeats, so the buffer is a genuine beat-by-beat series; the thread auto-normalises its
 *  own min/max, so the raw ms values feed it directly. Empty state shows a muted flat thread + the
 *  "Waiting…" caption. Same data binding (live.rrRecent) as the bar strip this replaced. */
@Composable
private fun RRStrip(
    rrRecent: List<Int>,
    lockPct: Int? = null,
    type40Frames: Int? = null,
    type40WithRr: Int? = null,
    /** When false, parent shows opticalLockCaption — strip stays "Waiting…" during lock. */
    ownOpticalCaption: Boolean = true,
) {
    val values = rrRecent.takeLast(18)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (values.size >= 2) {
            // Live thread — flows (glint + pulse) as new intervals land. Heart-pink (LiquidThread default).
            LiquidThread(
                bpm = values.map { it.toDouble() },
                animated = true,
                height = 58.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Empty / single-sample state: a muted flat hairline placeholder at the same height, so the
            // card doesn't jump when the first pair of intervals arrives and the thread takes over.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LifeChapterLacquer.RR_STRIP_PLACEHOLDER_H_DP.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Palette.hairline),
                )
            }
        }
        val feel = HrvAnalyzer.feelRmssdMs(rrRecent)
        if (feel == null && values.isNotEmpty() && values.size < LifeChapterLacquer.RR_FEEL_NEED) {
            RrFeelProgressHairline(
                beatCount = values.size,
                accent = Palette.metricCyan,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            when {
                values.isEmpty() && lockPct != null && ownOpticalCaption ->
                    opticalLockCaption(
                        context = LocalContext.current,
                        type40Frames = type40Frames ?: 0,
                        type40WithRr = type40WithRr ?: 0,
                        lockPct = lockPct,
                        leadRes = R.string.optical_lock_waiting_lead,
                    )
                values.isEmpty() && lockPct != null -> rrOpticalAwaitCaption()
                values.isEmpty() -> rrFeelClimbCaption(0)
                feel != null -> rrFeelReadyCaption(values.size, values, feel)
                else -> rrFeelClimbWithMsCaption(values.size, values)
            },
            style = NoopType.footnote,
            color = Palette.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One R-R / Event proof tile. When [offline] the value is dimmed to textTertiary (regardless of the
 *  passed accent) so an idle tile reads as a muted empty state, not a broken live readout in
 *  cyan/amber — matching the rrStrip's "Waiting for R-R intervals." treatment above. */
@Composable
private fun LiveProofMetric(modifier: Modifier, label: String, value: String, tint: Color, offline: Boolean = false) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .frostedCardSurface(
                tint = if (offline) null else tint,
                cornerRadius = 10.dp,
                washStrength = 0.85f,
            )
            .padding(10.dp)
            .semantics { contentDescription = "$label $value" },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label.uppercase(), style = NoopType.footnote, color = Palette.textTertiary)
        Text(
            value,
            style = NoopType.captionNumber,
            color = if (offline) Palette.textTertiary else tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// MARK: - Live steps (MG counter / 4 estimate path)

@Composable
private fun LiveStepsCard(viewModel: AppViewModel, model: WhoopModel, connected: Boolean) {
    val context = LocalContext.current
    val profile = remember { ProfileStore.from(context) }
    val live by viewModel.live.collectAsStateWithLifecycle()
    // Prefer live type-47 v18 counter (MG notify stream); fall back to DB samples.
    var dbCounter by remember { mutableStateOf<Int?>(null) }
    var sessionStart by remember { mutableStateOf<Int?>(null) }
    // Re-read live.liveStepCounter each tick — do not capture a stale snapshot in the loop key.
    LaunchedEffect(connected, model) {
        while (true) {
            val liveCounter = viewModel.live.value.liveStepCounter
            if (liveCounter == null) {
                dbCounter = runCatching {
                    val now = System.currentTimeMillis() / 1000
                    val dayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()
                    viewModel.repo.stepSamples(viewModel.activeStrapId, dayStart, now, 30)
                        .maxByOrNull { it.ts }?.counter
                }.getOrNull()
            }
            delay(1000)
        }
    }
    val counter = live.liveStepCounter ?: dbCounter
    LaunchedEffect(counter) {
        if (sessionStart == null && counter != null) sessionStart = counter
    }
    val delta = if (counter != null && sessionStart != null) {
        val d = counter!! - sessionStart!!
        if (d < 0) d + 65536 else d
    } else 0
    val est = if (delta > 0 && profile.stepTicksPerStep > 0) {
        (delta / profile.stepTicksPerStep).roundToInt()
    } else null
    val act = when (live.liveActivityClass) {
        0 -> stringResource(R.string.live_act_still)
        1 -> stringResource(R.string.live_act_walk)
        2 -> stringResource(R.string.live_act_run)
        else -> "—"
    }
    val stepsTrailing = when {
        live.liveStepCounter != null -> stringResource(R.string.live_steps_trailing_frame)
        connected -> stringResource(R.string.live_steps_trailing_db)
        else -> stringResource(R.string.live_link_offline)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        SectionHeader(
            title = stringResource(R.string.live_steps_title),
            overline = if (model == WhoopModel.WHOOP5_MG) {
                stringResource(R.string.live_steps_overline_mg)
            } else {
                stringResource(R.string.live_steps_overline_w4)
            },
            trailing = stepsTrailing,
        )
        NoopCard(tint = Palette.effortColor) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MiniStat(
                        stringResource(R.string.live_steps_counter),
                        counter?.toString() ?: "—",
                        Modifier.weight(1f),
                        tint = Palette.effortColor,
                    )
                    MiniStat(
                        stringResource(R.string.live_steps_session_d),
                        if (counter != null) "$delta" else "—",
                        Modifier.weight(1f),
                    )
                    MiniStat(
                        stringResource(R.string.live_steps_est),
                        est?.toString() ?: "—",
                        Modifier.weight(1f),
                        tint = Palette.metricAmber,
                    )
                    MiniStat(
                        stringResource(R.string.live_steps_class),
                        act,
                        Modifier.weight(1f),
                    )
                }
                Text(
                    if (model == WhoopModel.WHOOP5_MG) {
                        stringResource(R.string.live_steps_blurb_mg)
                    } else {
                        stringResource(R.string.live_steps_blurb_w4)
                    },
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
        }
    }
}

/**
 * Honest banked vitals on Live — never invents BP/SpO2%/AFib.
 * Live HR lives in BodyConsole above; this card is banked secondary only (Health owns the full grid).
 */
@Composable
private fun HonestLiveVitalsCard(viewModel: AppViewModel, bpm: Int?, live: LiveState) {
    var spo2 by remember { mutableStateOf<Double?>(null) }
    var vo2 by remember { mutableStateOf<Double?>(null) }
    var bpSys by remember { mutableStateOf<Double?>(null) }
    var bpDia by remember { mutableStateOf<Double?>(null) }
    var bpDay by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                val today = java.time.LocalDate.now().toString()
                val days = viewModel.recentDays.value
                val d = days.lastOrNull { it.day == today } ?: days.lastOrNull()
                spo2 = d?.spo2Pct
                vo2 = runCatching {
                    viewModel.repo.metricSeries("my-whoop-noop", "vo2max_est", "0000-01-01", "9999-12-31")
                        .maxByOrNull { it.day }?.value
                }.getOrNull()
                runCatching {
                    val sys = viewModel.repo.labMarkersByKey("my-whoop", "bp_systolic").maxByOrNull { it.day }
                    val dia = viewModel.repo.labMarkersByKey("my-whoop", "bp_diastolic").maxByOrNull { it.day }
                    if (sys != null && dia != null && sys.day == dia.day &&
                        sys.value != null && dia.value != null
                    ) {
                        bpSys = sys.value
                        bpDia = dia.value
                        bpDay = sys.day
                    } else {
                        bpSys = null
                        bpDia = null
                        bpDay = null
                    }
                }
            }
            delay(5000)
        }
    }
    val bp = remember(bpSys, bpDia, bpDay) { HonestVitalsLabels.bpLine(bpSys, bpDia, bpDay) }
    val spo2Line = remember(spo2) { HonestVitalsLabels.spo2Line(spo2) }
    val vo2Line = remember(vo2) { HonestVitalsLabels.vo2Line(vo2) }
    val bankedTrailing = when {
        live.bonded -> stringResource(R.string.live_link_bonded)
        live.connected -> stringResource(R.string.live_linked)
        else -> stringResource(R.string.live_link_offline)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        SectionHeader(
            title = stringResource(R.string.live_banked_vitals_title),
            overline = stringResource(R.string.live_banked_vitals_overline),
            trailing = bankedTrailing,
        )
        HonestProvenanceLegend()
        NoopCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(R.string.live_banked_vitals_blurb),
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HonestVitalTile(
                        name = "Blood pressure",
                        line = bp,
                        modifier = Modifier.weight(1f),
                        valueTint = Palette.statusPositive,
                    )
                    HonestVitalTile(
                        name = "SpO₂",
                        line = spo2Line,
                        modifier = Modifier.weight(1f),
                        valueTint = Palette.metricCyan,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HonestVitalTile(
                        name = "VO₂ max",
                        line = vo2Line,
                        modifier = Modifier.weight(1f),
                        valueTint = Palette.accent,
                    )
                    HonestVitalTile(
                        name = "Open stream",
                        line = HonestVitalsLabels.VitalLine(
                            valueText = if (bpm != null) "HR+" else if (live.connected) "…" else "—",
                            provenance = if (bpm != null) {
                                HonestVitalsLabels.Provenance.MEASURED
                            } else {
                                HonestVitalsLabels.Provenance.BLANK
                            },
                            caption = "Steps @57 · no open BP/SpO₂",
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "SpO₂ and cuff BP aren't invented from open BLE — Lab Book for cuff BP.",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun LiveDatastreamCard(
    live: LiveState,
    bpm: Int?,
    model: WhoopModel,
    deviceName: String?,
) {
    val context = LocalContext.current
    val strapBatt = remember(
        live.connected, live.batteryPct, live.charging,
        live.batteryFreshCount, live.linkUpAtMs,
    ) {
        resolveStrapBatteryDisplay(context, live)
    }
    val linkLabel = when {
        live.encryptedBond || (live.bonded && model != WhoopModel.WHOOP5_MG) ->
            stringResource(R.string.live_fully_bonded)
        live.streamingLiveHR || live.heartRate != null -> stringResource(R.string.live_link_hr_only)
        live.connected -> stringResource(R.string.live_link_waiting_stream)
        live.scanning -> stringResource(R.string.live_searching)
        else -> stringResource(R.string.live_link_offline)
    }
    val hue = when {
        bpm != null -> Palette.accent
        live.connected -> Palette.metricAmber
        else -> Palette.textSecondary
    }
    val yes = stringResource(R.string.live_yes)
    val no = stringResource(R.string.live_no)
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        SectionHeader(
            title = stringResource(R.string.live_datastream_title),
            overline = model.displayName,
            trailing = linkLabel,
        )
        NoopCard(tint = hue) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    deviceName ?: model.displayName,
                    style = NoopType.headline,
                    color = Palette.textPrimary,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    MiniStat(
                        stringResource(R.string.live_stat_hr),
                        bpm?.let { "$it" } ?: "—",
                        Modifier.weight(1f),
                        tint = if (bpm != null) Palette.metricRose else Palette.textTertiary,
                    )
                    MiniStat("R-R", if (live.rrRecent.isNotEmpty()) "${live.rrRecent.size}" else "—", Modifier.weight(1f))
                    MiniStat(
                        stringResource(R.string.live_trust_battery),
                        strapBatt?.let { "${it.pctInt}%" } ?: "—",
                        Modifier.weight(1f),
                    )
                    MiniStat(
                        stringResource(R.string.live_worn),
                        when {
                            !live.connected -> "—"
                            live.worn -> yes
                            else -> no
                        },
                        Modifier.weight(1f),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    MiniStat(
                        stringResource(R.string.live_bond),
                        if (live.bonded) yes else no,
                        Modifier.weight(1f),
                    )
                    MiniStat(
                        stringResource(R.string.live_encrypted),
                        if (live.encryptedBond) yes else no,
                        Modifier.weight(1f),
                    )
                    MiniStat(
                        stringResource(R.string.live_stream),
                        if (live.streamingLiveHR || bpm != null) {
                            stringResource(R.string.live_on)
                        } else {
                            stringResource(R.string.live_off)
                        },
                        Modifier.weight(1f),
                    )
                    MiniStat(
                        stringResource(R.string.live_charge),
                        live.charging?.let { if (it) yes else no } ?: "—",
                        Modifier.weight(1f),
                    )
                }
                live.lastEvent?.let {
                    Text(
                        stringResource(R.string.live_last_event, it),
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                }
                live.strapFirmware?.let {
                    Text(
                        stringResource(R.string.live_firmware, it),
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                }
                // Exact BLE listen inventory — same proprietary channels WHOOP app uses when we hold exclusive bond.
                if (live.rawPacketsThisSession > 0 || live.rawListenSummary != null) {
                    Text(
                        "RAW listen · ${live.rawPacketsThisSession} pkts",
                        style = NoopType.subhead,
                        color = Palette.accent,
                    )
                    live.rawListenSummary?.let {
                        Text("UUIDs: $it", style = NoopType.footnote, color = Palette.textSecondary)
                    }
                    live.rawFrameTypesSummary?.let {
                        Text("Types: $it", style = NoopType.footnote, color = Palette.textSecondary)
                    }
                    Text(
                        if (live.encryptedBond || live.bonded)
                            "Exclusive bond: listening on proprietary fd4b notify (type47 v18 etc.) — same path WHOOP app uses for live telemetry."
                        else if (live.alongsideMode)
                            "Alongside mode: open 2A37/2A19 + best-effort fd4b. Encrypted history stays with WHOOP app."
                        else
                            "Partial link — open stream only until encrypted bond.",
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                }
                Text(
                    "Values above are only what this phone received from the strap. Missing fields stay blank — never invented. Official Charge/Effort/Stress numbers need WHOOP export or nights of bonded bank — not invented from UI screenshots.",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
        }
    }
}

// MARK: - Signal Trust rail

@Composable
private fun SignalTrustRail(live: LiveState, bpm: Int?, activeConnection: Boolean) {
    val tiles = signalTiles(LocalContext.current, live, bpm, activeConnection)
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        SectionHeader(
            title = stringResource(R.string.live_signal_trust_title),
            overline = stringResource(R.string.live_signal_trust_overline),
        )
        // Two tiles per row (a LazyVerticalGrid can't live inside the scrolling ScreenScaffold —
        // infinite-height constraints — so use fixed Rows, the correct Compose idiom here).
        tiles.chunked(2).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(Metrics.gap), modifier = Modifier.fillMaxWidth()) {
                rowTiles.forEach { tile ->
                    SignalTrustTile(tile, modifier = Modifier.weight(1f))
                }
                // Pad an odd final row so the lone tile keeps half-width (matches the grid above).
                if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class SignalTile(
    val title: String,
    val value: String,
    val detail: String,
    val tint: Color,
)

private fun signalTiles(
    context: android.content.Context,
    live: LiveState,
    bpm: Int?,
    activeConnection: Boolean,
): List<SignalTile> {
    val feelRmssd = HrvAnalyzer.feelRmssdMs(live.rrRecent)
    val strapBatt = resolveStrapBatteryDisplay(context, live)
    return listOf(
    SignalTile(
        context.getString(R.string.live_trust_hr),
        bpm?.let { "$it ${LifeChapterLacquer.HRV_BPM_CAPTION}" } ?: context.getString(R.string.live_trust_missing),
        if (activeConnection || ringStreaming(live)) {
            context.getString(R.string.live_trust_streaming_now)
        } else {
            context.getString(R.string.live_trust_no_active_stream)
        },
        if (bpm == null) Palette.textTertiary else Palette.accent,
    ),
    SignalTile(
        "${LifeChapterLacquer.RR_PROOF_LABEL} intervals",
        when {
            live.rrRecent.isNotEmpty() -> "${live.rrRecent.size} recent"
            live.type40FramesThisSession > 0 -> opticalLockTrustValue(context, live.type40RrLockPct)
            else -> context.getString(R.string.live_trust_missing)
        },
        when {
            feelRmssd != null -> rrRmssdMsCaption(feelRmssd)
            live.rrRecent.isNotEmpty() -> rrFeelClimbCaption(live.rrRecent.size)
            live.type40FramesThisSession > 0 ->
                opticalLockCaption(
                    context = context,
                    type40Frames = live.type40FramesThisSession,
                    type40WithRr = live.type40WithRrThisSession,
                    lockPct = live.type40RrLockPct,
                    leadRes = R.string.optical_lock_trust_lead,
                )
            else -> LifeChapterLacquer.RR_NEEDS_FRAMES_DETAIL
        },
        when {
            live.rrRecent.isNotEmpty() -> Palette.metricCyan
            live.type40FramesThisSession > 0 -> Palette.metricCyan
            else -> Palette.textTertiary
        },
    ),
    SignalTile(
        context.getString(R.string.live_trust_connection),
        when {
            activeConnection && live.encryptedBond -> context.getString(R.string.live_trust_encrypted)
            activeConnection -> context.getString(R.string.live_trust_partial)
            ringStreaming(live) -> context.getString(R.string.live_link_streaming)
            live.connected -> context.getString(R.string.live_link_connected)
            else -> context.getString(R.string.live_trust_offline)
        },
        when {
            activeConnection && live.encryptedBond -> context.getString(R.string.live_controls_unlocked)
            else -> liveConnectionBondDetail(context, ringStreaming(live))
        },
        connectionModeColor(live, activeConnection),
    ),
    SignalTile(
        context.getString(R.string.live_trust_history_sync),
        if (live.backfilling) "${live.syncChunksThisSession} chunks" else lastSyncLabel(live),
        liveHistorySyncDetail(
            context,
            backfilling = live.backfilling,
            lastSyncError = live.lastSyncError,
            lastSyncAt = live.lastSyncAt,
        ),
        if (live.backfilling) Palette.metricCyan else Palette.textSecondary,
    ),
    SignalTile(
        context.getString(R.string.live_trust_battery),
        strapBatt?.let { "${it.pctInt}%" }
            ?: context.getString(R.string.live_battery_unknown),
        when {
            strapBatt?.charging == true ->
                context.getString(R.string.live_battery_charging)
            !live.connected && strapBatt != null ->
                "Predicted from last sync"
            else -> context.getString(R.string.live_battery_last_reported)
        },
        batteryTint(strapBatt?.pct),
    ),
    // Wear is only trustworthy on a live link: `worn` defaults true and is only updated by
    // WRIST_ON/OFF events, so while OFFLINE it would read a false-green "On wrist". Gate value + tint
    // on activeConnection (triage fix for PR#191, parity with the macOS Wear tile).
    SignalTile(
        context.getString(R.string.live_trust_wear_state),
        if (activeConnection) {
            if (live.worn) context.getString(R.string.live_trust_on_wrist) else context.getString(R.string.live_trust_off_wrist)
        } else {
            "Unknown"
        },
        if (activeConnection) (if (live.worn) "Eligible for live physiology" else "Wear the strap for scoring") else "Connect to read wear state",
        when {
            !activeConnection -> Palette.textTertiary
            live.worn -> Palette.accent
            else -> Palette.statusWarning
        },
    ),
)
}

@Composable
private fun SignalTrustTile(tile: SignalTile, modifier: Modifier = Modifier) {
    NoopCard(modifier = modifier.heightIn(min = 112.dp), padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Overline(tile.title)
            Text(tile.value, style = NoopType.headline, color = tile.tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tile.detail, style = NoopType.footnote, color = Palette.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// MARK: - Pure helpers (shared by the body console + the trust rail)

private fun signalTrustSummary(
    context: android.content.Context,
    live: LiveState,
    activeConnection: Boolean,
): String = when {
    activeConnection && live.encryptedBond && live.rrRecent.isNotEmpty() ->
        liveRrLockedTrustSummary(context)
    activeConnection && live.encryptedBond ->
        liveEncryptedAwaitTrustSummary(context, live.type40RrLockPct)
    activeConnection && live.streamingLiveHR && live.rrRecent.isEmpty() ->
        liveHrFlowingTrustSummary(context, live.type40RrLockPct)
    activeConnection -> livePartialBondTrustSummary(context)
    live.connected -> liveAwaitingStreamTrustSummary()
    // The actionable "Scan and connect…" CTA now lives in the above-the-fold OfflineConnectCallout,
    // so this ring caption stays a calm empty-state descriptor rather than a competing CTA.
    else -> liveOfflineTrustSummary()
}

private fun connectionModeDetail(
    context: android.content.Context,
    live: LiveState,
    activeConnection: Boolean,
): String = when {
    activeConnection && live.encryptedBond && live.rrRecent.isNotEmpty() -> liveRrLockedModeDetail(context)
    activeConnection && live.encryptedBond ->
        liveEncryptedAwaitModeDetail(context, live.type40RrLockPct)
    (activeConnection || ringStreaming(live)) && live.rrRecent.isEmpty() && live.streamingLiveHR ->
        liveHrOpticalModeDetail(context, live.type40RrLockPct)
    activeConnection || ringStreaming(live) -> liveHrActiveModeDetail(context)
    live.connected -> liveRadioUntrustedModeDetail(context)
    else -> liveNoStreamModeDetail(context)
}

private fun batteryTint(pct: Double?): Color = when {
    pct == null -> Palette.textTertiary
    pct <= 15 -> Palette.metricRose
    pct <= 30 -> Palette.statusWarning
    else -> Palette.accent
}

/**
 * Coarse relative-time label for the "History synced N ago" sync-status line. Pure + unit-tested
 * (RelativeAgoTest); [nowSec] is injectable for determinism. Buckets to just-now / min / h / d. (PR #85)
 */
internal fun relativeAgo(epochSec: Long, nowSec: Long = System.currentTimeMillis() / 1000L): String {
    val d = (nowSec - epochSec).coerceAtLeast(0)
    return when {
        d < 60L -> "just now"
        d < 3600L -> "${d / 60L} min ago"
        d < 86_400L -> "${d / 3600L} h ago"
        else -> "${d / 86_400L} d ago"
    }
}

/** Live workout distance from metres, 2-decimal precision, re-labelled to the active system (km / mi). */
private fun liveDistance(distanceM: Double, system: UnitSystem): String = when (system) {
    UnitSystem.METRIC -> java.lang.String.format(java.util.Locale.US, "%.2f km", distanceM / 1000.0)
    UnitSystem.IMPERIAL ->
        java.lang.String.format(java.util.Locale.US, "%.2f mi", UnitFormatter.kmToMiles(distanceM / 1000.0))
}

/** Live pace from seconds-per-km, re-labelled to minutes per km / per mile. A per-mile pace is per-km
 *  divided by miles-per-km (a mile is longer, so the time per unit is larger). */
private fun livePace(secPerKm: Double, system: UnitSystem): String {
    val sec = if (system == UnitSystem.IMPERIAL) secPerKm / UnitFormatter.MILES_PER_KILOMETER else secPerKm
    val unit = if (system == UnitSystem.IMPERIAL) "/mi" else "/km"
    return java.lang.String.format(java.util.Locale.US, "%d:%02d %s", (sec / 60).toInt(), (sec % 60).toInt(), unit)
}
