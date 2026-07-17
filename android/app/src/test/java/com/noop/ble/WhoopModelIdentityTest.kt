package com.noop.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins BLE-name → model inference and multi-bond 5/MG pick order so Gilbert's MG is never
 * mislabeled as WHOOP 4 after an update, and a dead sibling bond isn't preferred over live MG.
 */
class WhoopModelIdentityTest {

    @Test
    fun fromBleName_classic4Serial() {
        assertEquals(WhoopModel.WHOOP4, WhoopModel.fromBleName("WHOOP 4C1594026"))
        assertEquals(WhoopModel.WHOOP4, WhoopModel.fromBleName("WHOOP 4.0"))
    }

    @Test
    fun fromBleName_mgAnd5() {
        assertEquals(WhoopModel.WHOOP5_MG, WhoopModel.fromBleName("WHOOP MG"))
        assertEquals(WhoopModel.WHOOP5_MG, WhoopModel.fromBleName("WHOOP 5.0"))
        assertEquals(WhoopModel.WHOOP5_MG, WhoopModel.fromBleName("WHOOP 5AM"))
    }

    @Test
    fun fromBleName_rejectsNonWhoop() {
        assertNull(WhoopModel.fromBleName(null))
        assertNull(WhoopModel.fromBleName(""))
        assertNull(WhoopModel.fromBleName("Polar H10"))
    }

    @Test
    fun resolveForEstimates_prefersLiveDetectionAndPersistedMg() {
        assertEquals(
            WhoopModel.WHOOP5_MG,
            WhoopModel.resolveForEstimates(WhoopModel.WHOOP4, whoop5Detected = true, persisted = null),
        )
        assertEquals(
            WhoopModel.WHOOP5_MG,
            WhoopModel.resolveForEstimates(
                WhoopModel.WHOOP4,
                whoop5Detected = false,
                persisted = WhoopModel.WHOOP5_MG,
            ),
        )
        assertEquals(
            WhoopModel.WHOOP4,
            WhoopModel.resolveForEstimates(WhoopModel.WHOOP4, whoop5Detected = false, persisted = null),
        )
    }

    @Test
    fun pickBondedWhoop5_prefersPinThenSavedThenLiveThenMg() {
        val dead = WhoopBleClient.BondedWhoopCandidate("AA:01", "WHOOP 5AM", false)
        val liveMg = WhoopBleClient.BondedWhoopCandidate("AA:02", "WHOOP MG", true)
        val other = WhoopBleClient.BondedWhoopCandidate("AA:03", "WHOOP 5.0", false)
        val all = listOf(dead, liveMg, other)

        assertEquals(
            "AA:03",
            WhoopBleClient.pickBondedWhoop5(all, preferredAddress = "AA:03", savedAddress = "AA:01")!!.address,
        )
        assertEquals(
            "AA:01",
            WhoopBleClient.pickBondedWhoop5(all, preferredAddress = null, savedAddress = "AA:01")!!.address,
        )
        assertEquals(
            "AA:02",
            WhoopBleClient.pickBondedWhoop5(all, preferredAddress = null, savedAddress = null)!!.address,
        )
        assertEquals(
            "AA:02",
            WhoopBleClient.pickBondedWhoop5(
                listOf(dead, other, liveMg),
                preferredAddress = null,
                savedAddress = null,
            )!!.address,
        )
        // No pin/saved/live: MG-named wins over a generic 5.0 sibling.
        assertEquals(
            "AA:02",
            WhoopBleClient.pickBondedWhoop5(
                listOf(dead, liveMg.copy(gattConnectedOrConnecting = false), other),
                preferredAddress = null,
                savedAddress = null,
            )!!.address,
        )
    }
}
