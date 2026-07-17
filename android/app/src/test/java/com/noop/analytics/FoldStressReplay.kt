package com.noop.analytics

import com.noop.data.GravitySample
import com.noop.data.HrSample
import com.noop.data.RrInterval
import com.noop.data.StepSample
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * DIAGNOSTIC REPLAY (not a regression test — auto-skips when the local CSV dumps are absent).
 *
 * Mission B (FABLE5_MEGA_PROMPT / Fable #206-#207): replay the REAL DaytimeStress pipeline over the
 * Fold's banked streams for Fri 10 / Sat 11 / Sun 12 Jul and print per-day tip curve + zone minutes,
 * so the STRESS_FACTORS δ table can be filled against the WHOOP anchors:
 *   Fri high-zone 2h08m · Sat high-zone 3h02m · Sun tip ~21:39 = 2.1 HIGH (NOOP UI showed 2.4).
 * Mirrors loadDaytimeStressShared's input assembly (motion series, sedentary bouts, SI, HF, auto
 * workout damp windows, band sleep state, prior-day calm HR).
 */
class FoldStressReplay {

    private val dir = File(
        "C:/Users/Gilbert/AppData/Local/Temp/claude/C--Users-Gilbert/" +
            "3848041f-b916-4190-b3c2-ae14525e2350/scratchpad",
    )
    private val tz = -18_000L

    private fun replayDay(label: String, priorCalm: List<Double>, priorDays: Int, cutoffTs: Long? = null) {
        val fileLabel = label.substringBefore('@')
        fun lines(name: String) = File(dir, "st_${fileLabel}_$name.csv").readLines().filter { it.isNotBlank() }
            .filter { cutoffTs == null || it.substringBefore(',').substringBefore('.').toLong() <= cutoffTs }
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
        val steps = lines("steps").map { l ->
            val p = l.split(',')
            StepSample("d", p[0].substringBefore('.').toLong(), p[1].toInt(), p.getOrNull(2)?.toIntOrNull())
        }
        val band = lines("band").map { l ->
            val p = l.split(','); p[0].substringBefore('.').toLong() to p[1].toInt()
        }

        val motion = if (grav.isNotEmpty()) WorkoutDetector.activitySeries(grav) else emptyList()
        val sedentary = if (grav.size >= 2) SedentaryDetector.detectSedentaryBouts(grav, minMinutes = 20) else emptyList()
        val daySi = StressIndex.stressIndex(rr)
        val freq = HrvFreqDomain.freqDomain(rr)
        val hfVagal = freq?.let { b ->
            val lf = b.lf ?: return@let false
            val denom = lf + b.hf
            denom > 0.0 && (b.hf / denom) >= DaytimeStress.hfCalmShare
        } ?: false
        val workoutWindows = ArrayList<Pair<Long, Long>>()
        runCatching {
            AutoWorkoutDetector.detect(hr, gravity = grav).forEach { workoutWindows.add(it.startSec to it.endSec) }
        }

        val res = DaytimeStress.analyze(
            hr, rr, tz,
            motion = motion, steps = steps, sedentaryBouts = sedentary,
            priorCalmHrs = priorCalm, daySi = daySi, hfVagalTrusted = hfVagal,
            workoutWindows = workoutWindows,
            sleepState = band,
            priorCalmDayCount = priorDays,
        )
        println("[$label] scored=${res.scored.size} buckets (~${"%.1f".format(res.scoredHoursApprox)}h) " +
            "dayMean=${res.dayMean?.let { "%.2f".format(it) }} peak=${res.peak?.level?.let { "%.2f".format(it) }}@${res.peak?.hour}h " +
            "high=${DaytimeStress.Result.formatZoneCompact(res.highZoneMinutes)} " +
            "mod=${DaytimeStress.Result.formatZoneCompact(res.moderateZoneMinutes)} " +
            "calm=${DaytimeStress.Result.formatZoneCompact(res.calmZoneMinutes)} " +
            "sustainedHigh=${res.sustainedHigh} si=${daySi?.let { "%.0f".format(it) }} hf=$hfVagal " +
            "workoutWins=${workoutWindows.size}")
        // Tip curve, hourly means of the 15-min levels for a compact WHOOP-comparable read.
        val byHour = res.scored.groupBy { it.hour }
        val curve = (0..23).joinToString(" ") { h ->
            val ls = byHour[h]?.mapNotNull { it.level }
            if (ls.isNullOrEmpty()) "$h:—" else "$h:" + "%.1f".format(ls.average())
        }
        println("[$label] curve $curve")
        // The evening tip at ~21:30-21:45 local (the WHOOP SS anchor on Sun).
        val tipBuckets = res.scored.filter { it.hour == 21 }
        if (tipBuckets.isNotEmpty()) {
            println("[$label] 21h buckets: " + tipBuckets.joinToString { "%.2f".format(it.level ?: 0.0) })
        }
    }

    /** Fable #322: strain over the same real days (was NULL — any >90 s HR gap blanked the day).
     *  WHOOP anchors (0–21): Fri 14.8 · Sat 9.7 · Sun 2.8. NOOP stores 0–100 (×0.21 → WHOOP scale). */
    @Test
    fun replayStrainFriSatSun() {
        assumeTrue("scratchpad CSVs not present; diagnostic skipped", File(dir, "st_fri_hr.csv").exists())
        val rhr = mapOf("fri" to 55.0, "sat" to 52.0, "sun" to 53.0)
        for (label in listOf("fri", "sat", "sun")) {
            val hr = File(dir, "st_${label}_hr.csv").readLines().filter { it.isNotBlank() }.map { l ->
                val p = l.split(','); com.noop.data.HrSample("d", p[0].substringBefore('.').toLong(), p[1].toInt())
            }
            val s = StrainScorer.strain(
                hr, maxHR = StrainScorer.tanakaHRmax(30.0), restingHR = rhr.getValue(label),
            )
            println("[strain-$label] n=${hr.size} strain0to100=$s whoopScale=${s?.let { "%.1f".format(it * 0.21) }}")
        }
    }

    @Test
    fun replayFriSatSun() {
        assumeTrue("scratchpad CSVs not present; diagnostic skipped", File(dir, "st_fri_hr.csv").exists())
        replayDay("fri", priorCalm = emptyList(), priorDays = 0)
        replayDay("sat", priorCalm = listOf(55.0), priorDays = 1)
        replayDay("sun", priorCalm = listOf(55.0, 52.0), priorDays = 2)
        // Live-at-21:40 semantics for the tip@clock δ row (the WHOOP 2.1 HIGH screenshot anchor):
        // the day's calm reference then only had data up to that minute. 2026-07-12 21:40 local(-5) =
        // 2026-07-13 02:40 UTC = 1783997000... computed: epoch for 2026-07-12 21:40 EST.
        replayDay("sun@2140", priorCalm = listOf(55.0, 52.0), priorDays = 2,
            cutoffTs = java.time.LocalDateTime.of(2026, 7, 12, 21, 40)
                .toEpochSecond(java.time.ZoneOffset.ofTotalSeconds(tz.toInt())))
    }
}
