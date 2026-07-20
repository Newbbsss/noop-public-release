package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Primary-bout alignment (WHOOP compare 2026-07-17): clip long post-wake stillness /
 * pre-onset fluff out of the scored TIB without inventing stages.
 */
class SleepPrimaryBoutAlignTest {

    private fun seg(start: Long, end: Long, stage: String) =
        StageSegment(start = start, end = end, stage = stage)

    @Test
    fun trailingLongWake_clippedToPrimaryBout() {
        // Mimic Fold morning: sleep ~03:44–08:00, then still/wake until 12:20.
        val bed = 3L * 3600 + 44 * 60
        val lastSleep = 8L * 3600
        val wakeTail = 12L * 3600 + 20 * 60
        val stages = listOf(
            seg(bed, bed + 20 * 60, "wake"),
            seg(bed + 20 * 60, lastSleep, "light"),
            seg(lastSleep, wakeTail, "wake"),
        )
        val out = SleepStager.alignToPrimaryBout(bed, wakeTail, stages)
        assertTrue("wake time should move earlier", out.end < wakeTail)
        assertTrue(
            "end near last sleep + wake pad",
            out.end <= lastSleep + SleepStager.primaryBoutWakePadMin * 60L + 1,
        )
        val awakeS = out.stages.filter { it.stage == "wake" }.sumOf { it.end - it.start }
        val tib = out.end - out.start
        assertTrue("awake share collapses vs 40%+ TIB", awakeS.toDouble() / tib < 0.25)
        assertTrue(out.efficiency > 0.75)
    }

    @Test
    fun leadingLongWake_trimmedToSolPad() {
        val start = 2L * 3600
        val sleep0 = 5L * 3600
        val end = 9L * 3600
        val stages = listOf(
            seg(start, sleep0, "wake"),
            seg(sleep0, end, "light"),
        )
        val out = SleepStager.alignToPrimaryBout(start, end, stages)
        assertEquals(sleep0 - SleepStager.primaryBoutSolPadMin * 60L, out.start)
        assertEquals(end, out.end)
    }

    @Test
    fun midSustainedWake_keepsLongerCluster() {
        val start = 0L
        // Gap > rejoin max so a later short nap stays out of the primary bout.
        val gapMin = SleepStager.primaryBoutRejoinGapMaxMin + 30
        val stages = listOf(
            seg(0, 2 * 3600, "light"),           // 2h sleep
            seg(2 * 3600, 2 * 3600 + gapMin * 60L, "wake"),
            seg(2 * 3600 + gapMin * 60L, 2 * 3600 + gapMin * 60L + 3600, "light"), // 1h nap
        )
        val end = 2 * 3600 + gapMin * 60L + 3600
        val out = SleepStager.alignToPrimaryBout(start, end, stages)
        // Primary = first 2h cluster; nap dropped from this session window.
        assertTrue(out.end <= 2 * 3600 + SleepStager.primaryBoutWakePadMin * 60L + 1)
        val asleep = out.stages.filter { it.stage != "wake" }.sumOf { it.end - it.start }
        assertEquals(2 * 3600L, asleep)
    }

    @Test
    fun falseMorningWake_rejoinsReturnToSleep() {
        // User report shape: sleep ~23:11–~05:30, CK/HR false wake ~05:30–06:45 (≥45m),
        // return sleep ~06:45–08:15. Without rejoin, wake cuts ~06:15 while real wake ~8:15.
        val bed = 0L
        val falseWakeStart = 6L * 3600 + 15 * 60  // 06:15
        val returnSleep = falseWakeStart + 50 * 60 // 50m false wake (≥45 split)
        val realWake = 8L * 3600 + 15 * 60        // 08:15
        val stages = listOf(
            seg(bed, falseWakeStart, "light"),
            seg(falseWakeStart, returnSleep, "wake"),
            seg(returnSleep, realWake, "light"),
        )
        val out = SleepStager.alignToPrimaryBout(bed, realWake, stages)
        assertTrue("wake should extend past false morning cut", out.end >= realWake - 60)
        val asleep = out.stages.filter { it.stage != "wake" }.sumOf { it.end - it.start }
        assertEquals(
            (falseWakeStart - bed) + (realWake - returnSleep),
            asleep,
        )
    }

    @Test
    fun shortWaso_keptInsideBout() {
        val stages = listOf(
            seg(0, 3 * 3600, "light"),
            seg(3 * 3600, 3 * 3600 + 20 * 60, "wake"), // 20 min < 45 split
            seg(3 * 3600 + 20 * 60, 7 * 3600, "deep"),
        )
        val out = SleepStager.alignToPrimaryBout(0, 7 * 3600, stages)
        assertEquals(0L, out.start)
        assertEquals(7 * 3600L, out.end)
        assertEquals(3, out.stages.size)
    }

    @Test
    fun restorativeTargetShare_isHalfDeepPlusRem() {
        // Science audit: Restorative = deep+REM; target share 0.50 (not deep alone).
        assertEquals(0.50, RestScorer.restorativeTargetShare, 1e-9)
        assertEquals(45, SleepStager.primaryBoutRejoinMinAsleepMin)
        assertEquals(90, SleepStager.primaryBoutRejoinGapMaxMin)
    }
}
