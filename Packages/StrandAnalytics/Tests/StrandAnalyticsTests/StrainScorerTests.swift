import XCTest
@testable import StrandAnalytics
import WhoopProtocol

final class StrainScorerTests: XCTestCase {

    /// Build n consecutive 1 Hz HR samples at a constant bpm.
    private func hr(_ bpm: Int, _ n: Int, start: Int = 0) -> [HRSample] {
        (0..<n).map { HRSample(ts: start + $0, bpm: bpm) }
    }

    func testTanakaAndDefaultMax() {
        XCTAssertEqual(StrainScorer.tanakaHRmax(age: 30), 187.0, accuracy: 1e-9)
        XCTAssertEqual(StrainScorer.defaultMaxHR(age: 30), 190)
    }

    func testTrimpToStrainCeilingMapsTo100() {
        // Edwards 24 h ceiling TRIMP = 7200 → Effort exactly 100.0 with D = 7201
        // (rescaled from the old 21.0; the curve/saturation point is unchanged).
        XCTAssertEqual(StrainScorer.trimpToStrain(7200), 100.0, accuracy: 1e-9)
    }

    func testTrimpToStrainKnownValues() {
        XCTAssertEqual(StrainScorer.trimpToStrain(0), 0.0, accuracy: 1e-9)
        XCTAssertEqual(StrainScorer.trimpToStrain(-5), 0.0, accuracy: 1e-9)
        // 10.91 × 100/21 on the rescaled axis.
        XCTAssertEqual(StrainScorer.trimpToStrain(100), 51.96, accuracy: 1e-2)
    }

    func testStrainGoldenEdwardsZone5() {
        // 600 z5 samples at 1 Hz, resting 60, max 190. TRIMP = 600*5*(1/60)=50.
        // Effort = 100*ln(51)/ln(7201) = 44.27 (was 9.3 on the 0–21 axis).
        let s = StrainScorer.strain(hr(185, 600), maxHR: 190, restingHR: 60)
        XCTAssertEqual(s!, 44.27, accuracy: 1e-2)
    }

    func testStrainReturnsNilTooFewReadings() {
        XCTAssertNil(StrainScorer.strain(hr(150, 599), maxHR: 190, restingHR: 60))
    }

    func testStrainReturnsNilInvalidHRR() {
        XCTAssertNil(StrainScorer.strain(hr(150, 600), maxHR: 60, restingHR: 60))
        XCTAssertNil(StrainScorer.strain(hr(150, 600), maxHR: 50, restingHR: 60))
    }

    func testStrainMonotonicInZoneTime() {
        // More time at high intensity → higher strain. Compare 600 vs 1200 z5 samples.
        let short = StrainScorer.strain(hr(185, 600), maxHR: 190, restingHR: 60)!
        let long = StrainScorer.strain(hr(185, 1200), maxHR: 190, restingHR: 60)!
        XCTAssertGreaterThan(long, short)
    }

    func testStrainMonotonicInIntensity() {
        // Same duration, higher zone → higher strain.
        let z3 = StrainScorer.strain(hr(155, 600), maxHR: 190, restingHR: 60)!  // ~73% HRR → w3
        let z5 = StrainScorer.strain(hr(185, 600), maxHR: 190, restingHR: 60)!  // ~96% HRR → w5
        XCTAssertGreaterThan(z5, z3)
    }

    func testStrainBanisterAlsoBounded() {
        let s = StrainScorer.strain(hr(185, 600), maxHR: 190, restingHR: 60, method: .banister)!
        XCTAssertGreaterThan(s, 0)
        XCTAssertLessThanOrEqual(s, 100.0)
    }

    // MARK: - #482/#480 sparse-strap acceptance + honest-zero (regression guards)

    /// Build n samples at a fixed cadence (default 30 s — the WHOOP 5/MG live-HR rate).
    private func hrEvery(_ bpm: Int, _ n: Int, stepS: Int = 30, start: Int = 0) -> [HRSample] {
        (0..<n).map { HRSample(ts: start + $0 * stepS, bpm: bpm) }
    }

    func testSparseStreamScoresOnceItSpansEnoughTime() {
        // The 5/MG case: only ~30 live samples at 30 s cadence — far under minReadings (600), but
        // they SPAN ~15 min, so the score should compute rather than return nil (which made the live
        // gauge fall back to a stale prior-day value). HR 185 is z5, so it produces a real number.
        let sparse = hrEvery(185, 30)                                // 30 × 30 s = 870 s span
        XCTAssertGreaterThanOrEqual(sparse.last!.ts - sparse.first!.ts, StrainScorer.minSpanSeconds)
        XCTAssertNotNil(StrainScorer.strain(sparse, maxHR: 190, restingHR: 60))
    }

    func testSparseStreamStillNilUnderSampleFloor() {
        // A handful of readings (under minSparseReadings) is too little to trust even if spread out.
        let tooFew = hrEvery(185, 5, stepS: 200)                     // 5 samples, wide span, < floor
        XCTAssertNil(StrainScorer.strain(tooFew, maxHR: 190, restingHR: 60))
    }

    func testLightDayHonestlyScoresZeroNotFabricated() {
        // HR below soft-band floor (~33% HRR) earns ZERO — sparse path must not invent load.
        // max 184 / rest 60 → 33% HRR ≈ 101 bpm; 90 bpm stays below on both cadences.
        let denseLight = hr(90, 1200, start: 0)
        let sparseLight = hrEvery(90, 40)
        XCTAssertEqual(StrainScorer.strain(denseLight, maxHR: 184, restingHR: 60), 0.0)
        XCTAssertEqual(StrainScorer.strain(sparseLight, maxHR: 184, restingHR: 60), 0.0)
    }

    func testSparseStreamScoresRealWorkout() {
        // The same sparse cadence, but a genuine workout (z5) — Effort must be clearly > 0, proving the
        // zero above is about intensity, not about the sparse path swallowing real load.
        let sparseHard = hrEvery(175, 40)                            // 175 bpm ≈ 93% HRR → z5
        let s = StrainScorer.strain(sparseHard, maxHR: 184, restingHR: 60)
        XCTAssertNotNil(s)
        XCTAssertGreaterThan(s!, 0)
    }

    // MARK: - Fable #322 gap segmenting (Android parity)

    private func hrConstant(_ bpm: Int, n: Int, start: Int = 0) -> [HRSample] {
        (0..<n).map { HRSample(ts: start + $0, bpm: bpm) }
    }

    func testMiddayGapSegmentsInsteadOfNulling() {
        // Dense morning + 2-min dropout + evening must still score; equals continuous worn samples.
        let morning = hrConstant(135, n: 1200)
        let evening = hrConstant(135, n: 1200, start: 1320)
        let s = StrainScorer.strain(morning + evening, maxHR: 160, restingHR: 60)
        XCTAssertNotNil(s)
        let continuous = hrConstant(135, n: 2400)
        XCTAssertEqual(s!, StrainScorer.strain(continuous, maxHR: 160, restingHR: 60)!, accuracy: 0.01)
    }

    func testDisconnectedSparseStreamStillNullUnderCoverage() {
        let first = hrEvery(155, 10, stepS: 30)
        let second = hrEvery(155, 10, stepS: 30, start: 1000)
        XCTAssertNil(StrainScorer.strain(first + second, maxHR: 160, restingHR: 60))
    }

    func testEstimateHRmaxObservedVsTanaka() {
        // Thin history but known age → tanaka.
        let (v1, src1) = StrainScorer.estimateHRmax([150, 160, 170], age: 30)
        XCTAssertEqual(v1, 187.0, accuracy: 1e-9)
        XCTAssertEqual(src1, "tanaka")

        // No age, no history → unknown.
        let (v2, src2) = StrainScorer.estimateHRmax([150], age: nil)
        XCTAssertEqual(v2, 0.0)
        XCTAssertEqual(src2, "unknown")

        // Dense history with a sustained high tail above tanaka → observed.
        // The 99.5th percentile must exceed 187, so the top ~0.5% must be high:
        // 700 samples, top 10 (>0.5%) at 195 → p99.5 lands in the high tail.
        var hist = Array(repeating: 120.0, count: 690)
        hist.append(contentsOf: Array(repeating: 195.0, count: 10))
        let (v3, src3) = StrainScorer.estimateHRmax(hist, age: 30)
        XCTAssertEqual(src3, "observed")
        XCTAssertGreaterThan(v3, 187.0)
    }

    func testPercentileLinearInterp() {
        XCTAssertEqual(StrainScorer.percentile([10, 20, 30, 40], 50), 25.0, accuracy: 1e-9)
        XCTAssertEqual(StrainScorer.percentile([10, 20, 30, 40], 0), 10.0, accuracy: 1e-9)
        XCTAssertEqual(StrainScorer.percentile([10, 20, 30, 40], 100), 40.0, accuracy: 1e-9)
    }

    func testFitStrainDenominator() throws {
        // Pairs generated from a known D should recover that D. Pairs use the rescaled
        // 0–100 axis (maxStrain = 100), matching fitStrainDenominator's maxStrain term.
        let knownD = 5000.0
        func strainFor(_ t: Double) -> Double { 100 * log(t + 1) / log(knownD) }
        let pairs = [(100.0, strainFor(100)), (1000.0, strainFor(1000)), (50.0, strainFor(50))]
        let fitted = try StrainScorer.fitStrainDenominator(pairs)
        XCTAssertEqual(fitted, knownD, accuracy: 1.0)
    }

    func testFitStrainDenominatorThrowsTooFew() {
        XCTAssertThrowsError(try StrainScorer.fitStrainDenominator([(100, 10)]))
    }

    // MARK: - Soft band + movement floor (Android StrainScorer twin)

    func testEdwardsIgnoresBelow60PctHrr() {
        // RHR 50, HRmax 190 → 60% HRR = 134 bpm.
        XCTAssertEqual(StrainScorer.zoneWeight(92, restingHR: 50, hrReserve: 140), 0)
        XCTAssertEqual(StrainScorer.zoneWeight(133, restingHR: 50, hrReserve: 140), 0)
        XCTAssertEqual(StrainScorer.zoneWeight(134, restingHR: 50, hrReserve: 140), 2)
    }

    func testMovementFloorRestDayStaysZero() {
        XCTAssertEqual(StrainScorer.movementFloor(steps: 1_500, activeKcal: 80), 0.0, accuracy: 0.0)
        XCTAssertEqual(StrainScorer.movementFloor(steps: 2_400, activeKcal: 200), 0.0, accuracy: 0.0)
        XCTAssertNil(StrainScorer.withMovementFloor(trimpEffort: nil, steps: 1_500, activeKcal: 80))
        XCTAssertEqual(StrainScorer.withMovementFloor(trimpEffort: 0.0, steps: 1_500, activeKcal: nil)!, 0.0, accuracy: 0.0)
    }

    func testMovementFloorConvexWalkNotHotEarly() {
        let at12k = StrainScorer.movementFloor(steps: 12_000, activeKcal: nil)
        XCTAssertGreaterThan(at12k, 1.0)
        XCTAssertLessThan(at12k, 9.0)
        let at8k = StrainScorer.movementFloor(steps: 8_000, activeKcal: nil)
        let at20k = StrainScorer.movementFloor(steps: 20_000, activeKcal: nil)
        XCTAssertLessThan(at8k, at12k)
        XCTAssertGreaterThan(at20k, at12k)
        XCTAssertLessThanOrEqual(at20k, StrainScorer.movementFloorCap)
        XCTAssertEqual(StrainScorer.withMovementFloor(trimpEffort: 40.0, steps: 12_000, activeKcal: 400)!, 40.0, accuracy: 0.0)
        XCTAssertEqual(
            StrainScorer.movementFloor(steps: 12_000, activeKcal: nil),
            StrainScorer.withMovementFloor(trimpEffort: 0.0, steps: 12_000, activeKcal: nil)!,
            accuracy: 0.0)
    }

    func testSoftBandWarehouseShiftRegistersDeskSpikeDoesNot() {
        // RHR 55 / HRmax 184 → 33% HRR ≈ 97.6, 60% ≈ 132.4.
        let max = 184.0
        let rest = 55.0
        let brief = hrEvery(108, 14, stepS: 30)
        XCTAssertNil(StrainScorer.strain(brief, maxHR: max, restingHR: rest))
        let desk = hrEvery(85, 40, stepS: 30)
        XCTAssertEqual(StrainScorer.strain(desk, maxHR: max, restingHR: rest)!, 0.0, accuracy: 0.01)
        let broken = (0..<40).map { i in
            HRSample(ts: i * 30, bpm: i % 4 < 2 ? 108 : 85)
        }
        XCTAssertEqual(StrainScorer.strain(broken, maxHR: max, restingHR: rest)!, 0.0, accuracy: 0.01)
        let shift = hrEvery(108, 960, stepS: 30) // 8 h
        let effort = StrainScorer.strain(shift, maxHR: max, restingHR: rest)!
        XCTAssertGreaterThanOrEqual(effort, 55.0, "warehouse soft-band shift should land ≥55, got \(effort)")
        XCTAssertLessThan(effort, 90.0)
        let zone2 = hrEvery(140, 480, stepS: 30)
        let zoneEffort = StrainScorer.strain(zone2, maxHR: max, restingHR: rest)!
        XCTAssertGreaterThan(zoneEffort, effort)
    }
}
