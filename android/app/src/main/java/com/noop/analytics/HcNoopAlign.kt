package com.noop.analytics

import com.noop.data.DailyMetric
import kotlin.math.abs
import org.json.JSONArray

/**
 * Health Connect ↔ NOOP metric alignment helpers.
 *
 * Ground truth for this track is **WHOOP-app / phone data via Health Connect**, not open BLE.
 * Pure functions only — no invented vitals. When HC has staged sleep and NOOP's night is far off,
 * we gently blend toward HC (still keep some NOOP signal so staging isn't discarded wholesale).
 */
object HcNoopAlign {

    /** Absolute asleep-minute gap that triggers fusion toward HC. */
    const val SLEEP_GAP_BLEND_MIN = 25.0

    /** Weight on HC asleep minutes when blending toward phone/WHOOP-app (Fable Rest #22). */
    const val HC_SLEEP_BLEND = 0.65

    data class HcNight(
        val asleepMin: Double,
        val deepMin: Double? = null,
        val remMin: Double? = null,
        val lightMin: Double? = null,
        val hasStages: Boolean = false,
    )

    /**
     * Parse importer stagesJSON `[{stage,min},…]` into deep/rem/light minutes.
     * Returns nulls when JSON is missing or empty — never fabricates stages.
     */
    fun stagesFromJson(stagesJSON: String?): Triple<Double?, Double?, Double?> {
        if (stagesJSON.isNullOrBlank()) return Triple(null, null, null)
        return runCatching {
            val arr = JSONArray(stagesJSON)
            var deep = 0.0
            var rem = 0.0
            var light = 0.0
            var any = false
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val stage = o.optString("stage").lowercase()
                val min = o.optDouble("min", Double.NaN)
                if (min.isNaN() || min <= 0.0) continue
                any = true
                    when {
                    stage.contains("deep") -> deep += min
                    stage.contains("rem") -> rem += min
                    stage.contains("light") || stage.contains("core") ||
                        stage.contains("sleeping") || stage == "asleep" || stage == "sleep" -> light += min
                }
            }
            if (!any) Triple(null, null, null)
            else Triple(
                deep.takeIf { it > 0.0 },
                rem.takeIf { it > 0.0 },
                light.takeIf { it > 0.0 },
            )
        }.getOrDefault(Triple(null, null, null))
    }

    fun nightFromSession(
        asleepMin: Double,
        stagesJSON: String?,
    ): HcNight {
        val (deep, rem, light) = stagesFromJson(stagesJSON)
        val hasStages = deep != null || rem != null || light != null
        return HcNight(
            asleepMin = asleepMin,
            deepMin = deep,
            remMin = rem,
            lightMin = light,
            hasStages = hasStages,
        )
    }

    /**
     * Align NOOP's night toward Health Connect asleep minutes for Rest (Fable Rest #22).
     *
     * - HC missing / zero → NOOP unchanged.
     * - NOOP missing / zero TST + HC asleep → fill TST from HC (duration-only Rest proxy; never invent
     *   efficiency). Stage minutes copied only when HC actually has stages.
     * - Both present and gap ≥ [SLEEP_GAP_BLEND_MIN] → blend TST toward HC (stages optional — phone
     *   often has asleep without a stage breakdown). Prefer HC stages when present.
     * - Small gaps leave NOOP alone (strap staging often matches).
     */
    fun fuseDaily(daily: DailyMetric, hc: HcNight?): DailyMetric {
        if (hc == null || hc.asleepMin <= 0.0) return daily
        val noopTst = daily.totalSleepMin
        if (noopTst == null || noopTst <= 0.0) {
            return daily.copy(
                totalSleepMin = round1(hc.asleepMin),
                deepMin = if (hc.hasStages) hc.deepMin ?: daily.deepMin else daily.deepMin,
                remMin = if (hc.hasStages) hc.remMin ?: daily.remMin else daily.remMin,
                lightMin = if (hc.hasStages) hc.lightMin ?: daily.lightMin else daily.lightMin,
            )
        }
        if (abs(noopTst - hc.asleepMin) < SLEEP_GAP_BLEND_MIN) return daily
        val fused = HC_SLEEP_BLEND * hc.asleepMin + (1.0 - HC_SLEEP_BLEND) * noopTst
        return daily.copy(
            totalSleepMin = round1(fused),
            deepMin = if (hc.hasStages) hc.deepMin ?: daily.deepMin else daily.deepMin,
            remMin = if (hc.hasStages) hc.remMin ?: daily.remMin else daily.remMin,
            lightMin = if (hc.hasStages) hc.lightMin ?: daily.lightMin else daily.lightMin,
        )
    }

    private fun round1(v: Double): Double = (v * 10.0).toInt() / 10.0

    /** Absolute step gap for diagnostics (null if either side missing). */
    fun stepsGap(noopOrEst: Int?, hc: Int?): Int? {
        if (noopOrEst == null || hc == null) return null
        return abs(noopOrEst - hc)
    }

    /**
     * Display preference — **band only** (Gilbert 2026-07-17):
     *   1. Strap @57 motion counter (WHOOP 5/MG live/offload)
     *   2. Strap gravity/IMU motion estimate (WHOOP 4 / sparse counter)
     * Phone / Health Connect pedometer is **never** the Today number — it may still feed
     * Settings calibration compare only. Never sums sources (double-count).
     *
     * [hc] kept in the signature so call sites stay stable; intentionally unused for the digit.
     */
    @Suppress("UNUSED_PARAMETER")
    fun preferSteps(strap: Int?, hc: Int?, estimate: Int?): Int? =
        strap?.takeIf { it > 0 } ?: estimate?.takeIf { it > 0 }

    /** Which source [preferSteps] would pick — for honest UI captions (Fable #320). */
    enum class StepsSource { STRAP, ESTIMATE, UNAVAILABLE }

    /**
     * Caption source for Today Steps. Phone never wins.
     *
     * Legacy Android (pre-harden) copied `steps_est` onto [DailyMetric.steps] when `@57` was
     * missing, so strap and estimate can be equal on estimate-only days — treat that as
     * ESTIMATE so the tile says `est. · …` rather than a false `band`. True `@57` days that
     * coincidentally match an estimate are rare; wear-day verify if caption flips.
     */
    @Suppress("UNUSED_PARAMETER")
    fun stepsSource(strap: Int?, hc: Int?, estimate: Int?): StepsSource? = when {
        strap != null && strap > 0 -> {
            if (estimate != null && estimate > 0 && strap == estimate) StepsSource.ESTIMATE
            else StepsSource.STRAP
        }
        estimate != null && estimate > 0 -> StepsSource.ESTIMATE
        else -> null
    }

    /**
     * Caption under the Today Steps tile. Band steps are primary; phone is compare-only.
     * When strap wins but phone differs by ≥ [STEPS_SOURCE_GAP], add "≠ phone" as honesty —
     * never swap the digit to the phone count.
     */
    const val STEPS_SOURCE_GAP = 500

    fun stepsTileCaption(
        strap: Int?,
        hc: Int?,
        estimate: Int?,
        estimateDetail: String = "est. · band motion",
    ): String? = when (stepsSource(strap, hc, estimate)) {
        StepsSource.STRAP -> {
            val phone = hc?.takeIf { it > 0 }
            val gap = if (phone != null) stepsGap(strap, phone) else null
            if (gap != null && gap >= STEPS_SOURCE_GAP) "band · ≠ phone" else "band"
        }
        StepsSource.ESTIMATE -> estimateDetail.ifBlank { "est. · band motion" }
        StepsSource.UNAVAILABLE, null ->
            // Honest empty — do not silently show phone pedometer as the day total.
            if (hc != null && hc > 0) "no band steps" else null
    }

    /**
     * Duration-as-% of personal need — honest proxy when efficiency/stages are missing.
     * [needHours] null → RestScorer's 8 h default; callers with history pass personal need (Fable Rest #24).
     */
    fun durationAsSleepPerf(totalSleepMin: Double?, needHours: Double? = null): Double? {
        val tst = totalSleepMin ?: return null
        if (tst <= 0.0) return null
        val needMin = ((needHours ?: RestScorer.defaultSleepNeedHours).coerceAtLeast(1e-9)) * 60.0
        return (tst / needMin * 100.0).coerceIn(0.0, 120.0)
    }
}
