package com.noop.ui

import com.noop.analytics.ScoreConfidence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fable Rest #12 — hero footnote honesty. */
class RestConfidenceCaptionTest {

    @Test
    fun captionsArePlainAndEmDashFree() {
        for (tier in ScoreConfidence.entries) {
            val c = restConfidenceCaption(tier)
            assertFalse(c.contains('\u2014') || c.contains('\u2013'))
            assertFalse(c.isBlank())
        }
    }

    @Test
    fun buildingMentionsThinStages() {
        val c = restConfidenceCaption(ScoreConfidence.BUILDING)
        assertTrue(c.contains("building", ignoreCase = true))
        assertTrue(c.contains("stages", ignoreCase = true))
    }

    @Test
    fun gravitySparse_downgradesSolidToBuilding() {
        // #395 — confidence-only; never invents Rest score.
        val solid = ScoreConfidence.forRest(
            hasSession = true,
            hasStagedSleep = true,
            asleepSeconds = 7.0 * 3600.0,
            restorativeSeconds = 2.5 * 3600.0,
            efficiency = 0.90,
            gravitySparse = false,
        )
        val sparse = ScoreConfidence.forRest(
            hasSession = true,
            hasStagedSleep = true,
            asleepSeconds = 7.0 * 3600.0,
            restorativeSeconds = 2.5 * 3600.0,
            efficiency = 0.90,
            gravitySparse = true,
        )
        assertTrue(solid == ScoreConfidence.SOLID)
        assertTrue(sparse == ScoreConfidence.BUILDING)
    }
}
