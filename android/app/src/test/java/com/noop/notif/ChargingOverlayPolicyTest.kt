package com.noop.notif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingOverlayPolicyTest {

    @Test
    fun risingEdgeOpensOnce() {
        val s = ChargingOverlayPolicy.evaluate(
            charging = true, pct = 67, wasActive = false, anchorPct = null, dismissed = false,
        )
        assertTrue(s.active)
        assertTrue(s.openOverlay)
        assertEquals(67, s.anchorPct)
    }

    @Test
    fun percentTicksDoNotReopen() {
        var s = ChargingOverlayPolicy.evaluate(
            charging = true, pct = 67, wasActive = false, anchorPct = null, dismissed = false,
        )
        // User watched / dismissed.
        s = ChargingOverlayPolicy.evaluate(
            charging = true, pct = 68, wasActive = true, anchorPct = 67, dismissed = true,
        )
        assertTrue(s.active)
        assertFalse(s.openOverlay)
        assertEquals(68, s.anchorPct) // watermark rises
        s = ChargingOverlayPolicy.evaluate(
            charging = true, pct = 69, wasActive = true, anchorPct = 68, dismissed = true,
        )
        assertFalse(s.openOverlay)
    }

    @Test
    fun nullFlickerDoesNotEndSessionOrReopen() {
        val s = ChargingOverlayPolicy.evaluate(
            charging = null, pct = 70, wasActive = true, anchorPct = 70, dismissed = true,
        )
        assertTrue(s.active)
        assertFalse(s.openOverlay)
        assertFalse(s.clearDismissed)
    }

    @Test
    fun explicitUnplugEndsSession() {
        val s = ChargingOverlayPolicy.evaluate(
            charging = false, pct = 71, wasActive = true, anchorPct = 71, dismissed = true,
        )
        assertFalse(s.active)
        assertTrue(s.closeOverlay)
        assertTrue(s.clearDismissed)
    }

    @Test
    fun fivePointDischargeEndsSession() {
        val s = ChargingOverlayPolicy.evaluate(
            charging = null, pct = 64, wasActive = true, anchorPct = 70, dismissed = true,
        )
        assertFalse(s.active)
        assertTrue(s.clearDismissed)
    }

    @Test
    fun bleFlapLinkDownHoldsSessionWithoutReopen() {
        val s = ChargingOverlayPolicy.evaluate(
            charging = null, pct = 72, wasActive = true, anchorPct = 72, dismissed = true,
            linkConnected = false,
        )
        assertTrue(s.active)
        assertFalse(s.openOverlay)
        assertFalse(s.closeOverlay)
        assertFalse(s.clearDismissed)
    }

    @Test
    fun bleFlapReconnectChargingTrueDoesNotReopen() {
        val s = ChargingOverlayPolicy.evaluate(
            charging = true, pct = 73, wasActive = true, anchorPct = 72, dismissed = true,
            linkConnected = true,
        )
        assertTrue(s.active)
        assertFalse(s.openOverlay)
        assertEquals(73, s.anchorPct)
    }

    @Test
    fun falseWhileLinkDownDoesNotEndSession() {
        // Spurious charging=false during a flap must not clear the latch.
        val s = ChargingOverlayPolicy.evaluate(
            charging = false, pct = 70, wasActive = true, anchorPct = 70, dismissed = true,
            linkConnected = false,
        )
        assertTrue(s.active)
        assertFalse(s.closeOverlay)
        assertFalse(s.clearDismissed)
    }
}
