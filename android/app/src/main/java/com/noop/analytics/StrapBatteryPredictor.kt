package com.noop.analytics

/**
 * Cold-open / disconnected strap battery display (#battery-always-on).
 *
 * UI used to blank SoC whenever `!connected` — so process death and a dropped link showed an empty
 * ring even when Room/prefs still held the last reading. This predictor paints last-known SoC, aged
 * by a conservative linear drain from [BatteryEstimator] rated life, until a live BLE session has
 * been up long enough (or banked enough fresh samples) to trust the live percent.
 *
 * Distinct from recovery **Charge** score — this is device pack SoC only.
 */
object StrapBatteryPredictor {

    /** Prefer live SoC after this many fresh readings this link. */
    const val LIVE_MIN_SAMPLES = 2

    /** Or after this dwell with ≥1 fresh reading (ms). */
    const val LIVE_TRUST_AFTER_MS = 20_000L

    /**
     * How long a snapshot saved while charging may still paint the bolt / hold SoC when live
     * charging is unknown (BLE flap mid-charge). After this, treat as unplugged — otherwise a
     * sticky `snapshot.charging=true` from an earlier dock falsely shows “on charger” after unplug.
     */
    const val FRESH_CHARGING_SNAPSHOT_MS = 120_000L

    /** Hold predicted SoC without drain at most this long after a charging save. */
    const val CHARGING_HOLD_MAX_MS = 30L * 60_000L

    data class Snapshot(
        /** Last observed SoC, 0–100. */
        val pct: Double,
        /** Wall-clock ms when [pct] was saved. */
        val savedAtMs: Long,
        /** True when the strap reported charging at save time — hold SoC (do not drain downward). */
        val charging: Boolean = false,
        /** Rated-life family for drain rate (5/MG vs 4/3). */
        val whoop5Family: Boolean = true,
    )

    enum class Source {
        /** Live BLE SoC after the session is trusted. */
        LIVE,
        /** Aged from last saved SoC (or held while last known charging). */
        PREDICTED,
        /** Connected but not yet trusted — show live early only when no snapshot exists. */
        LIVE_EARLY,
    }

    data class Display(
        val pct: Double,
        val charging: Boolean,
        val source: Source,
    ) {
        val pctInt: Int get() = pct.roundToIntCoerced()
    }

    /**
     * Linear drain from last saved SoC using generation rated life (%/h = 100 / ratedHours).
     * When [Snapshot.charging] was true at save, hold the saved percent (do not invent a charge curve).
     * Clamped 0–100. Always returns a value when [snap] is non-null.
     */
    fun predict(snap: Snapshot, nowMs: Long = System.currentTimeMillis()): Double {
        val soc = snap.pct.coerceIn(0.0, 100.0)
        val holdCharging = snap.charging &&
            (nowMs - snap.savedAtMs) in 0L..CHARGING_HOLD_MAX_MS
        if (holdCharging) return soc
        val ratedH = BatteryEstimator.ratedLifeHours(snap.whoop5Family).coerceAtLeast(1.0)
        val ratePerHour = 100.0 / ratedH
        val elapsedH = ((nowMs - snap.savedAtMs).coerceAtLeast(0L)) / 3_600_000.0
        return (soc - ratePerHour * elapsedH).coerceIn(0.0, 100.0)
    }

    /** Charging chrome from a snapshot only while the save is still fresh (mid-charge flap). */
    fun snapshotShowsCharging(snap: Snapshot, nowMs: Long): Boolean =
        snap.charging && (nowMs - snap.savedAtMs) in 0L..FRESH_CHARGING_SNAPSHOT_MS

    /** True when a live session has enough fresh SoC to replace the prediction. */
    fun liveTrusted(
        connected: Boolean,
        livePct: Double?,
        batteryFreshCount: Int,
        linkUpAtMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!connected || livePct == null) return false
        if (batteryFreshCount >= LIVE_MIN_SAMPLES) return true
        if (batteryFreshCount >= 1 && linkUpAtMs != null &&
            nowMs - linkUpAtMs >= LIVE_TRUST_AFTER_MS
        ) {
            return true
        }
        return false
    }

    /**
     * Resolve what the UI should paint.
     *
     * - Trusted live → live SoC + live charging bit.
     * - Else snapshot → predicted (or held) SoC; charging only if snapshot was charging AND still
     *   connected (disconnected never shows a live bolt from a stale bit).
     * - Else connected with a live reading but no snapshot → early live (better than blank).
     * - Else null (never paired / never saved).
     */
    fun resolve(
        connected: Boolean,
        livePct: Double?,
        liveCharging: Boolean?,
        batteryFreshCount: Int,
        linkUpAtMs: Long?,
        snapshot: Snapshot?,
        nowMs: Long = System.currentTimeMillis(),
    ): Display? {
        if (liveTrusted(connected, livePct, batteryFreshCount, linkUpAtMs, nowMs)) {
            return Display(
                pct = livePct!!.coerceIn(0.0, 100.0),
                charging = liveCharging == true,
                source = Source.LIVE,
            )
        }
        if (snapshot != null) {
            val predicted = predict(snapshot, nowMs)
            return Display(
                pct = predicted,
                // Live bit wins. Unknown live: only a *fresh* charging snapshot (BLE flap while
                // still docked) — never a stale overnight bit after unplug.
                charging = when {
                    liveCharging == true -> true
                    liveCharging == false -> false
                    connected && snapshotShowsCharging(snapshot, nowMs) -> true
                    else -> false
                },
                source = Source.PREDICTED,
            )
        }
        if (connected && livePct != null) {
            return Display(
                pct = livePct.coerceIn(0.0, 100.0),
                charging = liveCharging == true,
                source = Source.LIVE_EARLY,
            )
        }
        return null
    }

    private fun Double.roundToIntCoerced(): Int =
        kotlin.math.round(this.coerceIn(0.0, 100.0)).toInt()
}
