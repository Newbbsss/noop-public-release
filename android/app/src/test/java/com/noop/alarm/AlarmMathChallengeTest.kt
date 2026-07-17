package com.noop.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmMathChallengeTest {
    @Test
    fun requireMath_masterToggle() {
        assertTrue(
            AlarmMathChallenge.requireMath(
                mathEnabled = true,
                mathOnDrowsy = false,
                hrBpm = 90,
                drowsyThreshold = 55,
                isFinalDeadline = false,
            ),
        )
    }

    @Test
    fun requireMath_drowsyOnlyOnFinalWithLowHr() {
        assertTrue(
            AlarmMathChallenge.requireMath(
                mathEnabled = false,
                mathOnDrowsy = true,
                hrBpm = 48,
                drowsyThreshold = 55,
                isFinalDeadline = true,
            ),
        )
        assertFalse(
            AlarmMathChallenge.requireMath(
                mathEnabled = false,
                mathOnDrowsy = true,
                hrBpm = 48,
                drowsyThreshold = 55,
                isFinalDeadline = false,
            ),
        )
        assertFalse(
            AlarmMathChallenge.requireMath(
                mathEnabled = false,
                mathOnDrowsy = true,
                hrBpm = null,
                drowsyThreshold = 55,
                isFinalDeadline = true,
            ),
        )
    }

    @Test
    fun next_producesAnswerInRange() {
        val p = AlarmMathChallenge.next(42L)
        assertTrue(p.answer in 0..30)
        assertEquals(true, p.prompt.contains("+") || p.prompt.contains("−"))
    }
}
