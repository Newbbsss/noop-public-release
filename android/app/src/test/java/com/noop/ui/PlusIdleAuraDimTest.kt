package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusIdleAuraDimTest {

    @Test
    fun reduceMotionLayerIsMuchDimmerThanFullBright() {
        val reduced = plusIdleAuraLayerAlpha(reducedMotion = true, breath = 1f)
        val full = plusIdleAuraLayerAlpha(reducedMotion = false, breath = 1f)
        assertEquals(0.48f, reduced, 1e-5f)
        assertEquals(1.0f, full, 1e-5f)
        assertTrue(reduced < 0.6f)
        assertTrue(reduced < full * 0.55f)
    }

    @Test
    fun stopScaleHalvesUnderReduceMotion() {
        assertEquals(0.52f, plusIdleAuraStopScale(true), 1e-5f)
        assertEquals(1f, plusIdleAuraStopScale(false), 1e-5f)
    }

    @Test
    fun plusButtonReducedAuraBelowLegacy078() {
        assertTrue(plusButtonReducedAuraAlpha(0f) < 0.78f)
        assertTrue(plusButtonReducedAuraAlpha(1f) < 0.78f)
        assertEquals(0.42f, plusButtonReducedAuraAlpha(0f), 1e-5f)
        assertEquals(0.60f, plusButtonReducedAuraAlpha(1f), 1e-5f)
    }
}
