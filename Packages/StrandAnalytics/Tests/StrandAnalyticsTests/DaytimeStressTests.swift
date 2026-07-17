import XCTest
@testable import StrandAnalytics
import WhoopProtocol

final class DaytimeStressTests: XCTestCase {

    /// Fill one local 15-min bucket starting at [hour]:[quarter*15] with `n` 1 Hz samples.
    private func bucketHR(_ hour: Int, quarter: Int, bpm: Int,
                          n: Int = DaytimeStress.minHourHRSamples) -> [HRSample] {
        let base = hour * 3_600 + quarter * DaytimeStress.bucketSeconds
        return (0..<n).map { HRSample(ts: base + $0, bpm: bpm) }
    }

    /// Fill a whole clock hour (4×15-min) at constant bpm.
    private func hourHR(_ hour: Int, bpm: Int,
                        nPerBucket: Int = DaytimeStress.minHourHRSamples) -> [HRSample] {
        (0..<DaytimeStress.bucketsPerHour).flatMap { q in bucketHR(hour, quarter: q, bpm: bpm, n: nPerBucket) }
    }

    func testEmptyWhenNoHR() {
        XCTAssertEqual(DaytimeStress.analyze(hr: [], rr: []), .empty)
    }

    func testHourBelowGateIsUnscored() {
        let hr = bucketHR(9, quarter: 0, bpm: 70, n: DaytimeStress.minHourHRSamples - 1)
        let r = DaytimeStress.analyze(hr: hr, rr: [])
        XCTAssertTrue(r.scored.isEmpty, "an under-gate hour must not be scored")
    }

    func testScoresMapOntoZeroToThree() {
        var hr: [HRSample] = []
        hr += hourHR(8, bpm: 62)
        hr += hourHR(9, bpm: 60)
        hr += hourHR(10, bpm: 61)
        hr += hourHR(11, bpm: 95)
        let r = DaytimeStress.analyze(hr: hr, rr: [])
        XCTAssertFalse(r.scored.isEmpty)
        for p in r.scored {
            let lvl = p.level!
            XCTAssertGreaterThanOrEqual(lvl, 0)
            XCTAssertLessThanOrEqual(lvl, 3)
        }
        XCTAssertEqual(r.peak?.hour, 11)
        let calm = r.scored.first { $0.hour == 9 }!.level!
        let tense = r.scored.first { $0.hour == 11 }!.level!
        XCTAssertGreaterThan(tense, calm)
    }

    func testNightHoursAreScoredNearFloor() {
        // WHOOP sleep band: night hours are ON the series near the calm floor.
        let hr = hourHR(3, bpm: 50) + hourHR(9, bpm: 60)
        let r = DaytimeStress.analyze(hr: hr, rr: [])
        XCTAssertTrue(r.hours.contains { $0.hour == 3 && $0.level != nil })
        XCTAssertTrue(r.hours.contains { $0.hour == 9 && $0.level != nil })
        let night = r.scored.first { $0.hour == 3 }!.level!
        XCTAssertLessThan(night, 1.2, "night should sit near WHOOP sleep floor")
    }

    func testSustainedHighFlagsAfterThreeConsecutiveHighHours() {
        var hr: [HRSample] = []
        for h in [8, 9, 10] { hr += hourHR(h, bpm: 58) }
        hr += hourHR(13, bpm: 120)
        hr += hourHR(14, bpm: 125)
        hr += hourHR(15, bpm: 130)
        let r = DaytimeStress.analyze(hr: hr, rr: [])
        XCTAssertTrue(r.sustainedHigh, "three trailing HIGH clock-hours should flag sustained stress")
        XCTAssertGreaterThanOrEqual(r.sustainedRun, DaytimeStress.sustainedBuckets)
    }

    func testFlatDayDoesNotFlagSustained() {
        var hr: [HRSample] = []
        for h in 8...16 { hr += hourHR(h, bpm: 64) }
        let r = DaytimeStress.analyze(hr: hr, rr: [])
        XCTAssertFalse(r.sustainedHigh)
        if let mean = r.dayMean {
            XCTAssertLessThan(mean, 1.0, "WHOOP calm floor — flat day mean < 1.0, was \(mean)")
        }
    }

    func testSleepHoursInTheWindowDoNotShiftTheWakingTimeline() {
        let waking: [HRSample] = zip(6...17, [62, 64, 63, 65, 64, 63, 62, 64, 66, 63, 64, 65])
            .flatMap { hourHR($0.0, bpm: $0.1) }
        let sleep: [HRSample] = zip(0...5, [50, 51, 52, 51, 50, 53])
            .flatMap { hourHR($0.0, bpm: $0.1) }

        let wakingOnly = DaytimeStress.analyze(hr: waking, rr: [])
        let withSleep = DaytimeStress.analyze(hr: sleep + waking, rr: [])

        XCTAssertEqual(withSleep.sustainedHigh, wakingOnly.sustainedHigh)
        for h in 6...17 {
            let withLvls = withSleep.scored.filter { $0.hour == h }.compactMap(\.level)
            let withoutLvls = wakingOnly.scored.filter { $0.hour == h }.compactMap(\.level)
            XCTAssertFalse(withLvls.isEmpty)
            XCTAssertEqual(withLvls.reduce(0, +) / Double(withLvls.count),
                           withoutLvls.reduce(0, +) / Double(withoutLvls.count),
                           accuracy: 1e-9)
        }
        XCTAssertFalse(withSleep.sustainedHigh)
        if let mean = withSleep.dayMean {
            XCTAssertLessThan(mean, 1.0)
        }
    }

    func testTimezoneOffsetShiftsWakingWindow() {
        let hr = hourHR(4, bpm: 60)
        let r = DaytimeStress.analyze(hr: hr, rr: [], tzOffsetSeconds: 3 * 3_600)
        XCTAssertTrue(r.hours.contains { $0.hour == 7 })
    }

    func testRMSSDLowersStressDirectionMatchesDailyScore() {
        var hr: [HRSample] = []
        var rr: [RRInterval] = []
        for h in [8, 9, 10, 11] { hr += hourHR(h, bpm: 65) }
        rr += hourRRVariable(8, rrMs: 900, jitter: 40)
        rr += hourRRVariable(9, rrMs: 900, jitter: 40)
        rr += hourRRVariable(10, rrMs: 900, jitter: 40)
        rr += hourRRVariable(11, rrMs: 900, jitter: 2)
        let r = DaytimeStress.analyze(hr: hr, rr: rr)
        let relaxed = r.scored.first { $0.hour == 9 }!.level!
        let tense = r.scored.first { $0.hour == 11 }!.level!
        XCTAssertGreaterThan(tense, relaxed)
    }

    func testFifteenMinuteBucketsProduceMultiplePointsPerClockHour() {
        let hrs = hourHR(10, bpm: 64) + hourHR(11, bpm: 64) + hourHR(12, bpm: 64)
        let r = DaytimeStress.analyze(hr: hrs, rr: [])
        let at10 = r.scored.filter { $0.hour == 10 }.count
        XCTAssertGreaterThanOrEqual(at10, 2)
    }

    /// R-R for one hour with a controllable beat-to-beat jitter (drives RMSSD).
    private func hourRRVariable(_ hour: Int, rrMs: Int, jitter: Int, n: Int = 60) -> [RRInterval] {
        let base = hour * 3_600
        return (0..<n).map { RRInterval(ts: base + $0 * 50, rrMs: rrMs + ($0 % 2 == 0 ? jitter : -jitter)) }
    }
}
