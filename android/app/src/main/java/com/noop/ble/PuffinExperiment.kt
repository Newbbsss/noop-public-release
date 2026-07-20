package com.noop.ble

import android.content.Context
import android.content.SharedPreferences

/**
 * Opt-in switch for the EXPERIMENTAL WHOOP 5.0/MG ("puffin") protocol probes.
 *
 * Direct port of the macOS `PuffinExperiment` (Strand/BLE/PuffinExperiment.swift). Live HR on a
 * 5/MG strap already works over the standard profile after CLIENT_HELLO. These probes go further —
 * sending puffin-framed commands (e.g. asking the strap to start its realtime stream) to learn what
 * a real 5/MG strap responds to. They are guesses, so they are OFF by default and only ever written
 * to the puffin command characteristic (fd4b0002). A 5/MG owner can flip this on under Settings →
 * Experimental to help map the protocol; everyone else is unaffected. It never touches WHOOP 4.0.
 *
 * The macOS app stored this in `UserDefaults` under the key `noopPuffinExperiments`; the Android
 * equivalent is [SharedPreferences]. The same key name is reused for parity.
 */
class PuffinExperiment(private val prefs: SharedPreferences) {

    /** True if the user opted in to the WHOOP 5/MG protocol probes (default false). */
    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY, false)
        set(v) = prefs.edit().putBoolean(KEY, v).apply()

    /** True if the user opted in to recording raw 5/MG backfill frames to a shareable JSONL file
     *  (default false). SEPARATE from [isEnabled]: probes SEND commands at the strap; capture only
     *  RECORDS what arrives — different risk profiles, so different switches. (#78 fork) */
    var isCaptureEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAPTURE, false)
        set(v) = prefs.edit().putBoolean(KEY_CAPTURE, v).apply()

    /** True if the user opted in to the WHOOP 5/MG "R22" deep-data unlock — the one probe that WRITES
     *  a persistent feature flag to the strap (the `enable_r22_*` SET_CONFIG sequence). Kept distinct
     *  from [isEnabled] because it changes strap state; reversible, default false. Mirrors the macOS
     *  `PuffinExperiment.deepDataKey`. Driven only from `WhoopBleClient.enableWhoop5DeepData()`. (#174) */
    var isDeepDataEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEEP_DATA, false)
        set(v) = prefs.edit().putBoolean(KEY_DEEP_DATA, v).apply()

    /** True if the user opted in to "Broadcast heart rate": NOOP writes the device-config flag
     *  whoop_live_hr_in_adv_ind_pkt="1" so the strap advertises the standard Heart Rate Service
     *  (0x180D) + its live HR, pairable by a Garmin/Zwift/gym HR client. Reversible. Default false.
     *  Mirrors the macOS `PuffinExperiment.broadcastHrKey`. (#181) */
    var broadcastHr: Boolean
        get() = prefs.getBoolean(KEY_BROADCAST_HR, false)
        set(v) = prefs.edit().putBoolean(KEY_BROADCAST_HR, v).apply()

    /** True if the user opted in to "Experimental sleep staging (V2)": detected nights are re-staged with
     *  [com.noop.analytics.SleepStagerV2] (transparent cardiorespiratory recipe) instead of the default
     *  Gilbert V1 [com.noop.analytics.SleepStager]. Pure analysis switch — changes ONLY which staging
     *  engine runs over an already-detected sleep window. **Trust path defaults OFF** for every strap
     *  family (MG and non-MG); V2 is research-only and can invent Deep/REM. WHOOP 4.0 stays hard-gated
     *  off in [IntelligenceEngine.sleepStagerV2ForFamily] even if this is on.
     *  One-shot [KEY_V2_TRUST_DEFAULT_APPLIED] forces OFF once so prior Fable installs that were
     *  migrated on leave the trust path without a manual Diagnostics tap; later user toggles stick. */
    var experimentalSleepV2: Boolean
        get() {
            if (!prefs.getBoolean(KEY_V2_TRUST_DEFAULT_APPLIED, false)) {
                prefs.edit()
                    .putBoolean(KEY_EXPERIMENTAL_SLEEP_V2, false)
                    .putBoolean(KEY_V2_TRUST_DEFAULT_APPLIED, true)
                    .putBoolean(KEY_V2_FABLE_DEFAULT_APPLIED, true) // retire old force-on marker
                    .apply()
            }
            return prefs.getBoolean(KEY_EXPERIMENTAL_SLEEP_V2, false)
        }
        set(v) = prefs.edit().putBoolean(KEY_EXPERIMENTAL_SLEEP_V2, v).apply()

    companion object {
        /** Persisted preferences file. */
        private const val PREFS = "noop_experiments"

        /** Shared key name with the macOS build (`PuffinExperiment.defaultsKey`). */
        const val KEY = "noopPuffinExperiments"

        /** 5/MG raw backfill capture (research aid for the puffin biometric decode). */
        const val KEY_CAPTURE = "noopWhoop5Capture"

        /** 5/MG R22 deep-data unlock opt-in (mirrors macOS `PuffinExperiment.deepDataKey`). */
        const val KEY_DEEP_DATA = "noopWhoop5DeepData"

        /** "Broadcast heart rate" opt-in (mirrors macOS `PuffinExperiment.broadcastHrKey`). */
        const val KEY_BROADCAST_HR = "noopBroadcastHr"

        /** "Experimental sleep staging (V2)" opt-in (mirrors macOS `PuffinExperiment.experimentalSleepV2Key`). */
        const val KEY_EXPERIMENTAL_SLEEP_V2 = "noopExperimentalSleepV2"

        /** Retired: prior Fable one-shot that forced V2 on. Kept so we can mark it applied. */
        private const val KEY_V2_FABLE_DEFAULT_APPLIED = "noopExperimentalSleepV2_fable76"

        /** One-shot: force V2 off for Gilbert trustworthy scoring (8.6.233+). */
        private const val KEY_V2_TRUST_DEFAULT_APPLIED = "noopExperimentalSleepV2_trust233"

        fun from(context: Context): PuffinExperiment =
            PuffinExperiment(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
