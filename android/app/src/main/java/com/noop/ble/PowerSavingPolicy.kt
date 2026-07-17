package com.noop.ble

/**
 * Strap-battery power saving (fork feature keyed like #477 product ask).
 *
 * When enabled and the connected strap is at/under the user threshold **and discharging**:
 * - stretch background history sync from 15 min → 45 min
 * - optionally release the always-on continuous-HRV stream (Live still arms on demand)
 *
 * Never active while charging. Off by default. Threshold clamped to 10–30%.
 * Pure policy — no Android / BLE deps (unit-tested).
 */
object PowerSavingPolicy {
    const val DEFAULT_INTERVAL_MS = 900_000L // 15 min
    const val STRETCHED_INTERVAL_MS = 2_700_000L // 45 min
    const val THRESHOLD_MIN = 10
    const val THRESHOLD_MAX = 30
    const val THRESHOLD_DEFAULT = 20

    fun clampThreshold(pct: Int): Int = pct.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX)

    /**
     * True when power-saving should apply right now.
     * [batteryPct] null → unknown SoC → never stretch (fail closed, keep normal cadence).
     * [charging] true → never; null treated as not charging (only explicit charging blocks).
     */
    fun isActive(
        enabled: Boolean,
        thresholdPct: Int,
        batteryPct: Double?,
        charging: Boolean?,
    ): Boolean {
        if (!enabled) return false
        if (charging == true) return false
        val soc = batteryPct ?: return false
        if (soc < 0.0 || soc > 100.0) return false
        return soc <= clampThreshold(thresholdPct).toDouble()
    }

    fun historySyncIntervalMs(
        enabled: Boolean,
        thresholdPct: Int,
        batteryPct: Double?,
        charging: Boolean?,
    ): Long =
        if (isActive(enabled, thresholdPct, batteryPct, charging)) {
            STRETCHED_INTERVAL_MS
        } else {
            DEFAULT_INTERVAL_MS
        }

    /**
     * When [releaseContinuousHrv] is on and power-saving is active, the continuous stream want
     * is forced off. Live screen / spot HRV still arm on demand.
     */
    fun continuousHrvAllowed(
        userWantsContinuous: Boolean,
        releaseContinuousWhenSaving: Boolean,
        enabled: Boolean,
        thresholdPct: Int,
        batteryPct: Double?,
        charging: Boolean?,
    ): Boolean {
        if (!userWantsContinuous) return false
        if (!releaseContinuousWhenSaving) return true
        return !isActive(enabled, thresholdPct, batteryPct, charging)
    }
}
