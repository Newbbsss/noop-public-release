import Foundation

/// Haptic Clock (#460): turn a wall-clock time into a deterministic list of wrist buzzes so a user
/// can read the time off the strap without looking at a screen.
///
/// This is a PURE, platform-agnostic encoder: time-in, pulse-list-out, no I/O and no BLE. The trigger
/// (`BLEManager.buzzTimeNow()` on Apple, `WhoopBleClient.buzzTimeNow()` on Android) walks the list and
/// fires each pulse through the EXISTING maverick notification buzz; only the *schedule* of buzzes is
/// new, the buzz itself is the hardware-confirmed one. The Kotlin twin is
/// `android/app/src/main/java/com/noop/protocol/HapticClock.kt`; the two pulse lists are pinned
/// identical by matching unit tests on both platforms.
///
/// Styles:
///   • DIGIT_HOLD (default): hour as N-second buzz, ~1s pause, minute-tens as M-second buzz
///     (e.g. 6:30 → 6s pause 3s). Pattern clamped to ~12s.
///   • MORSE: classic long=tens / short=ones place-value taps (hour then minutes).
public enum HapticClock {

    /// One buzz instruction: buzz the wrist for `durationMs`, then stay silent for `gapMs`.
    public struct Pulse: Equatable {
        public let durationMs: Int
        public let gapMs: Int
        public init(durationMs: Int, gapMs: Int) {
            self.durationMs = durationMs
            self.gapMs = gapMs
        }

        /// Morse: whether this is a "tens" pulse (long) versus a "units" pulse (short).
        public var isLong: Bool { durationMs >= HapticClock.longMs }

        /// Digit-hold: stacked motor loops per hold tick (hardware buzz is fixed-length).
        public var holdLoops: Int { durationMs >= HapticClock.holdTickMs ? 2 : 1 }
    }

    /// Playback tempo for Morse schedules (TOP-A #355). Scales gaps only.
    public enum Speed: String, CaseIterable, Sendable {
        case slow, normal, fast

        public var gapScale: Float {
            switch self {
            case .slow: return 1.45
            case .normal: return 1.0
            case .fast: return 0.72
            }
        }

        public var label: String {
            switch self {
            case .slow: return "Slow"
            case .normal: return "Normal"
            case .fast: return "Fast"
            }
        }
    }

    /// Encoding style for Buzz the time.
    public enum Style: String, CaseIterable, Sendable {
        case morse
        case digitHold
    }

    // Pulse + gap timing (ms). Kept in lock-step with HapticClock.kt — change both together.
    public static let longMs = 550
    public static let shortMs = 200
    public static let intraGapMs = 450
    public static let groupGapMs = 900
    public static let blockGapMs = 1500
    public static let announceGapMs = 350
    public static let announceToHourMs = 1200

    public static let holdTickMs = 900
    public static let holdTickGapMs = 100
    public static let holdPauseMs = 1000
    /// Gilbert budget: ~12s total pattern (noon = 12s hour hold).
    public static let maxHoldTotalSec = 12

    /// Encode `hour`:`minute` into the buzz schedule.
    /// - Parameters:
    ///   - hour: hour of day, 0...23
    ///   - minute: minute of hour, 0...59
    ///   - is24h: Morse only — if false, map hour to 12-hour dial before encoding.
    ///     Digit-hold always uses the 12-hour dial (1–12) so noon = 12s.
    ///   - speed: Morse gap scale (ignored for digit-hold).
    ///   - announce: prepend three short buzzes before the hour block.
    ///   - style: `.digitHold` (default) or `.morse`.
    public static func pulses(
        hour: Int,
        minute: Int,
        is24h: Bool,
        speed: Speed = .normal,
        announce: Bool = false,
        style: Style = .digitHold
    ) -> [Pulse] {
        switch style {
        case .digitHold:
            return digitHoldPulses(hour: hour, minute: minute, is24h: is24h, announce: announce)
        case .morse:
            return morsePulses(hour: hour, minute: minute, is24h: is24h, speed: speed, announce: announce)
        }
    }

    /// Digit-hold: continuous N-second buzz for the dial hour, ~1s pause, then M-second buzz for
    /// minute-tens. Example: 6:30 → 6s · pause · 3s (not "6 pause 6" — minute tens of :30 is 3).
    public static func digitHoldPulses(
        hour: Int,
        minute: Int,
        is24h: Bool,
        announce: Bool = false,
        maxTotalSec: Int = maxHoldTotalSec
    ) -> [Pulse] {
        let h24 = min(max(hour, 0), 23)
        let m = min(max(minute, 0), 59)
        // Hold counts always use the 12-hour dial (1–12) so noon = 12s and night hours stay ≤12.
        _ = is24h // Android keeps the param for API parity; dial hour is always twelveHour.
        var hourSec = min(max(twelveHour(h24), 0), 12)
        var tensSec = m / 10
        let budget = max(maxTotalSec, 1)
        while hourSec + (hourSec > 0 && tensSec > 0 ? 1 : 0) + tensSec > budget && (hourSec > 1 || tensSec > 0) {
            if tensSec >= hourSec && tensSec > 0 {
                tensSec -= 1
            } else if hourSec > 1 {
                hourSec -= 1
            } else {
                tensSec = 0
            }
        }
        if hourSec <= 0 && tensSec <= 0 && !announce { return [] }

        var out: [Pulse] = []
        if announce {
            for _ in 0..<3 { out.append(Pulse(durationMs: shortMs, gapMs: announceGapMs)) }
            closeGroup(&out, with: announceToHourMs)
        }
        appendHoldSeconds(&out, seconds: hourSec, trailGapMs: tensSec > 0 ? holdPauseMs : 0)
        appendHoldSeconds(&out, seconds: tensSec, trailGapMs: 0)
        if let last = out.last {
            out[out.count - 1] = Pulse(durationMs: last.durationMs, gapMs: 0)
        }
        return out
    }

    /// Classic Morse place-value encoder (long = tens, short = ones).
    public static func morsePulses(
        hour: Int,
        minute: Int,
        is24h: Bool,
        speed: Speed = .normal,
        announce: Bool = false
    ) -> [Pulse] {
        let h24 = min(max(hour, 0), 23)
        let m = min(max(minute, 0), 59)
        let displayHour = is24h ? h24 : twelveHour(h24)

        let hourTens = displayHour / 10
        let hourUnits = displayHour % 10
        let minTens = m / 10
        let minUnits = m % 10

        var out: [Pulse] = []

        if announce {
            for _ in 0..<3 { out.append(Pulse(durationMs: shortMs, gapMs: announceGapMs)) }
            closeGroup(&out, with: announceToHourMs)
        }

        appendGroup(&out, count: hourTens, durationMs: longMs)
        closeGroup(&out, with: groupGapMs)
        appendGroup(&out, count: hourUnits, durationMs: shortMs)
        closeGroup(&out, with: blockGapMs)

        appendGroup(&out, count: minTens, durationMs: longMs)
        closeGroup(&out, with: groupGapMs)
        appendGroup(&out, count: minUnits, durationMs: shortMs)

        if let last = out.last {
            out[out.count - 1] = Pulse(durationMs: last.durationMs, gapMs: 0)
        }
        if speed == .normal || out.isEmpty { return out }
        return out.map { p in
            let scaled = Int(Float(p.gapMs) * speed.gapScale)
            let gap = p.gapMs > 0 ? max(scaled, 80) : 0
            return Pulse(durationMs: p.durationMs, gapMs: gap)
        }
    }

    /// Plain-language legend for Automations / Test Centre.
    public static func readLegend() -> String {
        "Digit-hold (default): hour as N-second buzz, pause, then minute-tens as M-second buzz " +
            "(6:30 → 6s pause 3s; pattern capped ~\(maxHoldTotalSec)s). " +
            "Classic Morse: long = tens, short = ones (hour then minutes). Optional triple-buzz = time starting."
    }

    /// 24-hour hour → 12-hour dial reading (0→12, 13→1 … 23→11). Noon stays 12.
    public static func twelveHour(_ h24: Int) -> Int {
        let h = h24 % 12
        return h == 0 ? 12 : h
    }

    private static func appendHoldSeconds(_ out: inout [Pulse], seconds: Int, trailGapMs: Int) {
        guard seconds > 0 else { return }
        for i in 0..<seconds {
            let gap = i == seconds - 1 ? trailGapMs : holdTickGapMs
            out.append(Pulse(durationMs: holdTickMs, gapMs: gap))
        }
    }

    private static func appendGroup(_ out: inout [Pulse], count: Int, durationMs: Int) {
        guard count > 0 else { return }
        for _ in 0..<count {
            out.append(Pulse(durationMs: durationMs, gapMs: intraGapMs))
        }
    }

    private static func closeGroup(_ out: inout [Pulse], with gapMs: Int) {
        guard let last = out.last else { return }
        out[out.count - 1] = Pulse(durationMs: last.durationMs, gapMs: max(last.gapMs, gapMs))
    }
}
