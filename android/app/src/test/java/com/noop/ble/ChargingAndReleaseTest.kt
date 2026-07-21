package com.noop.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-predicate tests for two BLE-lane changes that can't exercise a live GATT stack (no Robolectric —
 * see [GattCrashSafetyTest]):
 *
 *   - PR #568 (charging bolt): [WhoopBleClient.shouldApplyChargingFromBatteryEvent] — a LIVE BATTERY_LEVEL
 *     event drives the charging pill; a historical one replayed mid-backfill does not. The old 45 s
 *     event-timestamp freshness gate is gone.
 *   - H3 / #520 (device-remove release): [WhoopBleClient.releasedLiveState] — releasing a strap clears the
 *     live link + every stale readout so a removed band can't keep showing live HR / a bond / a charge.
 */
class ChargingAndReleaseTest {

    // --- PR #568: charging-from-battery-event gate -----------------------------------------------------

    @Test fun liveBatteryEvent_appliesCharging() {
        assertTrue(WhoopBleClient.shouldApplyChargingFromBatteryEvent(replayedOffload = false))
    }

    @Test fun replayedHistoricalBatteryEvent_doesNotApplyCharging() {
        assertFalse(WhoopBleClient.shouldApplyChargingFromBatteryEvent(replayedOffload = true))
    }

    @Test fun consoleBatteryPackInstalled_meansOnCharger() {
        assertTrue(
            WhoopBleClient.chargingHintFromConsole("36, 7204249: LISTENER: Battery Pack Installed\n") == true,
        )
    }

    @Test fun consoleBatteryPackRemoved_meansOffCharger() {
        assertTrue(
            WhoopBleClient.chargingHintFromConsole("LISTENER: Battery Pack Uninstalled") == false,
        )
    }

    @Test fun unrelatedConsole_isIgnored() {
        assertNull(WhoopBleClient.chargingHintFromConsole("Fuel Gauge enabled"))
    }

    @Test fun liveChargingOnEvent_meansOnCharger() {
        assertEquals(
            true,
            WhoopBleClient.chargingHintFromEvent("CHARGING_ON(7)", replayedOffload = false),
        )
    }

    @Test fun liveChargingOffEvent_meansOffCharger() {
        assertEquals(
            false,
            WhoopBleClient.chargingHintFromEvent("CHARGING_OFF(8)", replayedOffload = false),
        )
    }

    @Test fun liveBatteryPackRemovedEvent_meansOffCharger() {
        assertEquals(
            false,
            WhoopBleClient.chargingHintFromEvent("BATTERY_PACK_REMOVED(22)", replayedOffload = false),
        )
    }

    @Test fun replayedChargingOffEvent_isIgnored() {
        assertNull(
            WhoopBleClient.chargingHintFromEvent("CHARGING_OFF(8)", replayedOffload = true),
        )
    }

    @Test fun socRise_infersCharging() {
        assertEquals(true, WhoopBleClient.inferChargingFromSoc(50.0, 51.0, currentlyCharging = false))
    }

    @Test fun socOnePointDropWhileCharging_staysSticky() {
        // −1% fuel-gauge wobble must not clear the bolt while docked (was −0.5% clear).
        assertNull(WhoopBleClient.inferChargingFromSoc(80.0, 79.0, currentlyCharging = true))
    }

    @Test fun socOnePointFiveDropWhileCharging_clears() {
        assertEquals(false, WhoopBleClient.inferChargingFromSoc(80.0, 78.4, currentlyCharging = true))
    }

    @Test fun socFlatWhileCharging_staysSticky() {
        assertNull(WhoopBleClient.inferChargingFromSoc(80.0, 80.0, currentlyCharging = true))
    }

    // --- H3 / #520: released LiveState -----------------------------------------------------------------

    @Test fun releasedState_dropsTheLinkAndClearsLiveReadouts() {
        val live = LiveState(
            connected = true, bonded = true, encryptedBond = true,
            heartRate = 72, rr = listOf(800, 810), rrRecent = listOf(800, 810),
            charging = true, pairingHint = "still bonded to the official app",
            scanning = true, statusNote = "Searching…",
        )
        val released = WhoopBleClient.releasedLiveState(live)
        assertFalse(released.connected)
        assertFalse(released.bonded)
        assertFalse(released.encryptedBond)
        assertNull(released.heartRate)
        assertTrue(released.rr.isEmpty())
        assertTrue(released.rrRecent.isEmpty())
        assertNull(released.charging)
        assertNull(released.pairingHint)
        assertFalse(released.scanning)
        assertNull(released.statusNote)
    }

    @Test fun releasedState_isIdempotentFromAnAlreadyDownState() {
        val down = LiveState()
        val released = WhoopBleClient.releasedLiveState(down)
        assertFalse(released.connected)
        assertNull(released.heartRate)
        assertNull(released.charging)
    }

    // --- Client-side type-40 / 0x2A37 R-R quality (no membership) -------------------------------------

    @Test fun withRRIntervals_dropsOutOfPhysiologySpikes() {
        val next = LiveState().withRRIntervals(listOf(0, 250, 800, 2100, 810))
        assertTrue(next.rr == listOf(800, 810))
        assertTrue(next.rrRecent == listOf(800, 810))
    }

    @Test fun withRRIntervals_keepsRollingBufferCap() {
        val seeded = LiveState(rrRecent = List(58) { 800 })
        val next = seeded.withRRIntervals(listOf(810, 820, 830))
        assertTrue(next.rrRecent.size == 60)
        assertTrue(next.rrRecent.takeLast(3) == listOf(810, 820, 830))
    }

    @Test fun clearedBiometrics_resetsType40SessionTallies() {
        val live = LiveState(
            heartRate = 72,
            rrRecent = listOf(800, 810),
            streamingLiveHR = true,
            type40FramesThisSession = 128,
            type40WithRrThisSession = 90,
        )
        val cleared = live.clearedBiometrics()
        assertNull(cleared.heartRate)
        assertTrue(cleared.rrRecent.isEmpty())
        assertFalse(cleared.streamingLiveHR)
        assertEquals(0, cleared.type40FramesThisSession)
        assertEquals(0, cleared.type40WithRrThisSession)
    }
}
