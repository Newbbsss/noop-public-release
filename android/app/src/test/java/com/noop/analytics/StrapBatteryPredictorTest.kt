package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrapBatteryPredictorTest {

    private val h = 3_600_000L

    @Test fun predictHoldsWhileChargingFresh() {
        val snap = StrapBatteryPredictor.Snapshot(
            pct = 40.0,
            savedAtMs = 0L,
            charging = true,
            whoop5Family = true,
        )
        assertEquals(40.0, StrapBatteryPredictor.predict(snap, nowMs = 10 * 60_000L), 1e-6)
    }

    @Test fun predictDrainsAfterStaleChargingSnapshot() {
        // Stale charging bit must not freeze SoC forever after unplug.
        val snap = StrapBatteryPredictor.Snapshot(
            pct = 100.0,
            savedAtMs = 0L,
            charging = true,
            whoop5Family = true,
        )
        val now = StrapBatteryPredictor.CHARGING_HOLD_MAX_MS + (28.8 * h).toLong()
        val expected = 100.0 - (100.0 / 288.0) * (now / h.toDouble())
        assertEquals(expected.coerceIn(0.0, 100.0), StrapBatteryPredictor.predict(snap, nowMs = now), 1e-6)
    }

    @Test fun predictLinearDrainWhoop5() {
        // 12-day rated life → 100/288 %/h. After 28.8h at 100% → 90%.
        val snap = StrapBatteryPredictor.Snapshot(
            pct = 100.0,
            savedAtMs = 0L,
            charging = false,
            whoop5Family = true,
        )
        val expected = 100.0 - (100.0 / 288.0) * 28.8
        assertEquals(expected, StrapBatteryPredictor.predict(snap, nowMs = (28.8 * h).toLong()), 1e-6)
    }

    @Test fun predictClampsAtZero() {
        val snap = StrapBatteryPredictor.Snapshot(
            pct = 1.0,
            savedAtMs = 0L,
            charging = false,
            whoop5Family = false, // 4.5-day pack drains faster
        )
        assertEquals(0.0, StrapBatteryPredictor.predict(snap, nowMs = 30 * h), 1e-6)
    }

    @Test fun liveTrustedNeedsSamplesOrDwell() {
        assertFalse(
            StrapBatteryPredictor.liveTrusted(
                connected = true, livePct = 50.0, batteryFreshCount = 1,
                linkUpAtMs = 0L, nowMs = 5_000L,
            ),
        )
        assertTrue(
            StrapBatteryPredictor.liveTrusted(
                connected = true, livePct = 50.0, batteryFreshCount = 2,
                linkUpAtMs = 0L, nowMs = 1_000L,
            ),
        )
        assertTrue(
            StrapBatteryPredictor.liveTrusted(
                connected = true, livePct = 50.0, batteryFreshCount = 1,
                linkUpAtMs = 0L, nowMs = StrapBatteryPredictor.LIVE_TRUST_AFTER_MS,
            ),
        )
    }

    @Test fun resolvePrefersPredictedUntilTrusted() {
        val snap = StrapBatteryPredictor.Snapshot(
            pct = 80.0, savedAtMs = 0L, charging = false, whoop5Family = true,
        )
        val d = StrapBatteryPredictor.resolve(
            connected = true,
            livePct = 79.0,
            liveCharging = false,
            batteryFreshCount = 1,
            linkUpAtMs = 0L,
            snapshot = snap,
            nowMs = 1_000L,
        )!!
        assertEquals(StrapBatteryPredictor.Source.PREDICTED, d.source)
        assertTrue(d.pct in 79.0..80.0)
    }

    @Test fun resolveLiveWhenTrusted() {
        val snap = StrapBatteryPredictor.Snapshot(
            pct = 80.0, savedAtMs = 0L, charging = false, whoop5Family = true,
        )
        val d = StrapBatteryPredictor.resolve(
            connected = true,
            livePct = 77.0,
            liveCharging = true,
            batteryFreshCount = 2,
            linkUpAtMs = 0L,
            snapshot = snap,
            nowMs = 1_000L,
        )!!
        assertEquals(StrapBatteryPredictor.Source.LIVE, d.source)
        assertEquals(77.0, d.pct, 1e-6)
        assertTrue(d.charging)
    }

    @Test fun resolveDisconnectedNeverBlankWithSnapshot() {
        val snap = StrapBatteryPredictor.Snapshot(
            pct = 55.0, savedAtMs = 0L, charging = false, whoop5Family = true,
        )
        val d = StrapBatteryPredictor.resolve(
            connected = false,
            livePct = null,
            liveCharging = null,
            batteryFreshCount = 0,
            linkUpAtMs = null,
            snapshot = snap,
            nowMs = h,
        )!!
        assertEquals(StrapBatteryPredictor.Source.PREDICTED, d.source)
        assertFalse(d.charging)
        assertNull(
            StrapBatteryPredictor.resolve(
                connected = false, livePct = null, liveCharging = null,
                batteryFreshCount = 0, linkUpAtMs = null, snapshot = null,
            ),
        )
    }

    @Test fun resolveStaleChargingSnapshot_doesNotShowBolt() {
        // Gilbert bug: unplug left snapshot.charging=true; reconnect with unknown live bit
        // painted “on charger”. Stale saves must not invent the bolt.
        val snap = StrapBatteryPredictor.Snapshot(
            pct = 67.0,
            savedAtMs = 0L,
            charging = true,
            whoop5Family = true,
        )
        val d = StrapBatteryPredictor.resolve(
            connected = true,
            livePct = 66.0,
            liveCharging = null,
            batteryFreshCount = 1,
            linkUpAtMs = 0L,
            snapshot = snap,
            nowMs = StrapBatteryPredictor.FRESH_CHARGING_SNAPSHOT_MS + 1L,
        )!!
        assertFalse(d.charging)
    }

    @Test fun resolveFreshChargingSnapshot_keepsBoltDuringFlap() {
        val snap = StrapBatteryPredictor.Snapshot(
            pct = 67.0,
            savedAtMs = 0L,
            charging = true,
            whoop5Family = true,
        )
        val d = StrapBatteryPredictor.resolve(
            connected = true,
            livePct = 67.0,
            liveCharging = null,
            batteryFreshCount = 1,
            linkUpAtMs = 0L,
            snapshot = snap,
            // Still PREDICTED (under LIVE_TRUST_AFTER_MS) + within fresh charging window.
            nowMs = 10_000L,
        )!!
        assertEquals(StrapBatteryPredictor.Source.PREDICTED, d.source)
        assertTrue(d.charging)
    }
}
