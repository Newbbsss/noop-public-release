package com.noop.analytics

import com.noop.data.ImuActivitySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImuStepsEstimatorTest {

    private fun window(
        ts: Long,
        cadenceHz: Double? = 1.8,
        strength: Double = 0.4,
        accel: Double = 0.05,
        n: Int = 100,
    ) = ImuActivitySample(
        deviceId = "my-whoop",
        ts = ts,
        accelEnergyG = accel,
        gyroEnergyDps = 10.0,
        jerkRms = 0.01,
        cadenceHz = cadenceHz,
        cadenceStrength = strength,
        sampleCount = n,
    )

    @Test
    fun cadenceSteps_nullBelowMinCoverage() {
        val windows = (0 until 30).map { window(1_780_000_000L + it) }
        assertNull(ImuStepsEstimator.dayCadenceSteps(windows))
        assertFalse(ImuStepsEstimator.dayEstimate(windows).fromCadence)
    }

    @Test
    fun cadenceSteps_sumsWhenEnoughRhythmicSeconds() {
        val windows = (0 until 120).map { window(1_780_000_000L + it, cadenceHz = 2.0) }
        // 120 s × 2.0 Hz = 240 steps
        assertEquals(240, ImuStepsEstimator.dayCadenceSteps(windows))
        val est = ImuStepsEstimator.dayEstimate(windows)
        assertTrue(est.fromCadence)
        assertEquals(240, est.steps)
        assertEquals(120, est.rhythmicSeconds)
    }

    @Test
    fun weakCadence_ignored() {
        val windows = (0 until 120).map {
            window(1_780_000_000L + it, cadenceHz = 2.0, strength = 0.05)
        }
        assertNull(ImuStepsEstimator.dayCadenceSteps(windows))
    }

    @Test
    fun motionBoost_scalesAccelEnergy() {
        val windows = listOf(
            window(1, cadenceHz = null, accel = 0.1),
            window(2, cadenceHz = null, accel = 0.2),
        )
        assertEquals(
            0.3 * ImuStepsEstimator.IMU_ENERGY_TO_MOTION,
            ImuStepsEstimator.dayMotionBoost(windows),
            1e-9,
        )
    }

    @Test
    fun mergeDayEstimate_takesMax_soBurstCadenceCannotUndercutDayMotion() {
        // Connect-burst cadence (~240) must not suppress a calibrated gravity day (~8000).
        val merged = ImuStepsEstimator.mergeDayEstimate(cadenceSteps = 240, motionEst = 8000)
        assertEquals(8000, merged!!.steps)
        assertFalse(merged.fromCadence)
    }

    @Test
    fun mergeDayEstimate_cadenceWinsWhenLargerOrAlone() {
        val both = ImuStepsEstimator.mergeDayEstimate(cadenceSteps = 9000, motionEst = 2000)
        assertEquals(9000, both!!.steps)
        assertTrue(both.fromCadence)
        val alone = ImuStepsEstimator.mergeDayEstimate(cadenceSteps = 240, motionEst = null)
        assertEquals(240, alone!!.steps)
        assertTrue(alone.fromCadence)
        assertNull(ImuStepsEstimator.mergeDayEstimate(null, null))
        assertNull(ImuStepsEstimator.mergeDayEstimate(0, 0))
    }
}
