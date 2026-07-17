package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure contracts for the Today floating-cloud explain epic (SHIP_IMPROVE_400 finale).
 * Keeps motion tokens honest so UI doesn't drift into glow / bounce / sheet language.
 */
class TodayCloudExplainTest {

    @Test
    fun growMs_isSignatureNotBounce() {
        assertTrue(TodayCloudLacquer.GROW_MS in 320..520)
    }

    @Test
    fun mistPeak_staysBelowOpaqueSlab() {
        assertTrue(TodayCloudLacquer.MIST_PEAK < 0.75f)
        assertTrue(TodayCloudLacquer.MIST_PEAK > 0.40f)
    }

    @Test
    fun bubbleCorner_notAiPill() {
        // DESIGN.md: cards top out ~16–20; cloud may soften slightly but never 32+
        assertTrue(TodayCloudLacquer.BUBBLE_CORNER_DP.value <= 24f)
        assertTrue(TodayCloudLacquer.BUBBLE_CORNER_DP.value >= 16f)
    }

    @Test
    fun listWash_tokenAlphaCeiling() {
        // Ambient wash intensity scaled into ≤0.16 draw alpha in Canvas
        assertTrue(TodayCloudLacquer.LIST_WASH_INTENSITY <= 1f)
        assertTrue(TodayCloudLacquer.LIST_WASH_INTENSITY >= 0.5f)
    }

    @Test
    fun lacquerCopy_dualScaleHonesty() {
        assertTrue(LifeChapterLacquer.EFFORT_SHEET_SCALE_BODY.contains("0–100"))
        assertTrue(LifeChapterLacquer.EFFORT_SHEET_SCALE_BODY.contains("0–21"))
        assertEquals("What shaped your Charge", LifeChapterLacquer.CHARGE_CLOUD_TITLE)
        assertEquals("Rest", LifeChapterLacquer.REST_CLOUD_TITLE)
    }
}
