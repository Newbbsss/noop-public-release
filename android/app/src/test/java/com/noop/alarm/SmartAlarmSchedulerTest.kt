package com.noop.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SmartAlarmSchedulerTest {

    private val start = 1_000L
    private val deadline = 3_000L

    @Test
    fun earlyCueCanMoveDeadlineEarlierWithinWindow() {
        assertTrue(SmartAlarmScheduler.shouldAdvanceScheduledAlarm(1_500L, deadline, start, deadline))
    }

    @Test
    fun laterAndEqualCuesNeverMoveAnAlreadyAdvancedAlarmLater() {
        assertFalse(SmartAlarmScheduler.shouldAdvanceScheduledAlarm(1_500L, 1_500L, start, deadline))
        assertFalse(SmartAlarmScheduler.shouldAdvanceScheduledAlarm(2_500L, 1_500L, start, deadline))
        assertFalse(SmartAlarmScheduler.shouldAdvanceScheduledAlarm(9_000L, 1_500L, start, deadline))
    }

    @Test
    fun outOfWindowCueClampsButStillCannotViolateDirection() {
        assertTrue(SmartAlarmScheduler.shouldAdvanceScheduledAlarm(-500L, deadline, start, deadline))
        assertFalse(SmartAlarmScheduler.shouldAdvanceScheduledAlarm(-500L, start, start, deadline))
        assertFalse(SmartAlarmScheduler.shouldAdvanceScheduledAlarm(1_500L, deadline, deadline, start))
    }

    @Test
    fun earlyFireRecursAfterOriginalDeadlineInsteadOfSameMorning() {
        val originalDeadline = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 13, 7, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val next = SmartAlarmScheduler.nextOccurrenceAfter(
            minuteOfDay = 7 * 60 + 30,
            afterMs = originalDeadline.timeInMillis,
        )

        assertTrue(next.timeInMillis > originalDeadline.timeInMillis)
        assertEquals(Calendar.JULY, next.get(Calendar.MONTH))
        assertEquals(14, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(7, next.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, next.get(Calendar.MINUTE))
    }
}
