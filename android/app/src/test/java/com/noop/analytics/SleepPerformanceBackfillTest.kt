package com.noop.analytics

import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fable Rest #9 — [IntelligenceEngine.backfillSleepPerformanceSeries] fills missing
 * `sleep_performance` points from banked DailyMetric sleep without inventing nights or
 * duplicating days already covered by the scored/watchRecoveries loops.
 */
class SleepPerformanceBackfillTest {

    @Test
    fun fillsMissingDayFromDailySleep() {
        val row = DailyMetric(
            deviceId = "health-connect",
            day = "2026-07-12",
            totalSleepMin = 420.0,
            efficiency = 0.9,
            deepMin = 60.0,
            remMin = 80.0,
            lightMin = 280.0,
        )
        val out = IntelligenceEngine.backfillSleepPerformanceSeries(
            existingRestDays = emptySet(),
            dailyRows = listOf(row),
            computedId = "my-whoop-noop",
        )
        assertEquals(1, out.size)
        assertEquals("2026-07-12", out[0].day)
        assertEquals("sleep_performance", out[0].key)
        assertEquals("my-whoop-noop", out[0].deviceId)
        assertTrue(out[0].value in 0.0..100.0)
    }

    @Test
    fun skipsDaysAlreadyCovered() {
        val row = DailyMetric(
            deviceId = "my-whoop",
            day = "2026-07-12",
            totalSleepMin = 420.0,
            efficiency = 0.9,
        )
        val out = IntelligenceEngine.backfillSleepPerformanceSeries(
            existingRestDays = setOf("2026-07-12"),
            dailyRows = listOf(row),
            computedId = "my-whoop-noop",
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun skipsNoSleepDays() {
        val row = DailyMetric(deviceId = "my-whoop", day = "2026-07-11", totalSleepMin = null)
        val out = IntelligenceEngine.backfillSleepPerformanceSeries(
            existingRestDays = emptySet(),
            dailyRows = listOf(row),
            computedId = "my-whoop-noop",
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun firstDayWinsAcrossDuplicateSources() {
        val a = DailyMetric(
            deviceId = "my-whoop-noop",
            day = "2026-07-10",
            totalSleepMin = 400.0,
            efficiency = 0.88,
        )
        val b = DailyMetric(
            deviceId = "health-connect",
            day = "2026-07-10",
            totalSleepMin = 450.0,
            efficiency = 0.95,
        )
        val out = IntelligenceEngine.backfillSleepPerformanceSeries(
            existingRestDays = emptySet(),
            dailyRows = listOf(a, b),
            computedId = "my-whoop-noop",
        )
        assertEquals(1, out.size)
        assertEquals(
            RestScorer.restFromDaily(a)!!,
            out[0].value,
            0.0001,
        )
    }
}
