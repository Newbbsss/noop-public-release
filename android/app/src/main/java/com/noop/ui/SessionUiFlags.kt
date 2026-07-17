package com.noop.ui

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

    /** SHIP #6 — one quiet toast after armed release with no sector (per process). */
    @Volatile
    var holdPlusCancelToastShown: Boolean = false

    /**
     * SHIP #72 — one-shot: Sleep Import CTA / Sources should open the WHOOP export picker
     * on Data Sources entry (not just land on the root list). Consumed by [DataSourcesScreen].
     */
    @Volatile
    var autoLaunchWhoopImport: Boolean = false
}
