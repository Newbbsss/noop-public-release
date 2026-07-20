package com.noop.analytics

import com.noop.data.GravitySample
import com.noop.data.HrSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Track B / 8.6.237 — mid-bout high-motion / CK-wake → WASO > 0 and asleep < TIB.
 * Pins [SleepStager.promoteMidBoutWake] so Light no longer swallows sustained arousals.
 */
class SleepStagerWasoWakeTest {

    private val dev = "test"
    private val refMidnight = 1_749_513_600L

    private fun startAtHour(hourUTC: Int): Long = refMidnight + hourUTC * 3_600L

    private fun stillGravity(start: Long, durationS: Int): List<GravitySample> =
        (0 until durationS).map {
            GravitySample(deviceId = dev, ts = start + it, x = 0.0, y = 0.0, z = 1.0)
        }

    /** Alternating orientation → |Δg| ≫ moveDeltaThreshold every sample (high moveFrac). */
    private fun movingGravity(start: Long, durationS: Int): List<GravitySample> =
        (0 until durationS).map { i ->
            if (i % 2 == 0) {
                GravitySample(deviceId = dev, ts = start + i, x = 1.0, y = 0.0, z = 0.0)
            } else {
                GravitySample(deviceId = dev, ts = start + i, x = 0.0, y = 1.0, z = 0.0)
            }
        }

    private fun hrStream(start: Long, durationS: Int, bpm: Int): List<HrSample> =
        (0 until durationS).map { HrSample(deviceId = dev, ts = start + it, bpm = bpm) }

    @Test
    fun promoteMidBoutWake_highMotionOrCkWake_becomesWake() {
        val labels = listOf("light", "light", "deep", "light", "rem")
        val feats = labels.indices.map { i ->
            SleepStager.EpochFeatures(
                index = i, midTs = 0.0, count = 0.0,
                moveFrac = if (i == 2) 0.5 else 0.0,
                ckSleep = i != 3,
                hr = 55.0, hrVar = Double.NaN, rmssd = Double.NaN, sdnn = Double.NaN,
                respRate = Double.NaN, rrv = Double.NaN, clock = 0.3,
            )
        }
        val out = SleepStager.promoteMidBoutWake(labels, feats, onsetIdx = 0, finalWakeIdx = 4)
        assertEquals(listOf("light", "light", "wake", "wake", "rem"), out)
    }

    @Test
    fun promoteMidBoutWake_outsideSleepPeriod_unchanged() {
        val labels = listOf("light", "deep", "light")
        val feats = labels.indices.map {
            SleepStager.EpochFeatures(
                index = it, midTs = 0.0, count = 0.0, moveFrac = 1.0, ckSleep = false,
                hr = 80.0, hrVar = Double.NaN, rmssd = Double.NaN, sdnn = Double.NaN,
                respRate = Double.NaN, rrv = Double.NaN, clock = 0.0,
            )
        }
        // Sleep period is only index 1 — leading/trailing stay as classified (forced wake later).
        val out = SleepStager.promoteMidBoutWake(labels, feats, onsetIdx = 1, finalWakeIdx = 1)
        assertEquals("wake", out[1])
        assertEquals("light", out[0])
        assertEquals("light", out[2])
    }

    @Test
    fun midSleepMotion_wasoPositive_asleepLessThanTib() {
        // 4 h still night + 10 min mid-bout thrash (≥ fragmentMergeMin so wake survives merge).
        val start = startAtHour(1)
        val dur = 4 * 60 * 60
        val burstStart = start + 2 * 60 * 60
        val burstDur = 10 * 60
        val grav = stillGravity(start, (burstStart - start).toInt()) +
            movingGravity(burstStart, burstDur) +
            stillGravity(burstStart + burstDur, (start + dur - burstStart - burstDur).toInt())
        val hr = hrStream(start, dur, 52)

        val segs = SleepStager.stageSession(
            start, start + dur, grav, hr, emptyList(), emptyList(),
        )
        val eff = SleepStager.efficiency(start, start + dur, segs)
        val session = DetectedSleep(
            start = start, end = start + dur, efficiency = eff, stages = segs,
            restingHR = 52, avgHRV = null,
        )
        val m = SleepStager.hypnogramMetrics(session)

        assertTrue("mid-bout motion must open WASO > 0 (got ${m.wasoS}s)", m.wasoS > 0.0)
        assertTrue(
            "asleep (TST) must be less than time in bed (got TST=${m.tstS}s TIB=${m.tibS}s)",
            m.tstS < m.tibS,
        )
        assertTrue("efficiency must be < 1 when WASO is present", m.efficiency < 1.0)
        // Sustained 10 min thrash should land roughly that much WASO (tolerance for CK edges).
        assertTrue(
            "WASO should be on the order of the 10 min burst (got ${m.wasoS / 60.0} min)",
            m.wasoS >= 3.0 * 60.0,
        )
    }
}
