package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [parsePersistedSegments], the pure helper behind the Sleep hero's real
 * hypnogram. Only the verbatim on-device segments array ([{start,end,stage}]) parses; both
 * imported minutes shapes fall through to null so imported nights keep the synthesized fallback.
 */
class SleepStageSegmentsTest {

    @Test
    fun parsesStagerSegmentsArray() {
        val json = """[
            {"start":1000,"end":1900,"stage":"light"},
            {"start":1900,"end":3700,"stage":"deep"},
            {"start":3700,"end":4000,"stage":"wake"}
        ]"""
        val segs = parsePersistedSegments(json)!!
        assertEquals(3, segs.size)
        assertEquals("deep", segs[1].stage)
        assertEquals(1800L, segs[1].end - segs[1].start)
    }

    @Test
    fun minutesDictReturnsNull() {
        assertNull(parsePersistedSegments("""{"light":210,"deep":80,"rem":95,"awake":25}"""))
    }

    @Test
    fun importedStageMinArrayReturnsNull() {
        assertNull(parsePersistedSegments("""[{"stage":"light","min":210.0},{"stage":"deep","min":80.0}]"""))
    }

    @Test
    fun singleTimedSegmentKeepsRealEpoch() {
        // 8.6.138: one real timed epoch must not fall through to weight synth.
        val segs = parsePersistedSegments("""[{"start":1000,"end":2000,"stage":"light"}]""")!!
        assertEquals(1, segs.size)
        assertEquals("light", segs[0].stage)
        assertEquals(1000L, segs[0].start)
        assertEquals(2000L, segs[0].end)
    }

    @Test
    fun garbageReturnsNull() {
        assertNull(parsePersistedSegments("not json"))
        assertNull(parsePersistedSegments(null))
        assertNull(parsePersistedSegments(""))
    }

    @Test
    fun stageSegmentsNeverInventsRemOrDeep() {
        // SHIP #79 — light-only bank must not paint REM/Deep bands.
        val segs = stageSegments(Stages(awake = 20.0, light = 300.0, deep = 0.0, rem = 0.0))
        assertTrue(segs.none { it.first == "rem" || it.first == "deep" })
        assertTrue(segs.any { it.first == "light" })
        val timed = synthesizeTimelineSegments(
            Stages(awake = 0.0, light = 240.0, deep = 0.0, rem = 0.0),
            onsetTs = 1_000L,
            wakeTs = 1_000L + 240L * 60L,
        )
        assertTrue(timed.none { it.stage.equals("rem", ignoreCase = true) })
        assertTrue(timed.none { it.stage.equals("deep", ignoreCase = true) })
    }
}
