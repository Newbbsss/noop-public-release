package com.noop.ui

import com.noop.data.SleepSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stages chart / NOOP Asleep must populate when a night window exists — including HC
 * awake-only JSON and `{stage,min}` minute arrays (not only timed `-noop` segments).
 */
class SleepHeroStagesTest {

    @Test
    fun parseSessionStages_readsStageMinArray() {
        val json = """[{"stage":"light","min":210.0},{"stage":"deep","min":80.0},{"stage":"awake","min":12.5}]"""
        val m = parseSessionStages(json)!!
        assertEquals(210.0, m.light, 0.01)
        assertEquals(80.0, m.deep, 0.01)
        assertEquals(12.5, m.awake, 0.01)
    }

    @Test
    fun stagesForHeroSession_fillsLightFromTibWhenAwakeOnly() {
        // ~5h window with HC-style awake-only JSON
        val start = 1_000_000L
        val end = start + 5L * 3600L
        val s = SleepSession(
            deviceId = "health-connect",
            startTs = start,
            endTs = end,
            stagesJSON = """[{"stage":"awake","min":12.5}]""",
        )
        val m = stagesForHeroSession(s)!!
        assertTrue(m.light > 200.0)
        assertEquals(12.5, m.awake, 0.01)
        assertEquals(0.0, m.deep, 0.01)
        assertEquals(0.0, m.rem, 0.01)
    }

    @Test
    fun enrichAwakeOnlyStages_writesLightMinutes() {
        val start = 1_000_000L
        val end = start + 4L * 3600L
        val s = SleepSession(
            deviceId = "health-connect",
            startTs = start,
            endTs = end,
            stagesJSON = """[{"stage":"awake","min":20.0}]""",
        )
        val out = enrichAwakeOnlyStages(s)
        assertNotNull(out.stagesJSON)
        val m = parseSessionStages(out.stagesJSON)!!
        assertTrue(m.light > 100.0)
        assertTrue((m.light + m.awake) >= 200.0)
        // Timed segments so hypnogram / Stage breakdown always have intervals.
        assertTrue((parsePersistedSegments(out.stagesJSON)?.size ?: 0) >= 2)
    }

    @Test
    fun heroDisplay_sessionFallbackWhenModelNull() {
        val start = 1_000_000L
        val end = start + 6L * 3600L
        val session = SleepSession(
            deviceId = "health-connect",
            startTs = start,
            endTs = end,
            stagesJSON = """[{"stage":"awake","min":15.0}]""",
        )
        val night = HeroNight(
            session = session,
            dayKey = "2026-07-15",
            realSegments = null,
            clockLabel = "Wed 15 Jul · 06:00–12:00",
        )
        val d = heroDisplay(null, night)
        assertNotNull(d)
        assertTrue(d!!.stages.asleep > 0.0)
    }

    @Test
    fun selectNight_synthesizesSegmentsForHcAwakeOnly() {
        val start = 1_784_111_271L
        val end = 1_784_130_190L
        val session = SleepSession(
            deviceId = "health-connect",
            startTs = start,
            endTs = end,
            stagesJSON = """[{"stage":"awake","min":8.58}]""",
        )
        val night = selectNight(listOf(listOf(session)), days = emptyList(), offset = 0)
        assertNotNull(night)
        assertTrue((night!!.realSegments?.sumOf { it.second.toDouble() } ?: 0.0) > 60.0)
    }

    @Test
    fun resolveStageTimeline_lightOnlyGetsSelectableIntervals() {
        // MAIN regression: HC light-only fell through to hypnogram+PipBar; DEBUG kept 4-row select.
        val start = 1_000_000L
        val end = start + 5L * 3600L + 7L * 60L
        val session = SleepSession(
            deviceId = "health-connect",
            startTs = start,
            endTs = end,
            stagesJSON = """[{"stage":"light","min":307.0}]""",
        )
        val stages = Stages(awake = 0.0, light = 307.0, deep = 0.0, rem = 0.0)
        val resolved = resolveStageTimeline(session, stages)
        assertTrue(resolved.intervals.isNotEmpty())
        assertEquals(307.0, resolved.stages.light, 1.0)
        assertEquals(0.0, resolved.stages.deep, 0.01)
        assertEquals(0.0, resolved.stages.rem, 0.01)
    }

    @Test
    fun resolveStageTimeline_barrenTimedYieldsToRicherDeepRem() {
        // P0: mono-light timed fill (enrich clobber) must not hide DailyMetric Deep/REM.
        val start = 1_784_000_000L
        val end = start + 8L * 3600L
        val session = SleepSession(
            deviceId = "my-whoop-noop",
            startTs = start,
            endTs = end,
            stagesJSON = """[{"start":$start,"end":$end,"stage":"light"}]""",
        )
        val richer = Stages(awake = 20.0, light = 200.0, deep = 90.0, rem = 80.0)
        val resolved = resolveStageTimeline(session, richer)
        assertTrue(resolved.stages.deep > 0.0)
        assertTrue(resolved.stages.rem > 0.0)
        assertTrue(resolved.intervals.any { it.stage.equals("deep", true) })
        assertTrue(resolved.intervals.any { it.stage.equals("rem", true) })
    }

    @Test
    fun isBarrenTimedStages_monoLightIsBarren() {
        val start = 100L
        val end = 200L
        assertTrue(isBarrenTimedStages(listOf(PersistedSegment(start, end, "light"))))
        assertTrue(
            isBarrenTimedStages(
                listOf(
                    PersistedSegment(start, start + 50, "wake"),
                    PersistedSegment(start + 50, end, "light"),
                ),
            ),
        )
        assertTrue(
            !isBarrenTimedStages(
                listOf(
                    PersistedSegment(start, start + 40, "light"),
                    PersistedSegment(start + 40, start + 80, "deep"),
                    PersistedSegment(start + 80, end, "rem"),
                ),
            ),
        )
    }
}
