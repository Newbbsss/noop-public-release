package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fable Sleep #27/#28 — soft-snap bed/wake to band asleep stretch.
 */
class SleepBandBoundRefineTest {

    private fun asleep(from: Long, to: Long, step: Long = 60L): List<Pair<Long, Int>> =
        generateSequence(from) { it + step }.takeWhile { it <= to }
            .map { it to SleepStager.bandStateAsleep }
            .toList()

    @Test
    fun emptyBand_keepsGravityBounds() {
        val (a, b) = SleepStager.refineBoundsFromBandState(1_000L, 10_000L, emptyList())
        assertEquals(1_000L, a)
        assertEquals(10_000L, b)
    }

    @Test
    fun longAsleepStretch_snapsWithinSlack() {
        // Gravity 05:00–10:00; band asleep 06:00–09:30 → snap onset later / wake earlier (within ±90m).
        val start = 5L * 3600
        val end = 10L * 3600
        val band = asleep(6L * 3600, (9L * 3600) + 30 * 60)
        val (a, b) = SleepStager.refineBoundsFromBandState(start, end, band)
        assertEquals(6L * 3600, a)
        // +60s pad past last asleep
        assertEquals((9L * 3600) + 30 * 60 + 60, b)
    }

    @Test
    fun stretchFarOutsideSlack_ignored() {
        val start = 2L * 3600
        val end = 10L * 3600
        // Asleep starts 4h after gravity start (>90 min slack) — leave gravity alone.
        val band = asleep(7L * 3600, 9L * 3600)
        // Wait — 7h is within 90min of... no, |7*3600 - 2*3600| = 5h > 90min
        val (a, b) = SleepStager.refineBoundsFromBandState(start, end, band)
        assertEquals(start, a)
        assertEquals(end, b)
    }

    @Test
    fun shortAsleepStretch_ignored() {
        val start = 2L * 3600
        val end = 10L * 3600
        val band = asleep(6L * 3600, 6L * 3600 + 30 * 60) // 30 min < 90 min min
        val (a, b) = SleepStager.refineBoundsFromBandState(start, end, band)
        assertEquals(start, a)
        assertEquals(end, b)
    }
}
