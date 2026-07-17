import XCTest
@testable import Strand

final class ChargingOverlayPolicyTests: XCTestCase {
    func testNewPlugOpensOnce() {
        let s = ChargingOverlayPolicy.evaluate(
            charging: true, pct: 40, wasActive: false, anchorPct: nil, dismissed: false)
        XCTAssertTrue(s.active)
        XCTAssertTrue(s.openOverlay)
        XCTAssertEqual(s.anchorPct, 40)
    }

    func testSoCTickDoesNotReopen() {
        var s = ChargingOverlayPolicy.evaluate(
            charging: true, pct: 40, wasActive: false, anchorPct: nil, dismissed: false)
        s = ChargingOverlayPolicy.evaluate(
            charging: true, pct: 41, wasActive: s.active, anchorPct: s.anchorPct, dismissed: false)
        XCTAssertTrue(s.active)
        XCTAssertFalse(s.openOverlay)
        XCTAssertEqual(s.anchorPct, 41)
    }

    func testLinkDownHoldsSession() {
        let s = ChargingOverlayPolicy.evaluate(
            charging: nil, pct: 50, wasActive: true, anchorPct: 48, dismissed: false,
            linkConnected: false)
        XCTAssertTrue(s.active)
        XCTAssertFalse(s.openOverlay)
        XCTAssertFalse(s.closeOverlay)
    }

    func testExplicitUnplugCloses() {
        let s = ChargingOverlayPolicy.evaluate(
            charging: false, pct: 55, wasActive: true, anchorPct: 50, dismissed: false)
        XCTAssertFalse(s.active)
        XCTAssertTrue(s.closeOverlay)
        XCTAssertTrue(s.clearDismissed)
    }
}
