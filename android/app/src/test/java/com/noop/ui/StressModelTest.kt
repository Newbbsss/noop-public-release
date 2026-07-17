package com.noop.ui

import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * StressModel gate — hot-fix: trailing empty today must not blank the whole screen.
 */
class StressModelTest {

    private fun day(
        day: String,
        restingHr: Int? = null,
        avgHrv: Double? = null,
    ) = DailyMetric(
        deviceId = "my-whoop-noop",
        day = day,
        restingHr = restingHr,
        avgHrv = avgHrv,
    )

    @Test
    fun trailingEmptyToday_usesPriorDayWithRhrHrv() {
        val days = listOf(
            day("2026-07-11", restingHr = 52, avgHrv = 56.0),
            day("2026-07-12", restingHr = 53, avgHrv = 55.0),
            day("2026-07-13"), // mid-day shell — no overnight vitals yet
        )
        val model = StressModel.build(days, emptyMap())
        assertNotNull(model)
        // Scored against 7/12 (last derivable), not blanked by empty 7/13.
        assertEquals(53, model!!.rhrToday)
    }

    @Test
    fun trailingEmptyToday_usesStoredDaytimeTip() {
        val days = listOf(
            day("2026-07-12", restingHr = 53, avgHrv = 55.0),
            day("2026-07-13"),
        )
        val stored = mapOf("2026-07-13" to 0.15)
        val model = StressModel.build(days, stored)
        assertNotNull(model)
        assertEquals(0.15, model!!.score, 1e-6)
        assertEquals(true, model.usingStored)
        assertEquals("2026-07-13", model.scoreDayKey)
        // #208: empty tip-day shell still surfaces prior night RHR/HRV on markers.
        assertEquals(53, model.rhrToday)
        assertEquals(55.0, model.hrvToday!!, 1e-9)
    }

    @Test
    fun trulyEmpty_returnsNull() {
        val days = listOf(
            day("2026-07-13"),
            day("2026-07-12"),
        )
        assertNull(StressModel.build(days, emptyMap()))
    }

    @Test
    fun walkBackScoreDay_exposesKeyForLatestCaption() {
        val days = listOf(
            day("2026-07-11", restingHr = 52, avgHrv = 56.0),
            day("2026-07-12", restingHr = 53, avgHrv = 44.0),
            day("2026-07-13"),
            day("2026-07-14"),
        )
        val model = StressModel.build(days, emptyMap())
        assertNotNull(model)
        assertEquals("2026-07-12", model!!.scoreDayKey)
        assertEquals(53, model.rhrToday)
        assertEquals(44.0, model.hrvToday!!, 1e-9)
    }
}
