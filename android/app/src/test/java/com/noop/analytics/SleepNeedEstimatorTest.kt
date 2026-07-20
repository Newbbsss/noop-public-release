package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class SleepNeedEstimatorTest {

    @Test
    fun ageBands_nsfCenters() {
        assertEquals(8.5, SleepNeedEstimator.ageBandHours(16.0), 1e-9)
        assertEquals(8.0, SleepNeedEstimator.ageBandHours(30.0), 1e-9)
        assertEquals(7.5, SleepNeedEstimator.ageBandHours(70.0), 1e-9)
        assertEquals(8.0, SleepNeedEstimator.ageBandHours(null), 1e-9)
    }

    @Test
    fun shortSleeper_cannotDropBelowPhysiology() {
        // Habit mean 6h used to set need=7.5; physiology floor keeps adult 8.0.
        val (need, n) = SleepNeedEstimator.personalNeedHours(
            asleepMinutes = listOf(360.0, 360.0, 360.0),
            ageYears = 30.0,
        )
        assertEquals(3, n)
        assertEquals(8.0, need, 1e-9)
    }

    @Test
    fun longSleeper_canRiseAbovePhys() {
        val (need, _) = SleepNeedEstimator.personalNeedHours(
            asleepMinutes = listOf(540.0, 540.0, 540.0), // 9h
            ageYears = 30.0,
        )
        assertTrue(need > 8.0)
        assertTrue(need <= 8.0 + SleepNeedEstimator.learnedCeilingAbovePhys)
    }

    @Test
    fun bmiAndWaist_bumpPhys() {
        val base = SleepNeedEstimator.physiologyNeedHours(ageYears = 30.0)
        val obese = SleepNeedEstimator.physiologyNeedHours(
            ageYears = 30.0, heightCm = 178.0, weightKg = 120.0,
        )
        assertTrue(obese > base)
        val waist = SleepNeedEstimator.physiologyNeedHours(
            ageYears = 30.0, waistCm = 110.0, sex = "male",
        )
        assertEquals(base + 0.15, waist, 1e-9)
    }

    @Test
    fun priorEffort_bumpsNeed() {
        val calm = SleepNeedEstimator.physiologyNeedHours(ageYears = 30.0, priorDayStrain = 5.0)
        val hard = SleepNeedEstimator.physiologyNeedHours(ageYears = 30.0, priorDayStrain = 18.0)
        assertEquals(calm + 0.55, hard, 1e-9)
    }

    @Test
    fun durationShortfall_powerCurve() {
        // 75% of need → ~66, not 75.
        val score = SleepNeedEstimator.durationScoreRaw(6.0, 8.0)
        val expected = 100.0 * (0.75).pow(1.40)
        assertEquals(expected, score, 1e-9)
        assertTrue(score < 75.0)
        assertEquals(100.0, SleepNeedEstimator.durationScoreRaw(8.0, 8.0), 1e-9)
    }
}
