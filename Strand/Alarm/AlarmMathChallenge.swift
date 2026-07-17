import Foundation

/// Tiny pure helpers for wake-alarm math dismiss. Twin of Android `AlarmMathChallenge.kt`.
/// Two-operand add/sub with answers in 2..30.
enum AlarmMathChallenge {
    struct Problem: Equatable {
        let prompt: String
        let answer: Int
    }

    static func next(seed: UInt64 = UInt64(Date().timeIntervalSince1970 * 1000)) -> Problem {
        var rng = SeededGenerator(seed: seed)
        let a = Int.random(in: 2...12, using: &rng)
        let b = Int.random(in: 2...12, using: &rng)
        if Bool.random(using: &rng) {
            return Problem(prompt: "\(a) + \(b)", answer: a + b)
        }
        let hi = max(a, b)
        let lo = min(a, b)
        return Problem(prompt: "\(hi) − \(lo)", answer: hi - lo)
    }

    /// Whether this fire should require math.
    /// - Always when `mathEnabled`.
    /// - Or when `mathOnDrowsy` and live `hrBpm` is present and below `drowsyThreshold`.
    /// Missing HR never invents drowsiness.
    static func requireMath(
        mathEnabled: Bool,
        mathOnDrowsy: Bool,
        hrBpm: Int?,
        drowsyThreshold: Int,
        isFinalDeadline: Bool
    ) -> Bool {
        if mathEnabled { return true }
        if !mathOnDrowsy || !isFinalDeadline { return false }
        guard let hr = hrBpm else { return false }
        return hr >= 1 && hr < drowsyThreshold
    }
}

/// Deterministic RNG so unit tests can pin `next(seed:)`.
private struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64
    init(seed: UInt64) { state = seed == 0 ? 0x9E3779B97F4A7C15 : seed }
    mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }
}
