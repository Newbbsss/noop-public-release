package com.noop.ui

import android.content.Context
import com.noop.analytics.AutoWorkoutDetector
import com.noop.analytics.DaytimeStress
import com.noop.analytics.HrvFreqDomain
import com.noop.analytics.SedentaryDetector
import com.noop.analytics.StressIndex
import com.noop.analytics.WorkoutDetector
import com.noop.data.MetricSeriesRow
import com.noop.data.WhoopRepository
import java.time.LocalDate

/**
 * Daily stress series for [StressModel] / Today pin — classic `stress` plus banked
 * [DaytimeStress.KEY_DAYTIME_STRESS] tips across the active∪canonical∪computed union.
 *
 * Hot-fix 2026-07-13: Stress used to read only `metricSeries("my-whoop","stress")`. On a live
 * install the overnight RHR/HRV row for *today* is often still null (mid-day / post-midnight), and
 * the only banked tip lives under `my-whoop-noop` / `daytime_stress` — so the screen fell through
 * to "Pair your strap" despite wear history + HR. Classic `stress` wins on a day both keys cover.
 *
 * Wear-location / dual-algo sibling: keep this loader as the shared Today↔Stress tip pipe. Prefer
 * extending [DaytimeStress] / prefs here over forking reads in StressScreen so Key Metrics Stay
 * aligned. Overnight HRV for Stress baselines still comes from DailyMetric.avgHrv (type-40 bank).
 */
suspend fun loadStressStoredSeries(vm: AppViewModel): Map<String, Double> {
    val ids = (
        WhoopRepository.importedSourceIdsFor(vm.activeStrapId) +
            WhoopRepository.computedSourceIdsFor(vm.activeStrapId) +
            listOf(DaytimeStress.SOURCE_DAYTIME_STRESS)
        ).distinct()
    val classic = linkedMapOf<String, Double>()
    val daytime = linkedMapOf<String, Double>()
    for (id in ids) {
        runCatching {
            vm.repo.metricSeries(id, "stress", "0000-01-01", "9999-12-31")
        }.getOrDefault(emptyList()).forEach { row ->
            classic.putIfAbsent(row.day, row.value.coerceIn(0.0, 3.0))
        }
        runCatching {
            vm.repo.metricSeries(id, DaytimeStress.KEY_DAYTIME_STRESS, "0000-01-01", "9999-12-31")
        }.getOrDefault(emptyList()).forEach { row ->
            daytime.putIfAbsent(row.day, row.value.coerceIn(0.0, 3.0))
        }
    }
    // Daytime tips fill gaps; a persisted daily `stress` row still wins that day.
    return daytime + classic
}

/**
 * Distinct prior calendar days with a resting HR — the Wear N of 4 / calm-baseline count.
 * Unions imported + computed sources so a strap UUID without RHR still picks up `my-whoop` /
 * `-noop` nights (the old active-only + ifEmpty path stuck at 0 of 4 while history lived elsewhere).
 */
suspend fun loadPriorCalmDayCount(vm: AppViewModel): Pair<Int, List<Double>> {
    val todayKey = LocalDate.now().toString()
    val priorFrom = LocalDate.now().minusDays(DaytimeStress.priorCalmBlendMax.toLong()).toString()
    val ids = (
        WhoopRepository.importedSourceIdsFor(vm.activeStrapId) +
            WhoopRepository.computedSourceIdsFor(vm.activeStrapId) +
            listOf(vm.activeStrapId, "my-whoop")
        ).distinct()
    // First RHR wins per day (imported before computed via source order above).
    val byDay = linkedMapOf<String, Double>()
    for (id in ids) {
        runCatching {
            vm.repo.dailyMetrics(id, priorFrom, todayKey)
        }.getOrDefault(emptyList()).forEach { row ->
            val rhr = row.restingHr?.toDouble() ?: return@forEach
            if (row.day < todayKey) byDay.putIfAbsent(row.day, rhr)
        }
    }
    return byDay.size to byDay.values.toList()
}

/**
 * Shared Today/Stress daytime load — same streams StressScreen uses (HR ∪ R-R ∪ gravity ∪ steps ∪
 * sedentary ∪ prior calm ∪ workouts ∪ sleep_state ∪ day vitals). Returns [DaytimeStress.Result.EMPTY]
 * when under-gated. MAIN hero/CalmTime/pins prefer this Now tip over the daily RHR/HRV load when scored.
 *
 * Band HR already COALESCE-unions PPG-derived gaps ([WhoopDao.hrSamples]) — Fable Stress #23.
 * Persists Now tip under [DaytimeStress.KEY_DAYTIME_STRESS] (Fable #42).
 *
 * Prior calm nights are counted via [loadPriorCalmDayCount] *before* the HR gate so Wear N of 4
 * stays honest when today's daytime series is still under-gated.
 */
suspend fun loadDaytimeStressShared(
    vm: AppViewModel,
    context: Context? = null,
): DaytimeStress.Result {
    val nowSeconds = System.currentTimeMillis() / 1000L
    val tzOffsetSeconds = java.util.TimeZone.getDefault().getOffset(nowSeconds * 1_000L) / 1_000L
    val localNow = nowSeconds + tzOffsetSeconds
    val from = (localNow - Math.floorMod(localNow, 86_400L)) - tzOffsetSeconds
    // Multi-day calm (#11 / #432): count prior RHR nights BEFORE the HR gate so Wear N of 4
    // stays honest when today's daytime series is still under-gated (EMPTY early path).
    val (priorCalmDayCount, priorCalm) = loadPriorCalmDayCount(vm)
    val hr = vm.repo.hrSamplesUnion(vm.activeStrapId, from, nowSeconds, limit = 200_000)
    if (hr.size < DaytimeStress.minHourHrSamples) {
        return DaytimeStress.Result.EMPTY.copy(
            priorCalmDayCount = priorCalmDayCount,
            bankingHrSamples = hr.size,
        )
    }
    val rr = vm.repo.rrIntervalsUnion(vm.activeStrapId, from, nowSeconds, limit = 200_000)
    val gravity = runCatching {
        vm.repo.gravitySamplesUnion(vm.activeStrapId, from, nowSeconds, limit = 200_000)
    }.getOrDefault(emptyList())
    val motion = if (gravity.isNotEmpty()) WorkoutDetector.activitySeries(gravity) else emptyList()
    val steps = runCatching {
        vm.repo.stepSamplesUnion(vm.activeStrapId, from, nowSeconds, limit = 200_000)
    }.getOrDefault(emptyList())
    val sedentary = if (gravity.size >= 2) {
        SedentaryDetector.detectSedentaryBouts(gravity, minMinutes = 20)
    } else {
        emptyList()
    }
    val wakeStart = context?.let { NoopPrefs.stressWakingStartHour(it) } ?: DaytimeStress.wakingStartHour
    val wakeEnd = context?.let { NoopPrefs.stressWakingEndHour(it) } ?: DaytimeStress.wakingEndHour

    val todayKey = LocalDate.now().toString()

    val daySi = StressIndex.stressIndex(rr)
    val freq = HrvFreqDomain.freqDomain(rr)
    val hfVagal = freq?.let { bands ->
        val lf = bands.lf ?: return@let false
        val denom = lf + bands.hf
        denom > 0.0 && (bands.hf / denom) >= DaytimeStress.hfCalmShare
    } ?: false

    val workouts = runCatching {
        vm.repo.workoutsUnion(vm.activeStrapId, from, nowSeconds, limit = 200)
            .ifEmpty { vm.repo.workoutsUnion("my-whoop", from, nowSeconds, limit = 200) }
    }.getOrDefault(emptyList())
    val workoutWindows = workouts.map { it.startTs to it.endTs }.toMutableList()
    // Auto-detected peaks (#63) join the same damp windows as logged workouts.
    runCatching {
        AutoWorkoutDetector.detect(hr, gravity = gravity)
            .forEach { workoutWindows.add(it.startSec to it.endSec) }
    }

    // Overnight lookback: sessions that started yesterday evening still shape today's moon band
    // (Gilbert ~06–13 sleep; clock-midnight alone misses the window).
    val sleepLookbackFrom = from - 18 * 3_600L
    val sleepState = runCatching {
        (
            vm.repo.sleepStateSamples(vm.activeStrapId, sleepLookbackFrom, nowSeconds, limit = 200_000) +
                vm.repo.sleepStateSamples("my-whoop", sleepLookbackFrom, nowSeconds, limit = 200_000)
            )
            .map { it.ts to it.state }
            .distinct()
    }.getOrDefault(emptyList())

    // Main-night sleep window(s) for night-floor shaping — late sleep past 06:00 must not
    // pollute the waking calm reference (WHOOP Stress Monitor moon band).
    // Union merged + computed + Health Connect so a HC-only night still paints asleep buckets.
    val sleepWindows = runCatching {
        (
            vm.repo.sleepSessionsMerged(vm.activeStrapId, sleepLookbackFrom, nowSeconds, limit = 64) +
                vm.repo.computedSleepSessionsUnion(vm.activeStrapId, sleepLookbackFrom, nowSeconds, limit = 64) +
                vm.repo.sleepSessions(
                    WhoopRepository.HEALTH_CONNECT_SOURCE,
                    sleepLookbackFrom,
                    nowSeconds,
                    limit = 64,
                )
            )
            .map { it.effectiveStartTs to it.endTs }
            .distinct()
    }.getOrDefault(emptyList())

    val todayRow = runCatching {
        // Prefer computed overnight vitals (my-whoop-noop) then imported — Fold 2026-07-16 banked
        // skin/resp/HRV under `-noop` while active strap UUID daily row was empty.
        val ids = (
            WhoopRepository.computedSourceIdsFor(vm.activeStrapId) +
                WhoopRepository.importedSourceIdsFor(vm.activeStrapId)
            ).distinct()
        ids.firstNotNullOfOrNull { id ->
            vm.repo.dailyMetrics(id, todayKey, todayKey).firstOrNull()
                ?.takeIf { it.skinTempDevC != null || it.respRateBpm != null || it.avgHrv != null || it.restingHr != null }
        } ?: ids.firstNotNullOfOrNull { id ->
            vm.repo.dailyMetrics(id, todayKey, todayKey).firstOrNull()
        }
    }.getOrNull()
    val skinElevated = (todayRow?.skinTempDevC?.let { kotlin.math.abs(it) } ?: 0.0) >=
        DaytimeStress.skinElevatedAbsC
    val respElevated = (todayRow?.respRateBpm ?: 0.0) >= DaytimeStress.respElevatedBpm

    val result = DaytimeStress.analyze(
        hr, rr, tzOffsetSeconds,
        motion = motion,
        steps = steps,
        sedentaryBouts = sedentary,
        wakingStart = wakeStart,
        wakingEnd = wakeEnd,
        priorCalmHrs = priorCalm,
        daySi = daySi,
        hfVagalTrusted = hfVagal,
        workoutWindows = workoutWindows,
        sleepState = sleepState,
        sleepWindows = sleepWindows,
        skinElevated = skinElevated,
        respElevated = respElevated,
        priorCalmDayCount = priorCalmDayCount,
    )

    // Persist Now tip for Trends / history (Fable #42) — never invents; only when scored.
    // Prefer engine [inSleepBandNow]; fall back to window/state probe for older Result shells.
    val tipWallNow = nowSeconds
    val inSleepBand = result.inSleepBandNow ||
        sleepWindows.any { (a, b) -> tipWallNow in a until b } ||
        sleepState.any { (ts, state) ->
            state == DaytimeStress.sleepStateAsleep && kotlin.math.abs(ts - tipWallNow) <= DaytimeStress.bucketSeconds
        }
    val tip = result.nowTip(wakeStart, wakeEnd, inSleepBand = inSleepBand)
    if (tip != null) {
        runCatching {
            vm.repo.upsertMetricSeries(
                listOf(
                    MetricSeriesRow(
                        DaytimeStress.SOURCE_DAYTIME_STRESS,
                        todayKey,
                        DaytimeStress.KEY_DAYTIME_STRESS,
                        tip.coerceIn(0.0, 3.0),
                    ),
                ),
            )
        }
    }
    // Widget Now tip (Fable #43 / SHIP #82) — patch prefs + Glance so night-ceiling drops clear the footer.
    context?.let { ctx ->
        try {
            com.noop.widget.WidgetSnapshotStore.patchStressTip(ctx, tip)
        } catch (_: Throwable) {
            // never let Glance hiccups kill daytime stress
        }
    }
    // EMPTY scored but past the day gate: bank toward first tip using the current 5-min bucket (#167).
    val withBand = if (result.inSleepBandNow == inSleepBand) result else result.copy(inSleepBandNow = inSleepBand)
    return if (withBand.scored.isEmpty() && withBand.bankingHrSamples <= 0) {
        val bucketSec = DaytimeStress.bucketSeconds
        val currentBucket = (localNow / bucketSec) * bucketSec
        val inBucket = hr.count { s ->
            val localTs = s.ts + tzOffsetSeconds
            (localTs / bucketSec) * bucketSec == currentBucket
        }
        withBand.copy(
            bankingHrSamples = inBucket.coerceIn(0, DaytimeStress.minHourHrSamples),
        )
    } else {
        withBand
    }
}

/**
 * Latest scored tip (Now) for the Stress hero / Today pin.
 *
 * During waking hours, prefer the latest waking-band bucket (WHOOP-style daytime tip).
 * At night (outside [wakingStart], [wakingEnd]), prefer the most recent scored bucket overall
 * so the night-floor path is visible — otherwise a stale evening HIGH tip sticks past midnight
 * (2026-07-14 pack: NOOP 2.7 @ 01:23 vs WHOOP 0.9–1.3 @ 01:05–01:20).
 *
 * [inSleepBand]: when the latest tip sits inside a detected sleep window / band asleep, apply
 * [DaytimeStress.sleepTipCeiling] (~0.95) instead of the looser overnight-awake ceiling (1.55).
 * Gilbert sleeps ~06–13; clock-night tips at 12:45 AM are often still awake (WHOOP 1.5).
 */
fun DaytimeStress.Result.nowTip(
    wakingStart: Int = DaytimeStress.wakingStartHour,
    wakingEnd: Int = DaytimeStress.wakingEndHour,
    currentHour: Int? = null,
    inSleepBand: Boolean? = null,
): Double? {
    val hour = currentHour
        ?: java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val inWaking = hour >= wakingStart && hour < wakingEnd
    val band = inSleepBand ?: inSleepBandNow
    val raw = if (inWaking) {
        scored.lastOrNull { it.hour >= wakingStart && it.hour < wakingEnd }?.level
            ?: scored.lastOrNull()?.level
    } else {
        scored.lastOrNull()?.level
            ?: scored.lastOrNull { it.hour >= wakingStart && it.hour < wakingEnd }?.level
    } ?: return null
    if (band) {
        return minOf(raw, DaytimeStress.sleepTipCeiling)
    }
    // Outside clock waking: cap stale evening HIGH but allow WHOOP-like MEDIUM overnight (≤1.55).
    if (!inWaking) {
        return minOf(raw, DaytimeStress.nightTipCeiling)
    }
    return raw
}

/** Share of scored 5-min buckets in the calm band (&lt; 1.0). Null when nothing scored. */
fun DaytimeStress.Result.calmBucketPct(): Int? {
    if (scored.isEmpty()) return null
    val calm = scored.count { (it.level ?: 0.0) < 1.0 }
    return ((calm.toDouble() / scored.size) * 100.0).toInt()
}
