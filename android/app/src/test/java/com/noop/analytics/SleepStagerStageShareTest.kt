package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Jul-13 screenshot pack: MAIN showed ~32% awake / 66% light / 2% deep / 0% REM while WHOOP
 * was ~24 / 29 / 31 / 16. This pins Stage-2 share recovery on a sparse-cardiac synthetic night
 * (still body, missing RMSSD/RRV, HR terciles) without inventing stages from HC TIB.
 */
class SleepStagerStageShareTest {

    private fun feat(
        hr: Double,
        moveFrac: Double = 0.0,
        clock: Double,
    ) = SleepStager.EpochFeatures(
        index = 0, midTs = 0.0, count = 0.0, moveFrac = moveFrac, ckSleep = true,
        hr = hr, hrVar = Double.NaN, rmssd = Double.NaN, sdnn = Double.NaN,
        respRate = Double.NaN, rrv = Double.NaN, clock = clock,
    )

    /** Sparse night: early low HR, mid mid HR, late high HR — all still. */
    private fun sparseNightFeats(n: Int = 120): List<SleepStager.EpochFeatures> {
        return (0 until n).map { i ->
            val clock = (i + 0.5) / n.toDouble()
            val hr = when {
                clock < 0.35 -> 48.0 + (i % 5) // deep-leaning
                clock < 0.65 -> 58.0 + (i % 4) // light-leaning
                else -> 72.0 + (i % 5) // rem-leaning late
            }
            feat(hr = hr, clock = clock)
        }
    }

    @Test
    fun constants_widenDeepRemBandsForJul13Pack() {
        assertTrue(SleepStager.stageHRLowPct >= 40.0)
        assertTrue(SleepStager.stageHRHighPct <= 62.0)
        assertTrue(SleepStager.stageStillMoveFrac >= 0.15)
    }

    @Test
    fun sparseCardiacNight_deepAndRemSharesRecoverTowardWhoop() {
        val feats = sparseNightFeats()
        val sleepFeats = feats // all in-bed for this synthetic
        val hrLo = SleepStager.percentile(sleepFeats.map { it.hr }, SleepStager.stageHRLowPct)!!
        val hrHi = SleepStager.percentile(sleepFeats.map { it.hr }, SleepStager.stageHRHighPct)!!
        val labels = sleepFeats.map {
            SleepStager.classifyOne(
                it,
                hrLo = hrLo, hrHi = hrHi,
                rmssdHi = 50.0, hrvarHi = 120.0,
                rrvHi = 1.0, rrvLo = 0.5,
                cardiacSparse = true,
            )
        }
        // Soften late-deep wipe: only demote when early deep already adequate.
        val reimposed = SleepStager.reimposePhysiology(
            labels, sleepFeats, onsetIdx = 0, finalWakeIdx = labels.lastIndex,
        )
        val n = reimposed.size.toDouble()
        fun share(stage: String) = reimposed.count { it == stage } / n
        val deep = share("deep")
        val rem = share("rem")
        val light = share("light")
        val wake = share("wake")
        // Before fix: deep≈0.02 rem≈0. Target: deep≥0.20 rem≥0.10 (WHOOP 0.31 / 0.16 — leave headroom).
        assertTrue("deep share was ${"%.2f".format(deep)}, expected ≥0.20", deep >= 0.20)
        assertTrue("rem share was ${"%.2f".format(rem)}, expected ≥0.10", rem >= 0.10)
        assertTrue("light share was ${"%.2f".format(light)}, expected ≤0.55", light <= 0.55)
        assertEquals(0.0, wake, 1e-9) // all still → no wake in this synthetic
        // Record δ vs the MAIN 32/66/2 / 0 baseline for CONTINUE.
        val deepPct = (deep * 100).roundToInt()
        val remPct = (rem * 100).roundToInt()
        val lightPct = (light * 100).roundToInt()
        assertTrue("post-fix deep $deepPct% should beat MAIN 2%", deepPct >= 20)
        assertTrue("post-fix rem $remPct% should beat MAIN 0%", remPct >= 10)
        assertTrue("post-fix light $lightPct% should be below MAIN 66%", lightPct <= 55)
    }
}
