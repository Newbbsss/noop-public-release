package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Fable Rest #25 — Sleep hero Rest % must prefer Today’s series, with honest hours caption. */
class RestSleepTruthTest {

    @Test
    fun seriesWinsOverModel() {
        assertEquals(88.0, sleepHeroRestScore(88.0, 72.0)!!, 1e-9)
    }

    @Test
    fun fallsBackToModel() {
        assertEquals(72.0, sleepHeroRestScore(null, 72.0)!!, 1e-9)
    }

    @Test
    fun rejectsOutOfRange() {
        assertNull(sleepHeroRestScore(150.0, null))
        assertEquals(80.0, sleepHeroRestScore(150.0, 80.0)!!, 1e-9)
    }

    @Test
    fun captions() {
        assertEquals("Same Rest % as Today", restSleepTruthCaption(true, 420.0))
        assertEquals(
            "Hours asleep · Rest % appears here when this night is scored",
            restSleepTruthCaption(false, 420.0),
        )
        assertNull(restSleepTruthCaption(false, null))
    }

    @Test
    fun sleepNeedMinutesUsesPersonalFloor() {
        assertEquals(8 * 60, sleepNeedMinutesForAlarm(null))
        assertEquals((7.5 * 60).toInt(), sleepNeedMinutesForAlarm(7.0 * 60)) // floors at 7.5h
        assertEquals(8 * 60, sleepNeedMinutesForAlarm(8.0 * 60))
    }

    @Test
    fun restAlarmBridgeJoinsRestAndAim() {
        assertEquals(
            "Same Rest % as Today · Aim for 8h by 7:00 wake",
            restAlarmBridgeCaption(
                showingRestPct = true,
                asleepMin = 420.0,
                needMin = 8 * 60,
                alarmEnabled = true,
                wakeClockLabel = "7:00",
            ),
        )
        assertEquals(
            "Aim for 7h 30m sleep for Rest",
            restAlarmBridgeCaption(
                showingRestPct = false,
                asleepMin = null,
                needMin = 7 * 60 + 30,
                alarmEnabled = false,
                wakeClockLabel = null,
            ),
        )
    }
}
