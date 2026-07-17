import XCTest
@testable import Strand

final class SixAxisMotionLabelsTests: XCTestCase {
    private func sample(_ kind: SixAxisSourceKind, ax: Float = 0.1) -> SixAxisSample {
        SixAxisSample(ax: ax, ay: 0, az: 1, gx: 0, gy: 0, gz: 0, source: kind, tsMs: 1)
    }

    func testPreferStrapDriver_returnsWhateverIsPresent() {
        let live = sample(.strapLive)
        let offload = sample(.strapOffload, ax: 0.9)
        XCTAssertEqual(SixAxisMotionLabels.preferStrapDriver(live)?.source, .strapLive)
        XCTAssertEqual(SixAxisMotionLabels.preferStrapDriver(offload)?.source, .strapOffload)
        XCTAssertNil(SixAxisMotionLabels.preferStrapDriver(nil))
    }

    func testIsLiveDriver() {
        XCTAssertTrue(SixAxisMotionLabels.isLiveDriver(sample(.strapLive)))
        XCTAssertFalse(SixAxisMotionLabels.isLiveDriver(sample(.strapOffload)))
        XCTAssertFalse(SixAxisMotionLabels.isLiveDriver(nil))
    }

    func testSourceTitlesHonesty() {
        XCTAssertEqual(SixAxisMotionLabels.sourceTitle(.strapLive), "Strap IMU (live)")
        XCTAssertEqual(SixAxisMotionLabels.sourceTitle(.strapOffload), "Strap offload (not live)")
        XCTAssertTrue(SixAxisMotionLabels.sourceDetail(.strapOffload).contains("Not a live"))
        XCTAssertTrue(SixAxisMotionLabels.waitingDetail.contains("No phone"))
    }
}

final class UnitFormatterEffortHonestyTests: XCTestCase {
    func testEffortDisplayOrEmpty_skipsZeroAndNull() {
        XCTAssertEqual(UnitFormatter.effortDisplayOrEmpty(nil, scale: .whoop), "—")
        XCTAssertEqual(UnitFormatter.effortDisplayOrEmpty(0.0, scale: .whoop), "—")
        XCTAssertEqual(UnitFormatter.effortDisplayOrEmpty(0.0, scale: .hundred), "—")
        XCTAssertEqual(UnitFormatter.effortDisplayOrEmpty(20.0, scale: .whoop), "4.2")
        XCTAssertEqual(UnitFormatter.effortDisplayOrEmpty(20.0, scale: .hundred), "20")
        XCTAssertEqual(UnitFormatter.effortDisplayOrEmpty(0.0, scale: .whoop, empty: "Awaiting"), "Awaiting")
    }
}
