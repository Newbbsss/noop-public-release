package com.noop.ui

import com.noop.data.DailyMetric
import com.noop.data.SleepSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins sleep-export 2026-07-12 display honesty:
 * - Segment-summed asleep / awake win over a stale DailyMetric.totalSleepMin
 * - heroDisplay prefers night segments over model stages when both exist
 * - Never invent Deep/REM when segments are wake+light only
 */
class SleepExportConsistencyTest {

    /** awake 228m + light 285m (export-scale), Deep/REM absent. */
    private val exportScaleJson = run {
        val onset = 1_000_000L
        val wakeEnd = onset + 228 * 60
        val lightEnd = wakeEnd + 285 * 60
        """[{"start":$onset,"end":$wakeEnd,"stage":"wake"},{"start":$wakeEnd,"end":$lightEnd,"stage":"light"}]"""
    }

    @Test
    fun sessionStageSumWinsOverStaleTotalSleepMin() {
        val day = "2026-07-11"
        val days = listOf(
            DailyMetric(
                deviceId = "strap", day = day,
                totalSleepMin = 224.0, // export compare strip 3h44 — stale vs segments
                deepMin = 0.0, remMin = 0.0, lightMin = 200.0,
                efficiency = 0.56,
            ),
        )
        val session = SleepSession(
            deviceId = "strap",
            startTs = 1_000_000L,
            endTs = 1_000_000L + (228 + 285) * 60,
            efficiency = 0.56,
            stagesJSON = exportScaleJson,
        )
        val hero = StageMins(awake = 228.0, light = 285.0, deep = 0.0, rem = 0.0)
        val m = buildSleepModel(days, session = session, selectedDay = day, heroStages = hero)!!
        assertEquals(285.0, m.stages.asleep, 0.5)
        assertEquals(228.0, m.stages.awake, 0.5)
        assertEquals(0.0, m.stages.deep, 0.01)
        assertEquals(0.0, m.stages.rem, 0.01)
    }

    @Test
    fun heroDisplayPrefersSegmentSumsOverModel() {
        val day = "2026-07-11"
        val days = listOf(
            DailyMetric(
                deviceId = "strap", day = day,
                totalSleepMin = 224.0,
                deepMin = 0.0, remMin = 0.0, lightMin = 285.0,
                efficiency = 0.56,
            ),
        )
        val session = SleepSession(
            deviceId = "strap",
            startTs = 1_000_000L,
            endTs = 1_000_000L + (228 + 285) * 60,
            efficiency = 0.56,
            stagesJSON = exportScaleJson,
        )
        // Model built with efficiency-derived awake that disagrees with segments.
        val model = buildSleepModel(
            days, session = session, selectedDay = day,
            heroStages = StageMins(awake = 176.0, light = 285.0, deep = 0.0, rem = 0.0),
        )!!
        val night = HeroNight(
            session = session,
            dayKey = day,
            realSegments = listOf("wake" to 228f, "light" to 285f),
            clockLabel = "02:33 – 11:05",
        )
        val d = heroDisplay(model, night)!!
        assertEquals(285.0, d.stages.asleep, 0.01)
        assertEquals(228.0, d.stages.awake, 0.01)
        assertTrue(d.stages.deep + d.stages.rem == 0.0)
    }

    @Test
    fun parseSessionStagesReadsWakeLightOnly() {
        val mins = parseSessionStages(exportScaleJson)!!
        assertEquals(228.0, mins.awake, 0.5)
        assertEquals(285.0, mins.light, 0.5)
        assertEquals(0.0, mins.deep, 0.01)
        assertEquals(0.0, mins.rem, 0.01)
    }

    @Test
    fun sleepWindowsDiffer_whenMidpointsFarApart() {
        // NOOP overnight vs WHOOP afternoon-style window (export mismatch).
        val noopStart = 1_000_000L
        val noopEnd = noopStart + 8 * 3600
        val whoopStart = noopStart + 4 * 3600 // 4h later onset
        val whoopEnd = whoopStart + 6 * 3600
        assertTrue(sleepWindowsDiffer(noopStart, noopEnd, whoopStart, whoopEnd))
        assertTrue(!sleepWindowsDiffer(noopStart, noopEnd, noopStart + 30 * 60, noopEnd + 30 * 60))
    }

    @Test
    fun wakeDayCaption_formats() {
        assertEquals("Sat 11 Jul", wakeDayCaption("2026-07-11"))
    }
}
