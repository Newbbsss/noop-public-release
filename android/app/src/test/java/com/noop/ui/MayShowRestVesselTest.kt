package com.noop.ui

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
    fun healthConnectBlanked() {
        assertFalse(mayShowRestVessel(75.0, "health-connect", "Health Connect"))
    }
}
