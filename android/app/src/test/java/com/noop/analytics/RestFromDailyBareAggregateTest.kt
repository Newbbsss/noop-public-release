package com.noop.analytics

import com.noop.data.DailyMetric
import com.noop.data.WhoopRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fable Rest #27 — bare aggregate Rest from [RestScorer.restFromDaily].
 * Duration-only rows (HC / import without efficiency) must yield a duration proxy,
 * never invent Deep/REM, and never score a null/zero sleep night.
 */
class RestFromDailyBareAggregateTest {

    @Test
    fun noSleepIsNull() {
        assertNull(RestScorer.restFromDaily(DailyMetric(deviceId = "x", day = "2026-07-12")))
        assertNull(
            RestScorer.restFromDaily(
                DailyMetric(deviceId = "x", day = "2026-07-12", totalSleepMin = 0.0),
            ),
        )
    }

    @Test
    fun bareDurationOnlyUsesDurationProxy() {
        val daily = DailyMetric(
            deviceId = "x",
            day = "2026-07-12",
            totalSleepMin = 450.0,
            efficiency = null,
            deepMin = null,
            remMin = null,
            lightMin = null,
        )
        assertTrue(WhoopRepository.bareSleepAggregate(daily))
        val rest = RestScorer.restFromDaily(daily)
        assertNotNull(rest)
        val expected = HcNoopAlign.durationAsSleepPerf(450.0)!!.coerceIn(0.0, 100.0)
        assertEquals(expected, rest!!, 1e-9)
    }

    @Test
    fun stagedNightUsesCompositeNotDurationOnly() {
        val daily = DailyMetric(
            deviceId = "x",
            day = "2026-07-12",
            totalSleepMin = 480.0,
            efficiency = 0.90,
            deepMin = 80.0,
            remMin = 90.0,
            lightMin = 250.0,
        )
        assertTrue(!WhoopRepository.bareSleepAggregate(daily))
        val rest = RestScorer.restFromDaily(daily)
        assertNotNull(rest)
        val durationOnly = HcNoopAlign.durationAsSleepPerf(480.0)!!.coerceIn(0.0, 100.0)
        // Composite must differ from the bare duration proxy once stages are present.
        assertTrue(kotlin.math.abs(rest!! - durationOnly) > 0.5)
        assertTrue(rest in 0.0..100.0)
    }
}
