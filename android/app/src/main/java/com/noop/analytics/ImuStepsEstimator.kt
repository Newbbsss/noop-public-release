package com.noop.analytics

import com.noop.data.ImuActivitySample

/**
 * Day-level step estimate helpers from banked WHOOP 5/MG 1244-B IMU activity windows.
 *
 * Offload IMU is **not** a full-day pedometer: connect-time bursts may cover only minutes.
 * Use cadence when enough rhythmic seconds exist; otherwise fold accel energy into the
 * gravity motion-volume path ([StepsEstimateEngine]) so sparse gravity days densify.
 *
 * Never invents steps from stillness. Cadence is a FEATURE (ImuFeatureExtractor), not a vital.
 * When both cadence and a gravity(+boost) day estimate exist, take the **max** so a short
 * connect-burst cadence cannot undercut a full-day band-motion model.
 */
object ImuStepsEstimator {

    /** Fewest rhythmic 1 s windows before a cadence-derived day total is trusted as an estimate. */
    const val MIN_RHYTHMIC_SECONDS = 60

    /** Accel-energy scale into StepsEstimateEngine motion units (empirical densify gain). */
    const val IMU_ENERGY_TO_MOTION = 8.0

    data class DayEstimate(
        val steps: Int?,
        val rhythmicSeconds: Int,
        val windowCount: Int,
        val motionBoost: Double,
        /** True when [steps] came from cadence sum (honest caption: est. · band IMU). */
        val fromCadence: Boolean,
    )

    /**
     * Merge cadence-window sum with gravity(+IMU boost) day estimate.
     * Null when neither path has signal — never invents a digit.
     */
    data class MergedEstimate(
        val steps: Int,
        /** True when cadence supplied the winning total (caption: est. · band IMU). */
        val fromCadence: Boolean,
    )

    /**
     * Prefer the larger of cadence vs motion-volume estimate when both exist.
     * Cadence alone / motion alone still pass through. Ties prefer cadence (explicit IMU signal).
     */
    fun mergeDayEstimate(cadenceSteps: Int?, motionEst: Int?): MergedEstimate? {
        val c = cadenceSteps?.takeIf { it > 0 }
        val m = motionEst?.takeIf { it > 0 }
        return when {
            c != null && m != null ->
                if (c >= m) MergedEstimate(c, fromCadence = true)
                else MergedEstimate(m, fromCadence = false)
            c != null -> MergedEstimate(c, fromCadence = true)
            m != null -> MergedEstimate(m, fromCadence = false)
            else -> null
        }
    }

    /**
     * Sum cadence-derived footfalls over rhythmic windows (each buffer ≈ 1 s @ 100 Hz).
     * Null below [MIN_RHYTHMIC_SECONDS] — coverage too thin to claim a day total.
     */
    fun dayCadenceSteps(windows: List<ImuActivitySample>): Int? {
        var rhythmic = 0
        var steps = 0.0
        for (w in windows) {
            val hz = w.cadenceHz ?: continue
            if (hz <= 0.0 || w.cadenceStrength < ImuFeatureExtractor.minCadenceStrength) continue
            // Frame duration from sample count when present; default 1 s for validated 1244-B.
            val durSec = if (w.sampleCount > 0) w.sampleCount / 100.0 else 1.0
            steps += hz * durSec
            rhythmic++
        }
        if (rhythmic < MIN_RHYTHMIC_SECONDS) return null
        return steps.toInt().coerceIn(0, StepsEstimateEngine.MAX_DAILY_STEPS)
    }

    /** Sum of accel AC energy — densifies gravity motion volume when offload buffers exist. */
    fun dayMotionBoost(windows: List<ImuActivitySample>): Double {
        if (windows.isEmpty()) return 0.0
        var sum = 0.0
        for (w in windows) {
            if (w.accelEnergyG > 0.0 && w.accelEnergyG.isFinite()) {
                sum += w.accelEnergyG
            }
        }
        return sum * IMU_ENERGY_TO_MOTION
    }

    fun dayEstimate(windows: List<ImuActivitySample>): DayEstimate {
        val rhythmic = windows.count {
            val hz = it.cadenceHz
            hz != null && hz > 0.0 && it.cadenceStrength >= ImuFeatureExtractor.minCadenceStrength
        }
        val cadence = dayCadenceSteps(windows)
        return DayEstimate(
            steps = cadence,
            rhythmicSeconds = rhythmic,
            windowCount = windows.size,
            motionBoost = dayMotionBoost(windows),
            fromCadence = cadence != null,
        )
    }
}
