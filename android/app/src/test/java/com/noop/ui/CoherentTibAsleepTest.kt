package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoherentTibAsleepTest {
    @Test
    fun sessionShorterThanAsleep_raisesTib() {
        val (tib, asleep) = coherentTibAsleepMinutes(
            sessionTibMin = 360.0, // 6h window
            asleepMin = 420.0,     // 7h stage asleep
            awakeMin = 30.0,
        )
        // stageTib = 450; max(360, 450, 420) = 450
        assertEquals(450.0, tib!!, 0.01)
        assertEquals(420.0, asleep!!, 0.01)
    }

    @Test
    fun normalNight_prefersLargerOfSessionOrStageTib() {
        val (tib, asleep) = coherentTibAsleepMinutes(
            sessionTibMin = 480.0,
            asleepMin = 420.0,
            awakeMin = 40.0,
        )
        assertEquals(480.0, tib!!, 0.01)
        assertEquals(420.0, asleep!!, 0.01)
    }

    @Test
    fun nullsStayNull() {
        val (tib, asleep) = coherentTibAsleepMinutes(null, null, null)
        assertNull(tib)
        assertNull(asleep)
    }
}
