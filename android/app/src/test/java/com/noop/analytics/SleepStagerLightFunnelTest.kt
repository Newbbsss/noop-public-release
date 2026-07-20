package com.noop.analytics

import com.noop.data.GravitySample
import com.noop.data.HrSample
import com.noop.data.RrInterval
import com.noop.data.RespSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Read-only Light-funnel + wake-concordance diagnostics (Track C, 2026-07-20).
 * Mirrors [SleepStagerRemFunnelTest] shape: attribute first unmet Deep gate, count
 * reimpose/merge demotes, change nothing. Knobs stay measure-only.
 */
class SleepStagerLightFunnelTest {

    private val dev = "test"

    /** 2025-06-10 00:00:00 UTC — arbitrary fixed midnight. */
    private val refMidnight = 1_749_513_600L
    private fun startAtHour(hourUTC: Int): Long = refMidnight + hourUTC * 3_600L

    private fun stillGravity(start: Long, durationS: Int): List<GravitySample> =
        (0 until durationS).map { GravitySample(deviceId = dev, ts = start + it, x = 0.0, y = 0.0, z = 1.0) }

    private fun hrStream(start: Long, durationS: Int, bpm: Int): List<HrSample> =
        (0 until durationS).map { HrSample(deviceId = dev, ts = start + it, bpm = bpm) }

    private fun feature(
        moveFrac: Double, hr: Double, hrVar: Double, rmssd: Double, rrv: Double,
        clock: Double = 0.5,
    ): SleepStager.EpochFeatures = SleepStager.EpochFeatures(
        index = 0, midTs = 0.0, count = 0.0, moveFrac = moveFrac, ckSleep = true,
        hr = hr, hrVar = hrVar, rmssd = rmssd, sdnn = 0.0, respRate = 14.0, rrv = rrv, clock = clock,
    )

    // Bars: hrLo=55, hrHi=70, rmssdHi=50, hrvarHi=1, rrvHi=1, rrvLo=0.5
    private fun reason(
        f: SleepStager.EpochFeatures,
        cardiacSparse: Boolean = false,
    ): SleepStager.LightClassifyReason =
        SleepStager.lightFallthroughReason(f, hrLo = 55.0, hrHi = 70.0, rmssdHi = 50.0,
            hrvarHi = 1.0, rrvHi = 1.0, rrvLo = 0.5, cardiacSparse = cardiacSparse)

    @Test
    fun lightFallthroughReasonAttributesEachGate() {
        // Deep win: still + parasymp + low HR + regular resp.
        assertEquals(SleepStager.LightClassifyReason.WON_DEEP,
            reason(feature(0.0, 50.0, 0.0, 60.0, 0.1)))

        // Wake win: moving + cardiac.
        assertEquals(SleepStager.LightClassifyReason.WON_WAKE,
            reason(feature(0.5, 80.0, 5.0, 20.0, 0.1)))

        // REM win: still + cardiac + irregular resp.
        assertEquals(SleepStager.LightClassifyReason.WON_REM,
            reason(feature(0.0, 80.0, 5.0, 20.0, 2.0)))

        // notStill: above still bar (0.21) but not a wake win (mid HR, flat hrVar — no cardiac).
        assertEquals(SleepStager.LightClassifyReason.NOT_STILL,
            reason(feature(0.25, 60.0, 0.0, 60.0, 0.1)))

        // parasympFail: still + low HR + regular resp but RMSSD below high bar.
        assertEquals(SleepStager.LightClassifyReason.PARASYMP_FAIL,
            reason(feature(0.0, 50.0, 0.0, 20.0, 0.1)))

        // midHR: still + parasympOK + regular resp but HR above hrLo.
        assertEquals(SleepStager.LightClassifyReason.MID_HR,
            reason(feature(0.0, 60.0, 0.0, 60.0, 0.1)))

        // respIrregular: still + parasymp + low HR but rrv above rrvLo (not regular) and not REM
        // (no cardiac activation — mid-ish hrVar flat, hr low so not rem).
        assertEquals(SleepStager.LightClassifyReason.RESP_IRREGULAR,
            reason(feature(0.0, 50.0, 0.0, 60.0, 0.8)))
    }

    @Test
    fun nullWhenNoGravity() {
        assertNull(SleepStager.lightFunnelDiagnostic(0L, 1800L, emptyList(), emptyList(), emptyList(), emptyList()))
        assertNull(SleepStager.wakeConcordance(0L, 1800L, emptyList(), emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun flatStillLowHrNightPartitionsSleepEpochs() {
        // Still body, flat low HR, no R-R / resp → Deep via sparse path or Light via midHR/parasymp.
        // Flat 50 bpm still night with no RR: cardiacSparse → still+hrLow+no-resp → Deep wins many epochs.
        val start = startAtHour(2)
        val dur = 90 * 60
        val grav = stillGravity(start, dur)
        val hr = hrStream(start, dur, 50)
        val diag = SleepStager.lightFunnelDiagnostic(
            start, start + dur, grav, hr, emptyList<RrInterval>(), emptyList<RespSample>())
        assertNotNull(diag)
        val d = diag!!
        assertTrue("sleep period must contain epochs", d.sleepEpochs > 0)
        val attributed = d.lightAtClassify + d.wonWake + d.wonDeep + d.wonRem
        assertEquals("classifier-mouth reasons must partition sleep epochs", d.sleepEpochs, attributed)
        assertEquals(
            "blocked buckets must equal lightAtClassify",
            d.lightAtClassify,
            d.blockedNotStill + d.blockedParasympFail + d.blockedMidHR + d.blockedRespIrregular,
        )
        assertTrue(d.summary.contains("Light-funnel:"))
        assertFalse("RMSSD absent on no-RR night", d.rmssdChannelPresent)
    }

    @Test
    fun diagnosticIsReadOnly() {
        val start = startAtHour(2)
        val dur = 90 * 60
        val grav = stillGravity(start, dur)
        val hr = hrStream(start, dur, 50)
        val before = SleepStager.stageSession(start, start + dur, grav, hr, emptyList(), emptyList())
        SleepStager.lightFunnelDiagnostic(start, start + dur, grav, hr, emptyList(), emptyList())
        SleepStager.wakeConcordance(start, start + dur, grav, hr, emptyList(), emptyList())
        val after = SleepStager.stageSession(start, start + dur, grav, hr, emptyList(), emptyList())
        assertEquals("light funnel / wake concordance must not change hypnogram", before, after)
    }

    @Test
    fun wakeConcordanceAccountsForEverySleepEpoch() {
        val start = startAtHour(2)
        val dur = 90 * 60
        val grav = stillGravity(start, dur)
        val hr = hrStream(start, dur, 50)
        val c = SleepStager.wakeConcordance(
            start, start + dur, grav, hr, emptyList<RrInterval>(), emptyList<RespSample>())
        assertNotNull(c)
        val w = c!!
        assertTrue(w.sleepEpochs > 0)
        assertEquals(
            "agree + disagreements must partition sleep epochs",
            w.sleepEpochs,
            w.agree + w.ckWakeStagedSleep + w.ckSleepStagedWake,
        )
        assertTrue(w.agreementFrac in 0.0..1.0)
        assertTrue(w.summary.contains("wake-concordance:"))
    }
}
