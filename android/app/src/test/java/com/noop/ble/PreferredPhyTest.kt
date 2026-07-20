package com.noop.ble

import android.bluetooth.BluetoothDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [WhoopBleClient.preferredPhyMask] / [WhoopBleClient.phyLabel] (ryanbr #537 / #538).
 */
class PreferredPhyTest {

    @Test
    fun offIsPlain1M_todaysLink() {
        assertEquals(
            BluetoothDevice.PHY_LE_1M_MASK,
            WhoopBleClient.preferredPhyMask(fastLinkEnabled = false),
        )
    }

    @Test
    fun onAllowsBoth2MAnd1MFallback() {
        val mask = WhoopBleClient.preferredPhyMask(fastLinkEnabled = true)
        assertTrue("2M must be offered", mask and BluetoothDevice.PHY_LE_2M_MASK != 0)
        assertTrue(
            "1M must stay in the mask so the controller can fall back",
            mask and BluetoothDevice.PHY_LE_1M_MASK != 0,
        )
    }

    @Test
    fun theReleaseMaskIsTheOffMaskAndDropsThe2MOffer() {
        val off = WhoopBleClient.preferredPhyMask(fastLinkEnabled = false)
        assertEquals(BluetoothDevice.PHY_LE_1M_MASK, off)
        assertEquals(0, off and BluetoothDevice.PHY_LE_2M_MASK)
    }

    @Test
    fun onlyTheOnToOffEdgeReleasesThePhy() {
        assertTrue(WhoopBleClient.releasesOnDisable(wasEnabled = true, nowEnabled = false))
        assertEquals(false, WhoopBleClient.releasesOnDisable(wasEnabled = false, nowEnabled = false))
        assertEquals(false, WhoopBleClient.releasesOnDisable(wasEnabled = false, nowEnabled = true))
        assertEquals(false, WhoopBleClient.releasesOnDisable(wasEnabled = true, nowEnabled = true))
    }

    @Test
    fun labelsTheNegotiatedPhyValueNotTheMask() {
        assertEquals("1M", WhoopBleClient.phyLabel(BluetoothDevice.PHY_LE_1M))
        assertEquals("2M", WhoopBleClient.phyLabel(BluetoothDevice.PHY_LE_2M))
        assertEquals("coded", WhoopBleClient.phyLabel(BluetoothDevice.PHY_LE_CODED))
    }

    @Test
    fun labelsAnUnexpectedPhyRatherThanHidingIt() {
        assertEquals("unknown(9)", WhoopBleClient.phyLabel(9))
    }
}
