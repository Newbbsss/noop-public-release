package com.noop.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** SHIP #379 — charging mint must stay fixed 0xFF2DD4A0 (never theme gold/yellow). */
class StrapChargeMintTest {
    @Test
    fun mintIsFixedGreenNotThemePositive() {
        assertEquals(Color(0xFF2DD4A0), StrapChargeMint)
    }
}
