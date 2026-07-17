import Foundation

// WatchRecovery.swift — recovery/Charge from Apple Watch DAILY aggregates.
//
// The honesty-critical piece of "Apple Watch as a device". A WHOOP strap gives us
// dense overnight RR intervals, so RecoveryScorer runs off raw-derived nightly RMSSD.
// The Apple Watch does NOT. It gives a handful of HRV SDNN readings a day plus a
// resting HR, both as daily aggregates. So watch recovery is a genuinely lower-density
// computation.
//
// We do NOT invent a new formula. Recovery is HRV-and-RHR-vs-personal-baseline, and
// because every term is relative to the person's OWN baseline, the metric scale cancels
// out: SDNN-vs-SDNN-baseline behaves like RMSSD-vs-RMSSD-baseline. So we build SDNN and
// RHR baselines through the existing `Baselines` machinery and feed them straight into
// the SAME `RecoveryScorer.recovery(...)` the strap uses. Watch recovery and strap
// recovery therefore land on the same 0-100 scale and read against the same bands.
//
// What we drop vs the strap path: the respiration, sleep-performance and skin-temp terms
// are not supplied here (the watch's daily aggregate doesn't carry them in the same shape),
// so RecoveryScorer renormalises the remaining HRV + RHR weights. The HRV term stays the
// dominant driver either way.
//
// The hard honesty rule: we return nil recovery + `.calibrating` when today's SDNN is
// missing, OR the SDNN baseline isn't usable yet (same `Baselines.minNightsSeed` gate as
// strap Charge). We NEVER fabricate a number. Thin history still scores, but
// `ScoreConfidence.charge` marks it `.building` — that is the warm-up honesty, not a
// second longer null gate (Fable Rest #8 unify).
public enum WatchRecovery {

    /// Result of a watch-recovery computation: the score (nil while calibrating) and its
    /// confidence tier. Same shape the strap path carries onto a DailyMetric.
    public struct Result: Equatable, Sendable {
        /// Recovery in [0, 100], or nil when we can't honestly score yet (calibrating).
        public let recovery: Double?
        /// Per-score confidence, driven by the real SDNN-baseline density (not a hardcoded label).
        public let confidence: ScoreConfidence

        public init(recovery: Double?, confidence: ScoreConfidence) {
            self.recovery = recovery
            self.confidence = confidence
        }
    }

    /// Minimum nights of SDNN history before we'll score recovery from the watch.
    /// Unified with strap Charge (`Baselines.minNightsSeed` = 4). Sparse daily SDNN still warms
    /// up honestly via `.building` confidence until `ScoreConfidence.buildingNightsThreshold`,
    /// not by staying blank until night 7.
    public static let minBaselineNights = Baselines.minNightsSeed

    /// Compute recovery/Charge from the watch's daily SDNN + resting HR vs the person's own baseline.
    ///
    /// - Parameters:
    ///   - todaySDNN: today's HRV SDNN reading (ms), or nil if the watch logged none.
    ///   - todayRHR:  today's resting HR (bpm), or nil to drop the RHR term.
    ///   - sdnnHistory: ordered nightly SDNN values (oldest → newest), the baseline input.
    ///   - rhrHistory:  ordered nightly resting-HR values (oldest → newest).
    /// - Returns: a `Result` with recovery in [0,100] and a confidence tier, or nil recovery +
    ///   `.calibrating` when today's SDNN is missing, the baseline isn't usable, or history is thin.
    public static func compute(todaySDNN: Double?, todayRHR: Int?,
                               sdnnHistory: [Double], rhrHistory: [Double]) -> Result {
        // Build both baselines through the production model (Winsorized EWMA + cold-start gating),
        // exactly as the strap path does. SDNN feeds the HRV config; resting HR feeds the RHR config.
        let hrvBase = Baselines.foldHistory(sdnnHistory.map { Optional($0) }, cfg: Baselines.hrvCfg)
        let rhrBase = Baselines.foldHistory(rhrHistory.map { Optional($0) }, cfg: Baselines.restingHRCfg)

        // Honesty gate: same seed as strap Charge — today's SDNN + usable baseline (+ history length
        // matching the seed). Thin weeks score with `.building`, never a fabricated value.
        guard let sdnn = todaySDNN,
              hrvBase.usable,
              sdnnHistory.count >= minBaselineNights else {
            return Result(recovery: nil, confidence: .calibrating)
        }

        // Reuse the canonical Charge engine. Drop the resp / sleep / skin-temp terms (the watch
        // daily aggregate doesn't carry them here) — RecoveryScorer renormalises to HRV + RHR.
        // RHR is optional: when the watch logged no resting HR today we pass the HRV-only path.
        let recovery = RecoveryScorer.recovery(
            hrv: sdnn,
            rhr: todayRHR.map(Double.init) ?? rhrBase.baseline,   // missing RHR → at-baseline (z≈0, neutral term)
            resp: nil,
            hrvBaseline: hrvBase,
            rhrBaseline: todayRHR != nil ? rhrBase : nil,          // drop the RHR term entirely if no reading
            respBaseline: nil,
            sleepPerf: nil
        )

        // RecoveryScorer only returns nil on a cold-start HRV baseline, which we already gated above;
        // but stay honest if it ever does — never coerce a nil into a number.
        guard let recovery else {
            return Result(recovery: nil, confidence: .calibrating)
        }
        // Confidence from the REAL score + baseline density — same helper the strap Charge uses.
        let conf = ScoreConfidence.charge(recovery: recovery, hrvBaseline: hrvBase)
        return Result(recovery: recovery, confidence: conf)
    }
}
