package com.noop.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmSmartExtrasSummaryLabelTest {

    @Test
    fun bothOffIsNull() {
        assertNull(alarmSmartExtrasSummaryLabel(turnBack = false, wakeWhenRested = false))
    }

    @Test
    fun singlesAndBoth() {
        assertEquals("Turn-back on", alarmSmartExtrasSummaryLabel(true, false))
        assertEquals("Wake when rested", alarmSmartExtrasSummaryLabel(false, true))
        assertEquals("Turn-back · wake when rested", alarmSmartExtrasSummaryLabel(true, true))
    }
}
