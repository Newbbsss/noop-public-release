package com.noop.ui

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.automirrored.outlined.TrendingUp

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.analytics.FusionSource
import com.noop.ble.WhoopBleClient
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.noop.BuildConfig
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// MARK: - Navigation model
//
// The macOS app's sidebar holds many sections; on Android (mirroring the iOS RootTabView) we surface
// them through a unified floating "glass" bottom bar (Today · Trends · Sleep · More) for the everyday
// screens, with a "More" sheet that lists the full grouped set — so every destination is one tap away
// without a global hamburger/drawer. Destinations are grouped exactly as the sidebar groups them.
// Routes whose screens belong to later waves point at a ComingSoon placeholder so the app compiles today.

/** A single drawer destination: stable route, display title (localized via [titleRes]), sidebar icon. */
private enum class Destination(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    // Group: Today
    Today("today", R.string.nav_today, Icons.Filled.Home),
    Intelligence("intelligence", R.string.nav_intelligence, Icons.Filled.Psychology),
    // Optional, default-OFF (task #43): the Coupled view (WHOOP-style day read). Reached ONLY via the
    // Today dashboard "Coupled view" card tap-through, so it is deliberately NOT in any [DrawerGroup].
    CoupledView("coupled_view", R.string.nav_coupled_view, Icons.Filled.Hexagon),

    // Group: Live
    Live("live", R.string.nav_live, Icons.Filled.FavoriteBorder),
    Intervals("intervals", R.string.nav_intervals, Icons.Filled.Timeline),

    // Group: Recovery
    Sleep("sleep", R.string.nav_sleep, Icons.Filled.Bedtime),
    Breathe("breathe", R.string.nav_breathe, Icons.Filled.Air),
    Stress("stress", R.string.nav_stress, Icons.Filled.Spa),

    // Group: Activity
    Workouts("workouts", R.string.nav_workouts, Icons.Filled.FitnessCenter),
    Trends("trends", R.string.nav_trends, Icons.AutoMirrored.Filled.TrendingUp),

    // Group: Insight
    Coach("coach", R.string.nav_coach, Icons.Filled.AutoAwesome),
    InsightsHub("insights_hub", R.string.nav_insights_hub, Icons.Filled.Insights),
    Insights("insights", R.string.nav_insights, Icons.Filled.Insights),
    Explore("explore", R.string.nav_explore, Icons.Filled.Explore),
    Compare("compare", R.string.nav_compare, Icons.AutoMirrored.Filled.CompareArrows),
    // Product feature (FullRelease): NOOP scorers vs official WHOOP **app** labels — not Test Centre.
    WhoopAlgoCompare("whoop_algo_compare", R.string.nav_whoop_algo_compare, Icons.AutoMirrored.Filled.CompareArrows),

    // Group: Health
    Health("health", R.string.nav_health, Icons.Filled.MonitorHeart),
    Hydration("hydration", R.string.nav_hydration, Icons.Filled.WaterDrop),
    Nutrition("nutrition", R.string.nav_nutrition, Icons.Filled.Restaurant),
    VitalSigns("vital_signs", R.string.nav_vital_signs, Icons.Filled.HealthAndSafety),
    VitalSignsDetail("vital_detail/{key}", R.string.nav_vital_signs, Icons.Filled.HealthAndSafety),
    LabBook("lab_book", R.string.nav_lab_book, Icons.Filled.HealthAndSafety),
    PeriodCalendar("period_calendar", R.string.nav_period_calendar, Icons.Filled.WaterDrop),
    Rhythm("rhythm", R.string.nav_rhythm, Icons.Filled.MonitorHeart),
    AppleHealth("apple_health", R.string.nav_apple_health, Icons.Filled.HealthAndSafety),

    // Group: System
    Automations("automations", R.string.nav_automations, Icons.Filled.Bolt),
    // "Alarms" lives on Sleep → Alarm (#766). Route id stays "smart_alarm" for deep-links;
    // NavHost redirects to Sleep → Alarm (no separate Wake settings surface).
    SmartAlarm("smart_alarm", R.string.nav_alarms, Icons.Filled.Alarm),
    Devices("devices", R.string.nav_devices, Icons.Filled.Sensors),
    DataSources("data_sources", R.string.nav_data_sources, Icons.Filled.Storage),
    BackupSync("backup_sync", R.string.nav_backup_sync, Icons.Filled.CloudSync),
    FusedRecord("fused_record", R.string.nav_fused_record, Icons.AutoMirrored.Filled.CompareArrows),
    Notifications("notifications", R.string.nav_notifications, Icons.Filled.Notifications),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
    BugReport("bug_report", R.string.nav_bug_report, Icons.Filled.BugReport),
    FriendsNetwork("friends_network", R.string.nav_friends_network, Icons.Filled.Group),
    TestCentre("test_centre", R.string.nav_test_centre, Icons.Filled.BugReport),
    Goals("goals", R.string.nav_goals, Icons.Filled.Flag),
    StepTraining("step_training", R.string.nav_step_training, Icons.Filled.FitnessCenter),
    QuickStart("quick_start", R.string.nav_quick_start, Icons.Filled.Explore),

    // The "More" tab: its own navigated page (mirroring the iOS More tab) that hosts the full
    // grouped destination list. It is NOT itself in any [DrawerGroup] — it's the door to them.
    More("more", R.string.nav_more, Icons.Filled.MoreHoriz);

    companion object {
        /** Resolve the destination owning the current back-stack route (defaults to Today). */
        fun forRoute(route: String?): Destination {
            // Strip query args (e.g. data_sources?import=whoop) then match parameterised paths.
            val path = route?.substringBefore('?')
            return entries.firstOrNull {
                val destPath = it.route.substringBefore('?')
                destPath == path || destPath.substringBefore('/') == path?.substringBefore('/')
            } ?: Today
        }
    }
}

/** More-page groups, mirroring the iOS More tab exactly: Insights · Body · Data · App. `defaultExpanded`
 *  mirrors the iOS S2 default: Insights + Body open at rest, Data + App collapsed to just their header. */
// [header] is the STABLE persistence key (stored in SharedPreferences and kept byte-identical to iOS's
// `more.expandedSections` CSV — see [MoreSectionPrefs]); it must NEVER be localized. [headerRes] is the
// localized DISPLAY label the More page shows. Decoupling the two lets the label translate without
// touching the persisted open/closed state or the iOS parity of the stored string.
private data class DrawerGroup(
    val header: String,
    @StringRes val headerRes: Int,
    val items: List<Destination>,
    val defaultExpanded: Boolean,
)

// Mirrors the iOS RootTabView `moreTab` grouping + order one-for-one. Today / Trends / Sleep are NOT
// listed (they're bottom-bar tabs, exactly as on iOS). Android-only screens (Vital Signs, Wake Window,
// Notifications, Devices) are slotted into the matching iOS group.
private val drawerGroups: List<DrawerGroup> = listOf(
    DrawerGroup("Insights", R.string.more_group_insights, listOf(
        Destination.InsightsHub, Destination.Intelligence, Destination.Coach,
        Destination.Insights, Destination.Explore, Destination.Compare,
        // WhoopAlgoCompare: DEBUG-only — main must not surface algo-vs-WHOOP.
        *(if (BuildConfig.DEBUG) arrayOf(Destination.WhoopAlgoCompare) else emptyArray()),
    ), defaultExpanded = true),
    DrawerGroup("Body", R.string.more_group_body, listOf(
        Destination.Live, Destination.Workouts, Destination.Health, Destination.Nutrition,
        Destination.VitalSigns,
        Destination.LabBook, Destination.PeriodCalendar, Destination.Stress, Destination.Breathe,
        Destination.Intervals, Destination.Rhythm,
    ), defaultExpanded = true),
    DrawerGroup("Data", R.string.more_group_data, listOf(
        Destination.FusedRecord, Destination.AppleHealth, Destination.DataSources,
        Destination.BackupSync, Destination.Devices,
    ), defaultExpanded = false),
    DrawerGroup("App", R.string.more_group_app, listOf(
        Destination.Automations, Destination.Notifications,
        Destination.Goals,
        Destination.FriendsNetwork,
        Destination.BugReport,
        // Test Centre + UI demo lab stay DEBUG-only; algo-vs-WHOOP ships via WhoopAlgoCompare / Today.
        // SmartAlarm lives on Sleep → Alarm (not More) — Fable / Gilbert ask.
        *(if (BuildConfig.DEBUG) arrayOf(Destination.TestCentre) else emptyArray()),
        Destination.Settings,
    ), defaultExpanded = false),
)

/** The headers open by default at first run, derived from [drawerGroups.defaultExpanded] (Insights +
 *  Body), so the seed lives in one place and the persistence default can't drift from the UI default. */
private fun defaultExpandedHeaders(): Set<String> =
    drawerGroups.filter { it.defaultExpanded }.map { it.header }.toSet()

/**
 * Persisted open/closed state of the More page's collapsible groups (#860 item 2) - the Android twin of
 * the iOS `MoreSectionPrefs`. The set of EXPANDED group headers is stored as one sorted comma-joined
 * string under a single SharedPreferences key, encoded identically to iOS (same `more.expandedSections`
 * suffix, same CSV-of-headers, same Insights+Body default) so the two platforms behave the same. An empty
 * stored string is a valid state (everything collapsed), distinct from "never set" (which yields the seed).
 */
internal object MoreSectionPrefs {
    const val KEY = "noop.more.expandedSections"

    /** Read the expanded-header set; returns [default] when the key was never written (first run). */
    fun read(prefs: android.content.SharedPreferences, default: Set<String>): Set<String> {
        val raw = prefs.getString(KEY, null) ?: return default
        return decode(raw)
    }

    /** Persist the expanded-header set as a sorted, comma-joined string. */
    fun write(prefs: android.content.SharedPreferences, headers: Set<String>) {
        prefs.edit().putString(KEY, encode(headers)).apply()
    }

    /** Encode the set of expanded headers to a sorted, comma-joined string. */
    fun encode(headers: Set<String>): String = headers.sorted().joinToString(",")

    /** Decode the stored string to a set of expanded headers; blank tokens dropped, empty string -> empty set. */
    fun decode(raw: String): Set<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

/**
 * App shell: a single [Scaffold] with a floating [GlassBottomBar]
 * (Today · Trends · P.C. · Sleep · More)
 * driving one [NavHost], mirroring the iOS RootTabView. There is NO global toolbar and no nav drawer
 * — every screen self-titles via [ScreenScaffold], and the "More" sheet (opened from the bar) reaches
 * every destination in [drawerGroups], so nothing is lost. A single [AppViewModel] is created here and
 * shared with every screen, so the BLE connection and cached metrics stay app-wide singletons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: AppViewModel = viewModel()) {
    val nav = rememberNavController()

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val current = Destination.forRoute(currentRoute)
    // The Updates inbox sheet (opened by the Today header bell). The store is a process singleton so
    // the Today cards and the import path post to the same inbox this sheet renders.
    val context = androidx.compose.ui.platform.LocalContext.current
    val updateStore = remember { UpdateStore.from(context) }
    var showUpdatesInbox by remember { mutableStateOf(false) }
    // First-run Quick Start disabled — Goals board is the durable weighted checklist.
    // Mark seen so old installs do not re-trigger if something else sets the flag false.
    LaunchedEffect(Unit) {
        NoopPrefs.of(context).edit().putBoolean("noop.quickStartSeen", true).apply()
    }

    val cycleNavVisible by viewModel.showCycleTab.collectAsStateWithLifecycle()
    val cycleTrackingOn by viewModel.cycleTrackingEnabled.collectAsStateWithLifecycle()
    // Gilbert P0 — Cycle must ALWAYS be findable under More (male default profile used to hide it).
    // Sex-gate never removes the entry; Settings → Health & wellness owns the on/off master toggle.
    val cycleEligibleInMore = true
    // Do NOT kick PeriodCalendar when the Cycle tab is hidden — Settings promises Cycle stays
    // under More → For your body. Tab visibility only; route remains valid via More / Health.
    // Full-screen charging (AirPods-style) + ding — any tab, not only Live.
    StrapChargingHost(viewModel)

    // Fable 200 #104: Strap paused snackbar on involuntary drop — tap Reconnect.
    val live by viewModel.live.collectAsStateWithLifecycle()
    val userDisconnected by viewModel.userInitiatedDisconnect.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    // Use worn MG confirmation / retry cues (retry snack offers one-tap Use worn MG).
    LaunchedEffect(Unit) {
        viewModel.statusCue.collect { msg ->
            val retry = msg.contains("try Use worn MG again", ignoreCase = true)
            val success = msg.startsWith("Live Bluetooth", ignoreCase = true)
            val mg = if (retry) {
                runCatching { viewModel.pairedDevices() }.getOrDefault(emptyList())
                    .firstOrNull { isWornMgDeviceCandidate(it) }
            } else {
                null
            }
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = when {
                    mg != null -> "Use worn MG"
                    success -> "Devices"
                    else -> null
                },
                duration = if (retry || success) SnackbarDuration.Long else SnackbarDuration.Short,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (mg != null) {
                    viewModel.preferWornMgDevice(mg)
                    nav.navigatePush(Destination.Devices.route)
                } else if (success) {
                    nav.navigatePush(Destination.Devices.route)
                }
            }
        }
    }
    // FGS 5AM sibling honesty → Devices + snackbar with optional Use worn MG action.
    val openDevicesOnce = SessionUiFlags.openDevicesOnce
    LaunchedEffect(openDevicesOnce) {
        if (!openDevicesOnce) return@LaunchedEffect
        SessionUiFlags.openDevicesOnce = false
        nav.navigatePush(Destination.Devices.route)
        val mg = runCatching { viewModel.pairedDevices() }.getOrDefault(emptyList())
            .firstOrNull { isWornMgDeviceCandidate(it) }
        val result = snackbarHostState.showSnackbar(
            message = if (mg != null) {
                "Live link may be an unworn 5AM sibling"
            } else {
                "Use worn MG below if this is the unworn sibling"
            },
            actionLabel = if (mg != null) "Use worn MG" else null,
            duration = SnackbarDuration.Long,
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed && mg != null) {
            viewModel.preferWornMgDevice(mg)
        }
    }
    var wasConnected by remember { mutableStateOf(live.connected) }
    // SHIP #276 — BLE flaps must not stack duplicate "Strap paused" snacks after reconnect churn.
    var lastDropSnackAtMs by remember { mutableStateOf(0L) }
    LaunchedEffect(live.connected, userDisconnected) {
        val dropped = wasConnected && !live.connected && !userDisconnected
        wasConnected = live.connected
        if (!dropped) return@LaunchedEffect
        // Fable 200 #75 — count involuntary drops today; Settings offers battery-opt deep link when high.
        runCatching {
            val prefs = NoopPrefs.of(context)
            val today = java.time.LocalDate.now().toString()
            val dayKey = "fable_ble_drop_day"
            val countKey = "fable_ble_drop_count"
            val count = if (prefs.getString(dayKey, null) == today) {
                prefs.getInt(countKey, 0) + 1
            } else {
                1
            }
            prefs.edit().putString(dayKey, today).putInt(countKey, count).apply()
        }
        val now = System.currentTimeMillis()
        if (snackbarHostState.currentSnackbarData != null) return@LaunchedEffect
        if (now - lastDropSnackAtMs < 45_000L) return@LaunchedEffect
        lastDropSnackAtMs = now
        snackScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Strap paused — tap to reconnect",
                actionLabel = "Reconnect",
                duration = SnackbarDuration.Long,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.connect()
            }
        }
    }

    // Post-workout sport label ask (trains sport ID / ML).
    // SHIP #156 — sport sheet wins; journal/report prompts wait until sport is confirmed/dismissed.
    val pendingSport by viewModel.pendingSportConfirm.collectAsStateWithLifecycle()
    pendingSport?.let { row ->
        WorkoutSportConfirmSheet(
            suggested = row.sport,
            onConfirm = { sport -> viewModel.confirmWorkoutSport(sport) },
            onDismiss = { viewModel.dismissSportConfirm() },
        )
    }

    // Phone grip-pulse gestures (experimental approximation of Watch hand-clench).
    // #174 — default OFF; Settings opt-in. Double pulse → Workouts; single → haptic only.
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val noopPrefs = remember { NoopPrefs.of(context) }
    var gripPulseEnabled by remember { mutableStateOf(NoopPrefs.gripPulseEnabled(context)) }
    DisposableEffect(noopPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == NoopPrefs.KEY_GRIP_PULSE) {
                gripPulseEnabled = NoopPrefs.gripPulseEnabled(context)
            }
        }
        noopPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { noopPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    DisposableEffect(gripPulseEnabled) {
        if (!gripPulseEnabled) {
            return@DisposableEffect onDispose { }
        }
        val grip = com.noop.motion.GripGestureController(
            context = context,
            onSingle = {
                haptic.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
                )
            },
            onDouble = {
                haptic.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                )
                nav.navigatePush(Destination.Workouts.route)
            },
        )
        grip.start()
        onDispose { grip.stop() }
    }

    // Fable #208 — true nav overlay. The bar no longer occupies the Scaffold's bottomBar SLOT (which
    // reserved an opaque band of Scaffold canvas under the crescents — the "black slab"): it draws in
    // THIS root Box ON TOP of the NavHost, so scroll content and the sky continue underneath the
    // translucent islands and the veil is the only separation. Its measured height feeds
    // [LocalUnderBarInset] so overlay screens' scroll containers keep their last row reachable;
    // remaining pushed utility routes keep the old above-the-bar layout via the NavHost bottom pad.
    var barHeightPx by remember { mutableStateOf(0) }
    val barHeight = with(LocalDensity.current) { barHeightPx.toDp() }
    Box(modifier = Modifier.fillMaxSize().background(Palette.surfaceBase)) {
        NatureMotifOverlay(modifier = Modifier.fillMaxSize())
        Scaffold(
            // Transparent so the floating bar can sit over sky/content instead of a hard black slab.
            containerColor = Color.Transparent,
            // The snackbar keeps clearing the (now overlaid) bar.
            snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = barHeight)) },
        ) { inner ->
            val barSwipeRoutes = remember {
                buildList {
                    add(Destination.Today.route)
                    add(Destination.Trends.route)
                    add(Destination.Sleep.route)
                    add(Destination.More.route)
                }
            }
            // EVERY destination goes FULL-BLEED under the floating bar. Scaffolds add
            // LocalUnderBarInset to scroll padding so the last row stays reachable, and the
            // soft GlassDiffusionVeil dissolves content into the crescents.
            // Legacy NavHost bottom pad reserved an opaque surfaceBase band — the hard black
            // slab Gilbert saw on almost every Settings/More push (Workouts, Fuel, Step
            // training, Insights, What moves with you, …). One shared overlay path fixes it.
            // SHIP #204 — Health/Live stay on this path (regression guard).
            val layoutDir = LocalLayoutDirection.current
            CompositionLocalProvider(
                LocalUnderBarInset provides barHeight,
            ) {
            val reducedMotion = rememberReduceMotion()
            val navFade: androidx.compose.animation.core.FiniteAnimationSpec<Float> =
                NoopMotion.fadeTween(reduced = reducedMotion)
            val navSlide: androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset> =
                NoopMotion.slideTween(reduced = reducedMotion)
            NavHost(
                navController = nav,
                startDestination = Destination.Today.route,
                modifier = Modifier
                    .padding(
                        start = inner.calculateStartPadding(layoutDir),
                        top = inner.calculateTopPadding(),
                        end = inner.calculateEndPadding(layoutDir),
                        bottom = 0.dp,
                    )
                    // Edge-only swipe between primary bar tabs (SHIP #229 — full-width horizontal
                    // drag fought LazyColumn / Cycle month scroll; keep ~36dp left/right rails).
                    .pointerInput(currentRoute, barSwipeRoutes) {
                        if (currentRoute !in barSwipeRoutes) return@pointerInput
                        val edgePx = 36.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val x0 = down.position.x
                            val fromEdge = x0 <= edgePx || x0 >= size.width - edgePx
                            if (!fromEdge) return@awaitEachGesture
                            var total = 0f
                            var totalY = 0f
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.changedToUp()) break
                                val delta = change.positionChange()
                                total += delta.x
                                totalY += delta.y
                                // Yield to vertical scroll once the gesture is clearly vertical.
                                if (kotlin.math.abs(totalY) > kotlin.math.abs(total) &&
                                    kotlin.math.abs(totalY) > 24f
                                ) {
                                    return@awaitEachGesture
                                }
                                change.consume()
                            } while (event.changes.any { it.pressed })
                            if (kotlin.math.abs(total) < 80f ||
                                kotlin.math.abs(total) < kotlin.math.abs(totalY) * 1.6f
                            ) {
                                return@awaitEachGesture
                            }
                            val idx = barSwipeRoutes.indexOf(currentRoute)
                            if (idx < 0) return@awaitEachGesture
                            when {
                                total < 0f && idx < barSwipeRoutes.lastIndex ->
                                    nav.navigateTopLevel(barSwipeRoutes[idx + 1])
                                total > 0f && idx > 0 ->
                                    nav.navigateTopLevel(barSwipeRoutes[idx - 1])
                            }
                        }
                    },
                // Bar tabs: short fade-through. Push/pop: whisper shared-axis. Reduce Motion: instant.
                enterTransition = {
                    val from = initialState.destination.route
                    val to = targetState.destination.route
                    val barToBar = from in barSwipeRoutes && to in barSwipeRoutes
                    when {
                        reducedMotion -> fadeIn(animationSpec = navFade)
                        barToBar -> fadeIn(animationSpec = navFade)
                        else -> slideInHorizontally(animationSpec = navSlide) { it / 22 } + fadeIn(animationSpec = navFade)
                    }
                },
                exitTransition = {
                    val from = initialState.destination.route
                    val to = targetState.destination.route
                    val barToBar = from in barSwipeRoutes && to in barSwipeRoutes
                    when {
                        reducedMotion -> fadeOut(animationSpec = navFade)
                        barToBar -> fadeOut(animationSpec = navFade)
                        else -> slideOutHorizontally(animationSpec = navSlide) { -it / 28 } + fadeOut(animationSpec = navFade)
                    }
                },
                popEnterTransition = {
                    if (reducedMotion) fadeIn(animationSpec = navFade)
                    else slideInHorizontally(animationSpec = navSlide) { -it / 22 } + fadeIn(animationSpec = navFade)
                },
                popExitTransition = {
                    if (reducedMotion) fadeOut(animationSpec = navFade)
                    else slideOutHorizontally(animationSpec = navSlide) { it / 22 } + fadeOut(animationSpec = navFade)
                },
            ) {
                // --- Live, working screens (existing waves) ---
                composable(Destination.Today.route) {
                    TodayScreen(
                        viewModel = viewModel,
                        // The quick-action "+" lives in the Today header's top-right now (off the
                        // bottom bar) — it opens the same quick-action sheet the bar used to.
                        onQuickActions = { showUpdatesInbox = true },
                        // The Updates "ringer" — the bell sits before the +, and opens the inbox
                        // sheet AppRoot presents (it owns the nav for deep-links).
                        updateStore = updateStore,
                        onOpenUpdates = { showUpdatesInbox = true },
                        // The leading profile avatar opens Settings at Profile (photo + body),
                        // mirroring iOS's avatar-leading Today header. The drawer hamburger is unchanged.
                        onOpenSettings = {
                            SessionUiFlags.settingsFocusProfile = true
                            nav.navigatePush(Destination.Settings.route)
                        },
                        // The opt-in Hydration card (only shown when Hydration tracking is on) pushes its
                        // detail. A normal push so the back-stack returns to Today.
                        onOpenHydration = { nav.navigate(Destination.Hydration.route) },
                        onOpenNutrition = { nav.navigatePush(Destination.Nutrition.route) },
                        // #706/#684: the dashboard cards draw a tappable chevron; wire each to its detail,
                        // matching iOS. Stress + the vitals are pushes; Sleep is a top-level tab switch.
                        onOpenStress = { nav.navigatePush(Destination.Stress.route) },
                        onOpenHealth = { nav.navigatePush(Destination.Health.route) },
                        // Every metric/vital card opens its OWN focused detail trend (vital_detail/<key>),
                        // not the shared Health hub (2026-07-03). Mirrors the iOS liquidCard metricDetail.
                        onOpenMetric = { key -> nav.navigatePush("vital_detail/$key") },
                        onOpenSleep = { nav.navigateTopLevel(Destination.Sleep.route) },
                        // Optional Coupled view card (task #43): a normal push so back returns to Today.
                        onOpenCoupled = { nav.navigatePush(Destination.CoupledView.route) },
                        // The "workout in progress" indicator: raise the one-shot the Live screen consumes to
                        // re-open the in-exercise overlay, then route to Live. One tap from Today (iOS parity).
                        onOpenActiveWorkout = {
                            viewModel.openActiveWorkout()
                            nav.navigatePush(Destination.Live.route)
                        },
                        // The liquid header's strap battery ring taps through to Devices (iOS parity: the
                        // battery ring → router.openDevices()).
                        onOpenDevices = { nav.navigatePush(Destination.Devices.route) },
                        onOpenAlarm = { nav.openSleepAlarmTab() },
                        onOpenSources = { nav.navigatePush(Destination.DataSources.route) },
                        onOpenWorkouts = { nav.navigatePush(Destination.Workouts.route) },
                        onOpenPeriodCalendar = { nav.navigateTopLevel(Destination.PeriodCalendar.route) },
                    )
                }
                composable(Destination.Live.route) {
                    LiveScreen(
                        viewModel = viewModel,
                        onManageDevices = { nav.navigatePush(Destination.Devices.route) },
                    )
                }
                composable(Destination.Sleep.route) {
                    SleepScreen(
                        vm = viewModel,
                        onOpenJournal = { nav.navigatePush(Destination.Insights.route) },
                        onOpenAlarm = { nav.openSleepAlarmTab() },
                        onOpenSources = { nav.navigatePush(Destination.DataSources.route) },
                        onOpenWhoopImport = {
                            SessionUiFlags.autoLaunchWhoopImport = true
                            nav.navigatePush(Destination.DataSources.route)
                        },
                        onOpenTrends = { nav.navigateTopLevel(Destination.Trends.route) },
                        onOpenCharge = { nav.navigateTopLevel(Destination.Today.route) },
                    )
                }
                composable(Destination.CoupledView.route) {
                    CoupledScreen(
                        vm = viewModel,
                        // Tapping Sleep in the coupled read opens the full Sleep screen (iOS parity).
                        onOpenSleep = { nav.navigateTopLevel(Destination.Sleep.route) },
                    )
                }
                composable(Destination.Intervals.route) { IntervalsScreen(viewModel) }
                composable(Destination.Breathe.route) {
                    BreatheScreen(
                        viewModel,
                        onOpenStress = { nav.navigatePush(Destination.Stress.route) },
                    )
                }
                composable(Destination.Coach.route) { CoachScreen() }
                composable(Destination.Explore.route) { TrendsExploreScreen(viewModel) }
                composable(Destination.Automations.route) {
                    AutomationsScreen(
                        viewModel,
                        onOpenAlarm = { nav.openSleepAlarmTab() },
                    )
                }
                // Legacy smart_alarm deep-link / back-stack → Sleep → Alarm (no Wake settings surface).
                // Never leave a blank NavHost shell while LaunchedEffect hops — paint Alarm editor
                // as fallback chrome so deep-links / restored back-stack aren't an empty frame.
                composable(Destination.SmartAlarm.route) {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        nav.openSleepAlarmTab()
                    }
                    SmartAlarmScreen(vm = viewModel, embedded = true)
                }
                composable(Destination.Workouts.route) { WorkoutsScreen(viewModel) }
                composable(Destination.Intelligence.route) { IntelligenceScreen(viewModel) }

                // --- Placeholder routes (later waves fill these in) ---
                composable(Destination.Stress.route) {
                    StressScreen(
                        vm = viewModel,
                        onBreathe = { nav.navigatePush(Destination.Breathe.route) },
                    )
                }
                composable(Destination.Trends.route) {
                    TrendsScreen(
                        vm = viewModel,
                        onOpenSleepDay = { dayKey ->
                            viewModel.setBrowseDayKey(dayKey)
                            nav.navigateTopLevel(Destination.Sleep.route)
                        },
                        onOpenTodayDay = { dayKey ->
                            viewModel.setBrowseDayKey(dayKey)
                            nav.navigateTopLevel(Destination.Today.route)
                        },
                    )
                }
                composable(Destination.Insights.route) {
                    InsightsScreen(viewModel, onOpenInsightsHub = { nav.navigatePush(Destination.InsightsHub.route) })
                }
                composable(Destination.Compare.route) { CompareScreen(viewModel) }
                if (BuildConfig.DEBUG) {
                    composable(Destination.WhoopAlgoCompare.route) {
                        WhoopAlgoCompareScreen(
                            vm = viewModel,
                            onOpenHealthConnect = { nav.navigatePush(Destination.Health.route) },
                        )
                    }
                }
                composable(Destination.Health.route) {
                    HealthScreen(
                        vm = viewModel,
                        onVitalClick = { nav.navigatePush("vital_detail/$it") },
                        onOpenLabBook = { nav.navigatePush(Destination.LabBook.route) },
                        onOpenFusedRecord = { nav.navigatePush(Destination.FusedRecord.route) },
                        onOpenPeriodCalendar = { nav.navigateTopLevel(Destination.PeriodCalendar.route) },
                        onOpenSettingsPeriodTracking = {
                            SessionUiFlags.settingsFocusPeriodTracking = true
                            nav.navigatePush(Destination.Settings.route)
                        },
                    )
                }
                composable(Destination.Hydration.route) { HydrationScreen(viewModel) }
                composable(Destination.Nutrition.route) {
                    NutritionScreen(
                        viewModel = viewModel,
                        onOpenHydration = { nav.navigate(Destination.Hydration.route) },
                        onOpenToday = { nav.navigateTopLevel(Destination.Today.route) },
                    )
                }
                composable(Destination.VitalSigns.route) {
                    VitalSignsScreen(
                        vm = viewModel,
                        onVitalClick = { nav.navigatePush("vital_detail/$it") },
                    )
                }
                composable(Destination.VitalSignsDetail.route) { backStackEntry ->
                    VitalDetailScreen(
                        vm = viewModel,
                        key = backStackEntry.arguments?.getString("key").orEmpty(),
                    )
                }
                // --- v5 pillar screens (Wave 3 wiring) ---
                composable(Destination.InsightsHub.route) { InsightsHubScreen(viewModel) }
                composable(Destination.LabBook.route) { LabBookScreen(viewModel) }
                composable(Destination.PeriodCalendar.route) {
                    PeriodCalendarScreen(
                        viewModel,
                        onOpenSettingsPeriodTracking = {
                            SessionUiFlags.settingsFocusPeriodTracking = true
                            nav.navigatePush(Destination.Settings.route)
                        },
                    )
                }
                composable(Destination.Rhythm.route) {
                    // EXPERIMENTAL: self-gates on its own consent clickwrap (default OFF). The night
                    // summary + per-window Poincaré results land with the rhythm capture pipeline; until
                    // then it renders its honest "no clear reading yet" empty state behind the gate.
                    RhythmScreen(night = null, windows = emptyList())
                }
                composable(Destination.FusedRecord.route) { FusedRecordRoute(viewModel) }
                composable(Destination.AppleHealth.route) {
                    AppleHealthScreen(
                        viewModel,
                        onOpenDataSources = { nav.navigatePush(Destination.DataSources.route) },
                    )
                }
                composable(Destination.Devices.route) {
                    DevicesScreen(
                        viewModel,
                        onUseFileImport = { nav.navigatePush(Destination.DataSources.route) },
                        onOpenLive = { nav.navigatePush(Destination.Live.route) },
                    )
                }
                composable(Destination.DataSources.route) { DataSourcesScreen(viewModel) }
                composable(Destination.BackupSync.route) { BackupSyncScreen() }
                composable(Destination.Notifications.route) { NotificationsSettingsScreen(viewModel) }
                composable(Destination.Settings.route) {
                    SettingsScreen(
                        viewModel,
                        onOpenTestCentre = {
                            if (BuildConfig.DEBUG) nav.navigatePush(Destination.TestCentre.route)
                        },
                        onOpenBugReport = { nav.navigatePush(Destination.BugReport.route) },
                        onOpenGoals = { nav.navigatePush(Destination.Goals.route) },
                        onOpenBackupSync = { nav.navigatePush(Destination.BackupSync.route) },
                        onOpenStepTraining = { nav.navigatePush(Destination.StepTraining.route) },
                        onOpenQuickStart = { nav.navigatePush(Destination.QuickStart.route) },
                        onOpenDevices = { nav.navigatePush(Destination.Devices.route) },
                        onOpenDataSources = { nav.navigatePush(Destination.DataSources.route) },
                        onOpenNotifications = { nav.navigatePush(Destination.Notifications.route) },
                        onOpenFriendsNetwork = { nav.navigatePush(Destination.FriendsNetwork.route) },
                    )
                }
                composable(Destination.BugReport.route) { BugReportScreen(viewModel) }
                composable(Destination.FriendsNetwork.route) { FriendsNetworkScreen() }
                if (BuildConfig.DEBUG) {
                    composable(Destination.TestCentre.route) { TestCentreScreen(viewModel) }
                }
                composable(Destination.Goals.route) {
                    GoalsBoardScreen(
                        onOpenLive = { nav.navigatePush(Destination.Live.route) },
                        onOpenSettings = { nav.navigatePush(Destination.Settings.route) },
                        onOpenTestCentre = {
                            // MAIN has no Test Centre route — Settings is the honest landing.
                            if (BuildConfig.DEBUG) nav.navigatePush(Destination.TestCentre.route)
                            else nav.navigatePush(Destination.Settings.route)
                        },
                        onOpenSleep = { nav.navigateTopLevel(Destination.Sleep.route) },
                        onOpenToday = { nav.navigateTopLevel(Destination.Today.route) },
                        onOpenWhoopCompare = {
                            if (BuildConfig.DEBUG) nav.navigatePush(Destination.WhoopAlgoCompare.route)
                            else nav.navigateTopLevel(Destination.Today.route)
                        },
                        onOpenDevices = { nav.navigatePush(Destination.Devices.route) },
                        onOpenHealth = { nav.navigatePush(Destination.Health.route) },
                    )
                }
                composable(Destination.StepTraining.route) { StepTrainingScreen(viewModel) }
                composable(Destination.QuickStart.route) {
                    QuickStartGuideScreen(onDone = { nav.popBackStack() })
                }
                // The "More" page — drill-ins PUSH so system back returns to More, not Home.
                composable(Destination.More.route) {
                    MoreScreen(
                        onNavigate = { route ->
                            if (route == Destination.SmartAlarm.route) nav.openSleepAlarmTab()
                            else nav.navigatePush(route)
                        },
                        showCycle = cycleEligibleInMore,
                        cycleTrackingOn = cycleTrackingOn,
                    )
                }
            }
            }
        }

        // Fable #208 — the floating GlassBottomBar, drawn OVER the NavHost as a true overlay (the old
        // Scaffold bottomBar slot reserved an opaque band under it). Non-clickable surfaces (the veil)
        // don't consume touches, so content under the crescents still scrolls; only the islands and the
        // + coin are tappable. onSizeChanged feeds the measured height (incl. its navigationBarsPadding)
        // back to the NavHost pad + LocalUnderBarInset above. Cycle tab only when opt-in is on —
        // Settings / Health toggle actually removes it; Cycle stays reachable from More → For your body.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { barHeightPx = it.height },
        ) {
            GlassBottomBar(
                current = current,
                showPeriodCalendarTab = cycleNavVisible,
                liveLooksLike5AmSibling = WhoopBleClient.isStaleUnwornSiblingName(live.advertisingName.orEmpty()),
                onTabSelected = { dest ->
                    if (dest.route != currentRoute) nav.navigateTopLevel(dest.route)
                },
                // Double-tap a tab: pop nested pushes and land on that tab's main page.
                onTabReselected = { dest ->
                    val popped = nav.popBackStack(dest.route, inclusive = false)
                    if (!popped) nav.navigateTopLevel(dest.route)
                },
                onLogWorkout = { nav.navigatePush(Destination.Workouts.route) },
                // Fable 200 #56 — Strength lives under Workouts; + sheet links there (not a side route).
                onStrengthTrainer = { nav.navigateTopLevel(Destination.Workouts.route) },
                onOpenSettings = { nav.navigatePush(Destination.Settings.route) },
                onOpenUpdates = { showUpdatesInbox = true },
                onQuickRoute = { route ->
                    when (route) {
                        HYDRATION_SIP_ROUTE -> {
                            if (!NoopPrefs.hydrationTracking(context)) {
                                Toast.makeText(
                                    context,
                                    "Turn on Hydration tracking in Settings first.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                nav.navigatePush(Destination.Settings.route)
                            } else {
                                snackScope.launch {
                                    runCatching {
                                        com.noop.analytics.HydrationStore.log(viewModel.repo, 250)
                                    }
                                    Toast.makeText(context, context.getString(R.string.hydration_sip_button), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        else -> nav.navigatePush(route)
                    }
                },
            )
        }


        // The Updates inbox (opened by the Today header bell). Presented here so it has the nav for
        // deep-links — a row's "trends" key switches the bottom tab, mirroring the iOS NavRouter route.
        if (showUpdatesInbox) {
            ModalBottomSheet(
                onDismissRequest = { showUpdatesInbox = false },
                // Open full-height (no half-pull) so it reads like the iOS Updates sheet, and use the
                // BEIGE surfaceBase so the white NoopCards POP — surfaceRaised made white cards sit on a
                // white sheet (no contrast), which is why the Android inbox looked flat vs iOS.
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Palette.surfaceBase,
                contentColor = Palette.textPrimary,
            ) {
                UpdatesInboxScreen(
                    store = updateStore,
                    onClose = { showUpdatesInbox = false },
                    onDeepLink = { key ->
                        // Map inbox deep-link keys → routes. Alarm aliases open Sleep → Alarm (#337).
                        when (key.lowercase()) {
                            "trends" -> {
                                if (currentRoute != Destination.Trends.route) {
                                    nav.navigateTopLevel(Destination.Trends.route)
                                }
                            }
                            "sleep" -> {
                                if (currentRoute != Destination.Sleep.route) {
                                    nav.navigateTopLevel(Destination.Sleep.route)
                                }
                            }
                            "alarm", "alarms", "smart_alarm", "smart-alarm", "wake" -> {
                                nav.openSleepAlarmTab()
                            }
                            "today" -> {
                                if (currentRoute != Destination.Today.route) {
                                    nav.navigateTopLevel(Destination.Today.route)
                                }
                            }
                            else -> Unit
                        }
                    },
                    onRestore = { cardId ->
                        // Flip the shared dismissed flag back off so the card reappears, and signal a
                        // mounted Today to re-read it immediately (SharedPreferences isn't reactive).
                        TodayCardDismissal.setDismissed(context, cardId, false)
                        updateStore.restoreRequest = cardId
                    },
                )
            }
        }

        // DEBUG: pin notes anywhere so multiple UI fixes can be batched for the agent.
        if (BuildConfig.DEBUG) {
            ReviewPinOverlay(currentRoute = currentRoute)
        }
    }
}

// MARK: - More page
//
// The "More" tab's destination — a full navigated page (mirroring the iOS More tab's NavigationStack
// List), replacing the old pull-up ModalBottomSheet. It hosts the SAME grouped destinations
// ([drawerGroups]) inside a [ScreenScaffold], with the exact section-header + row styling the sheet
// used (uppercase [Overline] group labels, icon + label [NavigationDrawerItem] rows) — now with a
// trailing chevron so each row reads as a navigation push, matching the iOS disclosure rows. Tapping a
// row navigates top-level; there is no sheet to dismiss. The floating bottom bar stays visible because
// this is just another NavHost destination under the same Scaffold.

/** Body-care destinations pinned above More groups.
 *  SHIP #192 — Lab Book lives under Health only (not a More twin pin). */
private val moreBodyPinDestinations = listOf(
    Destination.PeriodCalendar,
    Destination.Health,
    Destination.StepTraining,
)

/** SHIP #115/#254 — quiet aliases so Alarm / Themes / Sources / Cycle / etc. find their rows. */
private fun moreSearchAliases(dest: Destination): List<String> = when (dest) {
    Destination.SmartAlarm -> listOf("alarm", "wake", "smart alarm")
    Destination.PeriodCalendar -> listOf(
        "cycle", "period", "period tracking", "p.c.", "pc", "menstrual", "menses",
        "cycle on", "cycle off", "turn on cycle",
    )
    Destination.Settings -> listOf(
        "themes", "appearance", "theme", "cycle", "period", "period tracking", "health",
        "strap", "worn", "mg", "5am", "sibling", "battery", "overnight", "hrv",
    )
    Destination.BackupSync -> listOf("backup", "export", "restore", "auto-restore")
    Destination.BugReport -> listOf("bug", "report", "feedback", "crash", "screenshot")
    Destination.FriendsNetwork -> listOf(
        "friends", "invite", "tailscale", "tailnet", "share", "charge", "effort",
    )
    Destination.DataSources -> listOf("sources", "whoop", "hc", "health connect", "import")
    Destination.Stress -> listOf("stress")
    Destination.Devices -> listOf("devices", "strap", "pair", "ble", "worn", "mg", "5am", "sibling", "unworn")
    Destination.Notifications -> listOf("notifications", "alerts")
    else -> emptyList()
}
private fun moreSearchHaystack(dest: Destination, title: String, context: android.content.Context): String = buildString {
    append(title)
    val secondary = when (dest) {
        Destination.Coach -> context.getString(R.string.more_sec_coach)
        Destination.Insights -> context.getString(R.string.more_sec_insights)
        Destination.InsightsHub -> context.getString(R.string.more_sec_insights_hub)
        else -> moreRowSecondary(dest)
    }
    secondary?.let { append(' '); append(it) }
    moreSearchAliases(dest).forEach { append(' '); append(it) }
}

/** The full grouped destination list as a navigated page (the iOS More tab's twin). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreScreen(
    onNavigate: (String) -> Unit,
    showCycle: Boolean = true,
    cycleTrackingOn: Boolean = true,
) {
    // S2 parity: each group's open/closed state, seeded from `defaultExpanded` (Insights + Body open,
    // Data + App collapsed). PERSISTED (#860 item 2): the user's open/closed choice must survive leaving
    // and re-entering the More page (and relaunch), not reset to the seed every visit. Backed by
    // [MoreSectionPrefs] (a CSV of expanded headers in SharedPreferences), mirroring the iOS
    // @AppStorage("more.expandedSections"). Seeded ONCE from the stored value so first run still shows the
    // Insights+Body default; every toggle writes through so the next visit reflects the saved state.
    val context = androidx.compose.ui.platform.LocalContext.current
    val expanded = remember {
        val stored = MoreSectionPrefs.read(NoopPrefs.of(context), defaultExpandedHeaders())
        androidx.compose.runtime.mutableStateMapOf<String, Boolean>().apply {
            drawerGroups.forEach { put(it.header, stored.contains(it.header)) }
        }
    }
    var query by remember { mutableStateOf("") }
    val queryTrimmed = query.trim()
    val searching = queryTrimmed.isNotEmpty()
    val visiblePins = remember {
        moreBodyPinDestinations
    }
    // SmartAlarm is registered in NavHost but omitted from drawerGroups (Sleep → Alarm). Include it
    // in the search corpus so alarm / wake / smart alarm still surface a navigable row.
    val moreSearchCorpus = remember {
        (visiblePins + drawerGroups.flatMap { it.items } + Destination.SmartAlarm)
            .distinct()
    }
    val searchMatches = remember(queryTrimmed) {
        if (queryTrimmed.isEmpty()) emptyList()
        else moreSearchCorpus.filter { dest ->
            val title = context.getString(dest.titleRes)
            moreSearchHaystack(dest, title, context).contains(queryTrimmed, ignoreCase = true)
        }
    }
    // Impeccable: liquid sky + short destination copy so Cycle / Lab Book / training are first-class.
    LazyScreenScaffold(
        title = stringResource(R.string.nav_more),
        subtitle = stringResource(R.string.more_subtitle),
        topBackground = { LiquidScreenSky() },
    ) {
        // Debug builds: Test Centre is the only charging-preview door (no duplicate More card).
        if (BuildConfig.DEBUG) {
            item {
                TextButton(onClick = { onNavigate(Destination.TestCentre.route) }) {
                    Text(
                        "Test Centre · charging preview",
                        style = NoopType.footnote,
                        color = Palette.accent,
                    )
                }
            }
        }
        item {
            val searchPlaceholder = stringResource(R.string.more_search_placeholder)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = {
                    Text(
                        searchPlaceholder,
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = Palette.textTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear search",
                                tint = Palette.textTertiary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Palette.textPrimary,
                    unfocusedTextColor = Palette.textPrimary,
                    cursorColor = Palette.accent,
                    focusedBorderColor = Palette.hairline,
                    unfocusedBorderColor = Palette.hairline,
                    focusedContainerColor = Palette.surfaceInset,
                    unfocusedContainerColor = Palette.surfaceInset,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = searchPlaceholder
                    },
            )
        }
        if (searching) {
            // Flat matches (aliases included); SmartAlarm surfaces even though it is not in drawerGroups.
            item {
                if (searchMatches.isEmpty()) {
                    Text(
                        "No matches",
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                } else {
                    NoopCard(padding = 0.dp) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            searchMatches.forEachIndexed { i, dest ->
                                MoreRow(
                                    dest = dest,
                                    onClick = { onNavigate(dest.route) },
                                    cycleTrackingOn = cycleTrackingOn,
                                )
                                if (i < searchMatches.lastIndex) {
                                    HorizontalDivider(
                                        color = Palette.hairline,
                                        modifier = Modifier.padding(start = 50.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Pin body-care destinations at the top so Cycle / Lab Book are never buried in a collapsed group.
            item {
                NoopCard(tint = Palette.restColor) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.more_body_pin_title),
                            style = NoopType.headline,
                            color = Palette.textPrimary,
                        )
                        Text(
                            stringResource(R.string.more_body_pin_blurb),
                            style = NoopType.footnote,
                            color = Palette.textTertiary,
                        )
                    }
                }
            }
            item {
                NoopCard(padding = 0.dp) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        visiblePins.forEachIndexed { i, dest ->
                            MoreRow(
                                dest = dest,
                                onClick = { onNavigate(dest.route) },
                                cycleTrackingOn = cycleTrackingOn,
                            )
                            if (i < visiblePins.lastIndex) {
                                HorizontalDivider(
                                    color = Palette.hairline,
                                    modifier = Modifier.padding(start = 50.dp),
                                )
                            }
                        }
                    }
                }
            }
            // Mirror the iOS More page groups (collapsible).
            drawerGroups.forEach { group ->
                val groupItems = group.items
                if (groupItems.isEmpty()) return@forEach
                val isOpen = expanded[group.header] ?: group.defaultExpanded
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MoreGroupHeader(
                            title = stringResource(group.headerRes),
                            expanded = isOpen,
                            onToggle = {
                                expanded[group.header] = !isOpen
                                val open = drawerGroups.map { it.header }.filter { expanded[it] == true }.toSet()
                                MoreSectionPrefs.write(NoopPrefs.of(context), open)
                            },
                        )
                        if (isOpen) {
                            NoopCard(padding = 0.dp) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    groupItems.forEachIndexed { i, dest ->
                                        MoreRow(
                                            dest = dest,
                                            onClick = { onNavigate(dest.route) },
                                            cycleTrackingOn = cycleTrackingOn,
                                        )
                                        if (i < groupItems.lastIndex) {
                                            HorizontalDivider(
                                                color = Palette.hairline,
                                                modifier = Modifier.padding(start = 50.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A tappable group header for the More page (S2): the same UPPERCASE [Overline] label as before, now
 *  with a trailing chevron that rotates between open (0deg) and closed (-90deg), mirroring the iOS
 *  collapsible More sections. Tapping toggles the group; the whole row is the tap target. */
@Composable
private fun MoreGroupHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(durationMillis = 240, easing = NavEasing),
        label = "moreGroupChevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = title
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Overline(title, modifier = Modifier.weight(1f), color = Palette.textTertiary)
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Palette.textTertiary,
            modifier = Modifier
                .size(Metrics.iconSmall)
                .rotate(rotation),
        )
    }
}

/** One tappable destination row in the More page — accent icon + title + trailing chevron in a
 *  comfortable tap target, mirroring the iOS MoreRow. */
@Composable
private fun MoreRow(
    dest: Destination,
    onClick: () -> Unit,
    cycleTrackingOn: Boolean = true,
) {
    val secondary = when (dest) {
        Destination.Coach -> stringResource(R.string.more_sec_coach)
        Destination.Insights -> stringResource(R.string.more_sec_insights)
        Destination.InsightsHub -> stringResource(R.string.more_sec_insights_hub)
        else -> moreRowSecondary(dest, cycleTrackingOn)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(dest.icon, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(dest.titleRes), style = NoopType.body, color = Palette.textPrimary)
            // Fable 200 #106 — one-line secondary description in footnote.
            if (secondary != null) {
                Text(secondary, style = NoopType.footnote, color = Palette.textTertiary, maxLines = 1)
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Palette.textTertiary,
            modifier = Modifier.size(Metrics.iconSmall),
        )
    }
}

/** Quiet one-liners for More destinations (Fable 200 #106). Null = title only. */
private fun moreRowSecondary(dest: Destination, cycleTrackingOn: Boolean = true): String? = when (dest) {
    Destination.Live -> "Strap HR · pair and stream"
    Destination.Workouts -> "Sessions, Effort, strength"
    Destination.Sleep -> "Nights, Rest, alarm tools"
    Destination.Insights -> "Journal effects on Charge"
    Destination.InsightsHub -> "What moves your Charge"
    Destination.Intelligence -> "Patterns from your days"
    Destination.Coach -> "On-device guidance"
    Destination.Explore -> "Browse metrics over time"
    Destination.Compare -> "Side-by-side days"
    Destination.WhoopAlgoCompare -> "NOOP scores vs WHOOP app (not open BLE)"
    Destination.Health -> "Live HR · banked vitals · Lab Book"
    Destination.Nutrition -> "Meals · Fuel · Sip"
    Destination.VitalSigns -> "Browse vitals by day"
    Destination.LabBook -> "Cuff BP · open from Health"
    Destination.PeriodCalendar ->
        if (cycleTrackingOn) "Cycle · period calendar"
        else "Off · enable in Settings → Health & wellness"
    Destination.Stress -> "Daytime tip and load"
    Destination.Breathe -> "Guided breath"
    Destination.Intervals -> "Zones and effort blocks"
    Destination.Rhythm -> "Experimental night windows"
    Destination.SmartAlarm -> "Sleep → Alarm · phone + strap"
    Destination.Devices -> "Pair straps · Use worn MG when multi-bond"
    Destination.DataSources -> "WHOOP · HC · imports"
    Destination.AppleHealth -> "Imported Apple Health export"
    Destination.FusedRecord -> "Merged day record"
    Destination.BackupSync -> "On-device backup"
    Destination.Automations -> "Buzz · shortcuts · routines"
    Destination.Settings -> "Strap, themes, Cycle · Use worn MG"
    Destination.BugReport -> "Photos + diagnostics · GitHub"
    Destination.FriendsNetwork -> "Invite · private pipe · Charge/Effort"
    Destination.Notifications -> "Alerts you allow"
    Destination.Goals -> "Verified complete · Go test"
    Destination.StepTraining -> "Strength plans"
    Destination.QuickStart -> "First-night checklist"
    Destination.TestCentre -> "DEBUG demos only · not Settings"
    else -> null
}

/**
 * Crescent capsule: stadium with a circular bite for the fat centre +.
 * Deep bite so the lacquer tip reaches toward the bright glow core (kiss, not canyon).
 */
private class CrescentBarShape(
    private val biteFromRight: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = size.height / 2f
        val bar = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r)))
        }
        // Deep cup into the gutter — tips wrap the 66dp coin's glow core.
        val biteR = size.height * 0.92f
        val cx = if (biteFromRight) size.width + biteR * 0.58f else -biteR * 0.58f
        val bite = Path().apply {
            addOval(Rect(center = Offset(cx, size.height / 2f), radius = biteR))
        }
        val out = Path().apply {
            op(bar, bite, PathOperation.Difference)
        }
        return Outline.Generic(out)
    }
}

// MARK: - Glass bottom bar
//
// Three crescent islands: left tabs · floating + · right tabs. Theme packs may enable frosted glass.

/** A single bottom-bar nav slot: the destination it switches to, plus the bar-specific icon/label. */
private data class BarTab(val dest: Destination, val icon: ImageVector, @StringRes val labelRes: Int)

/** The nav slots in iOS order: Today · Trends · Sleep · More.
 *  More is special-cased (it opens the sheet rather than a route), so it is appended at the call site. */
/** The nav slots: Today · Trends · Sleep · More. Cycle is NOT on the bar (Gilbert —
 *  put Cycle in + / Today when enabled — nav 3-tab left was unpleasing). */
private val barLeadingTabs = listOf(
    BarTab(Destination.Today, Icons.Outlined.GridView, R.string.nav_today),
    // SHIP #13 — outlined weight matches Today/Sleep/More (Filled TrendingUp was heavier).
    BarTab(Destination.Trends, Icons.AutoMirrored.Outlined.TrendingUp, R.string.nav_trends),
)
private val barTrailingTabs = listOf(
    BarTab(Destination.Sleep, Icons.Outlined.Bedtime, R.string.nav_sleep),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassBottomBar(
    current: Destination,
    onTabSelected: (Destination) -> Unit,
    onTabReselected: (Destination) -> Unit,
    /** When true, Cycle is offered from + quick actions / Today — never the bottom bar. */
    showPeriodCalendarTab: Boolean = false,
    /** Multi-bond Fold: live LE looks like unworn 5AM sibling — Quick actions Devices cue. */
    liveLooksLike5AmSibling: Boolean = false,
    onLogWorkout: () -> Unit = {},
    onStrengthTrainer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenUpdates: () -> Unit = {},
    onQuickRoute: (String) -> Unit = {},
) {
    val barShape = RoundedCornerShape(50)
    val leftCrescent = remember { CrescentBarShape(biteFromRight = true) }
    val rightCrescent = remember { CrescentBarShape(biteFromRight = false) }
    val frosted = ThemePackPrefs.current.frostedNav
    // More translucent islands so sky/scene reads through — continuous with the figure/sky, not a slab.
    // When day-cycle sky is off, raise island opacity slightly so the bar still separates from flat canvas.
    val navCtx = androidx.compose.ui.platform.LocalContext.current
    val dayCycleOn = remember { NoopPrefs.showDayCycleBackground(navCtx) }
    // Light + day-cycle: paper islands (not translucent white) so midday sky cannot blow out the bar.
    val islandColor = if (frosted) {
        if (Palette.isLight) {
            Palette.surfaceRaised.copy(alpha = if (dayCycleOn) 0.82f else 0.68f)
        } else {
            Color.White.copy(alpha = 0.12f)
        }
    } else {
        // Translucent lacquer — not an opaque black slab; sky still reads through.
        // #398 — day-cycle off stays more open so the + aura is not washed by island lacquer.
        Palette.surfaceRaised.copy(
            alpha = when {
                Palette.isLight && dayCycleOn -> 0.78f
                Palette.isLight -> 0.62f
                dayCycleOn -> 0.42f
                else -> 0.28f
            },
        )
    }
    val islandBorder = if (frosted) {
        // Light: ink hairline (white rim vanishes on paper / bright sky). Dark: soft white.
        if (Palette.isLight) {
            Palette.hairlineStrong.copy(alpha = 0.72f)
        } else {
            Color.White.copy(alpha = 0.32f)
        }
    } else {
        // Soft gold rim so crescents read with the + bloom, not as a flat black cutout.
        Palette.accent.copy(alpha = if (Palette.isLight) 0.48f else 0.28f)
    }
    // Always 4 tabs: Today · Trends | Sleep · More (Cycle never on the bar).
    val allTabs = remember {
        barLeadingTabs + barTrailingTabs + listOf(
            BarTab(Destination.More, Icons.Outlined.MoreHoriz, R.string.nav_more),
        )
    }
    // Balanced crescents: odd counts put the extra on the LEFT so Cycle stays with
    // Today/Trends (DESIGN.md order) and Sleep|More stay a paired right island.
    val mid = barLeftTabCount(allTabs.size)
    var showPlusSheet by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Fable 200 #2 / 300 #30: one-shot tip above the lacquer coin — dismiss on Got it or first armed hold.
    var showHoldCoach by remember { mutableStateOf(!NoopPrefs.holdPlusCoachSeen(context)) }
    val dismissHoldCoach: () -> Unit = {
        if (showHoldCoach) {
            showHoldCoach = false
            NoopPrefs.setHoldPlusCoachSeen(context)
        }
    }

    if (showPlusSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlusSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Palette.surfaceRaised,
            contentColor = Palette.textPrimary,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.plus_sheet_title), style = NoopType.headline, color = Palette.textPrimary)
                Text(
                    stringResource(R.string.plus_sheet_blurb),
                    style = NoopType.footnote,
                    color = Palette.textSecondary,
                )
                if (showPeriodCalendarTab) {
                    WetBounceButton(
                        label = stringResource(R.string.nav_period_calendar),
                        modifier = Modifier.fillMaxWidth(),
                        tint = Palette.restColor,
                        onClick = {
                            showPlusSheet = false
                            onQuickRoute(Destination.PeriodCalendar.route)
                        },
                    )
                }
                WetBounceButton(
                    label = stringResource(R.string.plus_next_look),
                    modifier = Modifier.fillMaxWidth(),
                    tint = Palette.accent,
                    onClick = {
                        val packs = ThemePacks.all
                        val i = packs.indexOfFirst { it.id == ThemePackPrefs.packId }.coerceAtLeast(0)
                        val next = packs[(i + 1) % packs.size]
                        ThemePackPrefs.set(context, next)
                        Toast.makeText(
                            context,
                            context.getString(R.string.plus_look_toast, next.localizedLabel(context)),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
                WetBounceButton(
                    label = stringResource(R.string.plus_live_hr),
                    modifier = Modifier.fillMaxWidth(),
                    tint = Palette.chargeColor,
                    onClick = {
                        showPlusSheet = false
                        onQuickRoute(Destination.Live.route)
                    },
                )
                WetBounceButton(
                    label = if (liveLooksLike5AmSibling) {
                        stringResource(R.string.plus_devices_worn_mg)
                    } else {
                        stringResource(R.string.plus_devices_reconnect)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    tint = if (liveLooksLike5AmSibling) Palette.accent else Palette.textSecondary,
                    onClick = {
                        showPlusSheet = false
                        if (liveLooksLike5AmSibling) {
                            // Triggers Devices + Use worn MG snackbar action (same path as FGS tap).
                            SessionUiFlags.openDevicesOnce = true
                        } else {
                            onQuickRoute(Destination.Devices.route)
                        }
                    },
                )
                WetBounceButton(
                    label = stringResource(R.string.plus_log_workout),
                    modifier = Modifier.fillMaxWidth(),
                    tint = Palette.effortColor,
                    onClick = {
                        showPlusSheet = false
                        onLogWorkout()
                    },
                )
                WetBounceButton(
                    label = stringResource(R.string.plus_updates),
                    modifier = Modifier.fillMaxWidth(),
                    tint = Palette.accent,
                    onClick = {
                        showPlusSheet = false
                        onOpenUpdates()
                    },
                )
                WetBounceButton(
                    label = stringResource(R.string.plus_nutrition),
                    modifier = Modifier.fillMaxWidth(),
                    tint = Palette.chargeColor,
                    onClick = {
                        showPlusSheet = false
                        onQuickRoute(Destination.Nutrition.route)
                    },
                )
                WetBounceButton(
                    label = stringResource(R.string.hydration_sip_button),
                    modifier = Modifier.fillMaxWidth(),
                    tint = Palette.metricCyan,
                    onClick = {
                        showPlusSheet = false
                        onQuickRoute(HYDRATION_SIP_ROUTE)
                    },
                )
                WetBounceButton(
                    label = stringResource(R.string.plus_journal),
                    modifier = Modifier.fillMaxWidth(),
                    tint = Palette.textSecondary,
                    onClick = {
                        showPlusSheet = false
                        onQuickRoute(Destination.Insights.route)
                    },
                )
                WetBounceButton(
                    label = stringResource(R.string.nav_breathe),
                    modifier = Modifier.fillMaxWidth(),
                    tint = Palette.restColor,
                    onClick = {
                        showPlusSheet = false
                        onQuickRoute(Destination.Breathe.route)
                    },
                )
                WetBounceButton(
                    label = stringResource(R.string.nav_settings),
                    modifier = Modifier.fillMaxWidth(),
                    tint = Palette.textSecondary,
                    onClick = {
                        showPlusSheet = false
                        onOpenSettings()
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 0.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Full-bleed bottom glass — short soft underlap into the crescents (not a tall slab that
        // reads as a hard cut above the Samsung system nav).
        GlassDiffusionVeil(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            height = 72.dp,
            fromTop = false,
            dayCycleOn = dayCycleOn,
            softenBottomEdge = true,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                // Tabletop / fold-unfold: allow a wider island row before capping (Fable Today #32).
                .widthIn(max = 640.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showHoldCoach) {
                HoldPlusCoachTip(onDismiss = dismissHoldCoach)
            }
        // + lives IN a narrow gutter; crescents cup it via deep bites. Glow is drawn in a
        // FULL-WIDTH layer BEHIND the row so a 40dp gutter can never clip the radiating aura.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp),
            contentAlignment = Alignment.Center,
        ) {
            val leftTabs = allTabs.take(mid)
            val rightTabs = allTabs.drop(mid)
            val (leftWeight, rightWeight) = barCrescentWeights(leftTabs.size, rightTabs.size)
            val leftCompact = leftTabs.size >= 3
            val rightCompact = rightTabs.size >= 3
            // Keep coin at 66dp. Extend crescents INTO the gutter (overlap) so tips reach the
            // bright glow — do not shrink the + to fake closeness.
            val plusGutter = 32.dp
            val crescentOverlap = 12.dp
            // Soft frost: no drop shadow on crescents (Fold/OLED slab edge) — hairline + veil only.
            // SHIP #8 — tighter gutter + deeper crescent bite so the gap reads as kiss, not void.
            val cycleOffBalanced = !showPeriodCalendarTab && leftTabs.size == 2 && rightTabs.size == 2
            val rightEndPad = if (cycleOffBalanced) 10.dp else 4.dp
            val reducedGlow = rememberReduceMotion()
            val screenHeightDp = LocalConfiguration.current.screenHeightDp
            val glowDiameter = plusIdleGlowDiameterDp(screenHeightDp).dp
            val glowLift = plusIdleGlowLiftDp(screenHeightDp).dp
            val infiniteGlow = rememberInfiniteTransition(label = "plusAura")
            val breath by infiniteGlow.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "plusAuraBreath",
            )
            // Theme-matched bloom — uses Palette.accent / gold from the active pack (not forced champagne gold).
            val auraPulse = if (reducedGlow) 0.6f else breath
            val glow = Palette.accent
            val glowLight = Palette.goldLight
            val glowDeep = Palette.goldDeep
            val layerAlpha = plusIdleAuraLayerAlpha(reducedGlow, auraPulse)
            val stopScale = plusIdleAuraStopScale(reducedGlow)
            Canvas(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = -glowLift)
                    .size(glowDiameter)
                    .zIndex(0f)
                    .graphicsLayer { alpha = layerAlpha },
            ) {
                val r = size.minDimension / 2f
                val hotBase = if (Palette.isLight) 0.55f else 0.90f
                val hot = Color.White.copy(alpha = hotBase * stopScale)
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to hot,
                            0.08f to glowLight.copy(alpha = 0.92f * stopScale),
                            0.18f to glow.copy(alpha = 0.78f * stopScale),
                            0.34f to glowDeep.copy(alpha = 0.48f * stopScale),
                            0.52f to glow.copy(alpha = 0.22f * stopScale),
                            0.72f to glow.copy(alpha = 0.08f * stopScale),
                            0.88f to glow.copy(alpha = 0.025f * stopScale),
                            1.00f to Color.Transparent,
                        ),
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                )
                val coreR = r * (0.34f + 0.06f * auraPulse)
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to hot.copy(alpha = 1f * stopScale),
                            0.22f to glowLight.copy(alpha = 0.90f * stopScale),
                            0.55f to glow.copy(alpha = 0.50f * stopScale),
                            0.82f to glowDeep.copy(alpha = 0.18f * stopScale),
                            1.00f to Color.Transparent,
                        ),
                        center = center,
                        radius = coreR,
                    ),
                    radius = coreR,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = leftCrescent,
                    color = islandColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .weight(leftWeight)
                        // Pull lacquer origin toward the + / bright core.
                        .offset(x = crescentOverlap)
                        .border(0.5.dp, islandBorder, leftCrescent),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        leftTabs.forEach { tab ->
                            BarSlot(
                                icon = tab.icon,
                                label = stringResource(tab.labelRes),
                                active = isBarTabActive(tab, current),
                                overflowDot = tab.dest == Destination.More &&
                                    current != Destination.Today &&
                                    current != Destination.Trends &&
                                    current != Destination.Sleep &&
                                    current != Destination.More,
                                compact = leftCompact,
                                modifier = Modifier.weight(1f),
                                onClick = { onTabSelected(tab.dest) },
                                onDoubleClick = { onTabReselected(tab.dest) },
                            )
                        }
                    }
                }

                // 66dp coin stays full size; gutter is layout only — crescents overlap into it.
                Box(
                    modifier = Modifier
                        .width(plusGutter)
                        .height(100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CenterPlusButton(
                        onClick = { showPlusSheet = true },
                        // Five spokes: Workout (top) · Live · Nutrition · Devices · Water.
                        // Strength stays under Workouts / tap sheet; Water (sip) returns to the dial.
                        radialActions = listOf(
                            PlusRadialAction("Workout", Icons.Filled.FitnessCenter, Destination.Workouts.route),
                            PlusRadialAction("Live HR · BLE", Icons.Filled.MonitorHeart, Destination.Live.route),
                            PlusRadialAction("Nutrition", Icons.Filled.Restaurant, Destination.Nutrition.route),
                            PlusRadialAction(
                                if (liveLooksLike5AmSibling) "Use worn MG" else "Devices",
                                Icons.Filled.Sensors,
                                Destination.Devices.route,
                            ),
                            PlusRadialAction("Water", Icons.Filled.WaterDrop, HYDRATION_SIP_ROUTE),
                        ),
                        onRadialSelect = { action ->
                            if (action.route == Destination.Devices.route && liveLooksLike5AmSibling) {
                                SessionUiFlags.openDevicesOnce = true
                            } else {
                                onQuickRoute(action.route)
                            }
                        },
                        onHoldArmed = dismissHoldCoach,
                        drawIdleGlow = false,
                    )
                }

                Surface(
                    shape = rightCrescent,
                    color = islandColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .weight(rightWeight)
                        .offset(x = -crescentOverlap)
                        .border(0.5.dp, islandBorder, rightCrescent),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(start = 6.dp, end = rightEndPad, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        rightTabs.forEach { tab ->
                            BarSlot(
                                icon = tab.icon,
                                label = stringResource(tab.labelRes),
                                active = isBarTabActive(tab, current),
                                overflowDot = tab.dest == Destination.More &&
                                    current != Destination.Today &&
                                    current != Destination.Trends &&
                                    current != Destination.Sleep &&
                                    current != Destination.More,
                                compact = rightCompact,
                                modifier = Modifier.weight(1f),
                                onClick = { onTabSelected(tab.dest) },
                                onDoubleClick = { onTabReselected(tab.dest) },
                            )
                        }
                    }
                }
            }
        }
        } // Column (coach tip + bar row)
    }
}

/** One-shot hold-+ coach (Fable 200 #2 / 300 #30 / 200 #102). Lacquer tip, gold rim — not a bolted card. */
@Composable
private fun HoldPlusCoachTip(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .padding(bottom = 8.dp)
            .border(0.5.dp, Palette.accent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .background(Palette.surfaceRaised.copy(alpha = 0.94f), RoundedCornerShape(14.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics {
                contentDescription =
                    "Tip. Tap the plus for the menu. Hold and swipe for Workout, Live HR, Nutrition, Devices, or Water. Tap to dismiss."
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Hold + · dial",
            style = NoopType.subhead,
            color = Palette.accent,
        )
        Text(
            "Workout · Live · Nutrition · Devices · Water",
            style = NoopType.footnote,
            color = Palette.textSecondary,
        )
        // SHIP #358/#372 — reduce-motion: no bounce reliance; plain scale/opacity teach line.
        if (rememberReduceMotion()) {
            Text(
                "Reduce motion on · hold still arms the dial (no bounce teach).",
                style = NoopType.caption,
                color = Palette.textTertiary,
            )
        }
        TextButton(onClick = onDismiss) {
            Text("Got it", style = NoopType.footnote, color = Palette.textTertiary)
        }
    }
}

/**
 * How many tabs sit in the left crescent.
 * 4 tabs → 2|2. 5 tabs (Cycle on) → 3|2 so Cycle stays with Today/Trends.
 */
internal fun barLeftTabCount(totalTabs: Int): Int = when {
    totalTabs <= 0 -> 0
    totalTabs <= 4 -> totalTabs / 2
    else -> (totalTabs + 1) / 2
}

/**
 * Island width weights for the floating bar (#396).
 * When one crescent holds more tabs (Cycle on → 3|2), give that side extra width so three
 * icons don't read optically heavier than two.
 */
internal fun barCrescentWeights(leftCount: Int, rightCount: Int): Pair<Float, Float> {
    val l = leftCount.coerceAtLeast(1)
    val r = rightCount.coerceAtLeast(1)
    val denserBoost = 1.32f
    return when {
        l == r -> l.toFloat() to r.toFloat()
        l > r -> (l * denserBoost) to r.toFloat()
        else -> l.toFloat() to (r * denserBoost)
    }
}

/** More-overflow cue — ash, never accent/gold (#397: don't compete with + aura). */
@Composable
internal fun barOverflowCueColor(): Color = Palette.textTertiary

/**
 * Layer alpha for the centre-+ idle aura (#394).
 * Reduce Motion used to keep ~0.98 (still bright for battery users); dim to a quiet static wash.
 */
internal fun plusIdleAuraLayerAlpha(reducedMotion: Boolean, breath: Float): Float =
    if (reducedMotion) 0.48f else (0.72f + 0.08f * breath.coerceIn(0f, 1f)).coerceIn(0f, 1f)

/** Multiplier on hot/glow stop alphas when Reduce Motion is on (#394). */
internal fun plusIdleAuraStopScale(reducedMotion: Boolean): Float =
    if (reducedMotion) 0.52f else 0.78f

/** PlusButton-owned idle glow alpha when Reduce Motion is on (was ~0.78 — still bright). */
internal fun plusButtonReducedAuraAlpha(holdBloom: Float): Float =
    (0.42f + 0.18f * holdBloom.coerceIn(0f, 1f)).coerceIn(0f, 1f)

/**
 * Idle + glow diameter in dp (#395).
 * A fixed 248–300dp disc on short Fold-cover heights clips under the system taskbar;
 * keep the bloom to ~40% of screen height (clamped 160…300).
 */
internal fun plusIdleGlowDiameterDp(screenHeightDp: Int): Int {
    val h = screenHeightDp.coerceAtLeast(200)
    return (h * 0.40f).toInt().coerceIn(160, 300)
}

/** Nudge the glow up on short covers so more of the bloom sits above the nav. */
internal fun plusIdleGlowLiftDp(screenHeightDp: Int): Int =
    if (screenHeightDp <= 420) 18 else 0

private fun isBarTabActive(tab: BarTab, current: Destination): Boolean {
    return if (tab.dest == Destination.More) {
        // SHIP #141 — More underline only on the More page itself.
        // Settings / Live / Workouts use the via-More overflow cue, not a fake active More tab.
        current == Destination.More
    } else {
        current == tab.dest
    }
}

/** Special hold-+ route: log a Hydration sip without opening a screen. */
private const val HYDRATION_SIP_ROUTE = "hydration_sip"

/** One vertex of the hold-to-reveal dial around the centre +. */
private data class PlusRadialAction(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

/**
 * Centre + — the "lacquer coin": a matte machined warm-black disc with an inlaid GOLD mark and a
 * fine gold rim. Designed on purpose (not stripped-to-avoid-ugly): gold is the app's primary-action
 * ink and the + IS the primary action, so it wears the token gold — never the theme swatch, which
 * could clash with Charge gold (Fable 300 #29). A lacquer/gold aura fills the gutter at rest with a
 * subtle idle breath (Fable 300 #356); Reduce Motion keeps a static wash. Hold still blooms the radial.
 *
 * States tell the story:
 *  - Press: the disc dips (scale), and the rim DRAWS a gold arc across the 160 ms arm time
 *    (Fable 200 #183) — hold-to-arm made visible instead of secret.
 *  - Armed: frosted triangle radial (Workout top); the + rotates 45° into an ×, showing that
 *    releasing back on the centre CANCELS (Fable 200 #4).
 *  - Reduce Motion: arc/rotation/bloom/breath render statically.
 * Tap → quick-actions sheet.
 */
@Composable
private fun CenterPlusButton(
    onClick: () -> Unit,
    radialActions: List<PlusRadialAction>,
    onRadialSelect: (PlusRadialAction) -> Unit,
    onHoldArmed: () -> Unit = {},
    /** When false, idle radiating glow is drawn by the parent (escapes the narrow gutter). */
    drawIdleGlow: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var holding by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var highlighted by remember { mutableStateOf<Int?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    val density = LocalDensity.current
    // The radial's frosted nodes still take a whisper of the theme swatch on selection GLASS, but the
    // coin itself is token gold on lacquer — the swatch never colours the primary mark (Fable 300 #29).
    val gold = Palette.accent
    val reduced = rememberReduceMotion()
    val holdBloom by animateFloatAsState(
        // Pre-152 fluid settle — 100ms tween snapped the dial; press() spring matches the lacquer coin.
        targetValue = if (holding) 1f else 0f,
        animationSpec = if (reduced) tween(0) else NoopMotion.press(),
        label = "plusHoldBloom",
    )
    // Hold-to-arm progress (Fable 200 #183): while pressed and not yet armed, the rim sweeps a gold
    // arc 0→360° across the arm window — released early (a tap) it snaps back. Static under
    // Reduce Motion (the LongPress haptic still marks the arm moment).
    val armSweep by animateFloatAsState(
        targetValue = if (pressed || holding) 1f else 0f,
        animationSpec = when {
            reduced -> tween(0)
            pressed || holding -> tween(110, easing = LinearEasing)
            else -> tween(70)
        },
        label = "plusArmSweep",
    )
    val pressScale by animateFloatAsState(
        targetValue = when {
            holding -> 0.93f
            pressed -> 0.97f
            else -> 1f
        },
        animationSpec = if (reduced) tween(0) else NoopMotion.press(),
        label = "plusPress",
    )
    val elev by animateFloatAsState(
        targetValue = if (holding) 1f else if (pressed) 3f else 4f,
        animationSpec = if (reduced) tween(0) else NoopMotion.press(),
        label = "plusElev",
    )

    // Five-spoke dial (pentagon-ish): Workout top · Live UL · Nutrition UR · Devices LL · Water LR.
    // Farther nodes = easier swipe. Offsets sized for Fold cover + phone.
    val radialOffsetsDp = listOf(
        0.dp to (-112).dp,
        (-96).dp to (-28).dp,
        96.dp to (-28).dp,
        (-78).dp to 86.dp,
        78.dp to 86.dp,
    )
    val hitRadius = 48.dp
    val buttonVisual = 66.dp
    val hitBox = 80.dp
    val holdMs = 110L

    fun pickIndex(localX: Float, localY: Float): Int? {
        if (radialActions.isEmpty()) return null
        val cx = with(density) { (hitBox / 2).toPx() }
        val cy = with(density) { (hitBox / 2).toPx() }
        val dx = localX - cx
        val dy = localY - cy
        val fromCentre = kotlin.math.sqrt(dx * dx + dy * dy)
        val minSwipe = with(density) { 22.dp.toPx() }
        if (fromCentre < minSwipe) return null

        // Nearest spoke by distance (works for 3–5 actions); angle is a soft prior for empty space.
        var best = 0
        var bestDist = Float.MAX_VALUE
        radialOffsetsDp.forEachIndexed { i, (ox, oy) ->
            if (i >= radialActions.size) return@forEachIndexed
            val tx = cx + with(density) { ox.toPx() }
            val ty = cy + with(density) { oy.toPx() }
            val d = (localX - tx) * (localX - tx) + (localY - ty) * (localY - ty)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        val maxD = with(density) { (hitRadius * 1.35f).toPx() }
        if (bestDist > maxD * maxD) return null
        return best.coerceIn(0, radialActions.lastIndex)
    }

    Box(
        modifier = modifier.size(hitBox),
        contentAlignment = Alignment.Center,
    ) {
        // Idle glow: parent FloatingBar draws the radiating particle bloom behind the row when
        // [drawIdleGlow] is false (narrow gutter would clip a 200dp aura). Keep a hot core here
        // only when this button owns the glow (previews / legacy call sites).
        if (drawIdleGlow) {
        val infinite = rememberInfiniteTransition(label = "plusIdleBreath")
        val breathPulse by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "plusIdleBreath",
        )
        val idleBreath = when {
            reduced || holding || pressed -> 0.5f
            else -> breathPulse
        }
        // Unmistakable gold glow (Gilbert QoL): wide halo + hot core + spark. + is the one glow exception.
        // Radial dial visuals are owned by the particle-lighting sibling — do not rewrite that Popup here.
        val auraAlpha = when {
            reduced -> plusButtonReducedAuraAlpha(holdBloom)
            holding || pressed -> 1f
            // SHIP #242 — light packs: raise idle aura so champagne doesn't vanish on paper.
            else -> if (Palette.isLight) 0.96f + 0.04f * idleBreath else 0.88f + 0.12f * idleBreath
        }
        Canvas(
            Modifier
                .size(168.dp)
                .graphicsLayer { alpha = auraAlpha * 0.88f }
                .clearAndSetSemantics { }, // SHIP #244 — decorative aura
        ) {
            val r = size.minDimension / 2f
            val champagne = Color(0xFFFFF1D0)
            val amber = Color(0xFFC4893A)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to champagne.copy(alpha = 0.38f),
                        0.22f to gold.copy(alpha = 0.34f),
                        0.50f to amber.copy(alpha = 0.12f),
                        0.78f to amber.copy(alpha = 0.04f),
                        1.00f to Color.Transparent,
                    ),
                    center = center,
                    radius = r,
                ),
                radius = r,
            )
        }
        Canvas(
            Modifier
                .size(96.dp)
                .graphicsLayer { alpha = auraAlpha },
        ) {
            val r = size.minDimension / 2f
            val champagne = Color(0xFFFFF1D0)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to champagne.copy(alpha = 0.88f),
                        0.40f to gold.copy(alpha = 0.48f),
                        0.78f to gold.copy(alpha = 0.12f),
                        1.0f to Color.Transparent,
                    ),
                    center = center,
                    radius = r,
                ),
                radius = r,
            )
        }
        // Tiny champagne spark so the glow reads even against bright day-cycle sky.
        Canvas(
            Modifier
                .size(36.dp)
                .graphicsLayer { alpha = auraAlpha * 0.92f },
        ) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFF6E0).copy(alpha = 0.90f),
                        gold.copy(alpha = 0.28f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = r,
                ),
                radius = r,
            )
        }
        } else if (holding || pressed) {
            // Hot core while armed even when parent owns idle radiate.
            Canvas(Modifier.size(96.dp).graphicsLayer { alpha = 0.95f }) {
                val r = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(gold.copy(alpha = 0.9f), gold.copy(alpha = 0.25f), Color.Transparent),
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                )
            }
        }

        if (holding || holdBloom > 0.02f) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.Center,
                onDismissRequest = { },
                properties = androidx.compose.ui.window.PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                ),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = holdBloom }
                        .then(
                            if (!reduced && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                Modifier.blur((10 + 6 * holdBloom).dp)
                            } else {
                                Modifier
                            },
                        )
                        // Light: soft charcoal wash (navy lacquer reads as a dark mode leftover on paper).
                        .background(
                            if (Palette.isLight) {
                                Color(0xFF1A1E24).copy(alpha = 0.32f * holdBloom)
                            } else {
                                Color(0xFF0B1220).copy(alpha = 0.42f * holdBloom)
                            },
                        ),
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    val coverH = LocalConfiguration.current.screenHeightDp
                    val dialHost = plusIdleGlowDiameterDp(coverH).coerceAtLeast(200).dp
                    Box(
                        Modifier
                            .padding(bottom = 72.dp)
                            .size(dialHost),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Particle / soft-light dial field (cool champagne) — does not rewrite the + gold aura.
                        RadialHoldDialField(
                            bloom = holdBloom,
                            selectedIndex = highlighted,
                            reducedMotion = reduced,
                            diameter = (168 + 24 * holdBloom).dp,
                        )
                        Text(
                            radialHoldDialCaption(hasSelection = highlighted != null),
                            style = NoopType.caption,
                            color = Color.White.copy(alpha = 0.62f * holdBloom),
                            modifier = Modifier.offset(y = 124.dp),
                        )
                        radialActions.zip(radialOffsetsDp).forEachIndexed { i, (action, off) ->
                            val selected = highlighted == i
                            val pop by animateFloatAsState(
                                // Quicker spoke pop: earlier gate + short appear (was 0.55 / 220+80ms).
                                // Dismiss stays ~140; Reduce Motion still tween(0).
                                targetValue = if (holding && holdBloom > 0.30f) 1f else 0f,
                                animationSpec = when {
                                    reduced -> tween(0)
                                    holding && holdBloom > 0.30f -> tween(
                                        durationMillis = 100,
                                        delayMillis = 0,
                                        easing = NoopMotion.EaseOutQuint,
                                    )
                                    else -> tween(140, easing = NoopMotion.EaseOutQuint)
                                },
                                label = "radialPop$i",
                            )
                            val scaleSel = if (selected) 1.20f else 1f
                            // Fable 200 #3 / #226: labels slide along the spoke while holding;
                            // Reduce Motion keeps labels snapped (no slide offset).
                            // SHIP #4 — selected spoke scales harder so the winning node is obvious.
                            val slide = if (reduced) 0.dp else 10.dp * pop
                            val labelDx = when {
                                off.first > 0.dp -> slide
                                off.first < 0.dp -> -slide
                                else -> 0.dp
                            }
                            val labelDy = when {
                                off.second < 0.dp -> -(slide * 0.55f)
                                else -> slide * 0.35f
                            }
                            Column(
                                Modifier
                                    .offset(x = off.first, y = off.second)
                                    .scale(scaleSel * (0.78f + 0.22f * pop))
                                    .graphicsLayer { alpha = pop },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(56.dp)
                                        .shadow(if (selected) 14.dp else 3.dp, CircleShape, clip = false)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) {
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        Color(0xFFF0D78A),
                                                        gold.copy(alpha = 0.98f),
                                                        gold.copy(alpha = 0.88f),
                                                    ),
                                                )
                                            } else {
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.28f),
                                                        Color.White.copy(alpha = 0.12f),
                                                    ),
                                                )
                                            },
                                        )
                                        .border(
                                            if (selected) 1.5.dp else 1.dp,
                                            Color.White.copy(alpha = if (selected) 0.88f else 0.32f),
                                            CircleShape,
                                        )
                                        // Tap a lit node (a11y / second-finger) — distinct from swipe-release.
                                        .clickable { onRadialSelect(action) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        action.icon,
                                        contentDescription = action.label,
                                        tint = if (selected) Color(0xFF1A1208) else Color.White,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                                Text(
                                    action.label,
                                    style = NoopType.caption.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    ),
                                    color = Color.White.copy(alpha = if (selected) 1f else 0.78f),
                                    modifier = Modifier.offset(x = labelDx, y = labelDy),
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(buttonVisual)
                .aspectRatio(1f)
                .scale(pressScale)
                .shadow(elev.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(
                    // Theme-matched lacquer — not a fixed black coin (Gilbert).
                    Brush.verticalGradient(
                        colors = listOf(
                            Palette.surfaceRaised.copy(alpha = 0.95f),
                            Palette.surfaceInset.copy(alpha = 0.98f),
                            Palette.surfaceBase,
                        ),
                    ),
                )
                // Theme accent rim (follows pack gold/accent).
                .border(1.25.dp, gold.copy(alpha = 0.72f + 0.28f * holdBloom), CircleShape)
                .pointerInput(radialActions) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            pressed = true
                            highlighted = null
                            holding = false
                            // Restricted pointer scope: use withTimeoutOrNull (not launch/delay).
                            val releasedEarly = withTimeoutOrNull(holdMs) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: return@withTimeoutOrNull true
                                    if (!change.pressed) return@withTimeoutOrNull true
                                }
                                @Suppress("UNREACHABLE_CODE")
                                false
                            }
                            if (releasedEarly == true) {
                                pressed = false
                                onClick()
                                continue
                            }
                            // Hold armed — frosted triangle.
                            holding = true
                            onHoldArmed()
                            haptic.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                            )
                            pickIndex(down.position.x, down.position.y)?.let { highlighted = it }
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                val selected = pickIndex(change.position.x, change.position.y)
                                if (selected != highlighted) {
                                    highlighted = selected
                                    if (selected != null) {
                                        // Fable 200 #5: platform CONFIRM (API 30+) — distinct from
                                        // TextHandleMove scroll ticks and LongPress arm. Fallback: ContextClick.
                                        val ok = if (android.os.Build.VERSION.SDK_INT >= 30) {
                                            view.performHapticFeedback(
                                                android.view.HapticFeedbackConstants.CONFIRM,
                                            )
                                        } else {
                                            view.performHapticFeedback(
                                                android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                            )
                                        }
                                        if (!ok) {
                                            haptic.performHapticFeedback(
                                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                                            )
                                        }
                                    }
                                }
                                if (!change.pressed) break
                            } while (true)
                            val pick = highlighted
                            pressed = false
                            holding = false
                            highlighted = null
                            if (pick != null && pick in radialActions.indices) {
                                onRadialSelect(radialActions[pick])
                            } else if (!SessionUiFlags.holdPlusCancelToastShown) {
                                // SHIP #6 — armed release with no sector felt broken; one quiet cue.
                                SessionUiFlags.holdPlusCancelToastShown = true
                                Toast.makeText(
                                    context,
                                    "Swipe to a shortcut or release centre to cancel",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            // Armed + no pick → cancel (don't open sheet after a failed swipe).
                        }
                    }
                }
                .semantics {
                    // Fable #19: lead with the swipe map — TalkBack users were missing it when the
                    // tap-menu sentence came first and the whole string ran long.
                    contentDescription =
                        "Tap for Quick actions menu. Hold and swipe: up Workout, left Live HR, right Devices. Release centre to cancel."
                },
            contentAlignment = Alignment.Center,
        ) {
            // Hold-to-arm rim arc (Fable 200 #183): gold sweep from 12 o'clock across the 160 ms arm
            // window, fading out as the radial bloom takes over. Skinny + glyph in GOLD (the primary-
            // action ink; Material Add is too heavy for the crescent kiss), rotating 45° into an ×
            // while armed — release on the centre cancels, and now the mark says so (Fable 200 #4).
            Canvas(Modifier.size(buttonVisual)) {
                if (armSweep > 0.01f && holdBloom < 0.98f) {
                    val inset = 1.5.dp.toPx()
                    drawArc(
                        color = gold.copy(alpha = 0.90f * (1f - holdBloom)),
                        startAngle = -90f,
                        sweepAngle = 360f * armSweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                rotate(degrees = 45f * holdBloom) {
                    // SHIP #1 — persistently skinny glyph (was 3.15dp Material-fat).
                    val stroke = 2.15.dp.toPx()
                    val arm = 28.dp.toPx() * 0.50f
                    drawLine(
                        color = gold,
                        start = Offset(center.x - arm, center.y),
                        end = Offset(center.x + arm, center.y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = gold,
                        start = Offset(center.x, center.y - arm),
                        end = Offset(center.x, center.y + arm),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** Tab selection — accent on icon + label + thin underline only (Gilbert). No full-tab wash. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BarSlot(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    overflowDot: Boolean = false,
    /** Slightly smaller icon/label when a crescent holds 3+ tabs (#396). */
    compact: Boolean = false,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
) {
    val iconTint = if (active) {
        Palette.accent.copy(alpha = 0.92f)
    } else {
        // SHIP #10 — inactive ash still reads as quiet chrome, not dead.
        Palette.textPrimary.copy(alpha = 0.72f)
    }
    val labelTint = if (active) {
        Palette.accent.copy(alpha = 0.92f)
    } else {
        Palette.textPrimary.copy(alpha = 0.72f)
    }
    val iconDp = if (compact) 18.dp else 20.dp
    val labelSp = if (compact) 8.5.sp else 9.5.sp
    val underlineDp = if (compact) 14.dp else 18.dp
    // SHIP #248 — large text: icons-only; TalkBack still gets the full name via contentDescription.
    val fontScale = LocalDensity.current.fontScale
    val iconsOnly = fontScale >= 1.15f
    Column(
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 48.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onDoubleClick = onDoubleClick,
            )
            .padding(vertical = 2.dp, horizontal = 2.dp)
            .semantics {
                contentDescription = if (overflowDot) {
                    "$label. Opened from More · still on More path. Double tap to return to its main page."
                } else {
                    "$label. Double tap to return to its main page."
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(iconDp))
        }
        if (!iconsOnly) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                label,
                style = NoopType.footnote.copy(
                    fontSize = labelSp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = 0.sp,
                ),
                color = labelTint,
                maxLines = 1,
            )
            // Overflow stays TalkBack-only ("Opened from More"). Never append "via More" beside
            // the More label — that read as "More via More".
        }
        }
        // Gold underline when active; suppress when overflowDot so dot + underline don't fight.
        Box(
            Modifier
                .padding(top = 1.dp)
                .width(underlineDp)
                .height(1.5.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(
                    when {
                        overflowDot -> Color.Transparent
                        active -> Palette.accent
                        else -> Color.Transparent
                    },
                ),
        )
    }
}

// MARK: - Navigation motion (DESIGN.md + NoopMotion)
//
// One decelerating curve (EaseOutQuint). Bar tabs fade-through; pushes use a soft shared-axis.
// Specs live on NoopMotion so Sleep↔Alarm and WetBounce share the same language.

/** @deprecated Prefer [NoopMotion.EaseOutQuint] — kept for local chevron tweens. */
private val NavEasing = NoopMotion.EaseOutQuint

/**
 * BrandMark — the NOOP logo glyph at a small in-app size: an OPEN recovery ring (≈80%
 * arc, round caps, starting at −90° / 12 o'clock, clockwise) in the gold gradient with a
 * solid gold core dot at the centre. This is the same brand glyph the RecoveryRing hero
 * carries (the "O" of NOOP), shrunk for the top bar / drawer header so the logo reads in
 * app. CLEAN/flat per the v3 restraint brief — no bloom, no halo, just the gradient ring.
 * Token-only (gold gradient + hairline track); decorative, so it carries no content label.
 */
@Composable
internal fun BrandMark(size: Dp = 22.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.13f          // ~2px-equivalent at 22dp
        val radius = (this.size.minDimension - stroke) / 2f
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        val capStroke = Stroke(width = stroke, cap = StrokeCap.Round)

        // Faint full-ring track (navy hairline) behind the open arc.
        drawCircle(
            color = Palette.hairline.copy(alpha = 0.5f),
            radius = radius,
            center = center,
            style = capStroke,
        )
        // Open recovery-ring arc: ~80% (288°), −90° start (12 o'clock), clockwise.
        drawArc(
            color = Palette.chargeColor,
            startAngle = -90f,
            sweepAngle = 288f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = capStroke,
        )
        // No centre glow-dot — user feedback: mid-element dots looked noisy on many screens.
    }
}

/**
 * Bottom-bar tab switch: single-top + save/restore state for each tab root.
 * Use ONLY for Today / Trends / P.C. / Sleep / More bar slots.
 */
private fun NavHostController.navigateTopLevel(route: String) {
    // Land on the tab ROOT every time — restoreState left users sticky on last More/Settings
    // (or Sleep drill-in). saveState kept so in-tab scroll can still be recovered if we re-enable.
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = false
    }
}

/** Today Quick Alarm / Automations / More / legacy smart_alarm → Sleep → Alarm pill. */
private fun NavHostController.openSleepAlarmTab() {
    SessionUiFlags.openSleepAlarmTab = true
    navigateTopLevel(Destination.Sleep.route)
}

/**
 * Drill-in navigation that PRESERVES the back stack (Settings, Lab Book from Health, etc.).
 * System back / edge swipe returns to the previous screen, not always Home.
 */
private fun NavHostController.navigatePush(route: String) {
    navigate(route) {
        launchSingleTop = true
        // restoreState not used — each push is a fresh instance of the destination on the stack
    }
}

/** Primary bottom-bar routes in left-to-right order for edge-swipe between menus.
 *  Period calendar is inserted dynamically when cycle tracking is on (see GlassBottomBar). */
private val barSwipeRoutesBase = listOf(
    Destination.Today.route,
    Destination.Trends.route,
    Destination.Sleep.route,
    Destination.More.route,
)

/**
 * Loader for the v5 "Your Data, Fused" screen: assembles today's [FusedRecord] off the repository via
 * [AppViewModel.fusedRecordForToday] (the pure FusionResolver per metric) and hands the pure
 * [FusedRecordScreen] its read-model. Keeps the screen itself I/O-free + previewable. Re-loads on entry.
 */
@Composable
private fun FusedRecordRoute(viewModel: AppViewModel) {
    var record by remember {
        mutableStateOf(FusedRecord(rows = emptyList(), dayOwner = null as FusionSource?, contributingSourceCount = 0))
    }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        record = runCatching { viewModel.fusedRecordForToday() }.getOrDefault(record)
        loaded = true
    }
    if (!loaded) {
        // Avoid flashing "Nothing to fuse yet" before the first bank read.
        ScreenScaffold(title = "Your Data, Fused", subtitle = "Loading…") {}
    } else {
        FusedRecordScreen(record = record)
    }
}

/**
 * Placeholder screen for routes later waves will build. Uses [ScreenScaffold] so the
 * dark, instrument-grade chrome is already correct when a real screen replaces it.
 */
@Composable
fun ComingSoon(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NoopCard(padding = 28.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = Palette.textTertiary,
                )
                Spacer(Modifier.height(4.dp))
                Text(text, style = NoopType.title2, color = Palette.textPrimary, textAlign = TextAlign.Center)
                Overline("Coming soon", color = Palette.textSecondary)
                Text(
                    "This section is on the way.",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}