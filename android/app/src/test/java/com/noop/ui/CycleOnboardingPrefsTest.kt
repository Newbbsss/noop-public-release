package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prefs carry onboardingComplete for the true Cycle first-run flow. */
class CycleOnboardingPrefsTest {

    @Test
    fun defaultPrefsNeedOnboarding() {
        val prefs = PeriodCalendar.Prefs()
        assertFalse(prefs.enabled)
        assertFalse(prefs.onboardingComplete)
    }

    @Test
    fun replaySetupClearsOnboardingFlag() {
        // Fable 200 #40 — Settings "Replay Cycle setup" only flips onboardingComplete off.
        val done = PeriodCalendar.Prefs(enabled = true, onboardingComplete = true, avgCycleLengthOverride = 28)
        val replay = done.copy(onboardingComplete = false)
        assertTrue(done.onboardingComplete)
        assertFalse(replay.onboardingComplete)
        assertTrue(replay.enabled)
        assertEquals(28, replay.avgCycleLengthOverride)
    }
}
