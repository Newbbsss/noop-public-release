package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusIdleGlowSizeTest {

    @Test
    fun shortCoverUsesSub248Glow() {
        // Fold-cover class (~248dp tall) — fixed 248–300 clipped under taskbar (#395).
        val d = plusIdleGlowDiameterDp(248)
        assertTrue(d < 248)
        assertEquals(160, d) // 248 * 0.40 → floor at 160
        assertTrue(plusIdleGlowLiftDp(248) > 0)
    }

    @Test
    fun tallPhoneKeepsFullBloom() {
        assertEquals(300, plusIdleGlowDiameterDp(900))
        assertEquals(0, plusIdleGlowLiftDp(900))
    }

    @Test
    fun midHeightsScaleSmoothly() {
        val mid = plusIdleGlowDiameterDp(600)
        assertTrue(mid in 160..300)
        assertEquals(240, mid) // 600 * 0.40
    }
}
