package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SharedDayBrowseTest {

    @Test
    fun offsetFromToday_yesterday() {
        val today = LocalDate.of(2026, 7, 12)
        assertEquals(1, SharedDayBrowse.offsetFromToday("2026-07-11", today))
        assertEquals(0, SharedDayBrowse.offsetFromToday("2026-07-12", today))
        assertNull(SharedDayBrowse.offsetFromToday("2026-07-13", today))
    }

    /**
     * Regression: between calendar midnight and the 04:00 logical rollover, a logical-today key
     * must map to offset 0 against the logical anchor — not 1 against [LocalDate.now]. The latter
     * fed Today’s browseDayKey consumer and walked selectedDayOffset forever into the past.
     */
    @Test
    fun offsetFromToday_logicalAnchor_preRolloverDoesNotAdvance() {
        val calendarToday = LocalDate.of(2026, 7, 13)
        val logicalToday = LocalDate.of(2026, 7, 12) // e.g. 00:15 local
        assertEquals(0, SharedDayBrowse.offsetFromToday(logicalToday.toString(), logicalToday))
        // Same key vs calendar midnight would wrongly look like "yesterday":
        assertEquals(1, SharedDayBrowse.offsetFromToday(logicalToday.toString(), calendarToday))
        // Stepping the published key older under a calendar anchor is the runaway pattern:
        assertEquals(2, SharedDayBrowse.offsetFromToday(logicalToday.minusDays(1).toString(), calendarToday))
        assertEquals(1, SharedDayBrowse.offsetFromToday(logicalToday.minusDays(1).toString(), logicalToday))
    }

    @Test
    fun nightOffsetForDay_newestFirst() {
        val keys = listOf("2026-07-12", "2026-07-11", "2026-07-10")
        assertEquals(0, SharedDayBrowse.nightOffsetForDay(keys, "2026-07-12"))
        assertEquals(2, SharedDayBrowse.nightOffsetForDay(keys, "2026-07-10"))
        assertEquals(-1, SharedDayBrowse.nightOffsetForDay(keys, "2026-06-01"))
    }

    @Test
    fun sleepNightOffset_missingBrowseFallsToNewest() {
        val keys = listOf("2026-07-15", "2026-07-14")
        assertEquals(0, SharedDayBrowse.sleepNightOffset(keys, null, logicalToday = "2026-07-15"))
        assertEquals(0, SharedDayBrowse.sleepNightOffset(keys, "2026-06-01", logicalToday = "2026-07-15"))
        // Intentional browse to yesterday while today's night is NOT yet newest → keep index
        // (newest is still yesterday in this fixture… use keys where newest is today):
        assertEquals(0, SharedDayBrowse.sleepNightOffset(keys, "2026-07-14", logicalToday = "2026-07-15"))
    }

    @Test
    fun sleepNightOffset_staleYesterdaySnapsToTodaysNight() {
        // Wed afternoon: newest wake-day is Wed; browse still Tue from pre-bank publish.
        val keys = listOf("2026-07-15", "2026-07-14", "2026-07-13")
        assertEquals(
            0,
            SharedDayBrowse.sleepNightOffset(keys, "2026-07-14", logicalToday = "2026-07-15"),
        )
        // Deep past while today's night exists also snaps (morning-wake recovery) when snap is on.
        assertEquals(
            0,
            SharedDayBrowse.sleepNightOffset(keys, "2026-07-13", logicalToday = "2026-07-15"),
        )
        // Exact match for today stays 0.
        assertEquals(
            0,
            SharedDayBrowse.sleepNightOffset(keys, "2026-07-15", logicalToday = "2026-07-15"),
        )
    }

    @Test
    fun sleepNightOffset_pinnedPastBrowseSticksWhileTodaysNightExists() {
        // Explicit Sleep ◀ / date pick: do not yank the user back to newest.
        val keys = listOf("2026-07-15", "2026-07-14", "2026-07-13")
        assertEquals(
            1,
            SharedDayBrowse.sleepNightOffset(
                keys, "2026-07-14", logicalToday = "2026-07-15", snapStalePrior = false,
            ),
        )
        assertEquals(
            2,
            SharedDayBrowse.sleepNightOffset(
                keys, "2026-07-13", logicalToday = "2026-07-15", snapStalePrior = false,
            ),
        )
    }

    @Test
    fun sleepNightOffset_keepsPastBrowseWhenNewestIsAlsoPast() {
        // No night for logical today yet — newest is yesterday; browsing yesterday is correct.
        val keys = listOf("2026-07-14", "2026-07-13")
        assertEquals(
            0,
            SharedDayBrowse.sleepNightOffset(keys, "2026-07-14", logicalToday = "2026-07-15"),
        )
        assertEquals(
            1,
            SharedDayBrowse.sleepNightOffset(keys, "2026-07-13", logicalToday = "2026-07-15"),
        )
    }
}
