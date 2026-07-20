import XCTest
@testable import WhoopProtocol

/// Synthetic-frame pins for `Whoop5RawOptical` (ryanbr/noop #546). No real-capture fixture required —
/// Gilbert port keeps this phone-unplug / unit-test only.
final class Whoop5RawOpticalTests: XCTestCase {
    private func frame() -> [UInt8] {
        var f = [UInt8](repeating: 0, count: Whoop5RawOptical.bufferLength)
        f[0] = 0xAA
        f[8] = 0x2F
        f[9] = 20
        putU32(&f, 11, 11_494_060)
        putU32(&f, 15, 1_784_054_004)

        let block = Whoop5RawOptical.blockStart + 4 * Whoop5RawOptical.blockLength
        f[block] = 2
        for (i, value) in [UInt8(2), 200, 0, 4, 0, 0].enumerated() { f[block + 1 + i] = value }
        for (i, value) in [UInt8(3), 32, 0, 0, 0, 0, 0].enumerated() { f[block + 7 + i] = value }
        for (i, value) in [UInt8(1), 32, 0, 0, 0, 0, 0].enumerated() { f[block + 14 + i] = value }
        putI32(&f, block + Whoop5RawOptical.headerLength, -123_456)
        putI32(&f, block + Whoop5RawOptical.headerLength + 4, 234_567)
        putI32(&f, block + Whoop5RawOptical.headerLength + Whoop5RawOptical.channelSlotLength, 9_876)
        putI32(&f, block + Whoop5RawOptical.headerLength + Whoop5RawOptical.channelSlotLength + 4, 11_318)
        return f
    }

    func testDecodesRepeatedBlockAndPairedChannels() throws {
        let decoded = try XCTUnwrap(Whoop5RawOptical.decode(frame()))
        XCTAssertEqual(decoded.recordIndex, 11_494_060)
        XCTAssertEqual(decoded.baseTs, 1_784_054_004)
        XCTAssertEqual(decoded.blocks.map(\.sampleCount), [0, 0, 0, 0, 2])

        let block = decoded.blocks[4]
        XCTAssertEqual(block.sharedMetadata, [0x02, 0xc8, 0x00, 0x04, 0x00, 0x00])
        XCTAssertEqual(block.channels[0].metadata, [0x03, 0x20, 0, 0, 0, 0, 0])
        XCTAssertEqual(block.channels[1].metadata, [0x01, 0x20, 0, 0, 0, 0, 0])
        XCTAssertEqual(block.channels[0].samples, [-123_456, 234_567])
        XCTAssertEqual(block.channels[1].samples, [9_876, 11_318])
        XCTAssertEqual(block.rawHeader.count, 21)
        XCTAssertTrue(decoded.blocks.allSatisfy { $0.channels.count == 2 })
    }

    func testInterpreterExposesHeadersWithoutWavelengthLabels() {
        // CRC-valid envelope so parseFrame reaches the v20 optical path (same pattern as V2021 tests).
        var f = frame()
        f[1] = 0x01
        let declared = Whoop5RawOptical.bufferLength - 8
        f[2] = UInt8(declared & 0xff); f[3] = UInt8((declared >> 8) & 0xff)
        f[4] = 0x01; f[5] = 0x00
        let h = crc16Modbus(Array(f[0..<6]))
        f[6] = UInt8(h & 0xff); f[7] = UInt8((h >> 8) & 0xff)
        f[Whoop5RawOptical.blockStart] = 25
        let payloadEnd = Whoop5RawOptical.bufferLength - 4
        let c = crc32(Array(f[8..<payloadEnd]))
        f[payloadEnd] = UInt8(c & 0xff); f[payloadEnd + 1] = UInt8((c >> 8) & 0xff)
        f[payloadEnd + 2] = UInt8((c >> 16) & 0xff); f[payloadEnd + 3] = UInt8((c >> 24) & 0xff)

        let parsed = parseFrame(f, family: .whoop5).parsed
        XCTAssertEqual(parsed["sensor_block_count"]?.intValue, 5)
        XCTAssertEqual(parsed["block_b0_sample_count"]?.intValue, 25)
        XCTAssertEqual(parsed["block_b1_sample_count"]?.intValue, 0)
        XCTAssertEqual(parsed["block_b4_header"]?.intArrayValue?.count, 21)
        XCTAssertEqual(parsed["channel_b0_0"]?.intArrayValue?.count, 25)
        XCTAssertEqual(parsed["channel_b0_1"]?.intArrayValue?.count, 25)
        XCTAssertNil(parsed["red"])
        XCTAssertNil(parsed["ir"])
    }

    func testStrictShapeAndCountGates() {
        var f = [UInt8](repeating: 0, count: Whoop5RawOptical.bufferLength)
        f[0] = 0xAA
        f[8] = 0x2F
        f[9] = 20
        XCTAssertNotNil(Whoop5RawOptical.decode(f))

        f[Whoop5RawOptical.blockStart] = UInt8(Whoop5RawOptical.channelCapacity + 1)
        XCTAssertNil(Whoop5RawOptical.decode(f))
        f[Whoop5RawOptical.blockStart] = 0
        f.append(0)
        XCTAssertNil(Whoop5RawOptical.decode(f))

        var wrongVersion = frame()
        wrongVersion[9] = 21
        XCTAssertNil(Whoop5RawOptical.decode(wrongVersion))

        // Any other type byte than 0x2F (hist) / 0x2B (live raw) stays rejected.
        var wrongType = frame()
        wrongType[8] = 0x2C
        XCTAssertNil(Whoop5RawOptical.decode(wrongType))
    }

    /// 2026-07-19 live-capture evidence: the live REALTIME_RAW_DATA carrier (inner type 0x2B) holds the
    /// byte-identical v20 body (181/181 frames, auto-gather-20260718/203825), so it must decode too.
    func testDecodesLiveRawDataCarrier0x2B() throws {
        var f = frame()
        f[8] = 0x2B
        let decoded = try XCTUnwrap(Whoop5RawOptical.decode(f))
        XCTAssertEqual(decoded.recordIndex, 11_494_060)
        XCTAssertEqual(decoded.baseTs, 1_784_054_004)
        XCTAssertEqual(decoded.blocks.map(\.sampleCount), [0, 0, 0, 0, 2])
        XCTAssertEqual(decoded.blocks[4].channels[0].samples, [-123_456, 234_567])
        XCTAssertEqual(decoded.blocks[4].channels[1].samples, [9_876, 11_318])
    }

    private func putU32(_ frame: inout [UInt8], _ offset: Int, _ value: UInt32) {
        frame[offset] = UInt8(value & 0xff)
        frame[offset + 1] = UInt8((value >> 8) & 0xff)
        frame[offset + 2] = UInt8((value >> 16) & 0xff)
        frame[offset + 3] = UInt8((value >> 24) & 0xff)
    }

    private func putI32(_ frame: inout [UInt8], _ offset: Int, _ value: Int32) {
        putU32(&frame, offset, UInt32(bitPattern: value))
    }
}
