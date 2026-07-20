package com.noop.analytics

import com.noop.data.DailyMetric
import kotlin.math.roundToInt

/** Yesterday Effort (0–100) relative to [anchorDay] wake-day; null if unknown. */
fun priorDayStrainForNeed(days: List<DailyMetric>, anchorDay: String?): Double? {
    val day = anchorDay ?: days.lastOrNull()?.day ?: return null
    val y = ActivityCostEngine.shiftDay(day, -1) ?: return null
    return days.firstOrNull { it.day == y }?.strain
}

/**
 * Live physiology need: profile snapshot + asleep pool + prior Effort.
 * [takeLastNights] null = full pool (Sleep tiles); 28 = Alarm/Today habit window.
 */
fun livePersonalNeedHours(
    days: List<DailyMetric>,
    profile: UserProfile?,
    anchorDay: String? = null,
    takeLastNights: Int? = 28,
): Pair<Double, Int> {
    val pool = days
        .mapNotNull { RestScorer.canonicalAsleepMin(it)?.takeIf { m -> m > 0.0 } }
        .let { list -> takeLastNights?.let { list.takeLast(it) } ?: list }
    return RestScorer.personalNeedHours(
        asleepMinutes = pool,
        profile = profile,
        priorDayStrain = priorDayStrainForNeed(days, anchorDay),
    )
}

fun liveSleepNeedMinutes(
    days: List<DailyMetric>,
    profile: UserProfile?,
    anchorDay: String? = null,
    takeLastNights: Int? = 28,
    coerceMin: Int = 6 * 60,
    coerceMax: Int = 10 * 60,
): Int = (livePersonalNeedHours(days, profile, anchorDay, takeLastNights).first * 60.0)
    .roundToInt()
    .coerceIn(coerceMin, coerceMax)

/** True when yesterday Effort raised tonight's physiology need above the calm baseline. */
fun priorEffortRaisedNeed(priorDayStrain: Double?): Boolean =
    SleepNeedEstimator.priorEffortBumpHours(priorDayStrain) > 0.0
