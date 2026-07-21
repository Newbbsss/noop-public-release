package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sparse-cardiac deep/REM reopen grounded in the 13 Jul screenshot (Deep 2%/10m, REM 0%
 * vs WHOOP Deep 33% / REM 16%, "No movement detail for this night").
 */
class SleepStagerSparseDeepRemTest {

    /** EpochFeatures: index, midTs, count, moveFrac, ckSleep, hr, hrVar, rmssd, sdnn, respRate, rrv, clock. */
    private fun feat(
        hr: Double,
        moveFrac: Double = 0.0,
        rmssd: Double = Double.NaN,
        hrVar: Double = Double.NaN,
        rrv: Double = Double.NaN,
        clock: Double = 0.2,
    ) = SleepStager.EpochFeatures(
        index = 0, midTs = 0.0, count = 0.0, moveFrac = moveFrac, ckSleep = true,
        hr = hr, hrVar = hrVar, rmssd = rmssd, sdnn = Double.NaN,
        respRate = Double.NaN, rrv = rrv, clock = clock,
    )

    @Test
    fun sparseCardiac_stillLowHr_classifiesDeepWithoutResp() {
        val label = SleepStager.classifyOne(
            feat(hr = 48.0, clock = 0.15),
            hrLo = 52.0, hrHi = 70.0,
            rmssdHi = 50.0, hrvarHi = 120.0,
            rrvHi = 1.0, rrvLo = 0.5,
            cardiacSparse = true,
        )
        assertEquals("deep", label)
    }

    @Test
    fun sparseCardiac_lateStillHighHr_classifiesRemWithoutHrVar() {
        val label = SleepStager.classifyOne(
            feat(hr = 72.0, clock = 0.55),
            hrLo = 52.0, hrHi = 70.0,
            rmssdHi = 50.0, hrvarHi = 120.0,
            rrvHi = 1.0, rrvLo = 0.5,
            cardiacSparse = true,
        )
        assertEquals("rem", label)
    }

    @Test
    fun denseNight_stillLowHr_withoutRmssd_staysLight() {
        // Dense HRV nights: NaN RMSSD is NOT pro-deep (8.6.242) — need finite high-tone bar.
        val label = SleepStager.classifyOne(
            feat(hr = 48.0, clock = 0.15),
            hrLo = 52.0, hrHi = 70.0,
            rmssdHi = 50.0, hrvarHi = 120.0,
            rrvHi = 1.0, rrvLo = 0.5,
            cardiacSparse = false,
        )
        assertEquals("light", label)
    }

    @Test
    fun denseNight_lateHighHrWithoutHrVarOpensRem() {
        // 2026-07-14 pack: WHOOP 16% REM vs NOOP 0% — reopen still+hrHigh late night when RRV absent.
        val label = SleepStager.classifyOne(
            feat(hr = 72.0, clock = 0.55),
            hrLo = 52.0, hrHi = 70.0,
            rmssdHi = 50.0, hrvarHi = 120.0,
            rrvHi = 1.0, rrvLo = 0.5,
            cardiacSparse = false,
        )
        assertEquals("rem", label)
    }

    @Test
    fun denseNight_finiteRmssdAtLowerBar_classifiesDeep() {
        // Jul-20 pack: stageHRVHighPct 70→58 — finite RMSSD just above the bar still clears parasympOK.
        val label = SleepStager.classifyOne(
            feat(hr = 48.0, rmssd = 60.0, clock = 0.15),
            hrLo = 52.0, hrHi = 70.0,
            rmssdHi = 58.0, hrvarHi = 120.0,
            rrvHi = 1.0, rrvLo = 0.5,
            cardiacSparse = false,
        )
        assertEquals("deep", label)
    }

    @Test
    fun onsetPersistEpochs_tightenedToFive() {
        assertEquals(5, SleepStager.onsetPersistEpochs)
    }
}
