package com.noop.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomAlarmsSummaryLabelTest {

    @Test
    fun emptyIsNull() {
        assertNull(customAlarmsSummaryLabel(emptyList()))
    }

    @Test
    fun singleOnAndOff() {
        assertEquals("1 custom on", customAlarmsSummaryLabel(listOf(CustomAlarm(enabled = true))))
        assertEquals("1 custom · off", customAlarmsSummaryLabel(listOf(CustomAlarm(enabled = false))))
    }

    @Test
    fun mixedAndAllOn() {
        assertEquals(
            "2 custom on",
            customAlarmsSummaryLabel(
                listOf(CustomAlarm(enabled = true), CustomAlarm(enabled = true)),
            ),
        )
        assertEquals(
            "1 of 3 custom on",
            customAlarmsSummaryLabel(
                listOf(
                    CustomAlarm(enabled = true),
                    CustomAlarm(enabled = false),
                    CustomAlarm(enabled = false),
                ),
            ),
        )
        assertEquals(
            "2 custom · all off",
            customAlarmsSummaryLabel(
                listOf(CustomAlarm(enabled = false), CustomAlarm(enabled = false)),
            ),
        )
    }
}
