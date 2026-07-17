package com.noop.protocol

/**
 * Haptic Clock (#460 / TOP-A 2026-07-13): turn a wall-clock time into a deterministic list of wrist
 * buzzes so a user can read the time off the strap without looking at a screen — a long pulse counts
 * tens, a short pulse counts units, in the order hour-tens, hour-units, minute-tens, minute-units.
 *
 * This is a PURE, platform-agnostic encoder: time-in, pulse-list-out, no I/O and no BLE. The trigger
 * ([com.noop.ble.WhoopBleClient.buzzTimeNow]) walks the list and fires each pulse through the EXISTING
 * maverick notification buzz; only the *schedule* of buzzes is new, the buzz itself is the
 * hardware-confirmed one. Kotlin twin of the Apple `HapticClock.swift`; the two pulse lists are pinned
 * identical by matching unit tests on both platforms (e.g. 3:25 → the same list) for the default
 * [Speed.NORMAL] / no-announce path.
 *
 * Reading the buzzes:
 *  - LONG pulse  = one "ten"   in the current digit group
 *  - SHORT pulse = one "unit"  in the current digit group
 *  - a short gap separates pulses; a long gap separates the four digit groups (HH-tens, HH-units,
 *    MM-tens, MM-units); an extra-long gap separates the hour block from the minute block.
 *  - a digit of 0 emits NO pulse — the group is signalled only by the surrounding group gaps.
 *  - optional announce: three short buzzes before the hour block so the user knows "time is coming".
 */
object HapticClock {

    /** One buzz instruction: buzz the wrist for [durationMs], then stay silent for [gapMs]. */
    data class Pulse(val durationMs: Int, val gapMs: Int) {
        /** Whether this is a "tens" pulse (long buzz) versus a "units" pulse (short). Swift twin:
         *  `Pulse.isLong`. Lets the trigger weight the buzz without knowing the timing table. */
        val isLong: Boolean get() = durationMs >= LONG_MS
        /** Digit-hold mode: one stacked motor loop per second of hold (hardware buzz is fixed-length). */
        val holdLoops: Int get() = if (durationMs >= HOLD_TICK_MS) 2 else 1
    }

    /** Playback tempo for the schedule (TOP-A #355). Scales gaps only — pulse "weights" stay long/short. */
    enum class Speed(val gapScale: Float, val label: String) {
        SLOW(1.45f, "Slow"),
        NORMAL(1.0f, "Normal"),
        FAST(0.72f, "Fast"),
    }

    /**
     * Encoding style for Buzz the time.
     * - [MORSE]: classic long=tens / short=ones place-value taps.
     * - [DIGIT_HOLD]: continuous hold — hour as N-second buzz, ~1s pause, minute-tens as M-second buzz
     *   (e.g. 6:30 → 6s pause 3s). Total pattern clamped to [MAX_HOLD_TOTAL_SEC] (~12s budget).
     */
    enum class Style { MORSE, DIGIT_HOLD }

    // Pulse + gap timing (ms). Kept in lock-step with HapticClock.swift — change both together.
    // #981: the buzz itself is a fixed hardware pattern, so durationMs+gapMs is only the start-to-start
    // SPACING between buzzes. The old 250ms intra-gap left near-zero silence between unit taps, so they
    // blended on the wrist and were "almost impossible to distinguish". Widened gaps (intra-gap most of
    // all) give clear silence between taps and between digit groups while keeping the sequence practical.
    const val LONG_MS = 550        // a "tens" pulse
    const val SHORT_MS = 200       // a "units" pulse
    const val INTRA_GAP_MS = 450   // silence between two pulses inside one digit group
    const val GROUP_GAP_MS = 900   // silence between adjacent digit groups
    const val BLOCK_GAP_MS = 1500  // silence between the hour block and the minute block
    const val ANNOUNCE_GAP_MS = 350
    const val ANNOUNCE_TO_HOUR_MS = 1200

    /** One second of digit-hold feel (stacked motor loops; hardware pulse length is fixed). */
    const val HOLD_TICK_MS = 900
    const val HOLD_TICK_GAP_MS = 100
    const val HOLD_PAUSE_MS = 1000
    /** Gilbert budget: ~12s total pattern (noon = 12s hour hold). */
    const val MAX_HOLD_TOTAL_SEC = 12

    /**
     * Encode [hour]:[minute] into the buzz schedule.
     *
     * @param hour hour of day, 0..23 (24-hour input — the app already stores wall time this way).
     * @param minute minute of hour, 0..59.
     * @param is24h if `false`, the hour is mapped to 12-hour clock form (12,1..11) before encoding so
     *   the wrist count matches a 12-hour face. AM/PM is NOT signalled; only the dial reading is buzzed.
     * @param speed gap scale for slow / normal / fast practice (TOP-A #355). Morse only.
     * @param announce when true, prepend three short buzzes so the start of the hour block is obvious.
     * @param style [Style.DIGIT_HOLD] (default) = hour-seconds pause minute-tens; [Style.MORSE] = classic taps.
     * @return the ordered pulse list. Empty only for the degenerate all-zero 24h midnight 0:00 with no
     *   announce, which has no pulses to emit; callers should treat an empty list as "nothing to buzz".
     */
    fun pulses(
        hour: Int,
        minute: Int,
        is24h: Boolean,
        speed: Speed = Speed.NORMAL,
        announce: Boolean = false,
        style: Style = Style.DIGIT_HOLD,
    ): List<Pulse> {
        return when (style) {
            Style.DIGIT_HOLD -> digitHoldPulses(hour, minute, is24h, announce = announce)
            Style.MORSE -> morsePulses(hour, minute, is24h, speed = speed, announce = announce)
        }
    }

    /**
     * Digit-hold: continuous N-second buzz for the dial hour, ~1s pause, then M-second buzz for
     * minute-tens. Example: 6:30 → 6s · pause · 3s (not "6 pause 6" — minute tens of :30 is 3).
     * Total hold+pause seconds clamped to [MAX_HOLD_TOTAL_SEC].
     */
    fun digitHoldPulses(
        hour: Int,
        minute: Int,
        is24h: Boolean,
        announce: Boolean = false,
        maxTotalSec: Int = MAX_HOLD_TOTAL_SEC,
    ): List<Pulse> {
        val h24 = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        // Hold counts always use the 12-hour dial (1–12) so noon = 12s and night hours stay ≤12.
        val displayHour = twelveHour(h24)
        var hourSec = displayHour.coerceIn(0, 12)
        var tensSec = m / 10
        val budget = maxTotalSec.coerceAtLeast(1)
        while (
            hourSec + (if (hourSec > 0 && tensSec > 0) 1 else 0) + tensSec > budget &&
            (hourSec > 1 || tensSec > 0)
        ) {
            if (tensSec >= hourSec && tensSec > 0) tensSec-- else if (hourSec > 1) hourSec-- else tensSec = 0
        }
        if (hourSec <= 0 && tensSec <= 0 && !announce) return emptyList()

        val out = ArrayList<Pulse>()
        if (announce) {
            repeat(3) { out.add(Pulse(SHORT_MS, ANNOUNCE_GAP_MS)) }
            closeGroup(out, ANNOUNCE_TO_HOUR_MS)
        }
        appendHoldSeconds(out, hourSec, trailGapMs = if (tensSec > 0) HOLD_PAUSE_MS else 0)
        appendHoldSeconds(out, tensSec, trailGapMs = 0)
        if (out.isNotEmpty()) {
            val last = out[out.size - 1]
            out[out.size - 1] = last.copy(gapMs = 0)
        }
        return out
    }

    /** Classic Morse place-value encoder (long = tens, short = ones). */
    fun morsePulses(
        hour: Int,
        minute: Int,
        is24h: Boolean,
        speed: Speed = Speed.NORMAL,
        announce: Boolean = false,
    ): List<Pulse> {
        // Clamp defensively rather than throw — this can be driven from a stored pref or a strap tap.
        val h24 = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        val displayHour = if (is24h) h24 else twelveHour(h24)

        val hourTens = displayHour / 10
        val hourUnits = displayHour % 10
        val minTens = m / 10
        val minUnits = m % 10

        val out = ArrayList<Pulse>()

        if (announce) {
            repeat(3) { out.add(Pulse(SHORT_MS, ANNOUNCE_GAP_MS)) }
            closeGroup(out, ANNOUNCE_TO_HOUR_MS)
        }

        // Hour block: tens group, then units group.
        appendGroup(out, hourTens, LONG_MS)
        closeGroup(out, GROUP_GAP_MS)
        appendGroup(out, hourUnits, SHORT_MS)
        // Separate hour block from minute block with the longer block gap.
        closeGroup(out, BLOCK_GAP_MS)

        // Minute block: tens group, then units group.
        appendGroup(out, minTens, LONG_MS)
        closeGroup(out, GROUP_GAP_MS)
        appendGroup(out, minUnits, SHORT_MS)

        // The final pulse needs no trailing gap — trim it so the sequence ends on a buzz.
        if (out.isNotEmpty()) {
            val last = out[out.size - 1]
            out[out.size - 1] = last.copy(gapMs = 0)
        }
        if (speed == Speed.NORMAL || out.isEmpty()) return out
        return out.map { p ->
            p.copy(gapMs = (p.gapMs * speed.gapScale).toInt().coerceAtLeast(if (p.gapMs > 0) 80 else 0))
        }
    }

    /** Plain-language legend for Automations / Test Centre (TOP-A #344). */
    fun readLegend(): String =
        "Digit-hold (default): hour as N-second buzz, pause, then minute-tens as M-second buzz " +
            "(6:30 → 6s pause 3s; pattern capped ~${MAX_HOLD_TOTAL_SEC}s). " +
            "Classic Morse: long = tens, short = ones (hour then minutes). Optional triple-buzz = time starting."

    /** Append [seconds] of hold ticks (≈1s each). Trailing [trailGapMs] after the last tick. */
    private fun appendHoldSeconds(out: MutableList<Pulse>, seconds: Int, trailGapMs: Int) {
        if (seconds <= 0) return
        repeat(seconds) { i ->
            val gap = if (i == seconds - 1) trailGapMs else HOLD_TICK_GAP_MS
            out.add(Pulse(HOLD_TICK_MS, gap))
        }
    }

    /** Which face digit the practice UI should highlight (TOP-A #354). */
    enum class DigitRole { ANNOUNCE, HOUR_TENS, HOUR_ONES, MIN_TENS, MIN_ONES }

    /**
     * One highlight window for phone practice mode (#354): which digit is "speaking" and for how long.
     * Spans cover silent zeros (no buzz) so the user still sees which place-value is active.
     */
    data class PracticeSpan(
        val role: DigitRole,
        /** 0..9 for a time digit; -1 for the announce triple-buzz. */
        val digit: Int,
        val startMs: Long,
        val endMs: Long,
    )

    /**
     * Timeline of digit highlights matching [pulses] timing (same speed / announce). Pure — UI walks
     * this while [com.noop.ble.WhoopBleClient.buzzTimeNow] fires the strap.
     */
    fun practiceSpans(
        hour: Int,
        minute: Int,
        is24h: Boolean,
        speed: Speed = Speed.NORMAL,
        announce: Boolean = false,
    ): List<PracticeSpan> {
        val h24 = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        val displayHour = if (is24h) h24 else twelveHour(h24)
        val digits = intArrayOf(displayHour / 10, displayHour % 10, m / 10, m % 10)
        val roles = arrayOf(
            DigitRole.HOUR_TENS, DigitRole.HOUR_ONES, DigitRole.MIN_TENS, DigitRole.MIN_ONES,
        )
        val gapScale = speed.gapScale
        fun scaled(ms: Int): Int =
            if (speed == Speed.NORMAL) ms else (ms * gapScale).toInt().coerceAtLeast(if (ms > 0) 80 else 0)

        val spans = ArrayList<PracticeSpan>()
        var t = 0L

        if (announce) {
            val announceDur = 3L * (SHORT_MS + scaled(ANNOUNCE_GAP_MS)) + scaled(ANNOUNCE_TO_HOUR_MS)
            spans.add(PracticeSpan(DigitRole.ANNOUNCE, -1, t, t + announceDur))
            t += announceDur
        }

        digits.forEachIndexed { i, digit ->
            val pulseMs = if (i % 2 == 0) LONG_MS else SHORT_MS
            val groupBuzz = if (digit <= 0) {
                0L
            } else {
                digit * pulseMs.toLong() + (digit - 1L) * scaled(INTRA_GAP_MS)
            }
            val trail = when (i) {
                0 -> scaled(GROUP_GAP_MS)
                1 -> scaled(BLOCK_GAP_MS)
                2 -> scaled(GROUP_GAP_MS)
                else -> 0
            }
            // Silent zero still gets a visible window so practice names the place-value.
            val hold = if (digit <= 0) scaled(GROUP_GAP_MS).toLong().coerceAtLeast(400L) else groupBuzz
            // When digit > 0, trailing separator is part of the span so highlight lasts until next digit.
            val spanEnd = if (digit <= 0) t + hold else t + groupBuzz + trail
            spans.add(PracticeSpan(roles[i], digit, t, spanEnd.coerceAtLeast(t + 1)))
            t = if (digit <= 0) t + hold else t + groupBuzz + trail
        }
        return spans
    }

    /** 24-hour hour → 12-hour dial reading (0→12, 13→1 … 23→11). Noon stays 12. */
    fun twelveHour(h24: Int): Int {
        val h = h24 % 12
        return if (h == 0) 12 else h
    }

    /** Append [count] identical pulses (each duration [durationMs]) separated by the intra-group gap. */
    private fun appendGroup(out: MutableList<Pulse>, count: Int, durationMs: Int) {
        if (count <= 0) return
        repeat(count) { out.add(Pulse(durationMs, INTRA_GAP_MS)) }
    }

    /**
     * Widen the trailing pulse's gap to at least [gapMs] (a group/block separator). If nothing has been
     * emitted yet (a leading zero digit group, e.g. minute-tens of 0), there is no pulse to widen — the
     * missing pulse is itself the "0", and the surrounding gaps still bound the groups, so this is a
     * no-op. We take the MAX rather than overwrite so that when later groups are empty (e.g. 12:00 has
     * no minute pulses) an earlier, wider block separator isn't clobbered by a narrower group separator
     * that follows it on the same trailing pulse.
     */
    private fun closeGroup(out: MutableList<Pulse>, gapMs: Int) {
        if (out.isEmpty()) return
        val last = out[out.size - 1]
        out[out.size - 1] = last.copy(gapMs = maxOf(last.gapMs, gapMs))
    }
}
