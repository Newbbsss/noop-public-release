import XCTest
@testable import Strand

final class AlarmMathChallengeTests: XCTestCase {
    func testRequireMath_alwaysWhenEnabled() {
        XCTAssertTrue(
            AlarmMathChallenge.requireMath(
                mathEnabled: true,
                mathOnDrowsy: false,
                hrBpm: nil,
                drowsyThreshold: 55,
                isFinalDeadline: false
            )
        )
    }

    func testRequireMath_drowsyNeedsFinalAndLowHr() {
        XCTAssertFalse(
            AlarmMathChallenge.requireMath(
                mathEnabled: false,
                mathOnDrowsy: true,
                hrBpm: 48,
                drowsyThreshold: 55,
                isFinalDeadline: false
            )
        )
        XCTAssertTrue(
            AlarmMathChallenge.requireMath(
                mathEnabled: false,
                mathOnDrowsy: true,
                hrBpm: 48,
                drowsyThreshold: 55,
                isFinalDeadline: true
            )
        )
        XCTAssertFalse(
            AlarmMathChallenge.requireMath(
                mathEnabled: false,
                mathOnDrowsy: true,
                hrBpm: nil,
                drowsyThreshold: 55,
                isFinalDeadline: true
            )
        )
        XCTAssertFalse(
            AlarmMathChallenge.requireMath(
                mathEnabled: false,
                mathOnDrowsy: true,
                hrBpm: 70,
                drowsyThreshold: 55,
                isFinalDeadline: true
            )
        )
    }

    func testNext_isDeterministicForSeed() {
        let a = AlarmMathChallenge.next(seed: 42)
        let b = AlarmMathChallenge.next(seed: 42)
        XCTAssertEqual(a, b)
        XCTAssertFalse(a.prompt.isEmpty)
        XCTAssertGreaterThanOrEqual(a.answer, 0)
        XCTAssertLessThanOrEqual(a.answer, 24)
    }
}
