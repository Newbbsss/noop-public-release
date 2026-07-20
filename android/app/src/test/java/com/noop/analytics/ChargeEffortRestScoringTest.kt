package com.noop.analytics

import com.noop.data.HrSample
import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Charge / Effort / Rest scoring redesign (2026-06-12).
 *
 * Mirrors the Swift StrandAnalytics scoring tests so cross-platform parity holds:
 *  - Effort (StrainScorer) now maps TRIMP onto 0–100 (maxStrain 21→100, D=7201 unchanged).
 *  - Charge (RecoveryScorer) folds in a symmetric skin-temp penalty (wHRV 0.60→0.55,
 *    wSkinTemp 0.05); the no-skin-temp path is byte-identical to the old model.
 *  - Rest (RestScorer) composite: duration 0.45 + efficiency 0.20 + restorative 0.25 +
 *    consistency 0.10, stored under the sleep_performance key (0–100).
 *  - ScoreConfidence tiers (calibrating / building / solid).
 *
 * Goldens are computed from the closed-form formulas (see the design spec) — keep every
 * constant byte-identical to the Swift source.
 */
class ChargeEffortRestScoringTest {

    private val EPS = 1e-9

    // ── Effort (StrainScorer 0–100) ────────────────────────────────────────────

    @Test
    fun effort_scaleConstantsAreRescaled() {
        // The whole rebrand is a pure linear rescale: maxStrain 21→100, denominator unchanged.
        assertEquals(100.0, StrainScorer.maxStrain, 0.0)
        assertEquals(7201.0, StrainScorer.strainDenominator, 0.0)
    }

    @Test
    fun effort_trimpToStrainMapsOntoZeroHundred() {
        // 100 × ln(t+1) / ln(7201), 2 dp.
        assertEquals(0.0, StrainScorer.trimpToStrain(0.0), 0.0)
        assertEquals(0.0, StrainScorer.trimpToStrain(-5.0), 0.0) // TRIMP ≤ 0 → 0
        assertEquals(51.96, StrainScorer.trimpToStrain(100.0), EPS)
        assertEquals(69.99, StrainScorer.trimpToStrain(500.0), EPS)
        assertEquals(77.78, StrainScorer.trimpToStrain(1000.0), EPS)
        assertEquals(92.20, StrainScorer.trimpToStrain(3600.0), EPS)
        // TRIMP 7200 (Edwards daily ceiling) saturates at the top of the scale.
        assertEquals(100.0, StrainScorer.trimpToStrain(7200.0), EPS)
    }

    @Test
    fun effort_oldGoldensRescaledByHundredOverTwentyOne() {
        // Every former 0–21 golden is the 0–100 value × 21/100 (within 2-dp rounding).
        // trimp=1000 was 16.33 on the 0–21 scale → 16.33 × 100/21 ≈ 77.76, our 0–100 value is 77.78.
        val effort = StrainScorer.trimpToStrain(1000.0)
        val legacy21 = 21.0 * kotlin.math.ln(1001.0) / kotlin.math.ln(7201.0)
        assertEquals(legacy21 * (100.0 / 21.0), effort, 0.02)
    }

    /** 600 constant-bpm samples at 1 Hz so Edwards TRIMP = 600·weight·(1/60) = 10·weight. */
    private fun hrConstant(bpm: Int, n: Int = 600): List<HrSample> =
        (0 until n).map { HrSample(deviceId = "t", ts = it.toLong(), bpm = bpm) }

    @Test
    fun effort_edwardsZoneGoldens_onZeroHundredScale() {
        // restingHR 60, maxHR 160 → hrReserve 100 → %HRR = bpm − 60.
        // Below 60% HRR (bpm 115): no Edwards weight (ambulatory floor — compare 2026-07-17).
        assertEquals(
            0.0,
            StrainScorer.strain(hrConstant(115), maxHR = 160.0, restingHR = 60.0)!!,
            EPS,
        )
        // First paying zone is ≥60% HRR (weight 2): bpm 120 → trimp 20 → ~32.19
        val at60 = StrainScorer.strain(hrConstant(120), maxHR = 160.0, restingHR = 60.0)!!
        assertTrue(at60 > 0.0)
        // zone3 (70–80%): bpm 135 → trimp 30 → 38.66
        assertEquals(
            38.66,
            StrainScorer.strain(hrConstant(135), maxHR = 160.0, restingHR = 60.0)!!,
            EPS,
        )
        // zone5 (≥90%): bpm 155 → trimp 50 → 44.27
        assertEquals(
            44.27,
            StrainScorer.strain(hrConstant(155), maxHR = 160.0, restingHR = 60.0)!!,
            EPS,
        )
    }

    @Test
    fun effort_nullWhenTooFewSamplesOrInvalidHrr() {
        assertNull(StrainScorer.strain(hrConstant(135, n = 599), maxHR = 160.0, restingHR = 60.0))
        assertNull(StrainScorer.strain(hrConstant(135), maxHR = 60.0, restingHR = 60.0)) // HRR ≤ 0
    }

    // ── #482/#480 sparse-strap acceptance + honest-zero (parity with Swift StrainScorerTests) ──

    /** n samples at a fixed cadence (default 30 s — the WHOOP 5/MG live-HR rate). */
    private fun hrEvery(bpm: Int, n: Int, stepS: Int = 30): List<HrSample> =
        (0 until n).map { HrSample(deviceId = "t", ts = (it * stepS).toLong(), bpm = bpm) }

    @Test
    fun effort_sparseStreamScoresOnceItSpansEnoughTime() {
        // 5/MG: ~30 samples at 30 s — under minReadings (600) but spanning ~15 min, so it scores
        // rather than returning null (which made the live gauge show a stale prior-day value).
        val sparse = hrEvery(155, 30) // 30 × 30 s = 870 s span; 155 bpm ≈ z5 on max 160
        assertTrue(sparse.last().ts - sparse.first().ts >= StrainScorer.minSpanSeconds)
        assertNotNull(StrainScorer.strain(sparse, maxHR = 160.0, restingHR = 60.0))
    }

    @Test
    fun effort_sparseStreamStillNullUnderSampleFloor() {
        val tooFew = hrEvery(155, 5, stepS = 200) // 5 samples, wide span, under floor
        assertNull(StrainScorer.strain(tooFew, maxHR = 160.0, restingHR = 60.0))
    }

    @Test
    fun effortRejectsInvalidOrDuplicateHrEvidence() {
        val duplicates = List(600) { HrSample(deviceId = "t", ts = 1L, bpm = 155) }
        val invalid = hrEvery(320, 600, stepS = 1)
        assertNull(StrainScorer.strain(duplicates, maxHR = 160.0, restingHR = 60.0))
        assertNull(StrainScorer.strain(invalid, maxHR = 160.0, restingHR = 60.0))
    }

    @Test
    fun effortRejectsDisconnectedSparseStream() {
        // Post-#322 semantics: the two chunks are separate WORN segments whose summed coverage
        // (2 × 270 s = 540 s) stays under minSpanSeconds — still null, now for honest-coverage
        // reasons rather than the old any-gap-nulls-the-day rule.
        val first = hrEvery(155, 10, stepS = 30)
        val second = hrEvery(155, 10, stepS = 30).map { it.copy(ts = it.ts + 1_000L) }
        assertNull(StrainScorer.strain(first + second, maxHR = 160.0, restingHR = 60.0))
    }

    // ── Fable #322 — a gap must not blank a real day's Effort ─────────────────────────────────

    @Test
    fun effort_middayGapSegmentsInsteadOfNulling() {
        // A full morning of dense z2 wear, a 2-min BLE dropout, then more wear. The old rule
        // returned null for the WHOLE day (the Fold 2026-07-10..12 rows: 70k+ samples, Effort
        // "No Data" while WHOOP scored 14.8/9.7/2.8). Segmented, the day scores.
        val morning = hrConstant(135, n = 1200)                       // 20 min at 1 Hz
        val evening = hrConstant(135, n = 1200).map { it.copy(ts = it.ts + 1_320L) } // after a 2-min gap
        val s = StrainScorer.strain(morning + evening, maxHR = 160.0, restingHR = 60.0)
        assertNotNull(s)
        // The gap contributes ZERO: the split day equals one continuous stream of the same worn
        // samples (per-sample × cadence integration; both segments infer the same 1 s cadence).
        val continuous = hrConstant(135, n = 2400)
        assertEquals(StrainScorer.strain(continuous, maxHR = 160.0, restingHR = 60.0)!!, s!!, 0.01)
    }

    @Test
    fun effort_nullStrainDayWouldScoreFromDenseHr() {
        // Mirrors Fold MAIN: dailyMetric.strain=NULL with 60k+ HR samples. Gap-aware StrainScorer
        // must return a real Effort so IntelligenceEngine.backfillNullEffort can fill the column
        // without restaging sleep.
        val dense = hrConstant(140, n = 3_600) // 1 h @ 1 Hz
        val s = StrainScorer.strain(dense, maxHR = 184.0, restingHR = 55.0)
        assertNotNull(s)
        assertTrue(s!! > 0.0)
    }

    @Test
    fun missingGravityUnknownHrIsStillNotHalfMoving() {
        // Overnight Light honesty: epochs with no gravity and no HR must not use moveFrac=0.5
        // (that sat above stageStillMoveFrac and blocked Deep/REM cardiac paths).
        val start = 1_700_000_000.0
        val end = start + 30 * 60 // 30 min → a few epochs
        val grid = SleepStager.buildEpochGrid(
            start = start,
            end = end,
            gravTimes = emptyList(),
            gravDeltas = emptyList(),
            hr = emptyList(),
            rr = emptyList(),
            resp = emptyList(),
        )
        assertTrue(grid.moveFrac.isNotEmpty())
        assertTrue(grid.moveFrac.all { it <= SleepStager.stageStillMoveFrac })
    }

    // (Gapless streams stay byte-identical to the pre-#322 path — the zone goldens above pin that.)

    @Test
    fun effort_lightDayHonestlyScoresZeroNotFabricated() {
        // HR below ~50% HRR earns ZERO, by design — the sparse path must not invent load. With
        // max 184 / rest 60, zone 1 starts at 122 bpm; 105 bpm stays below it on both cadences.
        assertEquals(0.0, StrainScorer.strain(hrConstant(105, n = 1200), maxHR = 184.0, restingHR = 60.0))
        assertEquals(0.0, StrainScorer.strain(hrEvery(105, 40), maxHR = 184.0, restingHR = 60.0))
    }

    @Test
    fun effort_movementFloor_restDayStaysZero() {
        assertEquals(0.0, StrainScorer.movementFloor(1_500, 80.0), 0.0)
        assertEquals(0.0, StrainScorer.movementFloor(4_500, 200.0), 0.0)
        assertNull(StrainScorer.withMovementFloor(null, 1_500, 80.0))
        assertEquals(0.0, StrainScorer.withMovementFloor(0.0, 1_500, null)!!, 0.0)
    }

    @Test
    fun effort_movementFloor_ignoresTotalDayKcal() {
        // activeKcalEst is whole-day energy (~1.5–2k) — must not pin Effort at the cap.
        assertEquals(
            StrainScorer.movementFloor(6_000, null),
            StrainScorer.movementFloor(6_000, 1_800.0),
            0.0,
        )
        assertEquals(0.0, StrainScorer.movementFloor(null, 2_000.0), 0.0)
    }

    @Test
    fun effort_edwardsIgnoresBelow60PctHrr() {
        // RHR 50, HRmax 190 → 60% HRR = 134 bpm. Day max ~92 must not mint TRIMP.
        assertEquals(0, StrainScorer.zoneWeight(92.0, 50.0, 140.0))
        assertEquals(0, StrainScorer.zoneWeight(133.0, 50.0, 140.0))
        assertEquals(2, StrainScorer.zoneWeight(134.0, 50.0, 140.0))
    }

    @Test
    fun effort_movementFloor_convexWalkNotHotEarly() {
        // Smoking gun 2026-07-17: old log floor hit ~22 at ~12k steps while WHOOP Strain was 0.1.
        val at12k = StrainScorer.movementFloor(12_000, null)
        assertTrue("12k walk should move Effort a little", at12k > 0.5)
        assertTrue("12k walk must stay low single-digits (was ~22)", at12k < 8.0)

        val at8k = StrainScorer.movementFloor(8_000, null)
        val at20k = StrainScorer.movementFloor(20_000, null)
        assertTrue("mid walk stays tiny", at8k < at12k)
        assertTrue("high step day climbs faster (convex)", at20k > at12k * 2.0)
        assertTrue(at20k <= StrainScorer.movementFloorCap)

        // Cardio TRIMP still wins when higher.
        assertEquals(40.0, StrainScorer.withMovementFloor(40.0, 12_000, 400.0)!!, 0.0)
        // Steps alone never invent a hard-workout day.
        assertTrue(StrainScorer.movementFloor(30_000, 2_000.0) <= StrainScorer.movementFloorCap)
        assertEquals(
            StrainScorer.movementFloor(12_000, null),
            StrainScorer.withMovementFloor(0.0, 12_000, null)!!,
            0.0,
        )
    }

    @Test
    fun effort_sparseStreamScoresRealWorkout() {
        val s = StrainScorer.strain(hrEvery(175, 40), maxHR = 184.0, restingHR = 60.0)
        assertNotNull(s)
        assertTrue(s!! > 0.0)
    }

    // ── Charge (RecoveryScorer skin-temp term) ─────────────────────────────────

    @Test
    fun charge_weightConstants() {
        assertEquals(0.55, RecoveryScorer.wHRV, 0.0)
        assertEquals(0.20, RecoveryScorer.wRHR, 0.0)
        assertEquals(0.15, RecoveryScorer.wSleep, 0.0)
        assertEquals(0.05, RecoveryScorer.wResp, 0.0)
        assertEquals(0.05, RecoveryScorer.wSkinTemp, 0.0)
        // 1.0 °C per z-unit, matching the Swift reference (RecoveryScorer.skinTempScaleC = 1.0). A
        // prior 0.5 here doubled the skin-temp penalty vs macOS/iOS — fixed for parity (#219 audit).
        assertEquals(1.0, RecoveryScorer.skinTempDevScale, 0.0)
    }

    private val hrvBase = RecoveryScorer.DriverBaseline(mean = 60.0, spread = 8.0)
    private val rhrBase = RecoveryScorer.DriverBaseline(mean = 55.0, spread = 4.0)

    /** HRV at baseline, RHR at baseline, sleepPerf at center → composite z = 0. */
    private fun chargeAt(skinDev: Double?): Double? = RecoveryScorer.recovery(
        hrv = 60.0,
        rhr = 55.0,
        resp = null,
        hrvBaseline = hrvBase,
        rhrBaseline = rhrBase,
        respBaseline = null,
        sleepPerf = RecoveryScorer.sleepPerfCenter,
        skinTempDev = skinDev,
    )

    @Test
    fun charge_nullSkinTempIsIdenticalToBefore() {
        // Dropping the skin-temp term must renormalize the remaining weights so the score is
        // unchanged from the pre-skin-temp model.
        val absent = chargeAt(null)!!
        val devZero = chargeAt(0.0)!! // present but zero deviation → same z, same renormalization
        assertNotNull(absent)
        assertEquals(absent, devZero, EPS)
    }

    @Test
    fun charge_skinTempDeviationLowersCharge() {
        val baseScore = chargeAt(null)!!
        val penalized = chargeAt(1.0)!!
        assertTrue("skin-temp deviation should lower Charge", penalized < baseScore)
    }

    @Test
    fun charge_skinTempPenaltyIsSymmetric() {
        // ±1.0 °C deviation costs the same (illness or overreach both penalize).
        assertEquals(chargeAt(1.0)!!, chargeAt(-1.0)!!, EPS)
    }

    @Test
    fun charge_coldStartGateUnaffectedBySkinTemp() {
        // An unusable HRV baseline still refuses to score even with a skin-temp value present.
        val score = RecoveryScorer.recovery(
            hrv = 60.0,
            rhr = 55.0,
            resp = null,
            hrvBaseline = hrvBase,
            rhrBaseline = rhrBase,
            respBaseline = null,
            sleepPerf = 0.85,
            skinTempDev = 0.7,
            hrvBaselineUsable = false,
        )
        assertNull(score)
    }

    // ── Rest (RestScorer composite) ────────────────────────────────────────────

    @Test
    fun rest_weightConstants() {
        assertEquals(0.45, RestScorer.wDuration, 0.0)
        assertEquals(0.25, RestScorer.wRestorative, 0.0)
        assertEquals(0.14, RestScorer.deepFloorFactor, 0.0)
        assertEquals(0.16, RestScorer.durationQualityShareGate, 0.0)
        assertEquals(0.42, RestScorer.durationQualityFloor, 0.0)
        assertEquals(0.78, RestScorer.restWhoopAlignScale, 0.0)
        assertEquals(0.20, RestScorer.wEfficiency, 0.0)
        assertEquals(0.25, RestScorer.wRestorative, 0.0)
        assertEquals(0.10, RestScorer.wConsistency, 0.0)
        assertEquals(8.0, RestScorer.defaultSleepNeedHours, 0.0)
        assertEquals(7.5, RestScorer.minPersonalNeedHours, 0.0)
        assertEquals(0.50, RestScorer.restorativeTargetShare, 0.0)
    }

    @Test
    fun personalNeedHours_emptyDefaultsToPhysiologyAdult() {
        val (need, n) = RestScorer.personalNeedHours(emptyList())
        assertEquals(8.0, need, 0.0)
        assertEquals(0, n)
    }

    @Test
    fun personalNeedHours_shortSleeperStaysAtPhysiologyFloor() {
        // Chronically short sleeper (6h mean) no longer drops need to 7.5 — phys adult = 8.0.
        val (need, n) = RestScorer.personalNeedHours(listOf(360.0, 360.0, 360.0))
        assertEquals(8.0, need, 1e-9)
        assertEquals(3, n)
    }

    @Test
    fun personalNeedHours_followsLongerMean() {
        val (need, n) = RestScorer.personalNeedHours(listOf(540.0, 540.0)) // 9h
        assertEquals(9.0, need, 1e-9)
        assertEquals(2, n)
    }

    @Test
    fun restFromDaily_usesPersonalNeed() {
        val daily = DailyMetric(
            deviceId = "x",
            day = "2026-07-12",
            totalSleepMin = 360.0, // 6h
            efficiency = 0.90,
            deepMin = 60.0,
            remMin = 90.0,
        )
        val atDefault = RestScorer.restFromDaily(daily)!!
        val atSix = RestScorer.restFromDaily(daily, sleepNeedHours = 6.0)!!
        assertTrue("shorter personal need should raise Rest ($atSix vs $atDefault)", atSix > atDefault)
    }

    @Test
    fun rest_nullWhenNoAsleepTime() {
        assertNull(RestScorer.rest(0.0, 0.9, 0.0, 0.0))
    }

    @Test
    fun rest_compositeWithoutConsistencyUsesNeutral() {
        // 8h asleep (dur 100), eff 0.92 (92), deep 1.5h + REM 2h = 3.5h restorative,
        // share 0.4375 / 0.50 → 87.5. Deep share 0.1875 ≥ target → deepFactor 1.
        // Weights 0.45/0.20/0.25/0.10. Neutral consistency 50.
        val score = RestScorer.rest(
            asleepSeconds = 8 * 3600.0,
            efficiency = 0.92,
            deepSeconds = 1.5 * 3600.0,
            remSeconds = 2.0 * 3600.0,
        )!!
        val expected = (100.0 * 0.45 + 92.0 * 0.20 + 87.5 * 0.25 + 50.0 * 0.10) *
            RestScorer.restWhoopAlignScale
        assertEquals(expected, score, 0.01)
    }

    @Test
    fun rest_consistencyTermIncluded() {
        val score = RestScorer.rest(
            asleepSeconds = 8 * 3600.0,
            efficiency = 0.92,
            deepSeconds = 1.5 * 3600.0,
            remSeconds = 2.0 * 3600.0,
            consistency = 0.80,
        )!!
        val expected = (100.0 * 0.45 + 92.0 * 0.20 + 87.5 * 0.25 + 80.0 * 0.10) *
            RestScorer.restWhoopAlignScale
        assertEquals(expected, score, 0.01)
    }

    @Test
    fun rest_durationDominatesShortNight() {
        // 4h asleep against the 8h default → power-curve duration, not linear 50.
        val score = RestScorer.rest(
            asleepSeconds = 4 * 3600.0,
            efficiency = 0.95,
            deepSeconds = 1.0 * 3600.0,
            remSeconds = 1.0 * 3600.0,
        )!!
        val durationRaw = SleepNeedEstimator.durationScoreRaw(4.0, 8.0)
        val expected = (durationRaw * 0.45 + 95.0 * 0.20 + 100.0 * 0.25 + 50.0 * 0.10) *
            RestScorer.restWhoopAlignScale
        assertEquals(expected, score, EPS)
    }

    @Test
    fun rest_personalNeedRefinementRaisesDuration() {
        // Same 6h asleep scores higher once personal need is refined down to 6h (duration → 100).
        val defaultNeed = RestScorer.rest(6 * 3600.0, 0.90, 1.0 * 3600.0, 1.5 * 3600.0)!!
        val refined = RestScorer.rest(6 * 3600.0, 0.90, 1.0 * 3600.0, 1.5 * 3600.0, sleepNeedHours = 6.0)!!
        assertTrue(refined > defaultNeed)
    }

    @Test
    fun rest_oversleepDurationClampsAtHundred() {
        // 10h asleep against an 8h need does not over-credit: duration clamps at 100.
        val score = RestScorer.rest(10 * 3600.0, 0.90, 2.0 * 3600.0, 2.5 * 3600.0)!!
        val restShare = (2.0 + 2.5) / 10.0 // 0.45 → /0.5 → 90
        val expected = (100.0 * 0.45 + 90.0 * 0.20 + (restShare / 0.50 * 100.0) * 0.25 + 50.0 * 0.10) *
            RestScorer.restWhoopAlignScale
        assertEquals(expected, score, EPS)
    }

    // ── ScoreConfidence tiers ──────────────────────────────────────────────────

    private fun baseline(nValid: Int, status: BaselineStatus): BaselineState =
        BaselineState(baseline = 60.0, spread = 8.0, nValid = nValid, nightsSinceUpdate = 0, status = status)

    @Test
    fun confidence_chargeTiers() {
        // No score → calibrating regardless of baseline.
        assertEquals(
            ScoreConfidence.CALIBRATING,
            ScoreConfidence.forCharge(null, baseline(20, BaselineStatus.TRUSTED)),
        )
        // Score but baseline not usable → calibrating.
        assertEquals(
            ScoreConfidence.CALIBRATING,
            ScoreConfidence.forCharge(60.0, baseline(2, BaselineStatus.CALIBRATING)),
        )
        // Score, provisional/thin baseline (< 7 nights) → building.
        assertEquals(
            ScoreConfidence.BUILDING,
            ScoreConfidence.forCharge(60.0, baseline(5, BaselineStatus.PROVISIONAL)),
        )
        // Score, full trusted baseline → solid.
        assertEquals(
            ScoreConfidence.SOLID,
            ScoreConfidence.forCharge(60.0, baseline(20, BaselineStatus.TRUSTED)),
        )
    }

    @Test
    fun confidence_effortTiers() {
        assertEquals(ScoreConfidence.CALIBRATING, ScoreConfidence.forEffort(null, 5000))
        assertEquals(ScoreConfidence.BUILDING, ScoreConfidence.forEffort(40.0, 1200))
        assertEquals(ScoreConfidence.SOLID, ScoreConfidence.forEffort(40.0, 5000))
    }

    @Test
    fun confidence_restTiers() {
        // Mirrors Swift AnalyticsEngineTests: the tier depends on staged sleep for THIS night,
        // not on sleep-need history length.
        assertEquals(ScoreConfidence.CALIBRATING,
            ScoreConfidence.forRest(hasSession = false, hasStagedSleep = false))
        assertEquals(ScoreConfidence.BUILDING,
            ScoreConfidence.forRest(hasSession = true, hasStagedSleep = false))
        assertEquals(ScoreConfidence.SOLID,
            ScoreConfidence.forRest(hasSession = true, hasStagedSleep = true))
    }

    @Test
    fun confidence_rawStringsMatchSwift() {
        assertEquals("calibrating", ScoreConfidence.CALIBRATING.raw)
        assertEquals("building", ScoreConfidence.BUILDING.raw)
        assertEquals("solid", ScoreConfidence.SOLID.raw)
    }

    // H9: a high-efficiency night whose deep+REM share is implausibly low is flagged LOW-CONFIDENCE
    // (downgraded SOLID → BUILDING) — honest "staging may be off", no faked stages. Mirrors Swift.
    @Test
    fun confidence_restH9DowngradesLowRestorativeHighEfficiencyNight() {
        val asleep = 8.0 * 3600.0
        assertEquals(
            ScoreConfidence.BUILDING,
            ScoreConfidence.forRest(
                hasSession = true, hasStagedSleep = true,
                asleepSeconds = asleep, restorativeSeconds = asleep * 0.03, efficiency = 0.95,
            ),
        )
    }

    @Test
    fun confidence_restH9KeepsSolidWhenRestorativeHealthy() {
        val asleep = 8.0 * 3600.0
        assertEquals(
            ScoreConfidence.SOLID,
            ScoreConfidence.forRest(
                hasSession = true, hasStagedSleep = true,
                asleepSeconds = asleep, restorativeSeconds = asleep * 0.45, efficiency = 0.95,
            ),
        )
    }

    @Test
    fun confidence_restH9DoesNotFlagLowEfficiencyNight() {
        // A fragmented (low-efficiency) night legitimately carries little deep/REM — must NOT flag it.
        val asleep = 8.0 * 3600.0
        assertEquals(
            ScoreConfidence.SOLID,
            ScoreConfidence.forRest(
                hasSession = true, hasStagedSleep = true,
                asleepSeconds = asleep, restorativeSeconds = asleep * 0.03, efficiency = 0.60,
            ),
        )
    }

    @Test
    fun confidence_restH9NeverUpgradesNonSolidBase() {
        // No staged sleep → base BUILDING; H9 only DOWNGRADES, so it can't lift to SOLID.
        assertEquals(
            ScoreConfidence.BUILDING,
            ScoreConfidence.forRest(
                hasSession = true, hasStagedSleep = false,
                asleepSeconds = 8.0 * 3600.0, restorativeSeconds = 0.0, efficiency = 0.95,
            ),
        )
    }

    // Fable Rest #12 — hero surfaces the same tier from a banked DailyMetric row.
    @Test
    fun confidence_forRestFromDaily_noSessionIsCalibrating() {
        assertEquals(
            ScoreConfidence.CALIBRATING,
            ScoreConfidence.forRestFromDaily(null, hasSession = false),
        )
        assertEquals(
            ScoreConfidence.CALIBRATING,
            ScoreConfidence.forRestFromDaily(
                DailyMetric(deviceId = "x", day = "2026-07-12"),
                hasSession = false,
            ),
        )
    }

    @Test
    fun confidence_forRestFromDaily_durationOnlyIsBuilding() {
        assertEquals(
            ScoreConfidence.BUILDING,
            ScoreConfidence.forRestFromDaily(
                DailyMetric(deviceId = "x", day = "2026-07-12", totalSleepMin = 420.0),
                hasSession = true,
            ),
        )
    }

    @Test
    fun confidence_forRestFromDaily_stagedHealthyOneNightIsBuilding() {
        // SHIP #75 — first staged night must not read SOLID/STANDARD.
        assertEquals(
            ScoreConfidence.BUILDING,
            ScoreConfidence.forRestFromDaily(
                DailyMetric(
                    deviceId = "x",
                    day = "2026-07-12",
                    totalSleepMin = 480.0,
                    efficiency = 0.92,
                    deepMin = 80.0,
                    remMin = 90.0,
                ),
                hasSession = true,
                scoredRestNights = 1,
            ),
        )
    }

    @Test
    fun confidence_forRestFromDaily_stagedHealthyThreeNightsIsSolid() {
        assertEquals(
            ScoreConfidence.SOLID,
            ScoreConfidence.forRestFromDaily(
                DailyMetric(
                    deviceId = "x",
                    day = "2026-07-12",
                    totalSleepMin = 480.0,
                    efficiency = 0.92,
                    deepMin = 80.0,
                    remMin = 90.0,
                ),
                hasSession = true,
                scoredRestNights = 3,
            ),
        )
    }

    @Test
    fun confidence_forRestFromDaily_h9Downgrades() {
        assertEquals(
            ScoreConfidence.BUILDING,
            ScoreConfidence.forRestFromDaily(
                DailyMetric(
                    deviceId = "x",
                    day = "2026-07-12",
                    totalSleepMin = 480.0,
                    efficiency = 0.95,
                    deepMin = 5.0,
                    remMin = 5.0,
                ),
                hasSession = true,
            ),
        )
    }
}
