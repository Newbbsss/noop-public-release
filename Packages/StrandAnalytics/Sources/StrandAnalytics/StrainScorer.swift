import Foundation
import WhoopProtocol

// StrainScorer.swift — cardiovascular load on a 0–100 logarithmic strain ("Effort") scale.
//
// Ported from server/ingest/app/analysis/strain.py. INDEPENDENT implementation of
// published exercise-physiology methods (WHOOP-*like*, not a reproduction of the
// proprietary algorithm; not medical advice).
//
// Scale note: the metric was historically 0–21 (WHOOP's Strain axis); the
// "Charge / Effort / Rest" redesign rescales the OUTPUT to 0–100 by raising
// `maxStrain` 21.0 → 100.0 only. The denominator D = 7201 is unchanged, so the log
// curve and its saturation point (TRIMP 7200 ≈ max) are preserved — a max Effort day
// stays as rare as a 21.0 day used to be. Internal metric key stays `strain`.
//
// Pipeline:
//   1. Heart-Rate Reserve (Karvonen): HRR = HRmax − RHR.
//   2. Per-sample intensity as %HRR = (HR − RHR) / HRR × 100, clamped 0..100.
//   3. TRIMP accumulated over the window:
//        a. Edwards 5-zone summation (default): sample contributes its zone weight
//           (1..5 at 50/60/70/80/90 %HRR cut-offs) × duration.
//        b. Banister exponential: sample contributes duration × x × 0.64 × e^(b·x).
//   4. Logarithmic compression onto [0, 100]:
//        strain = 100 × ln(TRIMP + 1) / ln(D),  D = STRAIN_DENOMINATOR.
//
// References: Karvonen 1957 (%HRR); Edwards 1993 (5-zone TRIMP); Banister 1991
// (exponential TRIMP, b = 1.92 men / 1.67 women); Tanaka 2001 (HRmax = 208 − 0.7×age).

public enum StrainScorer {

    // MARK: - Constants (strain.py)

    /// Minimum HR readings before computing strain on a DENSE stream (≈10 min at 1 Hz).
    public static let minReadings: Int = 600
    /// Sparse-stream acceptance (#482/#480): a low-cadence strap — the WHOOP 5/MG sends live
    /// standard HR only ~every 30 s — would need ~5 h of continuous wear to reach `minReadings`,
    /// so Effort sat un-scored (nil → the gauge showed a stale prior-day value) for most of the day.
    /// Also accept once the HR series SPANS at least `minSpanSeconds` of wall-clock with a small
    /// sample floor. This never fabricates load: TRIMP still integrates honestly over whatever HR is
    /// there, so a genuine low-HR day scores 0 either way — it just lets the live gauge reflect TODAY
    /// instead of yesterday. A dense 1 Hz stream is unaffected (it clears `minReadings` first).
    public static let minSparseReadings: Int = 20
    /// Wall-clock coverage (seconds) that qualifies a sparse stream. 600 s = 10 min, matching the
    /// dense gate's ≈10 min of 600 × 1 Hz samples, so both cadences trust the number at the same age.
    public static let minSpanSeconds: Int = 600
    /// Live wrist PPG outside this range is a transport/artifact candidate, not Effort evidence.
    public static let minPlausibleBpm: Int = 30
    public static let maxPlausibleBpm: Int = 220
    /// A gap beyond this splits the stream into separate worn segments (Fable #322 / Android parity:
    /// a gap can only under-count TRIMP, never fabricate, so segmenting is honest).
    public static let maxSampleGapSeconds: Int = 90
    /// Top of the strain ("Effort") scale. Rescaled 21.0 → 100.0 for the
    /// Charge/Effort/Rest redesign; only the output scale changes, the curve does not.
    public static let maxStrain: Double = 100.0

    /// Logarithmic-map denominator D. Chosen so the Edwards daily ceiling
    /// (top zone weight 5 sustained 24 h = 7200) maps to exactly maxStrain:
    /// D = 7200 + 1 = 7201 makes ln(7201)/ln(7201) = 1.
    public static let strainDenominator: Double = 7201.0
    static var lnStrainDenominator: Double { log(strainDenominator) }

    /// Fallback per-sample duration (minutes) — 1 s at 1 Hz.
    static let fallbackSampleMin: Double = 1.0 / 60.0

    public static let defaultAge: Int = 30
    public static let defaultRestingHR: Double = 60

    /// Minimum HR samples before the observed high-percentile HRmax is trusted.
    public static let hrmaxMinSamples: Int = 600
    /// Upper percentile for the observed-HRmax estimate.
    public static let hrmaxPercentile: Double = 99.5

    /// Banister coefficients.
    public static let banisterScale: Double = 0.64
    public static let banisterBMen: Double = 1.92
    public static let banisterBWomen: Double = 1.67

    /// Edwards zone cut-offs as (%HRR threshold, weight), highest-first.
    /// Classic Edwards zone-1 at 50% is **not** applied unconditionally — desk/rest mornings used to
    /// mint Effort ~40 while WHOOP Strain stayed ~0.1. Hard zones stay ≥**60% HRR**. Sustained
    /// ~33–60% HRR can contribute soft weight 1 after a sustain gate — see `softBandMinPctHrr`.
    static let edwardsZones: [(threshold: Double, weight: Int)] = [
        (90.0, 5), (80.0, 4), (70.0, 3), (60.0, 2),
    ]
    /// Minimum %HRR that contributes classic Edwards weight (≥60).
    public static let edwardsMinPctHrr: Double = 60.0
    /// Soft occupational band floor (~33% HRR) — standing/warehouse shifts after sustain.
    public static let softBandMinPctHrr: Double = 33.0
    public static let softBandWeight: Int = 1
    /// Continuous elevated minutes before soft-band samples contribute TRIMP.
    public static let softBandSustainMinutes: Double = 12.0
    /// Allow this many consecutive below-floor samples before resetting the soft-band bout.
    public static let softBandDipGraceSamples: Int = 2

    /// TRIMP accumulation method.
    public enum Method: Sendable, Hashable { case edwards, banister }

    // MARK: - HRmax helpers

    /// Tanaka (2001): HRmax = 208 − 0.7 × age (gender-independent).
    public static func tanakaHRmax(age: Double) -> Double { 208.0 - 0.7 * age }

    /// Classic 220 − age. Last-resort fallback only.
    public static func defaultMaxHR(age: Int = defaultAge) -> Int { 220 - age }

    /// Linear-interpolated percentile of an already-sorted sequence (numpy-style).
    static func percentile(_ sortedValues: [Double], _ pct: Double) -> Double {
        let n = sortedValues.count
        if n == 0 { return 0 }
        if n == 1 { return sortedValues[0] }
        let position = (pct / 100.0) * Double(n - 1)
        let lower = Int(position)
        let upper = min(lower + 1, n - 1)
        let frac = position - Double(lower)
        return sortedValues[lower] + frac * (sortedValues[upper] - sortedValues[lower])
    }

    /// Estimate a personalized HRmax from a trailing HR series.
    /// Returns (hrmax bpm, source) where source ∈ {"observed", "tanaka", "unknown"}.
    public static func estimateHRmax(_ hrHistory: [Double], age: Double?) -> (Double, String) {
        let n = hrHistory.count
        let tanaka = age.map { tanakaHRmax(age: $0) }

        if n >= hrmaxMinSamples {
            let observed = percentile(hrHistory.sorted(), hrmaxPercentile)
            guard let t = tanaka else { return (observed, "observed") }
            return observed >= t ? (observed, "observed") : (t, "tanaka")
        }
        if let t = tanaka { return (t, "tanaka") }
        return (0.0, "unknown")
    }

    // MARK: - Karvonen %HRR and Edwards zone weight

    /// Karvonen %HRR, clamped [0, 100].
    static func pctHRR(_ bpm: Double, restingHR: Double, hrReserve: Double) -> Double {
        let pct = (bpm - restingHR) / hrReserve * 100.0
        if pct < 0 { return 0 }
        if pct > 100 { return 100 }
        return pct
    }

    /// Edwards 5-zone weight (0–5) from %HRR (unclamped; extremes agree with
    /// the clamped path at both ends).
    static func zoneWeight(_ bpm: Double, restingHR: Double, hrReserve: Double) -> Int {
        let pct = (bpm - restingHR) / hrReserve * 100.0
        for (threshold, weight) in edwardsZones where pct >= threshold { return weight }
        return 0
    }

    // MARK: - TRIMP accumulation

    /// Infer per-sample duration (minutes) from the first two timestamps. Falls
    /// back to 1 s when fewer than two samples or coincident timestamps.
    static func sampleDurationMinutes(_ hr: [HRSample]) -> Double {
        guard hr.count >= 2 else { return fallbackSampleMin }
        let deltaS = abs(Double(hr[1].ts - hr[0].ts))
        return deltaS > 0 ? deltaS / 60.0 : fallbackSampleMin
    }

    static func edwardsTRIMP(_ hr: [HRSample], restingHR: Double, hrReserve: Double,
                             sampleDurationMin: Double) -> Double {
        // Soft-band sustain in sample counts (uniform cadence from sampleDurationMinutes).
        let sustainSamples: Int
        if sampleDurationMin > 0 {
            sustainSamples = max(1, Int(ceil(softBandSustainMinutes / sampleDurationMin)))
        } else {
            sustainSamples = Int.max
        }
        var weighted = 0.0
        var elevatedStreak = 0
        var belowStreak = 0
        for s in hr {
            let bpm = Double(s.bpm)
            let classic = zoneWeight(bpm, restingHR: restingHR, hrReserve: hrReserve)
            if classic > 0 {
                elevatedStreak += 1
                belowStreak = 0
                weighted += Double(classic)
            } else {
                let pct = pctHRR(bpm, restingHR: restingHR, hrReserve: hrReserve)
                if pct >= softBandMinPctHrr {
                    elevatedStreak += 1
                    belowStreak = 0
                    if elevatedStreak >= sustainSamples { weighted += Double(softBandWeight) }
                } else {
                    belowStreak += 1
                    if belowStreak > softBandDipGraceSamples {
                        elevatedStreak = 0
                        belowStreak = 0
                    }
                }
            }
        }
        return weighted * sampleDurationMin
    }

    static func banisterTRIMP(_ hr: [HRSample], restingHR: Double, hrReserve: Double,
                              sampleDurationMin: Double, b: Double) -> Double {
        var acc = 0.0
        for s in hr {
            let x = pctHRR(Double(s.bpm), restingHR: restingHR, hrReserve: hrReserve) / 100.0
            if x > 0 { acc += sampleDurationMin * x * banisterScale * exp(b * x) }
        }
        return acc
    }

    // MARK: - Logarithmic map

    /// Map accumulated TRIMP onto [0, 100] via 100 × ln(TRIMP+1) / ln(D), 2 dp.
    /// TRIMP ≤ 0 → 0.
    public static func trimpToStrain(_ trimp: Double, denominator: Double = strainDenominator) -> Double {
        if trimp <= 0 { return 0 }
        let value = maxStrain * log(trimp + 1.0) / log(denominator)
        return (value * 100).rounded() / 100
    }

    // MARK: - Denominator calibration

    /// Calibrate D from (TRIMP, reference_strain) pairs via the through-origin
    /// least-squares line: ln(D) = maxStrain × Σ(x²) / Σ(xy), x = ln(TRIMP+1).
    /// (reference_strain pairs must be on the same 0–maxStrain axis as the output.)
    /// Throws when fewer than 2 usable pairs (TRIMP>0, strain>0) or degenerate.
    public static func fitStrainDenominator(_ pairs: [(trimp: Double, strain: Double)]) throws -> Double {
        let usable = pairs.filter { $0.trimp > 0 && $0.strain > 0 }
        guard usable.count >= 2 else { throw StrainError.tooFewPairs }
        var sumXX = 0.0, sumXY = 0.0
        for (trimp, strain) in usable {
            let x = log(trimp + 1.0)
            sumXX += x * x
            sumXY += x * strain
        }
        guard sumXY > 0 && sumXX > 0 else { throw StrainError.degenerate }
        return exp(maxStrain * sumXX / sumXY)
    }

    public enum StrainError: Error, Equatable, Sendable {
        case tooFewPairs
        case degenerate
    }

    // MARK: - Steps floor (Gilbert P0; Android StrainScorer twin)

    /// Steps below this contribute 0 to the movement floor (desk / noise).
    public static let movementStepsNoiseFloor: Int = 2_500
    /// Kept for API compat; kcal no longer raises the floor (total-day kcal was wrong).
    public static let movementKcalNoiseFloor: Double = 250.0
    /// Hard cap — steps alone never invent hard cardio Effort.
    public static let movementFloorCap: Double = 18.0
    public static let movementStepsScale: Double = 16_000.0
    public static let movementStepsExponent: Double = 1.35
    public static let movementKcalScale: Double = 1_000.0
    public static let movementKcalExponent: Double = 1.85

    /// Movement-derived Effort floor (0…movementFloorCap) from band steps.
    /// `activeKcal` is accepted for call-site compat but **ignored**.
    public static func movementFloor(steps: Int?, activeKcal: Double?) -> Double {
        func convex(excess: Double, scale: Double, exponent: Double) -> Double {
            guard excess > 0, scale > 0 else { return 0 }
            let u = max(0, excess / scale)
            let shaped = min(1.0, pow(u, exponent))
            return movementFloorCap * shaped
        }
        let fromSteps: Double
        if let steps, steps >= movementStepsNoiseFloor {
            fromSteps = convex(excess: Double(steps - movementStepsNoiseFloor),
                               scale: movementStepsScale, exponent: movementStepsExponent)
        } else {
            fromSteps = 0
        }
        let raw = min(max(fromSteps, 0), movementFloorCap)
        return (raw * 100).rounded() / 100
    }

    /// Combine cardio TRIMP Effort with the steps floor: `max(trimp, floor)`.
    public static func withMovementFloor(trimpEffort: Double?, steps: Int?, activeKcal: Double?) -> Double? {
        let floor = movementFloor(steps: steps, activeKcal: activeKcal)
        if let trimpEffort {
            return max(trimpEffort, floor)
        }
        return floor > 0 ? floor : nil
    }

    // MARK: - Public API

    /// Cardiovascular strain / "Effort" (0–100) from an HR series. APPROXIMATE.
    ///
    /// Returns nil when there isn't yet enough data to trust the number — fewer than
    /// `minReadings` samples AND less than `minSpanSeconds` of HR coverage (the sparse-strap
    /// path, #482) — or when maxHR ≤ restingHR (invalid HRR).
    ///
    /// Does **not** apply the steps floor — callers that have day steps should wrap with
    /// `withMovementFloor` (see AnalyticsEngine.analyzeDay and live Today).
    ///
    /// - Parameters:
    ///   - hr: time-ordered `[HRSample]`.
    ///   - maxHR: HRmax (bpm). Defaults to 220 − defaultAge when nil.
    ///   - restingHR: resting HR (bpm) for the HRR denominator (default 60).
    ///   - method: `.edwards` (default) or `.banister`.
    ///   - sex: "male"/"female" — selects the Banister coefficient (ignored by Edwards).
    ///   - denominator: log-map D (default STRAIN_DENOMINATOR).
    public static func strain(_ hr: [HRSample],
                              maxHR: Double? = nil,
                              restingHR: Double = defaultRestingHR,
                              method: Method = .edwards,
                              sex: String = "male",
                              denominator: Double = strainDenominator) -> Double? {
        // v7.0.2 perf (#707): TRIMP integrates over the day's HR stream; called once per day in the post-sync
        // scoring loop AND from the Today view (which re-reads on each live-HR tick). Memoize on the HR
        // fingerprint + every scalar that steers the score, so an identical re-request is a lookup. The
        // result is a single `Double?`; the HR array is not retained.
        let key = StrainKey(
            hr: StreamFingerprint.of(hr, ts: { $0.ts }, quant: { Int($0.bpm) }),
            maxHR: maxHR, restingHR: restingHR, method: method,
            sexF: sex.lowercased().hasPrefix("f"), denom: denominator)
        return strainCache.value(key) {
            strainUncached(hr, maxHR: maxHR, restingHR: restingHR, method: method, sex: sex, denominator: denominator)
        }
    }

    /// Key folds `sex` to the single bit the recipe reads (`hasPrefix("f")`) so "female"/"f"/"F" all hit.
    private struct StrainKey: Hashable {
        let hr: StreamFingerprint
        let maxHR: Double?; let restingHR: Double; let method: Method
        let sexF: Bool; let denom: Double
    }
    private static let strainCache = AnalyticsMemoCache<StrainKey, Double?>(capacity: 48)

    private static func strainUncached(_ hr: [HRSample], maxHR: Double?, restingHR: Double,
                                       method: Method, sex: String, denominator: Double) -> Double? {
        let effMax = maxHR ?? Double(defaultMaxHR())
        // Do not let duplicate timestamps or implausible PPG values fabricate load (Android parity).
        var seen = Set<Int>()
        let usable: [HRSample] = hr
            .filter { $0.bpm >= minPlausibleBpm && $0.bpm <= maxPlausibleBpm }
            .sorted { $0.ts < $1.ts }
            .filter { seen.insert($0.ts).inserted }
        // Disconnected gaps (Fable #322): segment at gaps > maxSampleGapSeconds and integrate each
        // worn stretch with its own inferred cadence; gaps contribute exactly zero. Coverage for the
        // sparse gate sums WORN spans only so a disconnected day can't count its holes.
        var segments: [[HRSample]] = []
        var segStart = 0
        if !usable.isEmpty {
            for i in 1...usable.count {
                if i == usable.count || usable[i].ts - usable[i - 1].ts > maxSampleGapSeconds {
                    segments.append(Array(usable[segStart..<i]))
                    segStart = i
                }
            }
        }
        let enoughData: Bool
        if usable.count >= minReadings {
            enoughData = true
        } else if usable.count >= minSparseReadings {
            let worn = segments.reduce(0) { acc, seg in
                guard seg.count >= 2 else { return acc }
                return acc + (seg.last!.ts - seg.first!.ts)
            }
            enoughData = worn >= minSpanSeconds
        } else {
            enoughData = false
        }
        if !enoughData || effMax <= restingHR { return nil }

        let hrReserve = effMax - restingHR
        var trimp = 0.0
        for seg in segments {
            let sampleDur = sampleDurationMinutes(seg)
            switch method {
            case .banister:
                let b = sex.lowercased().hasPrefix("f") ? banisterBWomen : banisterBMen
                trimp += banisterTRIMP(seg, restingHR: restingHR, hrReserve: hrReserve,
                                       sampleDurationMin: sampleDur, b: b)
            case .edwards:
                trimp += edwardsTRIMP(seg, restingHR: restingHR, hrReserve: hrReserve,
                                      sampleDurationMin: sampleDur)
            }
        }
        return trimpToStrain(trimp, denominator: denominator)
    }
}
