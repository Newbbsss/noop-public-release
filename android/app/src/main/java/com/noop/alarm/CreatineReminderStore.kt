package com.noop.alarm

import android.content.Context
import android.content.SharedPreferences

/**
 * Opt-in daily creatine reminder — local only, no cloud.
 * Default OFF; hour-of-day defaults to 09:00.
 */
class CreatineReminderStore(private val prefs: SharedPreferences) {

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    /** Hour of day 0–23 for the daily nudge. */
    var hourOfDay: Int
        get() = prefs.getInt(KEY_HOUR, DEFAULT_HOUR).coerceIn(0, 23)
        set(v) = prefs.edit().putInt(KEY_HOUR, v.coerceIn(0, 23)).apply()

    fun minuteOfDay(): Int = hourOfDay * 60

    companion object {
        private const val PREFS = "noop_creatine_remind"
        private const val KEY_ENABLED = "creatineRemind.enabled"
        private const val KEY_HOUR = "creatineRemind.hour"
        const val DEFAULT_HOUR = 9

        fun from(context: Context): CreatineReminderStore =
            CreatineReminderStore(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
