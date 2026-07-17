package com.noop.analytics

import com.noop.data.HrSample
import com.noop.data.RrInterval
import com.noop.data.StepSample
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/*
 * DaytimeStress.kt — intraday autonomic stress proxy from banked HR + R-R, with gravity
 * motion + step activity-class + sedentary-bout calm gates.
 *
 * CONTINUED 2026-07-12 WHOOP Stress Monitor match:
 *   • 5-minute buckets (was 15m ← 1h) — denser WHOOP-like curve without inventing beats.
 *   • Quiet HR p10/p25; calm logistic raw=0 → ~0.5 LOW.
 *   • Gravity L2 + step walk/run → stiller BPM subset; sedentary / still → calm bias.
 *   • Night scored near floor; waking-only calm reference (#357).
 *   • [HourPoint.asleep] marks sleep-window / band-asleep for Stress UI moon band.
 *
 * APPROXIMATE / non-clinical — not WHOOP’s proprietary Stress Monitor.
 */
object DaytimeStress {

    // MARK: - Tunables

    /** 5-minute buckets — WHOOP chart is near-continuous; 15m still looked stair-stepped. */
    const val bucketSeconds: Long = 300L
    /** ~12 buckets per clock hour (for UI hour counts / zone minutes). */
    const val bucketsPerHour: Int = 12

    /** Min HR samples per 5-min bucket (~25s at 1 Hz; scales from prior 75/15-min). */
    const val minHourHrSamples: Int = 25
    /** Adaptive floor when the day has few scored windows (Fable Stress #32). */
    const val minHourHrSamplesSparse: Int = 18
    const val minHourHrSpanSeconds: Long = 20L
    /** If fewer than this many buckets meet the dense gate, retry sparse gate once. */
    const val sparseDayBucketCap: Int = 16
    const val minPlausibleBpm: Int = 30
    const val maxPlausibleBpm: Int = 220
    const val highBandFloor: Double = 2.0
    /** ~3 waking hours of consecutive HIGH 5-min buckets. */
    const val sustainedHours: Int = 3
    val sustainedBuckets: Int get() = sustainedHours * bucketsPerHour

    /** Wall minutes covered by one scored bucket (UI captions). */
    val bucketMinutes: Int get() = (bucketSeconds / 60L).toInt()

    const val wakingStartHour: Int = 6
    const val wakingEndHour: Int = 22
    /**
     * Soft raw damp when a waking-band hour sits inside a detected sleep window (late sleepers
     * whose night runs past [wakingStartHour]). WHOOP keeps a night floor through late morning on
     * the 13 Jul screenshot night; without this, those hours pollute the calm reference.
     */
    const val sleepWindowCalmBias: Double = 1.55
    /** Optional tighter waking window (Fable Stress #31). */
    const val tightWakingStartHour: Int = 7
    const val tightWakingEndHour: Int = 21

    /**
     * raw=0 → ~0.36 LOW. Raised again 2026-07-14 afternoon pack: WHOOP tip/high ~0.7 with
     * chart spikes ~0.4–1.8 while NOOP still read hotter — calm floor + motion damp together.
     */
    const val calmAnchorOffset: Double = 2.00
    /**
     * Gravity L2 mean ≥ this → motion-busy. Slightly lower than 0.035 so light ambulation
     * gets the stronger [motionBusyDamp] instead of scoring as desk HR elevation.
     */
    const val motionBusyFloor: Double = 0.030
    /** Soft damp when gravity/step marks the bucket busy — cuts motion false highs. */
    const val motionBusyDamp: Double = 0.58
    /** Extra raw damp when bucket is inside a sedentary bout or dominated by still class. */
    const val sedentaryCalmBias: Double = 0.65
    const val nightCalmBias: Double = 1.35
    /**
     * When waking quiet HR is within this many bpm of overnight quiet, pull toward LOW
     * (Fable Stress #12 — overnight RHR absolute anchor).
     */
    const val overnightAnchorSlackBpm: Double = 5.0
    const val overnightCalmBias: Double = 0.55
    /**
     * Soft tip ceiling outside clock waking hours (NOW hero). Caps stale evening HIGH so a 2.7
     * desk spike doesn't stick past midnight — but must still allow WHOOP-like overnight
     * *awake* tips (Fold 2026-07-17 SS: WHOOP 1.5 @ 12:45 AM / 1.4 @ 5:20 AM while Gilbert's
     * sleep window is ~06–13). Cap was 1.0 (SHIP #83) and under-read those tips by ~0.5.
     */
    const val nightTipCeiling: Double = 1.55
    /**
     * Tighter ceiling when the tip bucket is inside a detected sleep window / band asleep.
     * WHOOP sleep-band tips sit ~0.2–0.9; keep Now from reading MEDIUM while the moon band is on.
     */
    const val sleepTipCeiling: Double = 0.95
    /** Soft blend of prior days' resting/calm HR into today's calm reference (Fable #11). */
    const val priorCalmBlendMax: Int = 14
    /** Baevsky SI soft damp / bump thresholds (Fable #16) — day-level lens, not per-bucket. */
    const val baevskyCalmSi: Double = 80.0
    const val baevskyHighSi: Double = 200.0
    const val baevskyCalmBias: Double = 0.30
    const val baevskyHighBump: Double = 0.15
    /** Trusted HF power soft damp (Fable #17) when HF ≥ 40% of LF+HF. */
    const val hfCalmShare: Double = 0.40
    const val hfCalmBias: Double = 0.25
    /** Logged workout window damp so Effort sessions don't inflate Stress the same way (Fable #62). */
    const val workoutOverlapBias: Double = 0.62
    /** Tighter Malik ectopic fraction under motion-busy buckets (Fable #15). */
    const val ectopicMotionThreshold: Double = 0.15
    /** WHOOP-style personalization: nights of prior calm needed (Fable #50). */
    const val calibrationNightsTarget: Int = 4
    /** Band sleep_state == asleep (HistoricalStreams @81 nibble). */
    const val sleepStateAsleep: Int = 2
    /** Soft bump when day skin-temp deviation is elevated (Fable #19). */
    const val skinElevatedAbsC: Double = 0.5
    const val skinElevatedBias: Double = 0.20
    /** Soft bump when day resp is elevated vs calm (Fable #21). */
    const val respElevatedBpm: Double = 18.0
    const val respElevatedBias: Double = 0.15
    const val KEY_DAYTIME_STRESS: String = "daytime_stress"
    const val SOURCE_DAYTIME_STRESS: String = "my-whoop-noop"

    // Step @63 activity class (community #316): 0=still, 1=walk, 2=run.
    const val stepClassStill: Int = 0
    const val stepClassWalk: Int = 1
    const val stepClassRun: Int = 2

    // MARK: - Output

    data class HourPoint(
        /** Local clock hour 0–23 (for waking filters / labels). */
        val hour: Int,
        val startTs: Long,
        val level: Double?,
        /** Quiet (lower-tail) HR bpm; name kept for schema stability. */
        val meanHr: Double?,
        val rmssd: Double?,
        /** Gravity/step walk-run busy — UI glyph + scrub caption (Fable #35/#49). */
        val motionBusy: Boolean = false,
        /** Band sleep_state asleep and/or inside a detected sleep session window. */
        val asleep: Boolean = false,
    ) {
        val hasData: Boolean get() = level != null
    }

    data class Result(
        val hours: List<HourPoint>,
        val sustainedHigh: Boolean,
        val sustainedRun: Int,
        val dayMean: Double?,
        val peak: HourPoint?,
        /** Distinct prior days that contributed to multi-day calm (Fable #50). */
        val priorCalmDayCount: Int = 0,
        /**
         * When [scored] is empty: today's HR sample count toward the first tip gate
         * ([minHourHrSamples]). UI-only honesty — never invents a stress number.
         */
        val bankingHrSamples: Int = 0,
        /**
         * Latest tip sits in a sleep window / band-asleep sample — drives sleepTipCeiling
         * and the Stress UI asleep cue without every caller recomputing windows.
         */
        val inSleepBandNow: Boolean = false,
    ) {
        val scored: List<HourPoint> get() = hours.filter { it.level != null }

        /** Nights still needed for a personal calm baseline; 0 when ready. */
        val calibrationNightsRemaining: Int
            get() = (calibrationNightsTarget - priorCalmDayCount).coerceAtLeast(0)

        /** Scored coverage in clock-hours (5-min buckets ÷ 12). */
        val scoredHoursApprox: Double
            get() = scored.size.toDouble() / bucketsPerHour

        /** Exact high-zone minutes: scored buckets with level ≥ [highBandFloor] × bucket minutes. */
        val highZoneMinutes: Int
            get() = minutesForBuckets(scored.count { (it.level ?: 0.0) >= highBandFloor })

        val calmZoneMinutes: Int
            get() = minutesForBuckets(scored.count { (it.level ?: 0.0) < 1.0 })

        val moderateZoneMinutes: Int
            get() = minutesForBuckets(
                scored.count {
                    val l = it.level ?: return@count false
                    l >= 1.0 && l < highBandFloor
                },
            )

        /**
         * Mean stress across asleep / sleep-window buckets (last night on the day timeline).
         * Null when no scored sleep-band points — never invents a floor.
         */
        val lastNightMean: Double?
            get() {
                val xs = scored.filter { it.asleep }.mapNotNull { it.level }
                return if (xs.isEmpty()) null else xs.sum() / xs.size
            }

        companion object {
            val EMPTY = Result(emptyList(), sustainedHigh = false, sustainedRun = 0,
                dayMean = null, peak = null, priorCalmDayCount = 0)

            /** Bucket count → wall-clock minutes (5-min steps; no round-up inflation). */
            fun minutesForBuckets(buckets: Int): Int =
                if (buckets <= 0) 0 else (buckets * (bucketSeconds / 60L)).toInt()

            /** WHOOP-style "2 hr 8 min" / "45 min". */
            fun formatZoneDuration(minutes: Int): String {
                if (minutes <= 0) return "0 min"
                val hr = minutes / 60
                val min = minutes % 60
                return when {
                    hr > 0 && min > 0 -> "$hr hr $min min"
                    hr > 0 -> "$hr hr"
                    else -> "$min min"
                }
            }

            /** Compact axis label: "2h 8m" / "45m" / "—". */
            fun formatZoneCompact(minutes: Int): String {
                if (minutes <= 0) return "—"
                val hr = minutes / 60
                val min = minutes % 60
                return when {
                    hr > 0 && min > 0 -> "${hr}h ${min}m"
                    hr > 0 -> "${hr}h"
                    else -> "${min}m"
                }
            }
        }
    }

    // MARK: - Math

    private fun mean(xs: List<Double>): Double? =
        if (xs.isEmpty()) null else xs.sum() / xs.size

    private fun std(xs: List<Double>, m: Double?): Double {
        if (m == null || xs.size <= 1) return 0.0
        val v = xs.sumOf { (it - m) * (it - m) } / xs.size
        return sqrt(v)
    }

    private fun rawScore(
        hr: Double?, meanHr: Double?, sdHr: Double,
        rmssd: Double?, meanRmssd: Double?, sdRmssd: Double,
    ): Double {
        var sum = 0.0
        if (hr != null && meanHr != null && sdHr > 0.0001) {
            sum += (hr - meanHr) / sdHr
        }
        if (rmssd != null && meanRmssd != null && sdRmssd > 0.0001) {
            sum += (meanRmssd - rmssd) / sdRmssd
        }
        return sum
    }

    private fun squash(raw: Double): Double =
        (3.0 / (1.0 + exp(-(raw - calmAnchorOffset)))).coerceIn(0.0, 3.0)

    // MARK: - Public API

    fun analyze(
        hr: List<HrSample>,
        rr: List<RrInterval>,
        tzOffsetSeconds: Long = 0L,
        motion: List<ActivityPoint> = emptyList(),
        steps: List<StepSample> = emptyList(),
        sedentaryBouts: List<InactivityPeriod> = emptyList(),
        wakingStart: Int = wakingStartHour,
        wakingEnd: Int = wakingEndHour,
        /** Prior days' calm/resting HR bpm (multi-day baseline, Fable #11). */
        priorCalmHrs: List<Double> = emptyList(),
        /** Day-level Baevsky SI when trusted (≥20 clean beats). */
        daySi: Double? = null,
        /** True when HF share of LF+HF is high enough to soft-damp (Fable #17). */
        hfVagalTrusted: Boolean = false,
        /** Wall-clock [start, end] pairs for logged workouts today (Fable #62). */
        workoutWindows: List<Pair<Long, Long>> = emptyList(),
        /** Band sleep_state samples (wall ts -> state); state 2 = asleep (Fable #22). */
        sleepState: List<Pair<Long, Int>> = emptyList(),
        /** Detected sleep session [start, end) wall pairs — night-floor for late sleep past 06:00. */
        sleepWindows: List<Pair<Long, Long>> = emptyList(),
        /** Day skin-temp deviation elevated (Fable #19). */
        skinElevated: Boolean = false,
        /** Day respiratory rate elevated (Fable #21). */
        respElevated: Boolean = false,
        /** Distinct prior calm days contributing to [priorCalmHrs] (Fable #50). */
        priorCalmDayCount: Int = 0,
    ): Result {
        val usableHr = hr.filter { it.bpm in minPlausibleBpm..maxPlausibleBpm }
        if (usableHr.isEmpty()) return Result.EMPTY

        val asleepCountByBucket = HashMap<Long, Int>()
        for ((ts, state) in sleepState) {
            if (state != sleepStateAsleep) continue
            val localTs = ts + tzOffsetSeconds
            val bucket = floorDiv(localTs, bucketSeconds) * bucketSeconds
            asleepCountByBucket[bucket] = (asleepCountByBucket[bucket] ?: 0) + 1
        }

        fun bandAsleep(bucketLocal: Long): Boolean =
            (asleepCountByBucket[bucketLocal] ?: 0) >= 3

        fun inSleepWindow(wallMid: Long): Boolean =
            sleepWindows.any { (a, b) -> wallMid in a until b }

        fun waking(bucketLocal: Long): Boolean {
            val h = localHourOfDay(bucketLocal)
            if (h < wakingStart || h >= wakingEnd) return false
            // Sleep-state asleep majority -> night for calm reference / sustained (Fable #22).
            if (bandAsleep(bucketLocal)) return false
            // Detected sleep windows (late sleep past wakingStart) also leave the calm pool.
            val wallMid = (bucketLocal - tzOffsetSeconds) + bucketSeconds / 2
            if (inSleepWindow(wallMid)) return false
            return true
        }

        fun inWorkout(wallMid: Long): Boolean =
            workoutWindows.any { (a, b) -> wallMid in a..b }

        val hrByBucket = HashMap<Long, MutableList<HrSample>>()
        for (s in usableHr) {
            val localTs = s.ts + tzOffsetSeconds
            val bucket = floorDiv(localTs, bucketSeconds) * bucketSeconds
            hrByBucket.getOrPut(bucket) { ArrayList() }.add(s)
        }
        val rrByBucket = HashMap<Long, MutableList<Double>>()
        for (s in rr) {
            val localTs = s.ts + tzOffsetSeconds
            val bucket = floorDiv(localTs, bucketSeconds) * bucketSeconds
            rrByBucket.getOrPut(bucket) { ArrayList() }.add(s.rrMs.toDouble())
        }
        val motionByBucket = HashMap<Long, MutableList<Double>>()
        for (p in motion) {
            val localTs = p.ts + tzOffsetSeconds
            val bucket = floorDiv(localTs, bucketSeconds) * bucketSeconds
            motionByBucket.getOrPut(bucket) { ArrayList() }.add(p.intensity)
        }
        val stepClassByBucket = HashMap<Long, MutableList<Int>>()
        for (s in steps) {
            val cls = s.activityClass ?: continue
            val localTs = s.ts + tzOffsetSeconds
            val bucket = floorDiv(localTs, bucketSeconds) * bucketSeconds
            stepClassByBucket.getOrPut(bucket) { ArrayList() }.add(cls)
        }

        data class HourAgg(
            val bucket: Long,
            val meanHr: Double?,
            val rmssd: Double?,
            val motionBusy: Boolean,
            val sedentaryCalm: Boolean,
            val workoutOverlap: Boolean,
            val bandAsleep: Boolean,
        )
        val orderedBuckets = hrByBucket.keys.sorted()
        val aggs = ArrayList<HourAgg>(orderedBuckets.size)
        for (b in orderedBuckets) {
            val hrs = (hrByBucket[b] ?: emptyList()).distinctBy { it.ts }
            val span = (hrs.maxOfOrNull { it.ts } ?: 0L) - (hrs.minOfOrNull { it.ts } ?: 0L)
            val motionMean = mean(motionByBucket[b] ?: emptyList()) ?: 0.0
            val classes = stepClassByBucket[b] ?: emptyList()
            val walkRun = classes.count { it == stepClassWalk || it == stepClassRun }
            val stillN = classes.count { it == stepClassStill }
            // Strict walk/run majority (not tie) — ties stay calm; real ambulation still damps hard.
            val stepBusy = classes.isNotEmpty() && walkRun > stillN && walkRun >= 3
            val stepStill = classes.isNotEmpty() && stillN > walkRun
            val motionBusy = motionMean >= motionBusyFloor || stepBusy
            val wallMid = (b - tzOffsetSeconds) + bucketSeconds / 2
            val inSedentary = sedentaryBouts.any { wallMid in it.start..it.end }
            val sedentaryCalm = inSedentary || stepStill
            val workoutOverlap = inWorkout(wallMid)
            val asleep = bandAsleep(b)
            val quietHr = if (hrs.size >= minHourHrSamplesSparse && span >= minHourHrSpanSeconds) {
                quietHourHr(hrs.map { it.bpm.toDouble() }, motionBusy = motionBusy)
            } else null
            val rrRes = HrvAnalyzer.analyzeRaw(
                rrByBucket[b] ?: emptyList(),
                ectopicThreshold = if (motionBusy) ectopicMotionThreshold else null,
            )
            aggs.add(HourAgg(b, quietHr, rrRes.rmssd, motionBusy, sedentaryCalm, workoutOverlap, asleep))
        }

        val referenceAggs = aggs.filter { waking(it.bucket) }
        val hrMeans = referenceAggs.mapNotNull { it.meanHr }
        val overnightQuiet = calmReference(
            aggs.filter { !waking(it.bucket) }.mapNotNull { it.meanHr },
            calmIsLow = true,
        )
        // Multi-day calm (#11): blend prior resting/calm HR into today's waking calm pool.
        val prior = priorCalmHrs.filter { it in minPlausibleBpm.toDouble()..maxPlausibleBpm.toDouble() }
            .takeLast(priorCalmBlendMax)
        val calmPool = hrMeans + prior
        val refHr = calmReference(calmPool.ifEmpty { hrMeans }, calmIsLow = true)
        val rmssdVals = referenceAggs.mapNotNull { it.rmssd }
        val refRmssd = calmReference(rmssdVals, calmIsLow = false)
        val sdHr = maxOf(std(hrMeans.ifEmpty { calmPool }, mean(hrMeans.ifEmpty { calmPool })), 3.0)
        val sdRmssd = maxOf(std(rmssdVals, mean(rmssdVals)), 5.0)
        val priorDays = priorCalmDayCount.coerceIn(0, priorCalmBlendMax)

        val points = ArrayList<HourPoint>(aggs.size)
        for (a in aggs) {
            val hourOfDay = localHourOfDay(a.bucket)
            val wallStart = a.bucket - tzOffsetSeconds
            val wallMidScore = wallStart + bucketSeconds / 2
            val asleepFlag = a.bandAsleep || inSleepWindow(wallMidScore)
            val level: Double? = if (a.meanHr != null) {
                var raw = rawScore(a.meanHr, refHr, sdHr, a.rmssd, refRmssd, sdRmssd)
                if (!waking(a.bucket) || asleepFlag) {
                    raw -= if (inSleepWindow(wallMidScore)) sleepWindowCalmBias else nightCalmBias
                }
                if (a.motionBusy) raw -= motionBusyDamp
                if (a.sedentaryCalm) raw -= sedentaryCalmBias
                if (waking(a.bucket) && !a.motionBusy && overnightQuiet != null &&
                    a.meanHr <= overnightQuiet + overnightAnchorSlackBpm
                ) {
                    raw -= overnightCalmBias
                }
                if (waking(a.bucket) && daySi != null) {
                    when {
                        daySi < baevskyCalmSi -> raw -= baevskyCalmBias
                        daySi > baevskyHighSi -> raw += baevskyHighBump
                    }
                }
                if (waking(a.bucket) && hfVagalTrusted) raw -= hfCalmBias
                if (a.workoutOverlap) raw -= workoutOverlapBias
                if (waking(a.bucket) && skinElevated) raw += skinElevatedBias
                if (waking(a.bucket) && respElevated) raw += respElevatedBias
                squash(raw)
            } else null
            points.add(
                HourPoint(
                    hourOfDay, wallStart, level, a.meanHr, a.rmssd,
                    motionBusy = a.motionBusy,
                    asleep = asleepFlag,
                ),
            )
        }

        // Fill missing 5-min slots between first→last so the chart is a dense time axis
        // (honest null gaps — never invents stress levels). Moon-band fillers use sleep
        // windows OR band sleep_state so the indigo wash stays continuous across sparse HR.
        val dense = expandContiguousBuckets(points, tzOffsetSeconds) { wallMid ->
            val localBucket = floorDiv(wallMid + tzOffsetSeconds, bucketSeconds) * bucketSeconds
            inSleepWindow(wallMid) || bandAsleep(localBucket)
        }

        // Sustained-high ignores workout-overlapping buckets (breathe nudge ≠ mid-workout).
        val wakingScored = dense.mapNotNull { p ->
            val lvl = p.level
            if (lvl == null || p.hour < wakingStart || p.hour >= wakingEnd) return@mapNotNull null
            val wallMid = p.startTs + bucketSeconds / 2
            if (inWorkout(wallMid)) return@mapNotNull null
            if (p.asleep) return@mapNotNull null
            p to lvl
        }
        if (wakingScored.isEmpty() && dense.none { it.level != null }) {
            return if (dense.isEmpty()) Result.EMPTY
            else Result(dense, sustainedHigh = false, sustainedRun = 0, dayMean = null, peak = null,
                priorCalmDayCount = priorDays)
        }

        var run = 0
        for ((_, lvl) in wakingScored.asReversed()) {
            if (lvl >= highBandFloor) run += 1 else break
        }
        val sustained = run >= sustainedBuckets
        val dayMean = mean(
            dense.filter { it.level != null && it.hour >= wakingStart && it.hour < wakingEnd && !it.asleep }
                .mapNotNull { it.level }
                .ifEmpty { dense.mapNotNull { it.level } },
        )
        val peak = dense.mapNotNull { p ->
            p.level?.takeIf { p.hour >= wakingStart && p.hour < wakingEnd && !p.asleep }?.let { p to it }
        }.maxByOrNull { it.second }?.first
            ?: dense.mapNotNull { p -> p.level?.let { p to it } }.maxByOrNull { it.second }?.first

        val lastScored = dense.lastOrNull { it.level != null }
        val inSleepNow = lastScored?.asleep == true

        return Result(
            dense, sustained, run, dayMean, peak,
            priorCalmDayCount = priorDays,
            inSleepBandNow = inSleepNow,
        )
    }

    /**
     * Insert null [HourPoint]s for every missing [bucketSeconds] between the first and last
     * observed bucket so Stress charts space points on a real timeline (WHOOP-dense axis).
     * [asleepAtWallMid] marks sleep-window fillers so the moon band stays continuous.
     */
    internal fun expandContiguousBuckets(
        points: List<HourPoint>,
        tzOffsetSeconds: Long,
        asleepAtWallMid: (Long) -> Boolean = { false },
    ): List<HourPoint> {
        if (points.size < 2) return points
        val byWall = points.associateBy { it.startTs }
        val first = points.minOf { it.startTs }
        val last = points.maxOf { it.startTs }
        val out = ArrayList<HourPoint>(((last - first) / bucketSeconds).toInt() + 1)
        var wall = first
        while (wall <= last) {
            val existing = byWall[wall]
            if (existing != null) {
                out.add(existing)
            } else {
                val local = wall + tzOffsetSeconds
                val wallMid = wall + bucketSeconds / 2
                out.add(
                    HourPoint(
                        hour = localHourOfDay(floorDiv(local, bucketSeconds) * bucketSeconds),
                        startTs = wall,
                        level = null,
                        meanHr = null,
                        rmssd = null,
                        asleep = asleepAtWallMid(wallMid),
                    ),
                )
            }
            wall += bucketSeconds
        }
        return out
    }

    // MARK: - Helpers

    private fun floorDiv(a: Long, b: Long): Long {
        val q = a / b
        val r = a % b
        return if (r != 0L && (r < 0L) != (b < 0L)) q - 1 else q
    }

    /** Local clock hour 0–23 from a local-epoch bucket start (works for any bucketSeconds). */
    internal fun localHourOfDay(bucketLocal: Long): Int =
        floorDiv(Math.floorMod(bucketLocal, 86_400L), 3_600L).toInt()

    private fun isWakingBucket(bucketLocal: Long): Boolean {
        val h = localHourOfDay(bucketLocal)
        return h >= wakingStartHour && h < wakingEndHour
    }

    private fun quietHourHr(bpms: List<Double>, motionBusy: Boolean): Double? {
        if (bpms.isEmpty()) return null
        val pool = if (motionBusy && bpms.size >= 8) {
            val mid = bpms.sorted()[bpms.size / 2]
            bpms.filter { it <= mid }.ifEmpty { bpms }
        } else bpms
        if (pool.size < 4) return mean(pool)
        // Dense calm → p10; heavy motion → p5 (stiller tail); light → p25.
        val q = when {
            motionBusy && pool.size >= 16 -> 0.05
            pool.size >= 20 -> 0.10
            else -> 0.25
        }
        return quantile(pool.sorted(), q)
    }

    private fun calmReference(xs: List<Double>, calmIsLow: Boolean): Double? {
        if (xs.isEmpty()) return null
        if (xs.size < 4) return mean(xs)
        val s = xs.sorted()
        return if (calmIsLow) quantile(s, 0.25) else quantile(s, 0.75)
    }

    private fun quantile(sorted: List<Double>, q: Double): Double {
        val n = sorted.size
        if (n == 0) return 0.0
        if (n == 1) return sorted[0]
        val pos = q * (n - 1)
        val lo = pos.toInt()
        val hi = min(lo + 1, n - 1)
        val frac = pos - lo
        return sorted[lo] + frac * (sorted[hi] - sorted[lo])
    }
}
