package com.noop.alarm

/**
 * Tiny pure helpers for wake-alarm math dismiss. Two-operand add/sub with answers in 2..30.
 */
object AlarmMathChallenge {
    data class Problem(val prompt: String, val answer: Int)

    fun next(seed: Long = System.currentTimeMillis()): Problem {
        val r = kotlin.random.Random(seed)
        val a = r.nextInt(2, 13)
        val b = r.nextInt(2, 13)
        return if (r.nextBoolean()) {
            Problem("$a + $b", a + b)
        } else {
            val hi = maxOf(a, b)
            val lo = minOf(a, b)
            Problem("$hi − $lo", hi - lo)
        }
    }

    /**
     * Whether this fire should require math.
     * - Always when [mathEnabled].
     * - Or when [mathOnDrowsy] and live [hrBpm] is present and below [drowsyThreshold].
     * Missing HR never invents drowsiness.
     */
    fun requireMath(
        mathEnabled: Boolean,
        mathOnDrowsy: Boolean,
        hrBpm: Int?,
        drowsyThreshold: Int,
        isFinalDeadline: Boolean,
    ): Boolean {
        if (mathEnabled) return true
        if (!mathOnDrowsy || !isFinalDeadline) return false
        val hr = hrBpm ?: return false
        return hr in 1 until drowsyThreshold
    }
}
