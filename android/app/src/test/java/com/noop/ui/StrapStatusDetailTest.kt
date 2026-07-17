package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Settings → Strap status detail copy, in particular that an in-flight scan takes
 * precedence over bonded/connected so the user gets "Looking…" feedback the moment Re-scan is
 * tapped (issue #1). The button's `enabled = !live.scanning` relies on the same scanning flag, so
 * a regression here is the visible half of "Re-scan does nothing".
 */
class StrapStatusDetailTest {

    @Test
    fun scanning_takesPrecedence_overEveryOtherState() {
        // Even when already bonded + connected, an active scan must say "Looking…".
        assertTrue(
            strapStatusDetail(bonded = true, connected = true, scanning = true)
                .startsWith("Looking for your WHOOP"),
        )
        assertTrue(
            strapStatusDetail(bonded = false, connected = false, scanning = true)
                .startsWith("Looking for your WHOOP"),
        )
    }

    @Test
    fun nonScanning_branches_areUnchanged() {
        assertEquals(
            LifeChapterLacquer.SETTINGS_STRAP_PAIRED,
            strapStatusDetail(bonded = true, connected = true, scanning = false),
        )
        assertEquals(
            LifeChapterLacquer.SETTINGS_STRAP_HANDSHAKE,
            strapStatusDetail(bonded = false, connected = true, scanning = false),
        )
        assertEquals(
            LifeChapterLacquer.SETTINGS_STRAP_BONDED_IDLE,
            strapStatusDetail(bonded = true, connected = false, scanning = false),
        )
        assertEquals(
            LifeChapterLacquer.SETTINGS_STRAP_NONE,
            strapStatusDetail(bonded = false, connected = false, scanning = false),
        )
    }

    @Test
    fun alongsideWhoopApp_appendsOpenHrHonesty_whenPaired() {
        val detail = strapStatusDetail(
            bonded = true,
            connected = true,
            scanning = false,
            alongsideWhoopApp = true,
        )
        assertTrue(detail.startsWith(LifeChapterLacquer.SETTINGS_STRAP_PAIRED))
        assertTrue(detail.contains(LifeChapterLacquer.SETTINGS_STRAP_ALONGSIDE_NOTE))
    }

    @Test
    fun powerSavingStatus_isHonest_whenSocUnknownOrCharging() {
        assertEquals(
            LifeChapterLacquer.SETTINGS_POWER_WAITING_SOC,
            settingsPowerSavingStatus(
                easingNow = false,
                batteryPct = null,
                charging = false,
                thresholdPct = 20,
            ),
        )
        assertEquals(
            LifeChapterLacquer.SETTINGS_POWER_CHARGING_PAUSED,
            settingsPowerSavingStatus(
                easingNow = false,
                batteryPct = 12.0,
                charging = true,
                thresholdPct = 20,
            ),
        )
    }
}
