package com.noop.analytics

import com.noop.protocol.DeviceFamily
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 8.6.234: product path is Gilbert V1 only — [IntelligenceEngine.sleepStagerV2ForFamily] always false.
 */
class SleepV2FamilyGateTest {

    @Test
    fun `V2 never runs on any family`() {
        assertFalse(IntelligenceEngine.sleepStagerV2ForFamily(enabled = true, family = DeviceFamily.WHOOP5))
        assertFalse(IntelligenceEngine.sleepStagerV2ForFamily(enabled = true, family = DeviceFamily.WHOOP4))
        assertFalse(IntelligenceEngine.sleepStagerV2ForFamily(enabled = false, family = DeviceFamily.WHOOP5))
    }
}
