package com.noop.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins [BackgroundHealth.isAggressiveVendor] (ryanbr #473) without a Context. */
class BackgroundHealthTest {

    @Test
    fun aggressiveVendorsMatchDontKillMyAppSet() {
        assertTrue(BackgroundHealth.isAggressiveVendor("Xiaomi"))
        assertTrue(BackgroundHealth.isAggressiveVendor("OPPO"))
        assertTrue(BackgroundHealth.isAggressiveVendor("OnePlus"))
        assertTrue(BackgroundHealth.isAggressiveVendor("realme"))
        assertTrue(BackgroundHealth.isAggressiveVendor("HUAWEI"))
        assertTrue(BackgroundHealth.isAggressiveVendor("vivo"))
        assertTrue(BackgroundHealth.isAggressiveVendor("Meizu"))
    }

    @Test
    fun standardVendorsAreNotFlagged() {
        assertFalse(BackgroundHealth.isAggressiveVendor("samsung"))
        assertFalse(BackgroundHealth.isAggressiveVendor("Google"))
        assertFalse(BackgroundHealth.isAggressiveVendor("motorola"))
    }
}
