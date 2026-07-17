package com.noop.motion

/**
 * Shared 6-axis IMU sample + honest source labels for the Settings / Test Centre motion tester.
 *
 * Honesty contract (WHOOP 5/MG) — **band only**, never phone sensors:
 * - Historical offload 1244-B buffers decode to full accel+gyro @ 100 Hz ([STRAP_OFFLOAD]) — real data,
 *   but **not** a live stream. Never label offload replay as "live".
 * - Live cmd-106 / types 51–56 remain firmware / membership gated; [STRAP_LIVE] is wired for the day
 *   they arrive (type-51), and stays unused until then.
 * - While live strap IMU is gated and no offload sample exists, the tester shows an empty / waiting
 *   band state — it does **not** fall back to phone accel/gyro.
 */
enum class SixAxisSourceKind {
    /** Strap REALTIME_IMU_DATA_STREAM (type 51) — currently never observed on 5/MG. */
    STRAP_LIVE,

    /** Decoded 1244-B type-0x2F historical offload buffer — real 6-axis, not live. */
    STRAP_OFFLOAD,
}

data class SixAxisSample(
    val ax: Float,
    val ay: Float,
    val az: Float,
    /** Gyroscope in deg/s. */
    val gx: Float,
    val gy: Float,
    val gz: Float,
    val source: SixAxisSourceKind,
    val tsMs: Long,
) {
    val accelMagG: Float
        get() = kotlin.math.sqrt(ax * ax + ay * ay + az * az)

    val gyroMagDps: Float
        get() = kotlin.math.sqrt(gx * gx + gy * gy + gz * gz)
}

object SixAxisMotionLabels {
    const val WAITING_TITLE = "Waiting for band IMU…"
    const val WAITING_DETAIL =
        "Move the strap — this tester uses band IMU only. " +
            "Live type-51 (cmd 106 / types 51–56) is still gated on 5/MG (ACK ≠ activation). " +
            "No phone sensors."

    fun sourceTitle(kind: SixAxisSourceKind): String = when (kind) {
        SixAxisSourceKind.STRAP_LIVE -> "Strap IMU (live)"
        SixAxisSourceKind.STRAP_OFFLOAD -> "Strap offload (not live)"
    }

    fun sourceDetail(kind: SixAxisSourceKind): String = when (kind) {
        SixAxisSourceKind.STRAP_LIVE ->
            "Live strap type-51 stream. Rare on 5/MG — R22 ACK ≠ activation."
        SixAxisSourceKind.STRAP_OFFLOAD ->
            "Decoded from a historical 1244-B offload buffer (100 Hz). Not a live stream."
    }

    /**
     * Band-only driver for the moving dot.
     * Prefer strap live type-51 when present; else offload (honestly labeled not-live); else null
     * (empty / waiting — never phone).
     */
    fun preferStrapDriver(strap: SixAxisSample?): SixAxisSample? {
        if (strap?.source == SixAxisSourceKind.STRAP_LIVE) return strap
        if (strap?.source == SixAxisSourceKind.STRAP_OFFLOAD) return strap
        return strap
    }

    fun isLiveDriver(sample: SixAxisSample?): Boolean =
        sample?.source == SixAxisSourceKind.STRAP_LIVE
}
