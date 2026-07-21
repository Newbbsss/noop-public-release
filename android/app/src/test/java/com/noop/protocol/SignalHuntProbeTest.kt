package com.noop.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HeartKey / Labrador GET-only catalog pins — never ON, never denylist, never invent ECG.
 */
class SignalHuntProbeTest {

    @Test
    fun heartKeyOpcodes_pinLabradorFamily() {
        val byCmd = SignalHuntProbe.HEARTKEY_OPCODES.associateBy { it.cmd }
        assertEquals("SELECT_WRIST", byCmd[123]?.protocolName)
        assertEquals("TOGGLE_LABRADOR_DATA_GENERATION", byCmd[124]?.protocolName)
        assertEquals("TOGGLE_LABRADOR_RAW_SAVE", byCmd[125]?.protocolName)
        assertEquals("TOGGLE_LABRADOR_FILTERED", byCmd[139]?.protocolName)
        assertEquals(4, SignalHuntProbe.HEARTKEY_OPCODES.size)
    }

    @Test
    fun heartKeyGetOnly_isOffOnly_noSelectWrist_noOn() {
        val burst = SignalHuntProbe.heartKeyGetOnlyBurst()
        assertEquals(3, burst.size)
        assertTrue(burst.all { it.tier == SignalHuntProbe.Tier.TOGGLE_OFF })
        assertTrue(burst.all { it.payload.contentEquals(byteArrayOf(0x00)) })
        assertEquals(setOf(124, 125, 139), burst.map { it.cmd }.toSet())
        assertFalse(burst.any { it.cmd == 123 }) // SELECT_WRIST is SET — catalog only
        assertFalse(burst.any { it.name.contains("_ON") })
        assertFalse(burst.any { SignalHuntProbe.isDenied(it.cmd) })
    }

    @Test
    fun researchBurst_excludesHeartKeyLabradorOn() {
        val research = SignalHuntProbe.researchBurstPaired()
        assertFalse(research.any { it.cmd in setOf(124, 125, 139) })
    }

    @Test
    fun heartKeyFfNames_areGetOnlyCatalog() {
        assertEquals(
            listOf("enable_raw_data_w_ecg", "enable_labrador"),
            SignalHuntProbe.HEARTKEY_FF_NAMES,
        )
        // Adjacent FF names stay in the dense GET_FF sweep too.
        assertTrue(SignalHuntProbe.FF_READ_NAMES.containsAll(SignalHuntProbe.HEARTKEY_FF_NAMES))
    }
}
