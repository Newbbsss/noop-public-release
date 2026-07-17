package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadialHoldDialMotesTest {

    @Test
    fun moteBudgetIsDenserThanLegacySparse42() {
        assertTrue(RADIAL_HOLD_DIAL_MOTE_COUNT > 42)
        assertEquals(80, RADIAL_HOLD_DIAL_MOTE_COUNT)
    }

    @Test
    fun buildDialMotesCoversAllSpokes() {
        val motes = buildDialMotes(RADIAL_HOLD_DIAL_MOTE_COUNT)
        assertEquals(RADIAL_HOLD_DIAL_MOTE_COUNT, motes.size)
        assertEquals(setOf(0, 1, 2, 3, 4), motes.map { it.spoke }.toSet())
        assertTrue(motes.any { it.rim })
        assertTrue(motes.any { it.cool })
        assertTrue(motes.any { !it.cool })
    }

    @Test
    fun captionGrammar() {
        assertEquals("Release to open", radialHoldDialCaption(true))
        assertEquals("Swipe a shortcut · release centre to cancel", radialHoldDialCaption(false))
    }
}
