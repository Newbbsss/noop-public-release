import XCTest
@testable import Strand

/// Swift twin of the Android #1020 tests in `BackfillContinuationTest.kt` (2026-07-18) — keeps the two
/// platforms locked on the same GET_DATA_RANGE straddle guard.
///
/// The long-form range reply carries (unix u32, value u32) record pairs on an 8-byte grid ONE BYTE OFF
/// the aligned 7+4k scan grid, so the raw scan latched ts_high24 + the next field's low byte as garbage
/// ≈2028–2030 "newest" words (pairing-log false alarms: 28238h / 12319h / 12312h / 18864h "ahead",
/// suppressing periodic backfill + raising the future-clock banner while the strap RTC was wall-clock-
/// exact). The wall+48 h bound must reject every one of those straddles; a garbage PAST word or nil is
/// acceptable (never poisons clock-trust / the #547 window), a garbage FUTURE one never is. Real captures
/// below are byte-for-byte from the Fold pairing log / logcat.
final class DataRangeSkewGuardTests: XCTestCase {

    /// Real capture that produced "newest 2029-10-06 17:59 · 28238h ahead" (three 0x706A5Bxx straddles
    /// at offsets 55/63/71; true record words 0x6A5B2DBC/0x6A5B2F23 = 2026-07-18 sit one byte off-grid).
    private let rangeLongForm28238h =
        "aa014c00010032d1242e2289010180130000331300004b1300003313000008000000000002006801000098fe1d0030aa6b691e65" +
        "0000bc2d5b6a707d0000bc2d5b6a707d0000232f5b6a707d000000004aa61349"

    /// Real capture that produced "12312h ahead": both in-window aligned words are future straddles.
    private let rangeLongForm12312h =
        "aa014c00010032d1247e220e010140760000187600002c7600001876000008000000000002001e010000d4fe1d0076277169a3" +
        "1000006da55b6a000000006da55b6a0000000089a65b6a7a1400000000c084c105"

    /// Real 32-byte periodic range poll (2026-07-18 23:58:26 UTC): no in-window aligned word at all.
    private let rangeShortPoll =
        "aa011800010022e1280222135c6ac2354001e003000000000000010091c33e75"

    /// Wall clock matching the capture day (2026-07-18 ~22:26 UTC).
    private let captureWallNow = 1_784_500_000

    private func hexBytes(_ s: String) -> [UInt8] {
        var out = [UInt8](); out.reserveCapacity(s.count / 2)
        var i = s.startIndex
        while i < s.endIndex {
            let j = s.index(i, offsetBy: 2)
            out.append(UInt8(s[i..<j], radix: 16)!)
            i = j
        }
        return out
    }

    func testDataRangeNewestUnix_neverLatchesFutureStraddle_realCaptures() {
        let maxPlausible = captureWallNow + 48 * 3600
        // The 2029 poison (1886018349 / 1886018351) must be gone; only the harmless past word survives.
        let newest1 = BLEManager.dataRangeNewestUnix(from: hexBytes(rangeLongForm28238h), wallNowUnix: captureWallNow)
        XCTAssertTrue(newest1 == nil || newest1! <= maxPlausible)
        XCTAssertFalse(newest1 == 1_886_018_349 || newest1 == 1_886_018_351)
        // Every aligned in-window word here was a future straddle ⇒ unknown, never future-dated.
        XCTAssertNil(BLEManager.dataRangeNewestUnix(from: hexBytes(rangeLongForm12312h), wallNowUnix: captureWallNow))
        XCTAssertNil(BLEManager.dataRangeNewestUnix(from: hexBytes(rangeShortPoll), wallNowUnix: captureWallNow))
    }

    func testDataRangeOldestUnix_sameFutureBound_realCaptures() {
        let maxPlausible = captureWallNow + 48 * 3600
        let frame = hexBytes(rangeLongForm28238h)
        let oldest = BLEManager.dataRangeOldestUnix(from: frame, wallNowUnix: captureWallNow)
        let newest = BLEManager.dataRangeNewestUnix(from: frame, wallNowUnix: captureWallNow)
        XCTAssertTrue(oldest == nil || oldest! <= maxPlausible)
        XCTAssertNil(BLEManager.dataRangeOldestUnix(from: hexBytes(rangeLongForm12312h), wallNowUnix: captureWallNow))
        // Single surviving candidate ⇒ oldest == newest ⇒ the call site leaves the #547 session
        // window half-formed (falls back to absolute-only) instead of closing it against real records.
        XCTAssertEqual(newest, oldest)
    }

    func testDataRangeUnix_genuineWordStillDecoded() {
        // Synthetic body: sane u32 at aligned offset 11, plus a garbage future word at offset 15.
        var frame = [UInt8](repeating: 0, count: 23)
        frame[6] = 0x22
        let sane = captureWallNow - 3_600
        for k in 0...3 { frame[11 + k] = UInt8((sane >> (8 * k)) & 0xFF) }
        let future = captureWallNow + 49 * 3600
        for k in 0...3 { frame[15 + k] = UInt8((future >> (8 * k)) & 0xFF) }
        XCTAssertEqual(sane, BLEManager.dataRangeNewestUnix(from: frame, wallNowUnix: captureWallNow))
        XCTAssertEqual(sane, BLEManager.dataRangeOldestUnix(from: frame, wallNowUnix: captureWallNow))
    }

    func testDataRangeUnix_skewBoundary() {
        // Exactly wall+48 h is a candidate (isFutureDatedNewest treats it as plausible skew too);
        // one second past is not.
        let atCap = captureWallNow + 48 * 3600
        let pastCap = atCap + 1
        let make: (Int) -> [UInt8] = { v in
            var b = [UInt8](repeating: 0, count: 15)
            for k in 0...3 { b[11 + k] = UInt8((v >> (8 * k)) & 0xFF) }
            return b
        }
        XCTAssertEqual(atCap, BLEManager.dataRangeNewestUnix(from: make(atCap), wallNowUnix: captureWallNow))
        XCTAssertNil(BLEManager.dataRangeNewestUnix(from: make(pastCap), wallNowUnix: captureWallNow))
    }
}
