package com.noop.ui

import android.content.Context
import com.noop.analytics.StrapBatteryPredictor
import com.noop.ble.LiveState

/** Resolve painted strap SoC from live BLE + last-saved snapshot (never blank when saved). */
fun resolveStrapBatteryDisplay(
    context: Context,
    live: LiveState,
    nowMs: Long = System.currentTimeMillis(),
): StrapBatteryPredictor.Display? =
    StrapBatteryPredictor.resolve(
        connected = live.connected,
        livePct = live.batteryPct,
        liveCharging = live.charging,
        batteryFreshCount = live.batteryFreshCount,
        linkUpAtMs = live.linkUpAtMs,
        snapshot = NoopPrefs.strapBatterySnapshot(context),
        nowMs = nowMs,
    )

fun resolveStrapBatteryDisplay(
    context: Context,
    connected: Boolean,
    livePct: Double?,
    liveCharging: Boolean?,
    batteryFreshCount: Int,
    linkUpAtMs: Long?,
    nowMs: Long = System.currentTimeMillis(),
): StrapBatteryPredictor.Display? =
    StrapBatteryPredictor.resolve(
        connected = connected,
        livePct = livePct,
        liveCharging = liveCharging,
        batteryFreshCount = batteryFreshCount,
        linkUpAtMs = linkUpAtMs,
        snapshot = NoopPrefs.strapBatterySnapshot(context),
        nowMs = nowMs,
    )
