package com.noop.analytics

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Physiology-first personal sleep need (hours).
 *
 * Habit mean alone rewarded chronic short sleep by lowering the bar. This estimator
 * builds an NSF/AASM-anchored physiology need from age (+ BMI/waist/prior Effort when
 * present), then blends with learned asleep mean **clamped so need never falls below phys**.
 *
 * Never invents body-fat %, ECG, or SpO₂ — missing fields simply skip that bump.
 */
object SleepNeedEstimator {

    const val physWeight: Double = 0.55
    const val learnedWeight: Double = 0.45
    /** Heavy sleepers may rise this far above physiology; short sleepers cannot drop below. */
    const val learnedCeilingAbovePhys: Double = 1.25

    /** NSF-style age-band centers (hours). */
    fun ageBandHours(ageYears: Double?): Double {
        val age = ageYears ?: return RestScorer.defaultSleepNeedHours
        return when {
            age < 18.0 -> 8.5
            age >= 65.0 -> 7.5
            else -> 8.0
        }
    }

    /** BMI from height (cm) + weight (kg); null if inputs unusable. */
    fun bmi(heightCm: Double?, weightKg: Double?): Double? {
        val h = heightCm ?: return null
        val w = weightKg ?: return null
        if (h < 100.0 || w < 20.0) return null
        val m = h / 100.0
        return w / (m * m)
    }

    fun bmiBumpHours(bmi: Double?): Double {
        val b = bmi ?: return 0.0
        return when {
            b >= 35.0 -> 0.40
            b >= 30.0 -> 0.25
            b < 18.5 -> 0.15
            else -> 0.0
        }
    }

    /**
     * NIH waist risk thresholds (cm). [sex] is "male" | "female" | "nonbinary".
     * Unset waist (≤0) skips. Nonbinary uses the male threshold (conservative).
     */
    fun waistBumpHours(waistCm: Double?, sex: String?): Double {
        val w = waistCm ?: return 0.0
        if (w <= 0.0) return 0.0
        val threshold = when (sex?.lowercase()) {
            "female" -> 88.0
            else -> 102.0
        }
        return if (w > threshold) 0.15 else 0.0
    }

    /**
     * Yesterday Effort (0–100) → recovery-night need inflation.
     * &lt;12 → 0; 12→0.35 linear to 16→0.55; above 16 caps at 0.55.
     */
    fun priorEffortBumpHours(priorDayStrain: Double?): Double {
        val s = priorDayStrain ?: return 0.0
        if (s < 12.0) return 0.0
        if (s >= 16.0) return 0.55
        val t = (s - 12.0) / 4.0
        return 0.35 + t * (0.55 - 0.35)
    }

    fun physiologyNeedHours(
        ageYears: Double? = null,
        heightCm: Double? = null,
        weightKg: Double? = null,
        waistCm: Double? = null,
        sex: String? = null,
        priorDayStrain: Double? = null,
    ): Double {
        val base = ageBandHours(ageYears)
        val bump = bmiBumpHours(bmi(heightCm, weightKg)) +
            waistBumpHours(waistCm, sex) +
            priorEffortBumpHours(priorDayStrain)
        return (base + bump).coerceIn(7.0, 10.0)
    }

    /**
     * Final need: blend phys + learned, then clamp to [phys, phys + 1.25].
     * Empty history → physiology alone (nightsUsed = 0).
     */
    fun personalNeedHours(
        asleepMinutes: Collection<Double>,
        ageYears: Double? = null,
        heightCm: Double? = null,
        weightKg: Double? = null,
        waistCm: Double? = null,
        sex: String? = null,
        priorDayStrain: Double? = null,
    ): Pair<Double, Int> {
        val phys = physiologyNeedHours(
            ageYears = ageYears,
            heightCm = heightCm,
            weightKg = weightKg,
            waistCm = waistCm,
            sex = sex,
            priorDayStrain = priorDayStrain,
        )
        val mins = asleepMinutes.filter { it > 0.0 }
        if (mins.isEmpty()) return phys to 0
        val learned = mins.sum() / mins.size / 60.0
        val blended = physWeight * phys + learnedWeight * learned
        val need = blended.coerceIn(phys, phys + learnedCeilingAbovePhys)
        return need to mins.size
    }

    /** Duration sub-score 0..100 with power-curve shortfall (r^1.40 when under need). */
    fun durationScoreRaw(asleepHours: Double, needHours: Double): Double {
        val need = needHours.coerceAtLeast(1e-9)
        val r = asleepHours / need
        return if (r >= 1.0) 100.0 else 100.0 * r.pow(1.40)
    }
}
