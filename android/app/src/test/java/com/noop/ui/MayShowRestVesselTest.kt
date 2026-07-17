package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TOP-D #351 / MAIN Rest vessel gate. */
class MayShowRestVesselTest {

    @Test
    fun noopComputedAlwaysShows() {
        assertTrue(mayShowRestVessel(82.0, "my-whoop-noop", "On-device"))
    }

    @Test
    fun whoopCsvImportShows() {
        // MAIN was blanking label "Whoop" via isNoopVesselProvenance — Rest looked forever empty/calibrating.
        assertTrue(mayShowRestVessel(91.0, "my-whoop", "Whoop"))
    }

    @Test
    fun restFromDailyFallbackShows() {
        assertTrue(mayShowRestVessel(70.0, null, null))
    }

    @Test
    fun whoopAppManualBlanked() {
        assertFalse(mayShowRestVessel(88.0, "whoop-app", "WHOOP app"))
    }

    @Test
    fun nullScoreNeverShows() {
        assertFalse(mayShowRestVessel(null, "my-whoop-noop", "On-device"))
    }

    @Test
    fun healthConnectAsleepShows() {
        // SHIP #74 — HC asleep fused into Rest must not stay blank like Charge recovery.
        assertTrue(mayShowRestVessel(75.0, "health-connect", "Health Connect"))
    }

    @Test
    fun restSourceCaptionMarksHc() {
        assertEquals(
            "HC asleep · Sleep tab",
            restSourceCaption(80.0, "Health Connect"),
        )
        assertEquals(
            "night readiness · Sleep tab",
            restSourceCaption(80.0, "On-device"),
        )
    }
}
