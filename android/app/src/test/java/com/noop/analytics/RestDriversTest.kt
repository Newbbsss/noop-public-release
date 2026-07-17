package com.noop.analytics

import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fable Rest #10 — What shaped Rest. */
class RestDriversTest {

    @Test
    fun fullNightYieldsFourDriversSortedByMagnitude() {
        val daily = DailyMetric(
            deviceId = "x",
            day = "2026-07-12",
            totalSleepMin = 450.0,
            efficiency = 0.92,
            deepMin = 70.0,
            remMin = 90.0,
        )
        val drivers = RestDrivers.restDrivers(daily)
        assertEquals(
            setOf("Sleep duration", "Sleep efficiency", "Deep + REM", "Consistency"),
            drivers.map { it.label }.toSet(),
        )
        val magnitudes = drivers.map { kotlin.math.abs(it.deltaPoints) }
        assertEquals(magnitudes.sortedDescending(), magnitudes)
        assertTrue(drivers.none { it.label.contains("—") || it.verdict.contains("—") })
    }

    @Test
    fun bareAggregateDurationOnly() {
        val daily = DailyMetric(
            deviceId = "x",
            day = "2026-07-12",
            totalSleepMin = 420.0,
            efficiency = null,
        )
        val drivers = RestDrivers.restDrivers(daily)
        assertEquals(1, drivers.size)
        assertEquals("Sleep duration", drivers[0].label)
    }

    @Test
    fun noSleepYieldsEmpty() {
        assertTrue(RestDrivers.restDrivers(DailyMetric(deviceId = "x", day = "2026-07-12")).isEmpty())
    }
}
