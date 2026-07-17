package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards crescent balance for the 4-tab bar (Today · Trends | Sleep · More). Cycle is not on the bar. */
class BarNavSplitTest {

    @Test
    fun fourTabsSplitEvenly() {
        assertEquals(2, barLeftTabCount(4))
    }

    @Test
    fun fiveTabsWouldPutExtraOnLeft() {
        // Historical: when Cycle was on the bar (5 tabs), left got 3.
        // Cycle is now + / Today only — keep the split helper honest for odd counts.
        assertEquals(3, barLeftTabCount(5))
    }

    @Test
    fun emptyAndTiny() {
        assertEquals(0, barLeftTabCount(0))
        assertEquals(1, barLeftTabCount(2))
        assertEquals(1, barLeftTabCount(3))
    }

    @Test
    fun denserCrescentGetsWidthBoost() {
        val (l, r) = barCrescentWeights(3, 2)
        assertEquals(3 * 1.24f, l, 1e-5f)
        assertEquals(2f, r, 1e-5f)
        // Per-tab left width > per-tab right width so 3 icons don't crowd (#396).
        assertTrue(l / 3f > r / 2f)
    }

    @Test
    fun balancedCrescentsStayEqual() {
        val (l, r) = barCrescentWeights(2, 2)
        assertEquals(2f, l, 1e-5f)
        assertEquals(2f, r, 1e-5f)
    }
}
