package com.noop.analytics

import com.noop.data.GravitySample
import com.noop.data.HrSample
import com.noop.data.RrInterval
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * DIAGNOSTIC REPLAY (not a regression test — auto-skips when the local CSV dumps are absent).
 *
 * Mission A trace (FABLE5_MEGA_PROMPT): why does the Fold debug build detect NO sleep session for
 * wake-day Sun 2026-07-12 (WHOOP: 06:21–12:57; release build detected 04:56–12:54), given dense 1 Hz HR
 * across the whole night? Feeds the REAL pulled-from-device streams through the REAL detector with the
 * gate trace on and prints every session + drop reason. Run:
 *   ./gradlew :app:testFullDebugUnitTest --tests com.noop.analytics.FoldSun12NightReplay
 * with the CSVs exported by the session's scratchpad script.
 */
class FoldSun12NightReplay {

    private val dir = File(
        "C:/Users/Gilbert/AppData/Local/Temp/claude/C--Users-Gilbert/" +
            "3848041f-b916-4190-b3c2-ae14525e2350/scratchpad",
    )

    private fun replay(suffix: String) {
        val tz = -18_000L
        fun lines(name: String) = File(dir, "$name$suffix.csv").readLines().filter { it.isNotBlank() }
        val hr = lines("hr").map { l ->
            val p = l.split(','); HrSample("d", p[0].substringBefore('.').toLong(), p[1].toInt())
        }
        val rr = lines("rr").map { l ->
            val p = l.split(','); RrInterval("d", p[0].substringBefore('.').toLong(), p[1].toInt())
        }
        val grav = lines("grav").map { l ->
            val p = l.split(',')
            GravitySample("d", p[0].substringBefore('.').toLong(), p[1].toDouble(), p[2].toDouble(), p[3].toDouble())
        }
        val band = lines("band").map { l ->
            val p = l.split(','); p[0].substringBefore('.').toLong() to p[1].toInt()
        }
        println("[$suffix] streams: hr=${hr.size} rr=${rr.size} grav=${grav.size} band=${band.size}")

        val trace = ArrayList<String>()
        val sessions = SleepStager.detectSleep(
            hr = hr, rr = rr, resp = emptyList(), gravity = grav,
            tzOffsetSeconds = tz, wristOff = emptyList(), bandSleepState = band,
            useSleepStagerV2 = false,
            traceSink = { trace.add(it) },
        )
        println("[$suffix] V1 detected ${sessions.size} sessions:")
        for (s in sessions) {
            val fmt = java.time.Instant.ofEpochSecond(s.start + tz)
            val fmt2 = java.time.Instant.ofEpochSecond(s.end + tz)
            println("  $fmt -> $fmt2 eff=${"%.2f".format(s.efficiency)} day=${AnalyticsEngine.dayString(s.end, tz)}")
        }
        println("[$suffix] ---- gate trace (${trace.size} lines) ----")
        trace.forEach { println("[$suffix] $it") }
    }

    @Test
    fun replaySun12NightWindow() {
        assumeTrue("scratchpad CSVs not present; diagnostic skipped", File(dir, "hr.csv").exists())
        replay("")        // full past-day window [Fri 18:00, Mon 00:00]
        replay("_cut")    // as-of ~13:05 Sun (the shape the release pass saw when it scored the day)
    }
}
