package com.noop.ui

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Shared day / night browse bus helpers (Fable 200 #19 / #31).
 *
 * Today browses by days-back-from-today; Sleep browses by newest-first night index.
 * Both publish/consume a local wake-day key (`yyyy-MM-dd`) via [AppViewModel.browseDayKey]
 * so Rest→Sleep and tab switches land on the same calendar day.
 */
object SharedDayBrowse {

    /**
     * Days-back offset from [today] for a wake-day key; null if unparsable or in the future.
     *
     * Default [today] is the **logical** day ([logicalDayNow]), not [LocalDate.now]. Today’s
     * `selectedDayOffset` is anchored on the logical day (04:00 rollover). Using calendar
     * midnight here after a process that published a logical key made `offset = calendar−logical`
     * (1 between 00:00–04:00), which rewrote the offset, republished an older key, and ran away
     * through history until the UI thrashed / crashed.
     */
    fun offsetFromToday(dayKey: String, today: LocalDate = logicalDayNow()): Int? {
        val d = runCatching { LocalDate.parse(dayKey) }.getOrNull() ?: return null
        val days = ChronoUnit.DAYS.between(d, today).toInt()
        return if (days < 0) null else days
    }

    /**
     * Index into a newest-first list of wake-day keys (Sleep [navDays] attribution).
     * Returns -1 when [dayKey] is absent.
     */
    fun nightOffsetForDay(navDayKeysNewestFirst: List<String>, dayKey: String): Int =
        navDayKeysNewestFirst.indexOfFirst { it == dayKey }

    /** Local wake-day key for a session end timestamp (matches Sleep [localDayString]). */
    fun localWakeDayKey(endTsSec: Long): String {
        val offsetSec = java.util.TimeZone.getDefault().getOffset(endTsSec * 1000) / 1000L
        return com.noop.analytics.AnalyticsEngine.dayString(endTsSec, offsetSec)
    }

    /**
     * Sleep night index for a shared [browseDayKey]. Prefer newest (0) when browse is absent or
     * missing from [navDayKeysNewestFirst].
     *
     * After a morning wake, the night that ended today is banked under wake-day == [logicalToday],
     * but browse may still be yesterday (Sleep auto-published "last night" before today's session
     * landed). When [snapStalePrior] is true and the newest wake-day is on/after logical today while
     * browse is still prior, snap to newest so "Last night" is today's sleep — not Tuesday.
     *
     * Sleep sets [snapStalePrior] false after an explicit ◀ / date-picker browse so past nights stick
     * while today's night exists (8.6.138). Today day-dots keep the default snap for morning recovery.
     */
    fun sleepNightOffset(
        navDayKeysNewestFirst: List<String>,
        browseDayKey: String?,
        logicalToday: String = logicalDayKeyNow(),
        snapStalePrior: Boolean = true,
    ): Int {
        if (navDayKeysNewestFirst.isEmpty()) return 0
        if (browseDayKey == null) return 0
        val newest = navDayKeysNewestFirst.first()
        val idx = nightOffsetForDay(navDayKeysNewestFirst, browseDayKey)
        if (idx < 0) return 0
        // Stale prior wake-day while today's night (or newer) is already banked.
        if (snapStalePrior && idx > 0 && newest >= logicalToday && browseDayKey < logicalToday) return 0
        return idx
    }
}
