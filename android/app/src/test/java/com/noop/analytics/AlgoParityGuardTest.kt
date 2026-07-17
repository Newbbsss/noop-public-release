package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard: debug (`com.noop.whoop.debug`) and MAIN (`com.noop.whoop`) compile the SAME
 * `src/main` analytics. Flavor dirs must not fork RestScorer / SleepStager / DaytimeStress /
 * SleepDebt constants. Both build types share one source tree — only applicationIdSuffix differs.
 *
 * If you add flavor-specific algo code under src/debug or src/full, this test documents the
 * contract that must still hold: one method, WHOOP-matched, shipped to both packages.
 */
class AlgoParityGuardTest {

    @Test
    fun restScorerWeights_sumToOneWhenAllPresent() {
        val sum = RestScorer.wDuration + RestScorer.wEfficiency +
            RestScorer.wRestorative + RestScorer.wConsistency
        assertEquals(1.0, sum, 1e-9)
    }

    @Test
    fun daytimeStressNightTipCeiling_belowHighBand() {
        assertTrue(DaytimeStress.nightTipCeiling < DaytimeStress.highBandFloor)
        assertTrue(DaytimeStress.nightTipCeiling >= 1.0)
    }

    @Test
    fun daytimeStressMotionRetune_keepsCalmHotterDamp() {
        // 8.6.77 WHOOP match: calm floor + stronger motion/workout damp (debug==MAIN src/main).
        assertEquals(2.00, DaytimeStress.calmAnchorOffset, 0.0)
        assertEquals(0.030, DaytimeStress.motionBusyFloor, 1e-9)
        assertEquals(0.58, DaytimeStress.motionBusyDamp, 1e-9)
        assertEquals(0.62, DaytimeStress.workoutOverlapBias, 1e-9)
        assertTrue(DaytimeStress.motionBusyDamp > 0.45)
        assertTrue(DaytimeStress.workoutOverlapBias > 0.50)
    }

    @Test
    fun sleepStagerOnset_notLooserThanFiveEpochs() {
        // Early onset was the Jul-13 screenshot miss (NOOP 02:31 vs WHOOP 03:48).
        assertTrue(SleepStager.onsetPersistEpochs >= 5)
        assertTrue(SleepStager.hrOnlyOnsetPersistBuckets >= 4)
    }

    @Test
    fun sleepStagerV2_dreamtDeepGate_isPointFour() {
        // #348 DREAMT retune — Gilbert port; must stay in sync with Swift twin.
        assertEquals(0.40, SleepStagerV2.deepGateThresh, 0.0)
    }

    @Test
    fun thinDeepNight_cannotScoreStrongRest() {
        // WHOOP Sleep% 64 on 2% deep / 0% REM — Rest must not read Strong (~80+).
        val asleep = 7.0 * 3600.0
        val deep = asleep * 0.02
        val rem = 0.0
        val score = RestScorer.rest(asleep, 0.69, deep, rem)!!
        assertTrue("thin deep Rest was $score, expected < 70", score < 70.0)
    }

    @Test
    fun sleepDebtOnTargetBand_isSharedConstant() {
        assertTrue(SleepDebt.ON_TARGET_BAND_MIN > 0.0)
    }

    @Test
    fun sleepStagerBands_matchMainAndDebugSharedSource() {
        // FullDebug and FullRelease share src/main — these bands must stay WHOOP-leaning.
        assertTrue(SleepStager.stageHRLowPct >= 40.0)
        assertTrue(SleepStager.stageHRHighPct <= 62.0)
        assertTrue(SleepStager.stageStillMoveFrac >= 0.15)
    }
}
