package com.noop.analytics

import com.noop.data.HrSample
import com.noop.data.RrInterval
import com.noop.data.StepSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests DaytimeStress.analyze — 5-min buckets, calm anchor, step/sedentary gates, sleep band.
 */
class DaytimeStressTest {

    /** Fill one local 5-min bucket starting at [hour]:[slot*5] with `n` samples spread across the bucket. */
    private fun bucketHr(
        hour: Int,
        quarter: Int,
        bpm: Int,
        n: Int = DaytimeStress.minHourHrSamples,
    ): List<HrSample> {
        val base = hour.toLong() * 3_600L + quarter.toLong() * DaytimeStress.bucketSeconds
        val span = (DaytimeStress.bucketSeconds - 1).coerceAtLeast(1L)
        return (0 until n).map { i ->
            val offset = if (n <= 1) 0L else (i.toLong() * span) / (n - 1)
            HrSample(deviceId = "t", ts = base + offset, bpm = bpm)
        }
    }

    /** Fill a whole clock hour (12×5-min) at constant bpm. */
    private fun hourHr(hour: Int, bpm: Int, nPerBucket: Int = DaytimeStress.minHourHrSamples): List<HrSample> =
        (0 until DaytimeStress.bucketsPerHour).flatMap { q -> bucketHr(hour, q, bpm, nPerBucket) }

    @Test
    fun sleepHoursInTheWindow_doNotShiftTheWakingTimeline() {
        val wakingBpm = listOf(62, 64, 63, 65, 64, 63, 62, 64, 66, 63, 64, 65)
        val waking = (6..17).flatMapIndexed { i, h -> hourHr(h, wakingBpm[i]) }
        val sleep = (0..5).flatMap { h -> hourHr(h, 50 + (h % 3)) }

        val noRr = emptyList<RrInterval>()
        val wakingOnly = DaytimeStress.analyze(waking, noRr)
        val withSleep = DaytimeStress.analyze(sleep + waking, noRr)

        assertEquals(wakingOnly.sustainedHigh, withSleep.sustainedHigh)
        for (h in 6..17) {
            val withLvl = withSleep.scored.filter { it.hour == h }.mapNotNull { it.level }
            val withoutLvl = wakingOnly.scored.filter { it.hour == h }.mapNotNull { it.level }
            assertTrue("waking hour $h should be scored", withLvl.isNotEmpty() && withoutLvl.isNotEmpty())
            assertEquals(
                withoutLvl.average(), withLvl.average(), 1e-6,
            )
        }
        val mean = withSleep.dayMean
        assertNotNull(mean)
        assertTrue("calm day mean < 1.0 (WHOOP floor), was $mean", mean!! < 1.0)
        assertFalse(withSleep.sustainedHigh)
    }

    @Test
    fun whoopCalmAnchor_rawZeroMapsNearHalf() {
        val hrs = (6..17).flatMap { h -> hourHr(h, bpm = 64) }
        val r = DaytimeStress.analyze(hrs, emptyList())
        assertNotNull(r.dayMean)
        assertTrue(r.dayMean!! < 1.0)
        assertTrue(r.dayMean!! > 0.2)
    }

    @Test
    fun activitySpikeInsideBucket_doesNotUseMeanHrAsQuiet() {
        val base = 10L * 3_600L
        val n = DaytimeStress.minHourHrSamples
        val span = DaytimeStress.bucketSeconds - 1
        val calmN = (n * 2) / 3
        val calm = (0 until calmN).map { i ->
            HrSample(deviceId = "t", ts = base + (i.toLong() * span) / (n - 1), bpm = 62)
        }
        val spike = (calmN until n).map { i ->
            HrSample(deviceId = "t", ts = base + (i.toLong() * span) / (n - 1), bpm = 110)
        }
        val otherHours = listOf(6, 7, 8, 9, 11, 12, 13, 14, 15).flatMap { h -> hourHr(h, bpm = 64) }
        val result = DaytimeStress.analyze(calm + spike + otherHours, emptyList())
        val hour10 = result.scored.firstOrNull { it.hour == 10 && it.startTs == base }
        assertNotNull(hour10)
        assertEquals(62.0, hour10!!.meanHr!!, 5.0)
        assertTrue(hour10.level!! < DaytimeStress.highBandFloor)
    }

    @Test
    fun stepWalkClass_marksMotionBusy_stillClassCalms() {
        val calmHours = (6..12).flatMap { h -> hourHr(h, 62) }
        // Busy hour 14: elevated HR but step class walk should prefer quieter half.
        val busyHr = hourHr(14, 95)
        val walkSteps = busyHr.map {
            StepSample(deviceId = "t", ts = it.ts, counter = 1, activityClass = DaytimeStress.stepClassWalk)
        }
        val withWalk = DaytimeStress.analyze(calmHours + busyHr, emptyList(), steps = walkSteps)
        val stillSteps = busyHr.map {
            StepSample(deviceId = "t", ts = it.ts, counter = 1, activityClass = DaytimeStress.stepClassStill)
        }
        val withStill = DaytimeStress.analyze(calmHours + busyHr, emptyList(), steps = stillSteps)
        val walkLvl = withWalk.scored.filter { it.hour == 14 }.mapNotNull { it.level }.average()
        val stillLvl = withStill.scored.filter { it.hour == 14 }.mapNotNull { it.level }.average()
        assertTrue(
            "still/sedentary bias should lower stress vs walk ($stillLvl vs $walkLvl)",
            stillLvl <= walkLvl + 0.05,
        )
    }

    @Test
    fun sedentaryBout_pullsBucketTowardCalm() {
        val hrs = (6..14).flatMap { h -> hourHr(h, 70) }
        val bout = listOf(InactivityPeriod(start = 10L * 3600, end = 11L * 3600 + 900, durationS = 4500.0))
        val with = DaytimeStress.analyze(hrs, emptyList(), sedentaryBouts = bout)
        val without = DaytimeStress.analyze(hrs, emptyList())
        val with10 = with.scored.filter { it.hour == 10 }.mapNotNull { it.level }.average()
        val without10 = without.scored.filter { it.hour == 10 }.mapNotNull { it.level }.average()
        assertTrue("sedentary bout should damp hour 10 ($with10 vs $without10)", with10 < without10)
    }

    @Test
    fun fiveMinuteBuckets_produceMultiplePointsPerClockHour() {
        val hrs = hourHr(10, 64)
        val r = DaytimeStress.analyze(hrs + hourHr(11, 64) + hourHr(12, 64), emptyList())
        val at10 = r.scored.count { it.hour == 10 }
        assertTrue("expected multiple 5-min points in hour 10, got $at10", at10 >= 4)
    }

    @Test
    fun duplicateOrImplausibleHrDoesNotCreateStressEvidence() {
        val duplicates = List(DaytimeStress.minHourHrSamples) {
            HrSample(deviceId = "t", ts = 6L * 3_600L, bpm = 80)
        }
        val invalid = hourHr(hour = 6, bpm = 255)
        assertTrue(DaytimeStress.analyze(duplicates, emptyList()).scored.isEmpty())
        assertTrue(DaytimeStress.analyze(invalid, emptyList()).scored.isEmpty())
    }

    @Test
    fun whoopExportVisual_calmDeskMeanNearLowBand() {
        // SS Today tip ~0.9 LOW at 5:34 PM; sleep band ~0.2–1.5; prior NOOP avg 2.3.
        // Flat 62–66 bpm waking day should land dayMean in LOW (<1.0), tip-like.
        val day = (6..17).flatMap { h -> hourHr(h, 62 + (h % 3)) }
        val r = DaytimeStress.analyze(day, emptyList())
        assertNotNull(r.dayMean)
        assertTrue("export-era calm day must not avg HIGH; mean=${r.dayMean}", r.dayMean!! < 1.2)
        val tipLike = r.scored.lastOrNull()?.level
        assertNotNull(tipLike)
        assertTrue("latest tip-like level should be LOW-ish; was $tipLike", tipLike!! < 1.5)
    }

    @Test
    fun highZoneMinutes_exactFiveMinBuckets_noHourRoundUp() {
        // 9 HIGH buckets → 45 min = 0h 45m (WHOOP-style minutes; never round to 1h).
        val quiet = (6..9).flatMap { h -> hourHr(h, 62) }
        val spikeHr = hourHr(14, 118) // twelve 5-min buckets elevated
        val moreSpike = hourHr(15, 120) // +12
        val tail = bucketHr(16, 0, 125) + bucketHr(16, 1, 122) // +2 → high-ish
        val r = DaytimeStress.analyze(quiet + spikeHr + moreSpike + tail, emptyList())
        val highBuckets = r.scored.count { (it.level ?: 0.0) >= DaytimeStress.highBandFloor }
        assertEquals(
            DaytimeStress.Result.minutesForBuckets(highBuckets),
            r.highZoneMinutes,
        )
        // Compact never invents a whole hour from a single bucket.
        assertEquals("15m", DaytimeStress.Result.formatZoneCompact(15))
        assertEquals("2 hr 8 min", DaytimeStress.Result.formatZoneDuration(128))
        assertEquals("2h 8m", DaytimeStress.Result.formatZoneCompact(128))
        if (highBuckets > 0) {
            assertTrue(
                "high zone must be exact bucket×${DaytimeStress.bucketMinutes}, not rounded hours; min=${r.highZoneMinutes} buckets=$highBuckets",
                r.highZoneMinutes == highBuckets * DaytimeStress.bucketMinutes,
            )
        }
    }

    @Test
    fun bandMinutes_sumEqualsScoredCoverage() {
        val day = (6..17).flatMap { h -> hourHr(h, 62 + (h % 5) * 8) }
        val r = DaytimeStress.analyze(day, emptyList())
        val sum = r.calmZoneMinutes + r.moderateZoneMinutes + r.highZoneMinutes
        assertEquals(DaytimeStress.Result.minutesForBuckets(r.scored.size), sum)
    }

    @Test
    fun sparseSampleFloor_scoresWithSparseGate() {
        val sparse = bucketHr(10, 0, 64, n = DaytimeStress.minHourHrSamplesSparse) +
            bucketHr(10, 1, 65, n = DaytimeStress.minHourHrSamplesSparse) +
            hourHr(11, 64) + hourHr(12, 64)
        val r = DaytimeStress.analyze(sparse, emptyList())
        assertTrue(
            "sparse ${DaytimeStress.minHourHrSamplesSparse}-sample buckets should score; got ${r.scored.size}",
            r.scored.isNotEmpty(),
        )
    }

    @Test
    fun motionBusy_flaggedOnWalkBuckets() {
        val calm = hourHr(9, 62)
        val busyHr = bucketHr(10, 0, 95) + bucketHr(10, 1, 98) + bucketHr(10, 2, 96) + bucketHr(10, 3, 94)
        val steps = busyHr.map {
            StepSample(deviceId = "t", ts = it.ts, counter = 1, activityClass = DaytimeStress.stepClassWalk)
        }
        val r = DaytimeStress.analyze(calm + busyHr + hourHr(11, 64), emptyList(), steps = steps)
        val busyPts = r.scored.filter { it.hour == 10 && it.motionBusy }
        assertTrue("walk buckets should mark motionBusy", busyPts.isNotEmpty())
    }

    @Test
    fun overnightQuietNearWaking_pullsTowardLow() {
        // Night ~50 bpm; waking quiet stays within +5 bpm of overnight → absolute anchor damp.
        val night = (0..5).flatMap { h -> hourHr(h, 50) }
        val wakingNear = (6..12).flatMap { h -> hourHr(h, 54) }
        val withNight = DaytimeStress.analyze(night + wakingNear, emptyList())
        val wakingOnly = DaytimeStress.analyze(wakingNear, emptyList())
        val withMean = withNight.dayMean
        val withoutMean = wakingOnly.dayMean
        assertNotNull(withMean)
        assertNotNull(withoutMean)
        assertTrue(
            "overnight RHR anchor should lower waking mean ($withMean vs $withoutMean)",
            withMean!! <= withoutMean!! + 0.02,
        )
    }

    @Test
    fun tightWakingWindow_excludesHourSixFromDayMean() {
        val hrs = (6..21).flatMap { h -> hourHr(h, if (h == 6) 110 else 62) }
        val wide = DaytimeStress.analyze(hrs, emptyList(), wakingStart = 6, wakingEnd = 22)
        val tight = DaytimeStress.analyze(hrs, emptyList(), wakingStart = 7, wakingEnd = 21)
        assertTrue(
            "tight window should drop elevated hour-6 from waking mean",
            (tight.dayMean ?: 99.0) < (wide.dayMean ?: 0.0),
        )
        assertTrue((tight.dayMean ?: 99.0) < 1.2)
    }

    @Test
    fun priorCalmHrs_raiseStressWhenTodayIsAboveBaseline() {
        val calmDay = (6..14).flatMap { h -> hourHr(h, 70) }
        val alone = DaytimeStress.analyze(calmDay, emptyList())
        val withPrior = DaytimeStress.analyze(calmDay, emptyList(), priorCalmHrs = listOf(52.0, 53.0, 51.0))
        assertNotNull(alone.dayMean)
        assertNotNull(withPrior.dayMean)
        assertTrue(
            "lower multi-day calm should raise today’s relative stress ($withPrior vs $alone)",
            withPrior.dayMean!! >= alone.dayMean!! - 0.01,
        )
    }

    @Test
    fun workoutWindow_dampsBucketAndSkipsSustainedRun() {
        val quiet = (6..10).flatMap { h -> hourHr(h, 62) }
        val spike = (11..14).flatMap { h -> hourHr(h, 120) }
        val window = listOf(11L * 3600 to 15L * 3600)
        val with = DaytimeStress.analyze(quiet + spike, emptyList(), workoutWindows = window)
        val without = DaytimeStress.analyze(quiet + spike, emptyList())
        val with11 = with.scored.filter { it.hour == 11 }.mapNotNull { it.level }.average()
        val without11 = without.scored.filter { it.hour == 11 }.mapNotNull { it.level }.average()
        assertTrue("workout overlap should damp hour 11 ($with11 vs $without11)", with11 < without11)
        assertFalse("sustained-high should ignore workout overlap", with.sustainedHigh)
    }

    @Test
    fun baevskyCalmSi_softDampsWakingRaw() {
        val day = (6..14).flatMap { h -> hourHr(h, 68) }
        val highSi = DaytimeStress.analyze(day, emptyList(), daySi = 250.0)
        val lowSi = DaytimeStress.analyze(day, emptyList(), daySi = 40.0)
        assertTrue(
            "low Baevsky SI should pull dayMean down vs high SI",
            (lowSi.dayMean ?: 99.0) <= (highSi.dayMean ?: 0.0) + 0.02,
        )
    }

    @Test
    fun sleepStateAsleep_appliesNightBiasOnClockWakingBucket() {
        // Same quiet HR; band asleep on hour 10 → night bias even inside 06–22 clock window.
        val calm = (6..14).flatMap { h -> hourHr(h, 62) }
        val asleepSamples = hourHr(10, 62).map { it.ts to DaytimeStress.sleepStateAsleep }
        val with = DaytimeStress.analyze(calm, emptyList(), sleepState = asleepSamples)
        val without = DaytimeStress.analyze(calm, emptyList())
        val with10 = with.scored.filter { it.hour == 10 }.mapNotNull { it.level }.average()
        val without10 = without.scored.filter { it.hour == 10 }.mapNotNull { it.level }.average()
        assertTrue("asleep band should damp hour 10 ($with10 vs $without10)", with10 < without10)
    }

    @Test
    fun skinAndRespElevated_softBumpWaking() {
        val day = (6..14).flatMap { h -> hourHr(h, 64) }
        val base = DaytimeStress.analyze(day, emptyList())
        val bumped = DaytimeStress.analyze(day, emptyList(), skinElevated = true, respElevated = true)
        assertTrue(
            "skin+resp elevated should raise dayMean",
            (bumped.dayMean ?: 0.0) > (base.dayMean ?: 99.0),
        )
    }

    @Test
    fun calibrationNightsRemaining_countsPriorCalmDays() {
        val day = (6..12).flatMap { h -> hourHr(h, 62) }
        val r = DaytimeStress.analyze(day, emptyList(), priorCalmDayCount = 2)
        assertEquals(2, r.priorCalmDayCount)
        assertEquals(DaytimeStress.calibrationNightsTarget - 2, r.calibrationNightsRemaining)
        val ready = DaytimeStress.analyze(day, emptyList(), priorCalmDayCount = 4)
        assertEquals(0, ready.calibrationNightsRemaining)
    }

    @Test
    fun lateSleepWindow_excludesMorningFromWakingReference() {
        // 13 Jul shape: sleep through ~13:00. Without sleepWindows, hours 6–12 (low HR) pollute
        // the waking calm reference. With a sleep window covering those hours, afternoon tip
        // should not be inflated relative to a day that never included them in the reference.
        val morningSleep = (6..12).flatMap { h -> hourHr(h, 52) }
        val afternoon = listOf(14, 15, 16, 17, 18, 19).flatMap { h -> hourHr(h, 72) }
        val noRr = emptyList<RrInterval>()
        // Sleep window 06:00–13:00 wall clock (tz 0).
        val window = listOf(6L * 3600L to 13L * 3600L)
        val withWindow = DaytimeStress.analyze(morningSleep + afternoon, noRr, sleepWindows = window)
        val afternoonOnly = DaytimeStress.analyze(afternoon, noRr)

        val tipWith = withWindow.scored.lastOrNull { it.hour == 19 }?.level
        val tipOnly = afternoonOnly.scored.lastOrNull { it.hour == 19 }?.level
        assertNotNull(tipWith)
        assertNotNull(tipOnly)
        // Sleep-window shaping should keep the afternoon tip close to the afternoon-only day
        // (not dragged high by treating morning sleep as waking calm).
        assertEquals(tipOnly!!, tipWith!!, 0.35)
        // Morning sleep hours may appear but must not drive sustained-high.
        assertFalse(withWindow.sustainedHigh)
    }

    @Test
    fun nightTipCeiling_capsStaleEveningHigh() {
        val evening = listOf(20, 21).flatMap { h -> hourHr(h, 95) }
        val calmDay = (6..17).flatMap { h -> hourHr(h, 62) }
        val r = DaytimeStress.analyze(calmDay + evening, emptyList())
        val wakingTip = r.scored.lastOrNull {
            it.hour >= DaytimeStress.wakingStartHour && it.hour < DaytimeStress.wakingEndHour
        }?.level
        assertNotNull(wakingTip)
        // Mirror nowTip night path: prefer last tip, then soft-cap (WHOOP overnight awake ≤1.55).
        val last = r.scored.lastOrNull()?.level ?: wakingTip!!
        val nightTip = minOf(last, DaytimeStress.nightTipCeiling)
        assertTrue(nightTip <= DaytimeStress.nightTipCeiling + 1e-9)
        assertTrue(nightTip < DaytimeStress.highBandFloor)
    }

    @Test
    fun sleepTipCeiling_tighterThanOvernightAwakeCap() {
        assertTrue(DaytimeStress.sleepTipCeiling < DaytimeStress.nightTipCeiling)
        // Sleep-band Now must stay LOW-ish; overnight awake may read MEDIUM (~1.5).
        assertTrue(DaytimeStress.sleepTipCeiling < 1.0)
        assertTrue(DaytimeStress.nightTipCeiling >= 1.5)
    }

    @Test
    fun fiveMinuteBuckets_areDenserThanLegacyQuarterHour() {
        assertEquals(300L, DaytimeStress.bucketSeconds)
        assertEquals(12, DaytimeStress.bucketsPerHour)
        assertEquals(5, DaytimeStress.bucketMinutes)
    }

    @Test
    fun expandContiguousBuckets_fillsNullGapsOnTimeline() {
        val a = DaytimeStress.HourPoint(10, 10L * 3600, 0.5, 60.0, null)
        // Skip one 5-min slot between a and b.
        val b = DaytimeStress.HourPoint(10, 10L * 3600 + 2 * DaytimeStress.bucketSeconds, 0.6, 61.0, null)
        val dense = DaytimeStress.expandContiguousBuckets(listOf(a, b), tzOffsetSeconds = 0L)
        assertEquals(3, dense.size)
        assertNull(dense[1].level)
        assertEquals(a.startTs + DaytimeStress.bucketSeconds, dense[1].startTs)
    }

    @Test
    fun sleepWindow_setsAsleepFlagAndLastNightMean() {
        val morningSleep = (6..10).flatMap { h -> hourHr(h, 52) }
        val afternoon = (14..16).flatMap { h -> hourHr(h, 72) }
        val window = listOf(6L * 3600L to 11L * 3600L)
        val r = DaytimeStress.analyze(morningSleep + afternoon, emptyList(), sleepWindows = window)
        assertTrue(r.scored.any { it.asleep })
        assertNotNull(r.lastNightMean)
        assertTrue(r.lastNightMean!! < 1.5)
        // Tip after wake should not force inSleepBandNow.
        val last = r.scored.lastOrNull()
        assertNotNull(last)
        if (last!!.hour >= 14) {
            assertFalse(r.inSleepBandNow)
        }
    }

    @Test
    fun asleepFlag_trueInsideSleepWindowEvenWithoutBandState() {
        val hrs = hourHr(8, 55)
        val window = listOf(8L * 3600L to 9L * 3600L)
        val r = DaytimeStress.analyze(hrs, emptyList(), sleepWindows = window)
        assertTrue(r.scored.isNotEmpty())
        assertTrue("expected asleep on sleep-window buckets", r.scored.all { it.asleep })
    }

    @Test
    fun calmAnchorOffset_mapsRawZeroNearCalmFloor() {
        // squash(raw) = 3/(1+e^(-(raw-2.00))); raw 0 → ~0.357 — Now calm floor, not daily mid 1.5.
        val raw0 = 3.0 / (1.0 + kotlin.math.exp(-(0.0 - DaytimeStress.calmAnchorOffset)))
        assertEquals(0.357, raw0, 0.02)
        assertTrue(raw0 < 0.5)
        assertTrue(raw0 < 1.0) // clearly below daily-load mid (~1.5 at raw 0 in StressModel)
        assertEquals(2.00, DaytimeStress.calmAnchorOffset, 1e-9)
    }

    @Test
    fun calmAnchor_keepsNeutralDaytimeNearLowBand() {
        // All-day calm HR should land tip in LOW (<1), not MEDIUM mid.
        val day = (6..20).flatMap { h -> hourHr(h, 58) }
        val r = DaytimeStress.analyze(day, emptyList())
        val tip = r.scored.lastOrNull { it.level != null }?.level
        assertNotNull(tip)
        assertTrue("calm day tip should be LOW-ish, got $tip", tip!! < 1.0)
    }

    @Test
    fun workContext_dampsAmbulatoryWarehouse_notSeatedAcute() {
        // Calm baseline + walk-elevated hour (Amazon standing shift) vs same HR seated.
        val calm = (6..9).flatMap { h -> hourHr(h, 62) }
        val busyHr = hourHr(10, 95)
        val walkSteps = busyHr.map {
            StepSample(deviceId = "t", ts = it.ts, counter = 1, activityClass = DaytimeStress.stepClassWalk)
        }
        val stillSteps = busyHr.map {
            StepSample(deviceId = "t", ts = it.ts, counter = 1, activityClass = DaytimeStress.stepClassStill)
        }
        val walkWork = DaytimeStress.analyze(
            calm + busyHr, emptyList(), steps = walkSteps, workContextActive = true,
        )
        val walkHome = DaytimeStress.analyze(
            calm + busyHr, emptyList(), steps = walkSteps, workContextActive = false,
        )
        val seatedWork = DaytimeStress.analyze(
            calm + busyHr, emptyList(), steps = stillSteps, workContextActive = true,
        )
        val walkWorkLvl = walkWork.scored.filter { it.hour == 10 }.mapNotNull { it.level }.average()
        val walkHomeLvl = walkHome.scored.filter { it.hour == 10 }.mapNotNull { it.level }.average()
        val seatedWorkLvl = seatedWork.scored.filter { it.hour == 10 }.mapNotNull { it.level }.average()
        assertTrue(
            "on-feet at work should damp vs same walk off-shift ($walkWorkLvl vs $walkHomeLvl)",
            walkWorkLvl < walkHomeLvl - 0.05,
        )
        assertTrue(
            "seated+work must not get occupational damp ($seatedWorkLvl vs $walkWorkLvl)",
            seatedWorkLvl > walkWorkLvl,
        )
    }
}
