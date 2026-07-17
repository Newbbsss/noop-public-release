package com.noop.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dense Today — past-day Charge must show Whoop CSV / On-device; blank WHOOP-app only. */
class MayShowChargeVesselTest {

    @Test
    fun noopComputedAlwaysShows() {
        assertTrue(mayShowChargeVessel(82.0, "my-whoop-noop", "On-device"))
    }

    @Test
    fun whoopCsvImportShows() {
        // Old isNoopVesselProvenance blanked label "Whoop" — past-day Charge looked empty.
        assertTrue(mayShowChargeVessel(91.0, "my-whoop", "Whoop"))
    }

    @Test
    fun bankedWithoutProvenanceShows() {
        assertTrue(mayShowChargeVessel(70.0, null, null))
    }

    @Test
    fun whoopAppManualBlanked() {
        assertFalse(mayShowChargeVessel(88.0, "whoop-app", "WHOOP app"))
    }

    @Test
    fun nullScoreNeverShows() {
        assertFalse(mayShowChargeVessel(null, "my-whoop-noop", "On-device"))
    }

    @Test
    fun healthConnectBlanked() {
        assertFalse(mayShowChargeVessel(75.0, "health-connect", "Health Connect"))
    }

    @Test
    fun appleHealthBlanked() {
        assertFalse(mayShowChargeVessel(80.0, "apple-health", "Apple Health"))
    }
}
