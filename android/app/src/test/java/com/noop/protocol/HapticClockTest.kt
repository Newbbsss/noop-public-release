package com.noop.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-logic tests for the Haptic Clock encoder (#460). Morse lists stay pinned for Apple parity;
 * digit-hold pins Gilbert's hour-seconds · pause · minute-tens encoding (6:30 → 6s pause 3s).
 */
class HapticClockTest {
    private fun lng(gap: Int) = HapticClock.Pulse(HapticClock.LONG_MS, gap)
    private fun shrt(gap: Int) = HapticClock.Pulse(HapticClock.SHORT_MS, gap)
    private fun hold(gap: Int) = HapticClock.Pulse(HapticClock.HOLD_TICK_MS, gap)

    private fun morse(
        hour: Int,
        minute: Int,
        is24h: Boolean,
        speed: HapticClock.Speed = HapticClock.Speed.NORMAL,
        announce: Boolean = false,
    ) = HapticClock.pulses(
        hour, minute, is24h, speed = speed, announce = announce, style = HapticClock.Style.MORSE,
    )

    /** 3:25 in 24-hour form: hour 03 (no tens, 3 units) — block — minute 25 (2 tens, 5 units). */
    @Test
    fun pulses_0325_24h_exactList() {
        val g = HapticClock.INTRA_GAP_MS
        val expected = listOf(
            shrt(g), shrt(g), shrt(HapticClock.BLOCK_GAP_MS),
            lng(g), lng(HapticClock.GROUP_GAP_MS),
            shrt(g), shrt(g), shrt(g), shrt(g), shrt(0),
        )
        assertEquals(expected, morse(3, 25, is24h = true))
    }

    /** 12-hour mapping: 15:25 → dial reads 3:25, so it must equal the 24h 3:25 list exactly. */
    @Test
    fun pulses_1525_12h_mapsTo0325() {
        assertEquals(
            morse(3, 25, is24h = true),
            morse(15, 25, is24h = false),
        )
    }

    @Test
    fun pulses_1005_24h_handlesZeroDigits() {
        val g = HapticClock.INTRA_GAP_MS
        val expected = listOf(
            lng(HapticClock.BLOCK_GAP_MS),
            shrt(g), shrt(g), shrt(g), shrt(g), shrt(0),
        )
        assertEquals(expected, morse(10, 5, is24h = true))
    }

    /** Midnight 0:00 in 24-hour Morse has no nonzero digits — nothing to buzz. */
    @Test
    fun pulses_midnight_24h_isEmpty() {
        assertEquals(emptyList<HapticClock.Pulse>(), morse(0, 0, is24h = true))
    }

    /** Midnight 0:00 in 12-hour Morse reads "12:00" → one ten + two units of hour. */
    @Test
    fun pulses_midnight_12h_readsTwelve() {
        val expected = listOf(
            lng(HapticClock.GROUP_GAP_MS),
            shrt(HapticClock.INTRA_GAP_MS), shrt(0),
        )
        assertEquals(expected, morse(0, 0, is24h = false))
    }

    @Test
    fun twelveHour_mapping() {
        assertEquals(12, HapticClock.twelveHour(12))
        assertEquals(12, HapticClock.twelveHour(0))
        assertEquals(1, HapticClock.twelveHour(13))
        assertEquals(11, HapticClock.twelveHour(23))
    }

    @Test
    fun pulses_clampsOutOfRange() {
        assertEquals(
            morse(23, 59, is24h = true),
            morse(99, 99, is24h = true),
        )
        assertEquals(
            morse(0, 0, is24h = true),
            morse(-5, -5, is24h = true),
        )
    }

    @Test
    fun pulses_announce_prependsTriple() {
        val base = morse(3, 25, is24h = true)
        val withAnn = morse(3, 25, is24h = true, announce = true)
        assertEquals(base.size + 3, withAnn.size)
        assertEquals(HapticClock.SHORT_MS, withAnn[0].durationMs)
        assertEquals(HapticClock.SHORT_MS, withAnn[1].durationMs)
        assertEquals(HapticClock.SHORT_MS, withAnn[2].durationMs)
        assertEquals(HapticClock.ANNOUNCE_TO_HOUR_MS, withAnn[2].gapMs)
    }

    @Test
    fun pulses_speed_scalesGaps() {
        val normal = morse(3, 25, is24h = true, speed = HapticClock.Speed.NORMAL)
        val slow = morse(3, 25, is24h = true, speed = HapticClock.Speed.SLOW)
        assertEquals(normal.size, slow.size)
        assertEquals(normal, morse(3, 25, is24h = true))
        val nGap = normal.first { it.gapMs > 0 }.gapMs
        val sGap = slow.first { it.gapMs > 0 }.gapMs
        assertEquals(true, sGap > nGap)
    }

    @Test
    fun practiceSpans_coversFourDigits() {
        val spans = HapticClock.practiceSpans(15, 42, is24h = true)
        assertEquals(4, spans.size)
        assertEquals(HapticClock.DigitRole.HOUR_TENS, spans[0].role)
        assertEquals(1, spans[0].digit)
        assertEquals(HapticClock.DigitRole.HOUR_ONES, spans[1].role)
        assertEquals(5, spans[1].digit)
        assertEquals(HapticClock.DigitRole.MIN_TENS, spans[2].role)
        assertEquals(4, spans[2].digit)
        assertEquals(HapticClock.DigitRole.MIN_ONES, spans[3].role)
        assertEquals(2, spans[3].digit)
        assertEquals(true, spans.zipWithNext().all { (a, b) -> a.endMs <= b.startMs + 1 })
    }

    @Test
    fun practiceSpans_announceFirst() {
        val spans = HapticClock.practiceSpans(3, 25, is24h = true, announce = true)
        assertEquals(HapticClock.DigitRole.ANNOUNCE, spans.first().role)
        assertEquals(-1, spans.first().digit)
        assertEquals(5, spans.size)
    }

    /** 6:30 digit-hold → 6s hour hold, 1s pause, 3s minute-tens (not "6 pause 6"). */
    @Test
    fun digitHold_630_isSixPauseThree() {
        val expected = buildList {
            repeat(5) { add(hold(HapticClock.HOLD_TICK_GAP_MS)) }
            add(hold(HapticClock.HOLD_PAUSE_MS))
            repeat(2) { add(hold(HapticClock.HOLD_TICK_GAP_MS)) }
            add(hold(0))
        }
        assertEquals(expected, HapticClock.digitHoldPulses(6, 30, is24h = false))
        assertEquals(expected, HapticClock.pulses(6, 30, is24h = false)) // default DIGIT_HOLD
    }

    /** Noon hold = 12s hour only (fits the ~12s budget). */
    @Test
    fun digitHold_noon_isTwelveSeconds() {
        val pulses = HapticClock.digitHoldPulses(12, 0, is24h = false)
        assertEquals(12, pulses.size)
        assertEquals(HapticClock.HOLD_TICK_MS, pulses.first().durationMs)
        assertEquals(0, pulses.last().gapMs)
    }
}
