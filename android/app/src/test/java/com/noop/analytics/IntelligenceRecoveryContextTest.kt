package com.noop.analytics

import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for pass-2 Charge input honesty in [IntelligenceEngine]. */
class IntelligenceRecoveryContextTest {

    private fun baseline(mean: Double, spread: Double) = BaselineState(
        baseline = mean,
        spread = spread,
        nValid = 14,
        nightsSinceUpdate = 0,
        status = BaselineStatus.TRUSTED,
    )

    private val baselines = ProfileBaselines(
        hrv = baseline(60.0, 8.0),
        restingHR = baseline(55.0, 4.0),
    )

    private val night = DailyMetric(
        deviceId = "my-whoop-noop",
        day = "2026-07-13",
        totalSleepMin = 420.0,
        efficiency = 0.90,
        deepMin = 70.0,
        remMin = 90.0,
        lightMin = 260.0,
        avgHrv = 60.0,
        restingHr = 55,
    )

    @Test
    fun personalRestContextShapesTheSleepTermUsedByCharge() {
        val defaultContext = IntelligenceEngine.recomputeRecovery(night, baselines)!!
        val personalContext = IntelligenceEngine.recomputeRecovery(
            daily = night,
            baselines = baselines,
            sleepNeedHours = 7.0,
            consistency = 0.90,
        )!!

        assertTrue(
            "meeting a 7h personal need with strong consistency should improve Charge's Rest term",
            personalContext > defaultContext,
        )
    }

    @Test
    fun freshSkinDeviationAffectsChargeInTheSamePass() {
        val noSkin = IntelligenceEngine.recomputeRecovery(night, baselines)!!
        val withDeviation = IntelligenceEngine.recomputeRecovery(
            night.copy(skinTempDevC = 1.0),
            baselines,
        )!!

        assertTrue("a 1°C deviation should lower Charge", withDeviation < noSkin)
    }

    @Test
    fun futureAndSameDayValuesCannotChangeHistoricalBaseline() {
        val prefix = listOf(
            "2026-07-01" to 50.0,
            "2026-07-02" to 52.0,
            "2026-07-03" to 51.0,
            "2026-07-04" to 53.0,
        )
        val targetDay = "2026-07-05"
        val beforeFuture = IntelligenceEngine.foldBaselineBeforeDay(
            prefix,
            targetDay,
            Baselines.hrvCfg,
        )
        val afterFuture = IntelligenceEngine.foldBaselineBeforeDay(
            prefix + listOf(targetDay to 200.0, "2026-07-06" to 10.0),
            targetDay,
            Baselines.hrvCfg,
        )

        assertEquals(beforeFuture, afterFuture)
        assertEquals(4, afterFuture.nValid)
    }
}
