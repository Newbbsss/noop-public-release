package com.noop.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [SixAxisMotionLabels.preferStrapDriver] honesty: band only — live type-51 wins,
 * offload drives when that is all we have (labeled not-live), null = waiting (never phone).
 */
class SixAxisMotionLabelsTest {

    private fun sample(kind: SixAxisSourceKind, ax: Float = 0.1f) = SixAxisSample(
        ax = ax, ay = 0f, az = 1f,
        gx = 0f, gy = 0f, gz = 0f,
        source = kind,
        tsMs = 1L,
    )

    @Test
    fun preferStrap_liveDrives() {
        val strap = sample(SixAxisSourceKind.STRAP_LIVE, ax = 0.5f)
        assertEquals(strap, SixAxisMotionLabels.preferStrapDriver(strap))
        assertTrue(SixAxisMotionLabels.isLiveDriver(strap))
    }

    @Test
    fun preferStrap_offloadWhenNoLive() {
        val offload = sample(SixAxisSourceKind.STRAP_OFFLOAD, ax = 0.9f)
        assertEquals(offload, SixAxisMotionLabels.preferStrapDriver(offload))
        assertFalse(SixAxisMotionLabels.isLiveDriver(offload))
    }

    @Test
    fun preferStrap_waitingWhenNull() {
        assertNull(SixAxisMotionLabels.preferStrapDriver(null))
        assertFalse(SixAxisMotionLabels.isLiveDriver(null))
    }

    @Test
    fun labels_areHonest() {
        assertEquals("Strap IMU (live)", SixAxisMotionLabels.sourceTitle(SixAxisSourceKind.STRAP_LIVE))
        assertEquals("Strap offload (not live)", SixAxisMotionLabels.sourceTitle(SixAxisSourceKind.STRAP_OFFLOAD))
        assertTrue(SixAxisMotionLabels.sourceDetail(SixAxisSourceKind.STRAP_OFFLOAD).contains("Not a live"))
        assertTrue(SixAxisMotionLabels.WAITING_DETAIL.contains("No phone"))
        assertTrue(SixAxisMotionLabels.WAITING_TITLE.contains("band"))
    }
}
