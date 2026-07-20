package com.noop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Process-lifetime UI flags (not persisted). Cleared on process death.
 */
object SessionUiFlags {
    /** Set when Devices is opened once this session — dims Today NeedsStrap Pair CTA (#289). */
    @Volatile
    var devicesOpenedThisSession: Boolean = false

    /**
     * One-shot: Today avatar → Settings should land on Profile (photo + body), not mid-list.
     * Consumed by [SettingsScreen] on enter.
     */
    @Volatile
    var settingsFocusProfile: Boolean = false

    /**
     * One-shot: Cycle / Health awareness → Settings should land on Health & wellness
     * (Period tracking master on/off). Consumed by [SettingsScreen] on enter.
     */
    @Volatile
    var settingsFocusPeriodTracking: Boolean = false

    /** SHIP #6 — one quiet toast after armed release with no sector (per process). */
    @Volatile
    var holdPlusCancelToastShown: Boolean = false

    /**
     * SHIP #72 — one-shot: Sleep Import CTA / Sources should open the WHOOP export picker
     * on Data Sources entry (not just land on the root list). Consumed by [DataSourcesScreen].
     */
    @Volatile
    var autoLaunchWhoopImport: Boolean = false

    /**
     * One-shot: Today Quick Alarm / Automations / More search should land on Sleep → Alarm
     * (not a separate Wake settings push). Consumed by [SleepScreen] while composed —
     * [mutableStateOf] so a flag set while already resumed on Sleep still recomposes.
     */
    var openSleepAlarmTab by mutableStateOf(false)

    /**
     * One-shot: FGS strap notification (5AM sibling honesty) → Devices so Use worn MG is one tap away.
     * Consumed by [NoopRoot] / AppRoot. [mutableStateOf] so a flag set while already resumed still navigates.
     */
    var openDevicesOnce by mutableStateOf(false)
}
