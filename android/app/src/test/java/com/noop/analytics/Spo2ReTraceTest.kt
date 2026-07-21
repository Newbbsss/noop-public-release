package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Connection-mode SpO2 reverse-engineering dump line (PR #945, reimplemented). Pure JVM. The
 * vectors are byte-identical to the Swift Spo2ReTraceTests so a shared log correlates identically from
 * either platform. Log-only diagnostics: nothing here ever becomes a user-facing SpO2 number.
 */
class Spo2ReTraceTest {

    @Test fun recordLinePinnedExactly() {
        val line = Spo2ReTrace.recordLine(
            frame = byteArrayOf(0x00, 0x0f, 0xff.toByte(), 0x10),
            version = 24, unix = 1_700_000_000, red = 512, ir = 480, skinRaw = 330,
        )
        assertEquals(
            "spo2re v=24 unix=1700000000 red=512 ir=480 skinRaw=330 sleep_state=null aux82=null len=4 raw=000fff10",
            line,
        )
    }

    @Test fun absentChannelsRenderNull() {
        // A record with no SpO2 channels mapped (e.g. a v25 motion record) must still dump in full -
        // proving "nothing banked" needs the negative case on the record itself.
        val line = Spo2ReTrace.recordLine(
            frame = byteArrayOf(1, 2, 3), version = 25, unix = 42, red = null, ir = null, skinRaw = null,
        )
        assertEquals(
            "spo2re v=25 unix=42 red=null ir=null skinRaw=null sleep_state=null aux82=null len=3 raw=010203",
            line,
        )
    }

    @Test fun hexRendersUnsignedFullFrame() {
        // 0xFF must render "ff" (unsigned, never a sign-extended "ffffffff"), and the FULL frame ships -
        // the unmapped tail bytes are exactly where a banked SpO2 would sit.
        val line = Spo2ReTrace.recordLine(
            frame = byteArrayOf(0xff.toByte(), 0x00, 0xab.toByte()),
            version = null, unix = null, red = null, ir = null, skinRaw = null,
        )
        assertTrue(line, line.endsWith("raw=ff00ab"))
        assertTrue(line, line.contains("v=null"))
    }

    @Test fun aux82AndSleepStateNamedNeverAsPct() {
        // Research P0: name sleep_state + aux82 for correlation — never label as SpO2 %.
        // 0x80 is bit-7 sentinel → whooprs_pct_gate=out (not product %).
        val line = Spo2ReTrace.recordLine(
            frame = byteArrayOf(1, 2), version = 18, unix = 99,
            red = null, ir = null, skinRaw = null, sleepState = 2, auxByte82 = 0x80,
        )
        assertTrue(line, line.contains("sleep_state=2"))
        assertTrue(line, line.contains("aux82=128"))
        assertTrue(line, line.contains("whooprs_pct_gate=out"))
        assertTrue(line, !line.contains("spo2Pct") && !line.contains("spo2%"))
        assertEquals(null, Spo2ReTrace.whoopRsSpo2PctCandidate(0x80))
        assertEquals(97, Spo2ReTrace.whoopRsSpo2PctCandidate(97))
    }

    @Test fun sampleCapBoundedAtEight() {
        assertEquals(8, Spo2ReTrace.MAX_SAMPLES)
        assertEquals(2, Spo2ReTrace.BASELINE_SAMPLES)
    }

    @Test fun researchInterestingPrefersNzAsleepGate() {
        assertFalse(Spo2ReTrace.isResearchInteresting(null, null))
        assertFalse(Spo2ReTrace.isResearchInteresting(0, 0))
        assertTrue(Spo2ReTrace.isResearchInteresting(0, 2)) // band asleep
        assertTrue(Spo2ReTrace.isResearchInteresting(26, 0)) // nz aux
        assertTrue(Spo2ReTrace.isResearchInteresting(97, 2)) // whooprs gate range
    }
}
