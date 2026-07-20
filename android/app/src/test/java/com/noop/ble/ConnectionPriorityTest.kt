package com.noop.ble

import android.bluetooth.BluetoothGatt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [WhoopBleClient.connectionPriorityFor] / [WhoopBleClient.releasesOnDisable]
 * (ryanbr #536 / #478 safe half).
 */
class ConnectionPriorityTest {

    @Test
    fun activeWorkIsAlwaysHigh() {
        assertEquals(
            BluetoothGatt.CONNECTION_PRIORITY_HIGH,
            WhoopBleClient.connectionPriorityFor(
                offloadActive = true, liveHrActive = false, idleThrottleEnabled = false,
            ),
        )
        assertEquals(
            BluetoothGatt.CONNECTION_PRIORITY_HIGH,
            WhoopBleClient.connectionPriorityFor(
                offloadActive = false, liveHrActive = true, idleThrottleEnabled = false,
            ),
        )
        assertEquals(
            BluetoothGatt.CONNECTION_PRIORITY_HIGH,
            WhoopBleClient.connectionPriorityFor(
                offloadActive = true, liveHrActive = false, idleThrottleEnabled = true,
            ),
        )
    }

    @Test
    fun idleWithThrottleOffStaysBalanced() {
        assertEquals(
            BluetoothGatt.CONNECTION_PRIORITY_BALANCED,
            WhoopBleClient.connectionPriorityFor(
                offloadActive = false, liveHrActive = false, idleThrottleEnabled = false,
            ),
        )
    }

    @Test
    fun idleWithThrottleOnDropsToLowPower() {
        assertEquals(
            BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER,
            WhoopBleClient.connectionPriorityFor(
                offloadActive = false, liveHrActive = false, idleThrottleEnabled = true,
            ),
        )
    }

    @Test
    fun liveHrAloneDoesNotEscalateByDefault() {
        val liveHrActive = true && false // realtimeArmed && escalateForLiveHr (shipped default)
        assertEquals(
            BluetoothGatt.CONNECTION_PRIORITY_BALANCED,
            WhoopBleClient.connectionPriorityFor(
                offloadActive = false, liveHrActive = liveHrActive, idleThrottleEnabled = false,
            ),
        )
        assertEquals(
            BluetoothGatt.CONNECTION_PRIORITY_HIGH,
            WhoopBleClient.connectionPriorityFor(
                offloadActive = true, liveHrActive = liveHrActive, idleThrottleEnabled = false,
            ),
        )
    }

    @Test
    fun liveHrEscalatesWhenTheKnobIsOptedIn() {
        assertEquals(
            BluetoothGatt.CONNECTION_PRIORITY_HIGH,
            WhoopBleClient.connectionPriorityFor(
                offloadActive = false, liveHrActive = true, idleThrottleEnabled = false,
            ),
        )
    }

    @Test
    fun disablingReleasesTheLinkBackToDefault() {
        assertTrue(WhoopBleClient.releasesOnDisable(wasEnabled = true, nowEnabled = false))
    }

    @Test
    fun onlyTheOnToOffEdgeReleases() {
        assertFalse(WhoopBleClient.releasesOnDisable(wasEnabled = false, nowEnabled = false))
        assertFalse(WhoopBleClient.releasesOnDisable(wasEnabled = false, nowEnabled = true))
        assertFalse(WhoopBleClient.releasesOnDisable(wasEnabled = true, nowEnabled = true))
    }
}
