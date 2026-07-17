package com.noop.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerSavingPolicyTest {

    @Test
    fun offByDefault_neverStretches() {
        assertEquals(
            PowerSavingPolicy.DEFAULT_INTERVAL_MS,
            PowerSavingPolicy.historySyncIntervalMs(
                enabled = false,
                thresholdPct = 20,
                batteryPct = 5.0,
                charging = false,
            ),
        )
    }

    @Test
    fun lowAndDischarging_stretchesTo45m() {
        assertEquals(
            PowerSavingPolicy.STRETCHED_INTERVAL_MS,
            PowerSavingPolicy.historySyncIntervalMs(
                enabled = true,
                thresholdPct = 20,
                batteryPct = 15.0,
                charging = false,
            ),
        )
        assertTrue(
            PowerSavingPolicy.isActive(
                enabled = true,
                thresholdPct = 20,
                batteryPct = 20.0,
                charging = null,
            ),
        )
    }

    @Test
    fun neverWhileCharging() {
        assertFalse(
            PowerSavingPolicy.isActive(
                enabled = true,
                thresholdPct = 30,
                batteryPct = 5.0,
                charging = true,
            ),
        )
        assertEquals(
            PowerSavingPolicy.DEFAULT_INTERVAL_MS,
            PowerSavingPolicy.historySyncIntervalMs(
                enabled = true,
                thresholdPct = 30,
                batteryPct = 5.0,
                charging = true,
            ),
        )
    }

    @Test
    fun aboveThreshold_normalCadence() {
        assertFalse(
            PowerSavingPolicy.isActive(
                enabled = true,
                thresholdPct = 15,
                batteryPct = 16.0,
                charging = false,
            ),
        )
    }

    @Test
    fun unknownBattery_failClosed() {
        assertFalse(
            PowerSavingPolicy.isActive(
                enabled = true,
                thresholdPct = 20,
                batteryPct = null,
                charging = false,
            ),
        )
    }

    @Test
    fun thresholdClamped() {
        assertEquals(10, PowerSavingPolicy.clampThreshold(5))
        assertEquals(30, PowerSavingPolicy.clampThreshold(99))
        assertEquals(22, PowerSavingPolicy.clampThreshold(22))
    }

    @Test
    fun releaseContinuousHrv_onlyWhenActive() {
        assertFalse(
            PowerSavingPolicy.continuousHrvAllowed(
                userWantsContinuous = true,
                releaseContinuousWhenSaving = true,
                enabled = true,
                thresholdPct = 20,
                batteryPct = 10.0,
                charging = false,
            ),
        )
        assertTrue(
            PowerSavingPolicy.continuousHrvAllowed(
                userWantsContinuous = true,
                releaseContinuousWhenSaving = true,
                enabled = true,
                thresholdPct = 20,
                batteryPct = 50.0,
                charging = false,
            ),
        )
        assertTrue(
            PowerSavingPolicy.continuousHrvAllowed(
                userWantsContinuous = true,
                releaseContinuousWhenSaving = false,
                enabled = true,
                thresholdPct = 20,
                batteryPct = 5.0,
                charging = false,
            ),
        )
    }
}
