import XCTest
@testable import Strand

final class WhoopModelIdentityTests: XCTestCase {
    func testFromBleNameInfersFamilies() {
        XCTAssertEqual(WhoopModel.fromBleName("WHOOP MG"), .whoop5mg)
        XCTAssertEqual(WhoopModel.fromBleName("WHOOP 5.0"), .whoop5mg)
        XCTAssertEqual(WhoopModel.fromBleName("WHOOP 5AM"), .whoop5mg)
        XCTAssertEqual(WhoopModel.fromBleName("WHOOP 4C1594026"), .whoop4)
        XCTAssertNil(WhoopModel.fromBleName("Oura"))
        XCTAssertNil(WhoopModel.fromBleName(nil))
    }

    func testResolveForEstimatesNeverFlipsMgToWhoop4() {
        XCTAssertEqual(
            WhoopModel.resolveForEstimates(selected: .whoop4, whoop5Detected: true, persisted: nil),
            .whoop5mg)
        XCTAssertEqual(
            WhoopModel.resolveForEstimates(
                selected: .whoop4, whoop5Detected: false, persisted: .whoop5mg),
            .whoop5mg)
        XCTAssertEqual(
            WhoopModel.resolveForEstimates(selected: .whoop4, whoop5Detected: false, persisted: nil),
            .whoop4)
    }
}
