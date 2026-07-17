package com.noop.alarm

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One classic phone alarm (exact time), separate from the smart wake window. */
data class CustomAlarm(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "Alarm",
    /** Minutes since local midnight. */
    val minutes: Int = 7 * 60,
    val enabled: Boolean = true,
    /**
     * Calendar.DAY_OF_WEEK values (1=Sun…7=Sat). Empty = every day.
     * Same convention as strap [smartAlarmWeekdays].
     */
    val weekdays: Set<Int> = emptySet(),
)

object CustomAlarmCodec {
    fun encode(alarms: List<CustomAlarm>): String {
        val arr = JSONArray()
        for (a in alarms) {
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("label", a.label)
                    .put("minutes", a.minutes.coerceIn(0, SmartAlarmStore.MINUTES_PER_DAY - 1))
                    .put("enabled", a.enabled)
                    .put("weekdays", JSONArray(a.weekdays.filter { it in 1..7 }.sorted())),
            )
        }
        return arr.toString()
    }

    fun decode(raw: String?): List<CustomAlarm> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val days = mutableSetOf<Int>()
                    val w = o.optJSONArray("weekdays")
                    if (w != null) {
                        for (j in 0 until w.length()) {
                            val d = w.optInt(j, -1)
                            if (d in 1..7) days.add(d)
                        }
                    }
                    add(
                        CustomAlarm(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            label = o.optString("label", "Alarm").ifBlank { "Alarm" },
                            minutes = o.optInt("minutes", 7 * 60).coerceIn(0, SmartAlarmStore.MINUTES_PER_DAY - 1),
                            enabled = o.optBoolean("enabled", true),
                            weekdays = days,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}

/**
 * Glanceable custom-alarm count for the Alarm page summary (#390).
 * Null when there are no custom rows (keep the default "custom times" CTA).
 */
fun customAlarmsSummaryLabel(alarms: List<CustomAlarm>): String? {
    if (alarms.isEmpty()) return null
    val on = alarms.count { it.enabled }
    val n = alarms.size
    return when {
        on == 0 -> if (n == 1) "1 custom · off" else "$n custom · all off"
        on == n -> if (n == 1) "1 custom on" else "$n custom on"
        else -> "$on of $n custom on"
    }
}

/**
 * Glanceable turn-back / wake-when-rested status for the Alarm page summary (#391).
 * Null when both are off (toggles still visible; no extra status clutter).
 */
fun alarmSmartExtrasSummaryLabel(turnBack: Boolean, wakeWhenRested: Boolean): String? = when {
    turnBack && wakeWhenRested -> "Turn-back · wake when rested"
    turnBack -> "Turn-back on"
    wakeWhenRested -> "Wake when rested"
    else -> null
}
