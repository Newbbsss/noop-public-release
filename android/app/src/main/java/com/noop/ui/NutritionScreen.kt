package com.noop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.alarm.CreatineReminderScheduler
import com.noop.alarm.CreatineReminderStore
import com.noop.analytics.CyclePhysiology
import com.noop.analytics.HydrationGoal
import com.noop.analytics.HydrationStore
import com.noop.analytics.NutritionStore
import com.noop.analytics.PeriodCalendar
import com.noop.data.PeriodCalendarStore
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Nutrition surface — BMR, meals, supplements (local-only). Pattern mirrors Sleep's hydration
 * quick-log: catalog chips + easy add. Cycle-aware BMR bump when tracking is on and phase needs fuel.
 */
@Composable
fun NutritionScreen(
    viewModel: AppViewModel,
    onOpenHydration: () -> Unit = {},
    onOpenToday: () -> Unit = {},
) {
    val context = LocalContext.current
    val showDayCycleBackground = remember { NoopPrefs.showDayCycleBackground(context) }
    val profile = remember { ProfileStore.from(context) }
    val cycleOn by viewModel.cycleTrackingEnabled.collectAsStateWithLifecycle()
    val nutriSeq by NutritionStore.mutationSeq.collectAsStateWithLifecycle()
    val hydraSeq by HydrationStore.mutationSeq.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val reduced = rememberReduceMotion()

    var tab by remember { mutableIntStateOf(0) } // 0 meals (+ BMR) · 1 supplements
    var meals by remember { mutableStateOf(emptyList<NutritionStore.MealEntry>()) }
    var supps by remember { mutableStateOf(emptyList<NutritionStore.SupplementEntry>()) }
    var dayKcal by remember { mutableIntStateOf(0) }
    var dayMacros by remember { mutableStateOf(NutritionStore.DayMacros(0, 0, 0)) }
    var proteinWeek by remember { mutableStateOf(List(7) { 0.0 }) }
    var carbsWeek by remember { mutableStateOf(List(7) { 0.0 }) }
    var fatWeek by remember { mutableStateOf(List(7) { 0.0 }) }
    var kcalWeek by remember { mutableStateOf(List(7) { 0.0 }) }
    var hydrationMl by remember { mutableStateOf(0.0) }
    var hydrationWeek by remember { mutableStateOf(List(7) { 0.0 }) }
    var cycleEffect by remember { mutableStateOf<CyclePhysiology.SoftEffect?>(null) }
    var showMealDialog by remember { mutableStateOf(false) }
    var showSuppDialog by remember { mutableStateOf(false) }
    var mealBurst by remember { mutableFloatStateOf(0f) }
    var sipBurst by remember { mutableFloatStateOf(0f) }
    var suppBurst by remember { mutableFloatStateOf(0f) }
    val mealBurstAnim by animateFloatAsState(
        targetValue = mealBurst,
        animationSpec = if (reduced) {
            tween(0)
        } else {
            tween(LifeChapterLacquer.SIP_BURST_MS, easing = FastOutSlowInEasing)
        },
        label = "nutritionMealBurst",
    )
    val sipBurstAnim by animateFloatAsState(
        targetValue = sipBurst,
        animationSpec = if (reduced) {
            tween(0)
        } else {
            tween(LifeChapterLacquer.SIP_BURST_MS, easing = FastOutSlowInEasing)
        },
        label = "nutritionSipBurst",
    )
    val suppBurstAnim by animateFloatAsState(
        targetValue = suppBurst,
        animationSpec = if (reduced) {
            tween(0)
        } else {
            tween(LifeChapterLacquer.SIP_BURST_MS - 80, easing = FastOutSlowInEasing)
        },
        label = "nutritionSuppBurst",
    )
    LaunchedEffect(mealBurst) {
        if (mealBurst >= 0.99f) {
            kotlinx.coroutines.yield()
            mealBurst = 0f
        }
    }
    LaunchedEffect(sipBurst) {
        if (sipBurst >= 0.99f) {
            kotlinx.coroutines.yield()
            sipBurst = 0f
        }
    }
    LaunchedEffect(suppBurst) {
        if (suppBurst >= 0.99f) {
            kotlinx.coroutines.yield()
            suppBurst = 0f
        }
    }
    val sipScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val creatineStore = remember { CreatineReminderStore.from(context) }
    var creatineRemind by remember { mutableStateOf(creatineStore.enabled) }
    val creatineLoggedToday = remember(supps) {
        supps.any { it.name.contains("creatine", ignoreCase = true) }
    }
    val hydrationGoalMl = remember(today, profile) {
        HydrationGoal.dailyGoalMl(profile.sex, today?.strain)
    }
    val hydraFrac = if (hydrationGoalMl > 0) {
        (hydrationMl / hydrationGoalMl.toDouble()).coerceIn(0.0, 1.0)
    } else {
        0.0
    }

    LaunchedEffect(nutriSeq, hydraSeq, cycleOn) {
        meals = NutritionStore.mealsForDay(context)
        supps = NutritionStore.supplementsForDay(context)
        dayKcal = NutritionStore.dayKcal(context)
        dayMacros = NutritionStore.dayMacros(context)
        proteinWeek = NutritionStore.proteinSeriesLastDays(context, 7)
        carbsWeek = NutritionStore.carbsSeriesLastDays(context, 7)
        fatWeek = NutritionStore.fatSeriesLastDays(context, 7)
        kcalWeek = NutritionStore.kcalSeriesLastDays(context, 7)
        hydrationMl = runCatching { HydrationStore.total(viewModel.repo) }.getOrDefault(0.0)
        hydrationWeek = runCatching {
            HydrationStore.history(viewModel.repo, days = 7).map { it.second }
        }.getOrDefault(List(7) { 0.0 })
        cycleEffect = if (cycleOn) {
            val store = PeriodCalendarStore.from(context)
            val snap = PeriodCalendar.evaluate(
                today = LocalDate.now(),
                events = store.loadEvents(),
                prefs = store.loadPrefs().copy(enabled = true),
            )
            CyclePhysiology.softEffectFromSnapshot(snap)
        } else null
    }

    val baseBmr = remember(profile.sex, profile.weightKg, profile.heightCm, profile.age) {
        CyclePhysiology.baseBmrKcal(
            sex = profile.sex,
            weightKg = profile.weightKg,
            heightCm = profile.heightCm,
            age = profile.age.toDouble(),
        )
    }
    val adjustedBmr = CyclePhysiology.adjustedBmrKcal(baseBmr, cycleEffect)
    // Light TDEE proxy: BMR × 1.4 (moderately active) — labeled as estimate.
    val baseGoal = (baseBmr * 1.4).roundToInt()
    val adjustedGoal = CyclePhysiology.adjustedGoalKcal(baseGoal.toDouble(), cycleEffect).roundToInt()

    LazyScreenScaffold(
        title = "Nutrition",
        subtitle = "Meals with BMR · supplements — on-device only",
        topBackground = if (showDayCycleBackground) { { LiquidScreenSky(fillHeight = true) } } else null,
        fullBleedBackground = showDayCycleBackground,
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Meals", "Supplements").forEachIndexed { i, label ->
                    val on = tab == i
                    val shape = RoundedCornerShape(50)
                    val badge = when {
                        i == 1 && supps.isNotEmpty() -> " · ${supps.size}"
                        else -> ""
                    }
                    Text(
                        "$label$badge",
                        style = NoopType.caption.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (on) Palette.textPrimary else Palette.textSecondary,
                        modifier = Modifier
                            .clip(shape)
                            .background(if (on) Palette.surfaceRaised.copy(alpha = 0.85f) else Palette.surfaceInset.copy(alpha = 0.35f))
                            .border(1.dp, if (on) Palette.restColor.copy(alpha = 0.28f) else Palette.hairline, shape)
                            .clickable { tab = i }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .semantics {
                                contentDescription = if (i == 1 && supps.isNotEmpty()) {
                                    "Supplements tab, ${supps.size} logged today"
                                } else {
                                    "$label tab"
                                }
                            },
                    )
                }
            }
        }
        item {
            // Quick hydration jump — cyan chrome + Sip +250 parity with Today (cohesion).
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val hydraShape = RoundedCornerShape(LifeChapterLacquer.CORNER_DP.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = LifeChapterLacquer.CHAPTER_MIN_HEIGHT_DP.dp)
                        .clip(hydraShape)
                        .background(Palette.surfaceInset.copy(alpha = LifeChapterLacquer.SURFACE_ALPHA))
                        .border(1.dp, Palette.metricCyan.copy(alpha = LifeChapterLacquer.BORDER_ALPHA), hydraShape),
                ) {
                    HydrationSipLifeMotes(
                        reduced = reduced,
                        accent = Palette.metricCyan,
                        modifier = Modifier.matchParentSize(),
                    )
                    HydrationGoalBloom(
                        reduced = reduced,
                        accent = Palette.metricCyan,
                        met = hydraFrac >= 1.0,
                        modifier = Modifier.matchParentSize(),
                    )
                    if (!reduced && sipBurstAnim > 0.01f) {
                        HydrationSipBurstSpark(
                            burst = sipBurstAnim,
                            accent = Palette.metricCyan,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = LifeChapterLacquer.PAD_V_DP.dp)
                            .semantics {
                                contentDescription = hydrationSipA11y(hydrationMl, hydrationGoalMl)
                            },
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Filled.WaterDrop, null, tint = Palette.metricCyan, modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("HYDRATION", style = NoopType.overline, color = Palette.metricCyan)
                                Text(
                                    "${"%.1f".format(hydrationMl / 1000.0)} / ${"%.1f".format(hydrationGoalMl / 1000.0)} L",
                                    style = NoopType.subhead.copy(fontWeight = FontWeight.SemiBold),
                                    color = Palette.textPrimary,
                                )
                                if (hydraFrac >= 1.0) {
                                    Text(
                                        hydrationGoalMetCaption(),
                                        style = NoopType.footnote,
                                        color = Palette.metricCyan,
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (!reduced) sipBurst = 1f
                                    sipScope.launch {
                                        runCatching { HydrationStore.log(viewModel.repo, 250) }
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Palette.metricCyan),
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text("Sip +250 ml", style = NoopType.caption)
                            }
                            TextButton(
                                onClick = onOpenHydration,
                                colors = ButtonDefaults.textButtonColors(contentColor = Palette.textTertiary),
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text("More", style = NoopType.footnote)
                            }
                        }
                        LiquidTube(
                            frac = hydraFrac,
                            tint = Palette.metricCyan,
                            height = 6.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (hydrationWeek.any { it > 0.0 }) {
                    Sparkline(
                        values = hydrationWeek,
                        color = Palette.metricCyan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                    )
                    Text("7-day hydration", style = NoopType.footnote, color = Palette.textTertiary)
                }
                TextButton(
                    onClick = onOpenToday,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .semantics { contentDescription = "See fuel and bedtime on Today" },
                ) {
                    Text("Fuel · bedtime on Today", color = Palette.textSecondary, style = NoopType.caption)
                }
            }
        }
        when (tab) {
            0 -> {
                item {
                    // Human meals + BMR together — life motes + Charge lacquer cohesion with Today Fuel.
                    val shape = RoundedCornerShape(LifeChapterLacquer.CORNER_DP.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(Palette.surfaceInset.copy(alpha = LifeChapterLacquer.SURFACE_ALPHA))
                            .border(1.dp, Palette.chargeColor.copy(alpha = LifeChapterLacquer.BORDER_ALPHA), shape),
                    ) {
                        NutritionMealLifeMotes(
                            reduced = reduced,
                            accent = Palette.chargeColor,
                            intensity = LifeChapterLacquer.FUEL_HERO_INTENSITY,
                            modifier = Modifier.matchParentSize(),
                        )
                        if (!reduced && mealBurstAnim > 0.01f) {
                            NutritionMealBurstSpark(
                                burst = mealBurstAnim,
                                accent = Palette.chargeColor,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = LifeChapterLacquer.PAD_V_DP.dp)
                                .semantics {
                                    contentDescription = fuelPeekA11y(
                                        dayKcal,
                                        meals.size,
                                        dayMacros.proteinG,
                                        dayMacros.carbsG,
                                        dayMacros.fatG,
                                    )
                                },
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("FUEL", style = NoopType.overline, color = Palette.chargeColor)
                                    Text(
                                        "$dayKcal",
                                        style = NoopType.number(36f, weight = FontWeight.Bold),
                                        color = Palette.textPrimary,
                                    )
                                    Text("kcal from meals", style = NoopType.caption, color = Palette.textSecondary)
                                }
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text("BMR", style = NoopType.overline, color = Palette.restColor)
                                    Text(
                                        "${adjustedBmr.roundToInt()}",
                                        style = NoopType.number(28f, weight = FontWeight.SemiBold),
                                        color = Palette.restBright,
                                    )
                                    Text("kcal / day", style = NoopType.caption, color = Palette.textSecondary)
                                }
                            }
                            if (dayMacros.proteinG > 0 || dayMacros.carbsG > 0 || dayMacros.fatG > 0) {
                                val bits = buildList {
                                    if (dayMacros.proteinG > 0) add("P ${dayMacros.proteinG}g")
                                    if (dayMacros.carbsG > 0) add("C ${dayMacros.carbsG}g")
                                    if (dayMacros.fatG > 0) add("F ${dayMacros.fatG}g")
                                }
                                Text(
                                    "Macros · ${bits.joinToString(" · ")}",
                                    style = NoopType.caption,
                                    color = Palette.textSecondary,
                                )
                            }
                            if (kcalWeek.any { it > 0.0 }) {
                                Text("KCAL · 7 DAYS", style = NoopType.overline, color = Palette.chargeColor)
                                Sparkline(
                                    values = kcalWeek,
                                    color = Palette.chargeColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(28.dp),
                                )
                                Text(
                                    "Week total ${kcalWeek.sum().roundToInt()} kcal (logged meals)",
                                    style = NoopType.footnote,
                                    color = Palette.textTertiary,
                                )
                            }
                            if (proteinWeek.any { it > 0.0 }) {
                                Text("PROTEIN · 7 DAYS", style = NoopType.overline, color = Palette.chargeColor)
                                Sparkline(
                                    values = proteinWeek,
                                    color = Palette.chargeColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp),
                                )
                                val weekSum = proteinWeek.sum().roundToInt()
                                Text(
                                    "Week total ${weekSum}g protein (logged macros only)",
                                    style = NoopType.footnote,
                                    color = Palette.textTertiary,
                                )
                            }
                            if (carbsWeek.any { it > 0.0 }) {
                                Text("CARBS · 7 DAYS", style = NoopType.overline, color = Palette.effortColor)
                                Sparkline(
                                    values = carbsWeek,
                                    color = Palette.effortColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(28.dp),
                                )
                            }
                            if (fatWeek.any { it > 0.0 }) {
                                Text("FAT · 7 DAYS", style = NoopType.overline, color = Palette.restColor)
                                Sparkline(
                                    values = fatWeek,
                                    color = Palette.restColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(28.dp),
                                )
                            }
                            Text(
                                "Goal ~$adjustedGoal kcal (BMR × 1.4 moderate). " +
                                    if (cycleEffect?.needsMoreFuel == true) {
                                        "Cycle +fuel ×${"%.2f".format(cycleEffect!!.bmrFactor)} — awareness, not medical."
                                    } else {
                                        "Harris–Benedict from your profile — estimate, not medical advice."
                                    },
                                style = NoopType.footnote,
                                color = Palette.textTertiary,
                            )
                            if (cycleEffect?.takeItEasy == true) {
                                Text(
                                    "Cycle-aware recovery: take it easy — soft Charge capacity ×" +
                                        "${"%.2f".format(cycleEffect!!.recoveryCapacityFactor)}.",
                                    style = NoopType.footnote,
                                    color = Palette.statusWarning,
                                )
                            }
                        }
                    }
                }
                item {
                    QuickMealChips(
                        onPick = { label, kcal ->
                            if (!reduced) mealBurst = 1f
                            NutritionStore.logMeal(context, label, kcal)
                        },
                        onCustom = { showMealDialog = true },
                    )
                }
                if (meals.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                                )
                                NutritionStore.undoLastMeal(context)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = "Undo last meal" },
                        ) {
                            Text("Undo last meal", color = Palette.textSecondary)
                        }
                    }
                }
                items(meals, key = { it.id }) { m ->
                    MealRow(m) {
                        NutritionStore.removeMeal(context, m.id)
                    }
                }
                if (meals.isEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("FUEL", style = NoopType.overline, color = Palette.chargeColor)
                            Text(
                                fuelPeekLine(0, 0, 0, 0, 0) +
                                    " · Macros optional. Stays on this phone — never invents SpO₂/BP.",
                                style = NoopType.footnote,
                                color = Palette.textTertiary,
                            )
                        }
                    }
                }
            }
            else -> {
                item {
                    Text(
                        "Catalog — tap to log taken today",
                        style = NoopType.caption,
                        color = Palette.textSecondary,
                    )
                }
                item {
                    // CLAUDE_FOLLOWUPS — local creatine daily nudge (opt-in, default off).
                    val suppShape = RoundedCornerShape(LifeChapterLacquer.CORNER_DP.dp)
                    Box {
                        if (!reduced && suppBurstAnim > 0.01f) {
                            SupplementLogBurstSpark(
                                burst = suppBurstAnim,
                                accent = Palette.restColor,
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(vertical = 4.dp),
                            )
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(suppShape)
                                .background(
                                    Palette.surfaceInset.copy(alpha = LifeChapterLacquer.SUPP_SURFACE_ALPHA),
                                )
                                .border(
                                    1.dp,
                                    Palette.restColor.copy(alpha = LifeChapterLacquer.BORDER_ALPHA),
                                    suppShape,
                                )
                                .padding(
                                    horizontal = 14.dp,
                                    vertical = LifeChapterLacquer.PAD_V_DP.dp,
                                )
                                .semantics {
                                    contentDescription = if (creatineLoggedToday) {
                                        "Creatine reminder · Taken today"
                                    } else {
                                        "Creatine reminder · not logged yet"
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("Creatine reminder", style = NoopType.subhead, color = Palette.textPrimary)
                                    if (creatineLoggedToday) {
                                        SupplementTakenLifeSpark(
                                            reduced = reduced,
                                            accent = Palette.restColor,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text("Taken", style = NoopType.footnote, color = Palette.restColor)
                                    }
                                }
                                Text(
                                    "Daily ~${CreatineReminderStore.DEFAULT_HOUR}:00 · skips if already logged · not medical advice",
                                    style = NoopType.footnote,
                                    color = Palette.textTertiary,
                                )
                            }
                            Switch(
                                checked = creatineRemind,
                                onCheckedChange = { on ->
                                    creatineRemind = on
                                    creatineStore.enabled = on
                                    if (on) CreatineReminderScheduler.schedule(context, creatineStore)
                                    else CreatineReminderScheduler.cancel(context)
                                },
                            )
                        }
                    }
                }
                item {
                    SupplementCatalogGrid { name, dose ->
                        if (!reduced) suppBurst = 1f
                        NutritionStore.logSupplement(context, name, dose)
                    }
                }
                item {
                    TextButtonLike("Custom supplement") { showSuppDialog = true }
                }
                items(supps, key = { it.id }) { s ->
                    SuppRow(s) { NutritionStore.removeSupplement(context, s.id) }
                }
                if (supps.isEmpty()) {
                    item {
                        Text(
                            "Creatine, magnesium, and more — local log only, not medical advice.",
                            style = NoopType.footnote,
                            color = Palette.textTertiary,
                        )
                    }
                }
            }
        }
    }

    if (showMealDialog) {
        MealLogDialog(
            onDismiss = { showMealDialog = false },
            onConfirm = { label, kcal, p, c, f ->
                if (!reduced) mealBurst = 1f
                NutritionStore.logMeal(context, label, kcal, p, c, f)
                showMealDialog = false
            },
        )
    }
    if (showSuppDialog) {
        SuppLogDialog(
            onDismiss = { showSuppDialog = false },
            onConfirm = { name, dose ->
                if (!reduced) suppBurst = 1f
                NutritionStore.logSupplement(context, name, dose)
                showSuppDialog = false
            },
        )
    }
}

@Composable
private fun NutritionHeroStat(overline: String, value: String, unit: String, tint: androidx.compose.ui.graphics.Color) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surfaceInset.copy(alpha = 0.55f))
            .border(1.dp, tint.copy(alpha = 0.28f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(overline, style = NoopType.overline, color = tint)
        Text(value, style = NoopType.number(40f, weight = FontWeight.Bold), color = Palette.textPrimary)
        Text(unit, style = NoopType.caption, color = Palette.textSecondary)
    }
}

@Composable
private fun QuickMealChips(onPick: (String, Int) -> Unit, onCustom: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val reduced = rememberReduceMotion()
    val chips = listOf(
        "Snack" to 150,
        "Light meal" to 350,
        "Meal" to 550,
        "Big meal" to 800,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quick log", style = NoopType.caption, color = Palette.textSecondary)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LifeChapterLacquer.CORNER_DP.dp))
                .background(Palette.surfaceInset.copy(alpha = 0.28f)),
        ) {
            NutritionMealLifeMotes(
                reduced = reduced,
                accent = Palette.chargeColor,
                intensity = LifeChapterLacquer.FUEL_CHIP_INTENSITY,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { (label, kcal) ->
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(Palette.surfaceRaised.copy(alpha = 0.70f))
                            .border(1.dp, Palette.hairline, shape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPick(label, kcal)
                            }
                            .padding(vertical = 10.dp)
                            .semantics { contentDescription = "Log $label $kcal kcal" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Filled.Restaurant, null, tint = Palette.chargeColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = NoopType.caption, color = Palette.textPrimary, maxLines = 1)
                        Text("$kcal", style = NoopType.footnote, color = Palette.textTertiary)
                    }
                }
            }
        }
        TextButtonLike("Custom meal", onCustom)
    }
}

@Composable
private fun SupplementCatalogGrid(onPick: (String, String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NutritionStore.SUPPLEMENT_CATALOG.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, dose) ->
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(shape)
                            .background(Palette.surfaceRaised.copy(alpha = 0.70f))
                            .border(1.dp, Palette.restColor.copy(alpha = 0.22f), shape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPick(name, dose)
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .semantics { contentDescription = "Log $name $dose" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Science, null, tint = Palette.restColor, modifier = Modifier.size(16.dp))
                        Column {
                            Text(name, style = NoopType.caption, color = Palette.textPrimary, maxLines = 1)
                            Text(dose, style = NoopType.footnote, color = Palette.textTertiary, maxLines = 1)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MealRow(m: NutritionStore.MealEntry, onDelete: () -> Unit) {
    val macros = buildList {
        if (m.proteinG > 0) add("P ${m.proteinG}g")
        if (m.carbsG > 0) add("C ${m.carbsG}g")
        if (m.fatG > 0) add("F ${m.fatG}g")
    }.joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.surfaceInset.copy(alpha = 0.40f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(m.label, style = NoopType.body, color = Palette.textPrimary)
            Text(
                if (macros.isNotEmpty()) "${m.kcal} kcal · $macros" else "${m.kcal} kcal",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
        }
        Icon(
            Icons.Filled.Delete,
            contentDescription = "Remove meal",
            tint = Palette.textTertiary,
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onDelete)
                .padding(10.dp),
        )
    }
}

@Composable
private fun SuppRow(s: NutritionStore.SupplementEntry, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.surfaceInset.copy(alpha = 0.40f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(s.name, style = NoopType.body, color = Palette.textPrimary)
            Text(s.dose.ifBlank { "taken" }, style = NoopType.footnote, color = Palette.textTertiary)
        }
        Icon(
            Icons.Filled.Delete,
            contentDescription = "Remove supplement",
            tint = Palette.textTertiary,
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onDelete)
                .padding(10.dp),
        )
    }
}

@Composable
private fun TextButtonLike(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Palette.hairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Add, null, tint = Palette.accent, modifier = Modifier.size(18.dp))
        Text(label, style = NoopType.body, color = Palette.textPrimary)
    }
}

@Composable
private fun MealLogDialog(
    onDismiss: () -> Unit,
    onConfirm: (label: String, kcal: Int, protein: Int, carbs: Int, fat: Int) -> Unit,
) {
    var label by remember { mutableStateOf("Meal") }
    var kcal by remember { mutableStateOf("500") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log meal", color = Palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    colors = fieldColors(),
                )
                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("kcal") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors(),
                )
                Text(
                    "Macros optional (g)",
                    style = NoopType.caption,
                    color = Palette.textTertiary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("P") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(),
                    )
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("C") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(),
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("F") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    label,
                    kcal.toIntOrNull() ?: 0,
                    protein.toIntOrNull() ?: 0,
                    carbs.toIntOrNull() ?: 0,
                    fat.toIntOrNull() ?: 0,
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Palette.surfaceRaised,
    )
}

@Composable
private fun SuppLogDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, dose: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log supplement", color = Palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = fieldColors(),
                )
                OutlinedTextField(
                    value = dose,
                    onValueChange = { dose = it },
                    label = { Text("Dose") },
                    singleLine = true,
                    colors = fieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, dose) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Palette.surfaceRaised,
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Palette.textPrimary,
    unfocusedTextColor = Palette.textPrimary,
    focusedBorderColor = Palette.restColor,
    unfocusedBorderColor = Palette.hairline,
    focusedLabelColor = Palette.textSecondary,
    unfocusedLabelColor = Palette.textTertiary,
    cursorColor = Palette.restColor,
)
