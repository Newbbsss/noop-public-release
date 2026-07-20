package com.noop.alarm

/**
 * Shared next-alarm copy for Today footnote and Alarm page (#389).
 *
 * Wall-clock minute-of-day formatting (not epoch→Date) so a timezone change cannot make Today
 * show a shifted clock while Alarm still shows the configured wake minutes.
 */
object NextAlarmDisplay {

    /** Shared wake presets for Today Quick alarm + Sleep|Alarm summary (6:30 / 7 / 7:30 / 8). */
    val WAKE_PRESET_MINUTES: List<Int> = listOf(6 * 60 + 30, 7 * 60, 7 * 60 + 30, 8 * 60)

    /**
     * Split wall-clock for dual Bedtime|Wake faces: large digits + optional smaller AM/PM.
     * 24-hour leaves [meridiem] null so UI never invents a period label.
     */
    data class ClockParts(val digits: String, val meridiem: String?)

    fun clockParts(minutes: Int, is24Hour: Boolean): ClockParts {
        val m = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60)
        val h = m / 60
        val mm = m % 60
        return if (is24Hour) {
            ClockParts("%d:%02d".format(h, mm), meridiem = null)
        } else {
            val h12 = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            ClockParts(
                digits = "%d:%02d".format(h12, mm),
                meridiem = if (h < 12) "AM" else "PM",
            )
        }
    }

    fun formatMinuteOfDay(minutes: Int, is24Hour: Boolean): String {
        val parts = clockParts(minutes, is24Hour)
        return if (parts.meridiem == null) parts.digits else "${parts.digits} ${parts.meridiem}"
    }

    fun wakeWindowTitle(targetMinutes: Int, windowMinutes: Int, is24Hour: Boolean): String {
        val start = ((targetMinutes % (24 * 60)) + (24 * 60)) % (24 * 60)
        val deadline = (start + windowMinutes) % (24 * 60)
        return "Wake ${formatMinuteOfDay(start, is24Hour)}–${formatMinuteOfDay(deadline, is24Hour)}"
    }

    fun countdownLabel(atMs: Long, nowMs: Long): String {
        val inMin = ((atMs - nowMs) / 60_000L).coerceAtLeast(0L)
        return if (inMin >= 60) "in ${inMin / 60}h ${inMin % 60}m" else "in ${inMin}m"
    }

    /**
     * Soonest armed wake (phone window or custom). Titles use minute-of-day; [atMs] is only for
     * countdown / ordering and must be recomputed after TZ changes.
     *
     * Countdown is to **window start** (earliest soft fire). When [windowMinutes] > 0, the label also
     * names the hard deadline so ALARM_PAGE #61 is honest vs deadline-only fire.
     */
    fun soonestLabel(
        phoneEnabled: Boolean,
        targetMinutes: Int,
        windowMinutes: Int,
        customAlarms: List<CustomAlarm>,
        nowMs: Long,
        is24Hour: Boolean,
    ): String? {
        val candidates = buildList {
            if (phoneEnabled) {
                CustomAlarmScheduler.nextOccurrenceMs(targetMinutes, emptySet(), nowMs)?.let { at ->
                    val title = wakeWindowTitle(targetMinutes, windowMinutes, is24Hour)
                    val deadlineNote = if (windowMinutes > 0) {
                        val deadlineMin = (targetMinutes + windowMinutes) % (24 * 60)
                        " · deadline ${formatMinuteOfDay(deadlineMin, is24Hour)}"
                    } else {
                        ""
                    }
                    add(title + deadlineNote to at)
                }
            }
            for (a in customAlarms) {
                if (!a.enabled) continue
                CustomAlarmScheduler.nextOccurrenceMs(a.minutes, a.weekdays, nowMs)?.let { at ->
                    add("Alarm ${formatMinuteOfDay(a.minutes, is24Hour)}" to at)
                }
            }
        }
        val next = candidates.minByOrNull { it.second } ?: return null
        return "${next.first} · ${countdownLabel(next.second, nowMs)}"
    }

    /**
     * Short next-wake line for the home widget (ALARM_PAGE #98) — window start + countdown only.
     */
    fun soonestShortLabel(
        phoneEnabled: Boolean,
        targetMinutes: Int,
        windowMinutes: Int,
        customAlarms: List<CustomAlarm>,
        nowMs: Long,
        is24Hour: Boolean,
    ): String? {
        val candidates = buildList {
            if (phoneEnabled) {
                CustomAlarmScheduler.nextOccurrenceMs(targetMinutes, emptySet(), nowMs)?.let { at ->
                    add("Wake ${formatMinuteOfDay(targetMinutes, is24Hour)}" to at)
                }
            }
            for (a in customAlarms) {
                if (!a.enabled) continue
                CustomAlarmScheduler.nextOccurrenceMs(a.minutes, a.weekdays, nowMs)?.let { at ->
                    add("Alarm ${formatMinuteOfDay(a.minutes, is24Hour)}" to at)
                }
            }
        }
        val next = candidates.minByOrNull { it.second } ?: return null
        return "${next.first} · ${countdownLabel(next.second, nowMs)}"
    }
}
