package com.noop.analytics

import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fable #201 — "Rest repeats across days" root cause (Fold DB proof, 2026-07-12).
 *
 * [IntelligenceEngine.sleepEditedDaily] receives `editsByStart` spanning the WHOLE recompute window
 * (every userEdited session in ~21 days). A twinless edited block (a hand-logged / edited night with no
 * detected twin ON THE SCORED DAY) used to be folded into EVERY scored day's aggregate via the `manual`
 * union channel, so one edited night overwrote every later day's sleep figures with IDENTICAL copies:
 * the Fold debug DB carried totalSleepMin=391.5 / sleep_performance=82.33 verbatim on 07-10, 07-11 AND
 * 07-12, while 07-11/07-12 genuinely had no scored night (their real state , which the copy masked,
 * keeping Today's Rest ring lying instead of honest). Same shape as the community "721 min identical
 * for 13 days" report (Issue547RepeatRepro proved analyzeDay can't emit it; the edits seam could).
 *
 * The fix scopes a twinless manual block to exactly ONE wake-day: the local day its effective END
 * (corrected onset + decoded in-bed span) falls on , the same attribution rule analyzeDay applies to
 * detected sessions. These tests drive the real seam with a synthetic edited night and assert:
 *   (A) a FOREIGN-day edited block leaves another day's daily untouched (no repeat);
 *   (B) the block still folds into ITS OWN wake-day (the #518/#508 hand-logged-nap fix is preserved).
 */
class SleepEditWakeDayScopeTest {

    private val tz = 0L // UTC device for readable math

    /** One edited night: Jul 10 05:35 → 10:28 UTC-ish, all-light so minutes are unambiguous. */
    private val editStart = 1_783_999_700L // arbitrary anchor; day computed from it below
    private val editInBedMin = 293.0
    private val editStages =
        """[{"start":$editStart,"end":${editStart + (editInBedMin * 60).toLong()},"stage":"light"}]"""
    private val editDay = AnalyticsEngine.dayString(editStart + (editInBedMin * 60).toLong(), tz)
    private val nextDay = AnalyticsEngine.dayString(editStart + (editInBedMin * 60).toLong() + 86_400L, tz)

    @Test
    fun foreignDayEditedBlockDoesNotRepeatOntoOtherDays() {
        // Scored day = the day AFTER the edited night, with NO detected sleep of its own.
        val daily = DailyMetric(deviceId = "my-whoop-noop", day = nextDay, totalSleepMin = null)
        val out = IntelligenceEngine.sleepEditedDaily(
            daily = daily,
            detected = emptyList(),
            editsByStart = mapOf(editStart to editStages),
            editOnsetByStart = mapOf(editStart to editStart),
            tzOffsetSeconds = tz,
            habitualMidsleepSec = null,
        )
        // Before the fix: out.totalSleepMin == 293.0 (the foreign night, repeated). Honest: unchanged.
        assertNull("a foreign-day edited block must not fill another day's sleep", out.totalSleepMin)
        assertEquals(daily, out)
    }

    @Test
    fun editedBlockStillFoldsIntoItsOwnWakeDay() {
        // Scored day = the edited night's OWN wake-day, still with no detected twin (hand-logged case).
        val daily = DailyMetric(deviceId = "my-whoop-noop", day = editDay, totalSleepMin = null)
        val out = IntelligenceEngine.sleepEditedDaily(
            daily = daily,
            detected = emptyList(),
            editsByStart = mapOf(editStart to editStages),
            editOnsetByStart = mapOf(editStart to editStart),
            tzOffsetSeconds = tz,
            habitualMidsleepSec = null,
        )
        assertNotNull("a hand-logged block must still fold into its own wake-day (#518/#508)", out.totalSleepMin)
        assertEquals(editInBedMin, out.totalSleepMin!!, 0.01)
    }
}
