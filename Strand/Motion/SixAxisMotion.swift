import Foundation

/// Shared 6-axis IMU sample + honest source labels for the Settings / Test Centre motion tester.
/// Twin of Android `com.noop.motion.SixAxisMotion`.
///
/// Honesty contract (WHOOP 5/MG) — **band only**, never phone sensors:
/// - Historical offload 1244-B buffers decode to full accel+gyro @ 100 Hz (`.strapOffload`) — real data,
///   but **not** a live stream. Never label offload replay as "live".
/// - Live cmd-106 / types 51–56 remain firmware / membership gated; `.strapLive` is wired for the day
///   they arrive (type-51), and stays unused until then.
/// - While live strap IMU is gated and no offload sample exists, the tester shows an empty / waiting
///   band state — it does **not** fall back to phone CoreMotion.
enum SixAxisSourceKind: String, Equatable, Sendable {
    case strapLive
    case strapOffload
}

struct SixAxisSample: Equatable, Sendable {
    let ax: Float
    let ay: Float
    let az: Float
    /// Gyroscope in deg/s.
    let gx: Float
    let gy: Float
    let gz: Float
    let source: SixAxisSourceKind
    let tsMs: Int64

    var accelMagG: Float {
        sqrt(ax * ax + ay * ay + az * az)
    }

    var gyroMagDps: Float {
        sqrt(gx * gx + gy * gy + gz * gz)
    }
}

enum SixAxisMotionLabels {
    static let waitingTitle = "Waiting for band IMU…"
    static let waitingDetail =
        "Move the strap — this tester uses band IMU only. " +
        "Live type-51 (cmd 106 / types 51–56) is still gated on 5/MG (ACK ≠ activation). " +
        "No phone sensors."

    static func sourceTitle(_ kind: SixAxisSourceKind) -> String {
        switch kind {
        case .strapLive: return "Strap IMU (live)"
        case .strapOffload: return "Strap offload (not live)"
        }
    }

    static func sourceDetail(_ kind: SixAxisSourceKind) -> String {
        switch kind {
        case .strapLive:
            return "Live strap type-51 stream. Rare on 5/MG — R22 ACK ≠ activation."
        case .strapOffload:
            return "Decoded from a historical 1244-B offload buffer (100 Hz). Not a live stream."
        }
    }

    /// Band-only driver for the moving dot. Prefer live type-51 when present; else offload
    /// (honestly labeled not-live); else nil (empty / waiting — never phone).
    static func preferStrapDriver(_ strap: SixAxisSample?) -> SixAxisSample? {
        strap
    }

    static func isLiveDriver(_ sample: SixAxisSample?) -> Bool {
        sample?.source == .strapLive
    }
}
