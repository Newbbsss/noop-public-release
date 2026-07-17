package com.noop.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fable #361 — Devices clock line copy from GET_CLOCK / SET_CLOCK session stamps. */
class StrapClockLineTest {

    private val now = 1_700_000_000_000L

    @Test
    fun nullWhenNoClockEvents() {
        assertNull(WhoopBleClient.strapClockLine(null, null, null, now))
    }

    @Test
    fun deltaAndSynced() {
        val line = WhoopBleClient.strapClockLine(
            deltaSec = 3L,
            checkedAtMs = now - 30_000L,
            lastSetAtMs = now - 120_000L,
            nowMs = now,
        )
        assertEquals("Clock δ +3s · synced 2 min ago", line)
    }

    @Test
    fun zeroDeltaJustNow() {
        val line = WhoopBleClient.strapClockLine(
            deltaSec = 0L,
            checkedAtMs = now,
            lastSetAtMs = now,
            nowMs = now,
        )
        assertTrue(line!!.startsWith("Clock δ 0s"))
        assertTrue(line.contains("just now"))
    }
}
