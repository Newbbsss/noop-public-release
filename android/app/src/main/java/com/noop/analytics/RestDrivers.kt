package com.noop.analytics

import com.noop.data.DailyMetric
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * RestDrivers — user-facing "What shaped Rest" rows (Fable Rest #10).
 *
 * Mirrors [RestScorer.rest]: duration / efficiency / restorative / consistency. Each row's
 * [ChargeDriver.deltaPoints] is the marginal swing versus that term sitting at a neutral 50
 * (same weight), so signed points mean "how this signal moved Rest vs a typical night".
 * Missing inputs drop their row (bare HC duration-only → duration row only).
 *
 * Reuses [ChargeDriver] shape so Today/Sleep can share the existing driver-row chrome.
 */
object RestDrivers {

    /**
     * Ordered Rest driver rows for one [DailyMetric] night, or empty when Rest can't score.
     * [consistency] optional 0..1 regularity; when null the scorer uses neutral 0.5 and we still
     * surface a Consistency row labeled as typical.
     */
    fun restDrivers(
        daily: DailyMetric,
        consistency: Double? = null,
        sleepNeedHours: Double? = null,
    ): List<ChargeDriver> {
        val tstMin = daily.totalSleepMin ?: return emptyList()
        if (tstMin <= 0.0) return emptyList()
        val asleepSeconds = tstMin * 60.0
        val needHours = (sleepNeedHours ?: RestScorer.defaultSleepNeedHours).coerceAtLeast(1e-9)
        val asleepHours = asleepSeconds / 3600.0
        val durationScore = min(100.0, asleepHours / needHours * 100.0)

        val eff = daily.efficiency
        if (eff == null) {
            // Bare aggregate — duration-only proxy; one honest row, never invent Deep/REM.
            val proxy = HcNoopAlign.durationAsSleepPerf(tstMin, needHours)?.coerceIn(0.0, 100.0) ?: return emptyList()
            val delta = (proxy - 50.0).roundToInt()
            return listOf(
                ChargeDriver(
                    label = "Sleep duration",
                    deltaPoints = delta,
                    valueText = formatHours(asleepHours),
                    baselineText = formatNeedHours(needHours),
                    verdict = when {
                        delta > 2 -> "enough sleep vs your need"
                        delta < -2 -> "short of your need"
                        else -> "near your need"
                    },
                ),
            )
        }

        val efficiencyScore = (eff * 100.0).coerceIn(0.0, 100.0)
        val deepSec = (daily.deepMin ?: 0.0) * 60.0
        val remSec = (daily.remMin ?: 0.0) * 60.0
        val restorativeShare = (deepSec + remSec) / asleepSeconds
        val deepAdequacy = ((deepSec / asleepSeconds) / RestScorer.deepShareTarget).coerceIn(0.0, 1.0)
        val deepFactor = RestScorer.deepFloorFactor + (1.0 - RestScorer.deepFloorFactor) * deepAdequacy
        val restorativeScore =
            min(100.0, restorativeShare / RestScorer.restorativeTargetShare * 100.0) * deepFactor
        val consistencyScore =
            ((consistency ?: RestScorer.NEUTRAL_CONSISTENCY) * 100.0).coerceIn(0.0, 100.0)

        // Confirm composite exists (same gate as RestScorer.rest).
        RestScorer.rest(
            asleepSeconds = asleepSeconds,
            efficiency = eff,
            deepSeconds = deepSec,
            remSeconds = remSec,
            sleepNeedHours = needHours,
            consistency = consistency,
        ) ?: return emptyList()

        fun delta(score: Double, weight: Double): Int =
            ((score - 50.0) * weight).roundToInt()

        val rows = ArrayList<ChargeDriver>(4)
        rows.add(
            ChargeDriver(
                label = "Sleep duration",
                deltaPoints = delta(durationScore, RestScorer.wDuration),
                valueText = formatHours(asleepHours),
                baselineText = formatNeedHours(needHours),
                verdict = when {
                    durationScore >= 95 -> "met your sleep need"
                    durationScore >= 80 -> "close to your need"
                    else -> "short of your need"
                },
            ),
        )
        rows.add(
            ChargeDriver(
                label = "Sleep efficiency",
                deltaPoints = delta(efficiencyScore, RestScorer.wEfficiency),
                valueText = "${efficiencyScore.roundToInt()}%",
                baselineText = "asleep / in bed",
                verdict = when {
                    efficiencyScore >= 90 -> "solid time asleep in bed"
                    efficiencyScore >= 75 -> "fair time asleep in bed"
                    else -> "a lot of wake in bed"
                },
            ),
        )
        rows.add(
            ChargeDriver(
                label = "Deep + REM",
                deltaPoints = delta(restorativeScore, RestScorer.wRestorative),
                valueText = "${(restorativeShare * 100.0).roundToInt()}% of asleep",
                baselineText = "target ~${(RestScorer.restorativeTargetShare * 100).toInt()}%",
                verdict = when {
                    restorativeScore >= 85 -> "strong restorative share"
                    restorativeScore >= 60 -> "moderate restorative share"
                    else -> "light on deep and REM"
                },
            ),
        )
        rows.add(
            ChargeDriver(
                label = "Consistency",
                deltaPoints = delta(consistencyScore, RestScorer.wConsistency),
                valueText = if (consistency == null) {
                    "typical"
                } else {
                    "${consistencyScore.roundToInt()}%"
                },
                baselineText = if (consistency == null) "no history yet" else "sleep/wake regularity",
                verdict = when {
                    consistency == null -> "using a typical night until history builds"
                    consistencyScore >= 80 -> "regular schedule"
                    consistencyScore >= 55 -> "somewhat regular"
                    else -> "irregular schedule"
                },
            ),
        )
        // Biggest movers first (same habit as Charge drivers).
        return rows.sortedByDescending { kotlin.math.abs(it.deltaPoints) }
    }

    private fun formatNeedHours(needHours: Double): String {
        val rounded = (needHours * 10.0).roundToInt() / 10.0
        return if (rounded == rounded.toInt().toDouble()) {
            "${rounded.toInt()} h need"
        } else {
            "$rounded h need"
        }
    }

    private fun formatHours(hours: Double): String {
        val h = hours.toInt()
        val m = ((hours - h) * 60.0).roundToInt().coerceIn(0, 59)
        return if (h <= 0) "${m}m" else "${h}h ${m}m"
    }
}
