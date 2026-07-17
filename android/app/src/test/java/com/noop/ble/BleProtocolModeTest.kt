package com.noop.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleProtocolModeTest {
    @Test
    fun autoPrefersWhoop5WhenBothServicesPresent() {
        assertEquals(
            com.noop.protocol.DeviceFamily.WHOOP5,
            resolveGattCommandFamily(hasWhoop4 = true, hasWhoop5 = true, mode = BleProtocolMode.AUTO),
        )
    }

    @Test
    fun autoUsesOnlyAvailableService() {
        assertEquals(
            com.noop.protocol.DeviceFamily.WHOOP4,
            resolveGattCommandFamily(hasWhoop4 = true, hasWhoop5 = false, mode = BleProtocolMode.AUTO),
        )
        assertEquals(
            com.noop.protocol.DeviceFamily.WHOOP5,
            resolveGattCommandFamily(hasWhoop4 = false, hasWhoop5 = true, mode = BleProtocolMode.AUTO),
        )
    }

    @Test
    fun forceModesPreferRequestedWhenPresent() {
        assertEquals(
            com.noop.protocol.DeviceFamily.WHOOP4,
            resolveGattCommandFamily(hasWhoop4 = true, hasWhoop5 = true, mode = BleProtocolMode.WHOOP4),
        )
        assertEquals(
            com.noop.protocol.DeviceFamily.WHOOP5,
            resolveGattCommandFamily(hasWhoop4 = true, hasWhoop5 = true, mode = BleProtocolMode.WHOOP5),
        )
    }

    @Test
    fun missingBothReturnsNull() {
        assertNull(resolveGattCommandFamily(false, false, BleProtocolMode.AUTO))
    }
}
