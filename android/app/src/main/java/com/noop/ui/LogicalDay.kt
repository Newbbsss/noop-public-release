package com.noop.ui

import com.noop.data.DailyMetric
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The "logical day" key the dashboard treats as Today.
 *
 * A naive `LocalDate.now()` rolls the moment the clock passes midnight, so between 00:00 and the
 * morning the dashboard would look up a brand-new calendar day that has no banked row yet and blank
 * out — even though the user is still in the same wear/sleep cycle as the previous evening (#144).
 *
 * The logical day rolls at [rolloverHour] (04:00 LOCAL) instead: it is the calendar date of
 * `now - rolloverHour hours`, so the small hours after midnight still resolve to the PRIOR calendar
 * date's row. This is a PRESENTATION-layer remap only — used purely to pick which stored row is
 * "Today" and to anchor the Today HR-trend window. Stored row keys are never rewritten (they stay
 * keyed on their own true calendar date), so the blast radius is deliberately tiny. An explicit
 * date label stays visible under the header so the remap is always honest.
 *
 * Pure + injectable so [LogicalDayTest] can pin the boundaries:
 *  - 23:59 → same calendar day (still the evening's logical day)
 *  - 01:00 → previous calendar day (the night still belongs to yesterday)
 *  - 04:01 → the new calendar day (a fresh logical day has begun)
 */
internal fun logicalDay(
    now: ZonedDateTime,
    rolloverHour: Int = LOGICAL_DAY_ROLLOVER_HOUR,
): LocalDate = now.minusHours(rolloverHour.toLong()).toLocalDate()

/** Convenience overload for the live call sites: the logical day for the current instant in [zone]. */
internal fun logicalDayNow(
    zone: ZoneId = ZoneId.systemDefault(),
    rolloverHour: Int = LOGICAL_DAY_ROLLOVER_HOUR,
): LocalDate = logicalDay(ZonedDateTime.now(zone), rolloverHour)

/** ISO `yyyy-MM-dd` key for the current logical day — matches how [DailyMetric.day] is stored. */
internal fun logicalDayKeyNow(
    zone: ZoneId = ZoneId.systemDefault(),
    rolloverHour: Int = LOGICAL_DAY_ROLLOVER_HOUR,
): String = logicalDayNow(zone, rolloverHour).toString()

/**
 * Start-of-logical-day as an epoch second in [zone] — the anchor for the Today HR-trend window so it
 * spans from the logical day's 00:00 (its real calendar midnight) rather than restarting at the new
 * calendar midnight while we're still showing yesterday's logical day in the small hours. (#144)
 */
internal fun logicalDayStartEpochSecond(
    now: ZonedDateTime,
    zone: ZoneId = now.zone,
    rolloverHour: Int = LOGICAL_DAY_ROLLOVER_HOUR,
): Long = logicalDay(now, rolloverHour).atStartOfDay(zone).toEpochSecond()

/**
 * Pure resolver behind the dashboard's "today" row (#304), extracted so the boundary is testable
 * without a live clock. Prefer the LOCAL-calendar-day row when it differs from the logical day AND has a
 * banked night (totalSleepMin != null) — the non-UTC pre-04:00 case, where the just-finished night is
 * banked under the new local calendar day while [logicalKey] still points at yesterday. Otherwise fall
 * back to the logical-day row, preserving the #144 anti-blank guard (never blank when a night isn't
 * banked yet). [localKey] == [logicalKey] (the common daytime case) collapses to the plain logical
 * lookup. Mirrors Swift Repository.resolveToday.
 */
internal fun resolveTodayRow(days: List<DailyMetric>, logicalKey: String, localKey: String): DailyMetric? {
    if (localKey != logicalKey) {
        days.lastOrNull { it.day == localKey && it.totalSleepMin != null }?.let { return it }
    }
    return days.lastOrNull { it.day == logicalKey }
}

/**
 * Day key for Today offset-0 (and past offsets).
 *
 * Prefer the ViewModel's resolved [todayRowDay] when it matches or is *ahead of* the logical
 * anchor (#304 pre-04:00 local wake-day). If [todayRowDay] is *behind* the logical key (stale
 * `_today` after a quiet overnight rollover before [refreshLogicalDayAnchor] rewrote it), stay on
 * the logical key so Charge / Effort / Rest never bind to yesterday while the header says Today.
 */
internal fun resolvedSelectedDayKey(
    selectedDayOffset: Int,
    selectedDay: LocalDate,
    todayRowDay: String?,
): String {
    if (selectedDayOffset != 0) return selectedDay.toString()
    val logical = selectedDay.toString()
    val resolved = todayRowDay ?: return logical
    return if (resolved >= logical) resolved else logical
}

/**
 * #911: the SINGLE anchor the home-screen widget push resolves the row it describes through, from BOTH
 * producers (the in-app republish in AppViewModel AND the background-service producer in
 * WhoopConnectionService), so the two can never drift apart. Pure + testable without a live clock.
 *
 * It is exactly what the dashboard does: resolve today's row ([resolveTodayRow], which carries the #304
 * pre-04:00 local-day carve-out and the #144 anti-blank guard), then use that row when it's scored, else
 * carry over the freshest STRICTLY-PRIOR scored day for the recovery-derived fields. Anchoring on today's
 * row (not "the newest row with any recovery score") is what fixes the rollover drift: the new logical
 * day exists but isn't scored yet, so a naive `days.lastOrNull { recovery != null }` kept pointing at
 * yesterday's scored row while Today had already moved on. The `it.day < anchorKey` bound ([anchorKey] =
 * today's own key) mirrors [lastScoredRecoveryDay] + its #547 future-day guard, so a stale or stray
 * future-dated scored row can never re-surface AS today. Mirrors Swift Repository.widgetAnchor.
 */
internal fun widgetAnchorRow(days: List<DailyMetric>, logicalKey: String, localKey: String): DailyMetric? {
    val todayRow = resolveTodayRow(days, logicalKey, localKey)
    if (todayRow?.recovery != null) return todayRow
    val anchorKey = todayRow?.day ?: logicalKey
    return days.lastOrNull { it.recovery != null && it.day < anchorKey }
}

/**
 * The freshest STRICTLY-PRIOR row that carries a real overnight VITAL (HRV / resting-HR / respiratory),
 * regardless of whether that night was recovery-scored. This is the carry-over the overnight-vitals
 * read-outs use, kept SEPARATE from [widgetAnchorRow] / [lastScoredRecoveryDay] (which are recovery-gated).
 *
 * HRV / resting-HR / respiratory exist independently of a recovery score: a post-update re-analysis can
 * null last night's recovery while PRESERVING its real avgHrv/restingHr. A recovery-gated whole-row carry
 * then skips that night and surfaces an OLDER scored day's numbers (or "No Data" if the older row lacks
 * the vital), which is wrong. Selecting the last row with ANY of the three vitals, bounded strictly before
 * [todayKey], keeps last night's OWN vitals in view. Pure + testable; days is oldest→newest. The
 * `it.day < todayKey` bound mirrors [widgetAnchorRow]'s future-day guard, so a stray future-dated row (a
 * bad strap clock) can never surface. Mirrors Swift Repository.lastVitalsDay.
 */
internal fun lastVitalsRow(days: List<DailyMetric>, todayKey: String): DailyMetric? =
    days.lastOrNull { (it.avgHrv != null || it.restingHr != null || it.respRateBpm != null) && it.day < todayKey }

/** 04:00 local — the hour the logical day rolls. Between midnight and this hour, Today stays put. */
internal const val LOGICAL_DAY_ROLLOVER_HOUR: Int = 4

/** Exposed for symmetry / call-site readability (start of the rollover window). */
internal val LOGICAL_DAY_ROLLOVER_TIME: LocalTime = LocalTime.of(LOGICAL_DAY_ROLLOVER_HOUR, 0)

/**
 * Evening hour that opens a wake-day's overnight bout window (prior day 18:00 → wake day 18:00).
 * Also the soft ceiling: after this hour on a calendar day with no bout yet, presentation follows
 * [logicalDay] again (new evening cycle) instead of extending "yesterday" forever.
 */
internal const val AWAKE_SPAN_EVENING_HOUR: Int = 18

/** Minimum session length (seconds) to count as tonight's overnight bout; shorter daytime naps are ignored. */
internal const val AWAKE_SPAN_MIN_BOUT_SEC: Long = 90L * 60L

/**
 * True when any sleep block belongs to [wakeDay]'s overnight bout: started after the prior evening
 * ([AWAKE_SPAN_EVENING_HOUR]) through wakeDay's evening, or ended on wakeDay before that evening.
 * Short daytime-onset naps are ignored so a 20-minute sit does not clear the awake-past-midnight span.
 */
internal fun hasOvernightBoutForWakeDay(
    wakeDay: LocalDate,
    sessions: List<Pair<Long, Long>>,
    zone: ZoneId,
    eveningHour: Int = AWAKE_SPAN_EVENING_HOUR,
    minBoutSec: Long = AWAKE_SPAN_MIN_BOUT_SEC,
): Boolean {
    val eveningStart = wakeDay.minusDays(1).atTime(eveningHour, 0).atZone(zone).toEpochSecond()
    val nextEvening = wakeDay.atTime(eveningHour, 0).atZone(zone).toEpochSecond()
    val wakeDayStart = wakeDay.atStartOfDay(zone).toEpochSecond()
    return sessions.any { (start, end) ->
        if (end < start) return@any false
        val dur = end - start
        if (dur < minBoutSec) {
            val hour = java.time.Instant.ofEpochSecond(start).atZone(zone).toLocalTime().hour
            // Short block only counts with overnight-ish onset (not a daytime nap).
            if (hour in 8 until 20) return@any false
        }
        val startedInWindow = start in eveningStart until nextEvening
        val endedOnWakeMorning = end in wakeDayStart until nextEvening
        startedInWindow || endedOnWakeMorning
    }
}

/**
 * Presentation "today" while still awake after calendar midnight (Gilbert): keep the prior calendar
 * day until tonight's overnight bout begins. Once a bout exists (or the new evening has started),
 * fall back to [logicalDay] (04:00 rollover for early risers).
 *
 * [wakeDayHasBankedNight] covers the morning-after case where the overnight session is already
 * scored on the calendar wake-day (Charge ready) but the live session merge briefly lags — without
 * it, presentation stayed on yesterday until 18:00 and Yesterday skipped a day.
 *
 * Pure + injectable for [LogicalDayTest] / awake-span tests.
 */
internal fun awakePresentationDay(
    now: ZonedDateTime,
    hasTonightOvernightBout: Boolean,
    wakeDayHasBankedNight: Boolean = false,
    rolloverHour: Int = LOGICAL_DAY_ROLLOVER_HOUR,
    eveningHour: Int = AWAKE_SPAN_EVENING_HOUR,
): LocalDate {
    val t = now.toLocalTime()
    val boutOrBanked = hasTonightOvernightBout || wakeDayHasBankedNight
    if (!boutOrBanked && t < LocalTime.of(eveningHour, 0)) {
        return now.toLocalDate().minusDays(1)
    }
    return logicalDay(now, rolloverHour)
}

/** ISO key for [awakePresentationDay]. */
internal fun awakePresentationDayKey(
    now: ZonedDateTime,
    hasTonightOvernightBout: Boolean,
    wakeDayHasBankedNight: Boolean = false,
    rolloverHour: Int = LOGICAL_DAY_ROLLOVER_HOUR,
    eveningHour: Int = AWAKE_SPAN_EVENING_HOUR,
): String = awakePresentationDay(
    now, hasTonightOvernightBout, wakeDayHasBankedNight, rolloverHour, eveningHour,
).toString()

/**
 * Window start for HR / Effort / day-span charts: midnight of the presentation day, so after
 * calendar midnight while still awake the curve keeps yesterday → now instead of blanking.
 */
internal fun awakeSpanStartEpochSecond(
    now: ZonedDateTime,
    hasTonightOvernightBout: Boolean,
    wakeDayHasBankedNight: Boolean = false,
    zone: ZoneId = now.zone,
    rolloverHour: Int = LOGICAL_DAY_ROLLOVER_HOUR,
    eveningHour: Int = AWAKE_SPAN_EVENING_HOUR,
): Long = awakePresentationDay(
    now, hasTonightOvernightBout, wakeDayHasBankedNight, rolloverHour, eveningHour,
)
    .atStartOfDay(zone)
    .toEpochSecond()

/**
 * True in the post-midnight awake continuation: UI should label the span as prior-day → now
 * (Language owns final copy; logic only exposes the gate).
 */
internal fun extendsAwakePastMidnight(
    now: ZonedDateTime,
    hasTonightOvernightBout: Boolean,
    wakeDayHasBankedNight: Boolean = false,
    eveningHour: Int = AWAKE_SPAN_EVENING_HOUR,
): Boolean = !hasTonightOvernightBout &&
    !wakeDayHasBankedNight &&
    now.toLocalTime() < LocalTime.of(eveningHour, 0)

/**
 * Whether a daily row is worth listing as its own navigable day (Charge / sleep / effort / vitals).
 * Empty calendar stubs (UTC twin, post-midnight blank, import placeholder) are dropped so the
 * day strip does not show two Fri/Sat/Sun labels for one real day.
 */
internal fun isMeaningfulDayRow(m: DailyMetric): Boolean =
    m.totalSleepMin != null ||
        m.recovery != null ||
        (m.strain != null && m.strain > 0.0) ||
        m.avgHrv != null ||
        m.restingHr != null ||
        m.respRateBpm != null ||
        (m.steps != null && m.steps > 0) ||
        (m.exerciseCount != null && m.exerciseCount > 0)

/**
 * Offsets (days back from [anchor]) for banked days that carry real data, always including 0.
 * Dedupes by calendar key so a stub twin cannot mint a second weekday mark.
 */
internal fun loggedDayOffsetsFromBank(
    days: List<DailyMetric>,
    anchor: LocalDate,
): List<Int> {
    val fromBank = days.mapNotNull { m ->
        if (!isMeaningfulDayRow(m)) return@mapNotNull null
        val d = runCatching { LocalDate.parse(m.day) }.getOrNull() ?: return@mapNotNull null
        java.time.temporal.ChronoUnit.DAYS.between(d, anchor).toInt().takeIf { it >= 0 }
    }
    return (fromBank + 0).distinct().sorted()
}

/**
 * Subtitle under the Today header title. When the title is already the weekday name (offset ≥ 2),
 * omit the repeated EEEE so Gilbert does not see a large "Friday" over a small "Friday, …".
 */
internal fun todayHeaderHumanDate(
    selectedDayOffset: Int,
    keyDate: LocalDate,
    locale: java.util.Locale = java.util.Locale.getDefault(),
): String {
    val pattern = if (selectedDayOffset >= 2) "d MMMM" else "EEEE, d MMMM"
    return keyDate.format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
}
