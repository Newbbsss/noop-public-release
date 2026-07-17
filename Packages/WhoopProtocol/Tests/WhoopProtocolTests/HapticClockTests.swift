import XCTest
@testable import WhoopProtocol

/// Pure-logic tests for the Haptic Clock encoder (#460). Android `HapticClockTest.kt` asserts the
/// same lists so the two platforms buzz identically.
final class HapticClockTests: XCTestCase {
    private typealias P = HapticClock.Pulse

    private func long(_ gap: Int) -> P { P(durationMs: HapticClock.longMs, gapMs: gap) }
    private func short(_ gap: Int) -> P { P(durationMs: HapticClock.shortMs, gapMs: gap) }
    private func hold(_ gap: Int) -> P { P(durationMs: HapticClock.holdTickMs, gapMs: gap) }

    private func morse(_ hour: Int, _ minute: Int, is24h: Bool) -> [P] {
        HapticClock.pulses(hour: hour, minute: minute, is24h: is24h, style: .morse)
    }

    /// 3:25 in 24-hour form: hour 03 (no tens, 3 units) — block — minute 25 (2 tens, 5 units).
    func test0325_24h_exactPulseList() {
        let g = HapticClock.intraGapMs
        let expected: [P] = [
            short(g), short(g), short(HapticClock.blockGapMs),
            long(g), long(HapticClock.groupGapMs),
            short(g), short(g), short(g), short(g), short(0),
        ]
        XCTAssertEqual(morse(3, 25, is24h: true), expected)
    }

    /// 12-hour mapping: 15:25 → dial reads 3:25, so it must equal the 24h 3:25 list exactly.
    func test1525_12h_mapsTo0325() {
        XCTAssertEqual(morse(15, 25, is24h: false), morse(3, 25, is24h: true))
    }

    /// 10:05 in 24-hour form: hour 10 (1 ten, 0 units) — block — minute 05 (0 tens, 5 units).
    func test1005_24h_handlesZeroDigits() {
        let g = HapticClock.intraGapMs
        let expected: [P] = [
            long(HapticClock.blockGapMs),
            short(g), short(g), short(g), short(g), short(0),
        ]
        XCTAssertEqual(morse(10, 5, is24h: true), expected)
    }

    /// Midnight 0:00 in 24-hour form has no nonzero digits — there is nothing to buzz.
    func testMidnight_24h_isEmpty() {
        XCTAssertEqual(morse(0, 0, is24h: true), [])
    }

    /// Midnight 0:00 in 12-hour form reads "12:00" → one ten + two units of hour, no minute pulses.
    func testMidnight_12h_readsTwelve() {
        let expected: [P] = [
            long(HapticClock.groupGapMs),
            short(HapticClock.intraGapMs), short(0),
        ]
        XCTAssertEqual(morse(0, 0, is24h: false), expected)
    }

    /// Noon stays 12 in 12-hour form (it does not collapse to 0).
    func testNoon_12h_isTwelve() {
        XCTAssertEqual(HapticClock.twelveHour(12), 12)
        XCTAssertEqual(HapticClock.twelveHour(0), 12)
        XCTAssertEqual(HapticClock.twelveHour(13), 1)
        XCTAssertEqual(HapticClock.twelveHour(23), 11)
    }

    /// Out-of-range inputs are clamped, not crashed.
    func testClampsOutOfRange() {
        XCTAssertEqual(morse(99, 99, is24h: true), morse(23, 59, is24h: true))
        XCTAssertEqual(morse(-5, -5, is24h: true), morse(0, 0, is24h: true))
    }

    /// 6:30 digit-hold → 6s hour hold, 1s pause, 3s minute-tens (not "6 pause 6").
    func testDigitHold_630_isSixPauseThree() {
        var expected: [P] = []
        for _ in 0..<5 { expected.append(hold(HapticClock.holdTickGapMs)) }
        expected.append(hold(HapticClock.holdPauseMs))
        for _ in 0..<2 { expected.append(hold(HapticClock.holdTickGapMs)) }
        expected.append(hold(0))
        XCTAssertEqual(HapticClock.digitHoldPulses(hour: 6, minute: 30, is24h: false), expected)
        XCTAssertEqual(HapticClock.pulses(hour: 6, minute: 30, is24h: false), expected) // default DIGIT_HOLD
    }

    /// Noon hold = 12s hour only (fits the ~12s budget).
    func testDigitHold_noon_isTwelveSeconds() {
        let pulses = HapticClock.digitHoldPulses(hour: 12, minute: 0, is24h: false)
        XCTAssertEqual(pulses.count, 12)
        XCTAssertEqual(pulses.first?.durationMs, HapticClock.holdTickMs)
        XCTAssertEqual(pulses.last?.gapMs, 0)
    }

    func testHoldLoops_forDigitHoldTick() {
        XCTAssertEqual(hold(0).holdLoops, 2)
        XCTAssertEqual(short(0).holdLoops, 1)
    }
}
