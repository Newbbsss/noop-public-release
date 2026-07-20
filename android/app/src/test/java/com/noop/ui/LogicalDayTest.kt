package com.noop.ui

import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Unit tests for [logicalDay], the pure helper behind the "Today must not blank at midnight" fix
 * (#144). The logical day rolls at 04:00 LOCAL, so between midnight and 4am the dashboard still
 * resolves to the prior calendar day's banked row instead of an empty new-calendar-day row.
 *
 * The three boundary cases the fix is judged on:
 *  - 23:59 → SAME calendar day (still the evening's logical day)
 *  - 01:00 → PREVIOUS calendar day (the small hours still belong to yesterday)
 *  - 04:01 → the NEW calendar day (a fresh logical day has begun)
 */
class LogicalDayTest {

    private val zone = ZoneId.of("UTC")

    /** Build a [ZonedDateTime] at the given wall-clock on 2026-06-12 in the test zone. */
    private fun at(hour: Int, minute: Int) =
        LocalDateTime.of(LocalDate.of(2026, 6, 12), LocalTime.of(hour, minute))
            .atZone(zone)

    @Test
    fun lateEveningStaysOnTheSameDay() {
        // 23:59 → 2026-06-12 (4h before is 19:59 same day).
        assertEquals(LocalDate.of(2026, 6, 12), logicalDay(at(23, 59)))
    }

    @Test
    fun afterMidnightBeforeRolloverIsThePreviousDay() {
        // 01:00 → 2026-06-11 (4h before is 21:00 the previous day).
        assertEquals(LocalDate.of(2026, 6, 11), logicalDay(at(1, 0)))
    }

    @Test
    fun justAfterRolloverIsTheNewDay() {
        // 04:01 → 2026-06-12 (4h before is 00:01, still the new calendar day).
        assertEquals(LocalDate.of(2026, 6, 12), logicalDay(at(4, 1)))
    }

    @Test
    fun exactlyAtRolloverIsTheNewDay() {
        // 04:00 → 2026-06-12 (4h before is exactly 00:00 — the boundary belongs to the new day).
        assertEquals(LocalDate.of(2026, 6, 12), logicalDay(at(4, 0)))
    }

    @Test
    fun justBeforeRolloverIsStillThePreviousDay() {
        // 03:59 → 2026-06-11 (4h before is 23:59 the previous day).
        assertEquals(LocalDate.of(2026, 6, 11), logicalDay(at(3, 59)))
    }

    @Test
    fun middayIsTheCurrentDay() {
        assertEquals(LocalDate.of(2026, 6, 12), logicalDay(at(12, 0)))
    }

    @Test
    fun midnightIsThePreviousDay() {
        // 00:00 → 2026-06-11: the instant the calendar rolls, the logical day must hold yesterday.
        assertEquals(LocalDate.of(2026, 6, 11), logicalDay(at(0, 0)))
    }

    @Test
    fun rolloverHourIsInjectable() {
        // With a 0-hour rollover the logical day is just the calendar day (no remap).
        val t = at(1, 0)
        assertEquals(LocalDate.of(2026, 6, 12), logicalDay(t, rolloverHour = 0))
    }

    @Test
    fun startOfLogicalDayAnchorsToThePriorMidnightInSmallHours() {
        // At 01:00 on the 12th the HR window must start at the 11th's 00:00, not the 12th's.
        val expected = LocalDate.of(2026, 6, 11).atStartOfDay(zone).toEpochSecond()
        assertEquals(expected, logicalDayStartEpochSecond(at(1, 0), zone))
    }

    @Test
    fun startOfLogicalDayAnchorsToTodayMidnightAfterRollover() {
        val expected = LocalDate.of(2026, 6, 12).atStartOfDay(zone).toEpochSecond()
        assertEquals(expected, logicalDayStartEpochSecond(at(9, 0), zone))
    }

    @Test
    fun resolvedSelectedDayKeyRejectsStalePriorTodayRow() {
        val logicalToday = LocalDate.of(2026, 7, 15)
        // Quiet overnight: `_today` still points at yesterday after logical advances.
        assertEquals(
            "2026-07-15",
            resolvedSelectedDayKey(0, logicalToday, todayRowDay = "2026-07-14"),
        )
    }

    @Test
    fun resolvedSelectedDayKeyKeepsPreRolloverLocalCarveOut() {
        // #304: local wake-day can sit ahead of logical before 04:00.
        val logicalYesterday = LocalDate.of(2026, 7, 14)
        assertEquals(
            "2026-07-15",
            resolvedSelectedDayKey(0, logicalYesterday, todayRowDay = "2026-07-15"),
        )
    }

    @Test
    fun resolvedSelectedDayKeyPastOffsetIgnoresTodayRow() {
        assertEquals(
            "2026-07-13",
            resolvedSelectedDayKey(2, LocalDate.of(2026, 7, 13), todayRowDay = "2026-07-15"),
        )
    }

    // ── Awake-past-midnight span (Gilbert: yesterday→now until overnight bout) ──────────────

    @Test
    fun awakePastMidnightWithoutBoutKeepsPriorCalendarDay() {
        // 01:00 Mon, still awake → Sunday.
        assertEquals(
            LocalDate.of(2026, 6, 11),
            awakePresentationDay(at(1, 0), hasTonightOvernightBout = false),
        )
        assertEquals(true, extendsAwakePastMidnight(at(1, 0), hasTonightOvernightBout = false))
    }

    @Test
    fun awakePastMidnightPastLogicalRolloverStillKeepsPriorDay() {
        // 05:00 Mon awake (past 04:00 logical) → still Sunday until bout.
        assertEquals(
            LocalDate.of(2026, 6, 11),
            awakePresentationDay(at(5, 0), hasTonightOvernightBout = false),
        )
        assertEquals(
            LocalDate.of(2026, 6, 12),
            logicalDay(at(5, 0)),
        )
    }

    @Test
    fun overnightBoutSwitchesToLogicalDay() {
        // 01:00 Mon with tonight's bout → logical (prior until 04:00).
        assertEquals(
            LocalDate.of(2026, 6, 11),
            awakePresentationDay(at(1, 0), hasTonightOvernightBout = true),
        )
        assertEquals(false, extendsAwakePastMidnight(at(1, 0), hasTonightOvernightBout = true))
        // 05:00 Mon with bout → new logical day.
        assertEquals(
            LocalDate.of(2026, 6, 12),
            awakePresentationDay(at(5, 0), hasTonightOvernightBout = true),
        )
    }

    @Test
    fun bankedWakeDayNightClearsAwakeSpanWithoutSession() {
        // 07:00 Mon: session merge lagged, but wake-day already has totalSleepMin → new Charge day.
        assertEquals(
            LocalDate.of(2026, 6, 12),
            awakePresentationDay(
                at(7, 0),
                hasTonightOvernightBout = false,
                wakeDayHasBankedNight = true,
            ),
        )
        assertEquals(
            false,
            extendsAwakePastMidnight(
                at(7, 0),
                hasTonightOvernightBout = false,
                wakeDayHasBankedNight = true,
            ),
        )
    }

    @Test
    fun todayHeaderHumanDateOmitsRepeatedWeekday() {
        val fri = LocalDate.of(2026, 7, 17) // Friday
        assertEquals(
            "Friday, 17 July",
            todayHeaderHumanDate(0, fri, java.util.Locale.UK),
        )
        assertEquals(
            "17 July",
            todayHeaderHumanDate(2, fri, java.util.Locale.UK),
        )
    }

    @Test
    fun loggedDayOffsetsDropsEmptyStubs() {
        val anchor = LocalDate.of(2026, 7, 20)
        val days = listOf(
            DailyMetric(deviceId = "t", day = "2026-07-20", recovery = 70.0),
            DailyMetric(deviceId = "t", day = "2026-07-19"), // empty stub
            DailyMetric(deviceId = "t", day = "2026-07-18", totalSleepMin = 420.0),
        )
        assertEquals(listOf(0, 2), loggedDayOffsetsFromBank(days, anchor))
    }

    @Test
    fun eveningWithoutBoutUsesCalendarLogicalDay() {
        // 20:00 — new evening cycle; do not extend to "yesterday".
        assertEquals(
            LocalDate.of(2026, 6, 12),
            awakePresentationDay(at(20, 0), hasTonightOvernightBout = false),
        )
        assertEquals(false, extendsAwakePastMidnight(at(20, 0), hasTonightOvernightBout = false))
    }

    /** Fri→Sat→Sun: weekend awake span must not invent a third day or skip Saturday. */
    @Test
    fun weekendAwakeSpanFriSatSunDoesNotDuplicateOrSkip() {
        // Friday 2026-07-17 23:00 — still Friday (evening cycle, not past midnight).
        val friEve = LocalDateTime.of(LocalDate.of(2026, 7, 17), LocalTime.of(23, 0)).atZone(zone)
        assertEquals(LocalDate.of(2026, 7, 17), awakePresentationDay(friEve, hasTonightOvernightBout = false))
        assertEquals(false, extendsAwakePastMidnight(friEve, hasTonightOvernightBout = false))

        // Saturday 01:30 still awake → Friday (yesterday→now), not a blank Sat stub.
        val satEarly = LocalDateTime.of(LocalDate.of(2026, 7, 18), LocalTime.of(1, 30)).atZone(zone)
        assertEquals(LocalDate.of(2026, 7, 17), awakePresentationDay(satEarly, hasTonightOvernightBout = false))
        assertEquals(true, extendsAwakePastMidnight(satEarly, hasTonightOvernightBout = false))

        // Sunday 02:00 still awake (no Sat overnight bout) → Saturday.
        val sunEarly = LocalDateTime.of(LocalDate.of(2026, 7, 19), LocalTime.of(2, 0)).atZone(zone)
        assertEquals(LocalDate.of(2026, 7, 18), awakePresentationDay(sunEarly, hasTonightOvernightBout = false))
        assertEquals(true, extendsAwakePastMidnight(sunEarly, hasTonightOvernightBout = false))

        // Sunday 07:00 with banked wake-night → Sunday Charge day (Wake→new Charge).
        val sunMorning = LocalDateTime.of(LocalDate.of(2026, 7, 19), LocalTime.of(7, 0)).atZone(zone)
        assertEquals(
            LocalDate.of(2026, 7, 19),
            awakePresentationDay(sunMorning, hasTonightOvernightBout = false, wakeDayHasBankedNight = true),
        )
    }

    @Test
    fun spanStartAnchorsToPriorMidnightWhileAwake() {
        val expected = LocalDate.of(2026, 6, 11).atStartOfDay(zone).toEpochSecond()
        assertEquals(
            expected,
            awakeSpanStartEpochSecond(at(2, 30), hasTonightOvernightBout = false, zone = zone),
        )
    }

    @Test
    fun hasOvernightBoutDetectsWakeDaySession() {
        val wake = LocalDate.of(2026, 6, 12)
        val start = wake.minusDays(1).atTime(23, 0).atZone(zone).toEpochSecond()
        val end = wake.atTime(7, 0).atZone(zone).toEpochSecond()
        assertEquals(true, hasOvernightBoutForWakeDay(wake, listOf(start to end), zone))
        assertEquals(false, hasOvernightBoutForWakeDay(wake, emptyList(), zone))
    }

    @Test
    fun hasOvernightBoutIgnoresShortDaytimeNap() {
        val wake = LocalDate.of(2026, 6, 12)
        val start = wake.atTime(14, 0).atZone(zone).toEpochSecond()
        val end = start + 20 * 60
        assertEquals(false, hasOvernightBoutForWakeDay(wake, listOf(start to end), zone))
    }
}
