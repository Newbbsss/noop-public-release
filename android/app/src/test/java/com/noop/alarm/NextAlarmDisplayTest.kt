package com.noop.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class NextAlarmDisplayTest {

    @Test
    fun clockPartsSplitsMeridiemIn12hOnly() {
        assertEquals(
            NextAlarmDisplay.ClockParts("7:00", "AM"),
            NextAlarmDisplay.clockParts(7 * 60, is24Hour = false),
        )
        assertEquals(
            NextAlarmDisplay.ClockParts("7:00", "PM"),
            NextAlarmDisplay.clockParts(19 * 60, is24Hour = false),
        )
        assertEquals(
            NextAlarmDisplay.ClockParts("12:00", "AM"),
            NextAlarmDisplay.clockParts(0, is24Hour = false),
        )
        assertEquals(
            NextAlarmDisplay.ClockParts("19:00", null),
            NextAlarmDisplay.clockParts(19 * 60, is24Hour = true),
        )
        assertEquals("7:00 AM", NextAlarmDisplay.formatMinuteOfDay(7 * 60, is24Hour = false))
        assertEquals("19:00", NextAlarmDisplay.formatMinuteOfDay(19 * 60, is24Hour = true))
    }

    @Test
    fun wakeWindowTitleUsesMinuteOfDayNotEpoch() {
        // Same configured minutes must read identically regardless of how an old epoch would format.
        assertEquals(
            "Wake 6:45–7:00",
            NextAlarmDisplay.wakeWindowTitle(6 * 60 + 45, 15, is24Hour = true),
        )
        assertEquals(
            "Wake 6:45 AM–7:00 AM",
            NextAlarmDisplay.wakeWindowTitle(6 * 60 + 45, 15, is24Hour = false),
        )
    }

    @Test
    fun titleSurvivesTimezoneJumpBecauseItIsWallClock() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val east = NextAlarmDisplay.wakeWindowTitle(7 * 60, 30, is24Hour = true)
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
            val london = NextAlarmDisplay.wakeWindowTitle(7 * 60, 30, is24Hour = true)
            assertEquals(east, london)
            assertEquals("Wake 7:00–7:30", london)
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun soonestLabelMatchesWindowTitleAndCountdown() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(2026, Calendar.JULY, 14, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val label = NextAlarmDisplay.soonestLabel(
                phoneEnabled = true,
                targetMinutes = 7 * 60,
                windowMinutes = 30,
                customAlarms = emptyList(),
                nowMs = now,
                is24Hour = true,
            )
            assertEquals("Wake 7:00–7:30 · deadline 7:30 · in 19h 0m", label)
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun soonestLabelNullWhenNothingArmed() {
        assertNull(
            NextAlarmDisplay.soonestLabel(
                phoneEnabled = false,
                targetMinutes = 7 * 60,
                windowMinutes = 30,
                customAlarms = listOf(CustomAlarm(enabled = false)),
                nowMs = System.currentTimeMillis(),
                is24Hour = true,
            ),
        )
    }

    @Test
    fun soonestShortLabelIsCompactForWidget() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(2026, Calendar.JULY, 15, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val label = NextAlarmDisplay.soonestShortLabel(
                phoneEnabled = true,
                targetMinutes = 7 * 60,
                windowMinutes = 30,
                customAlarms = emptyList(),
                nowMs = cal.timeInMillis,
                is24Hour = true,
            )
            assertEquals("Wake 7:00 · in 19h 0m", label)
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun countdownLabelFormatsHoursAndMinutes() {
        assertEquals("in 5m", NextAlarmDisplay.countdownLabel(5 * 60_000L, 0L))
        assertEquals("in 2h 5m", NextAlarmDisplay.countdownLabel((2 * 60 + 5) * 60_000L, 0L))
        assertTrue(NextAlarmDisplay.countdownLabel(0L, 10_000L).startsWith("in 0"))
    }
}
