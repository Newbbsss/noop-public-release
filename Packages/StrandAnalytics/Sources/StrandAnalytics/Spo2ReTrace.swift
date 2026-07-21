import Foundation

// Spo2ReTrace.swift - the Connection-mode SpO2 reverse-engineering dump (PR #945, reimplemented).
//
// WHOOP 4.0 has the Blood O2 sensor and the historical decode already maps the raw red/IR PPG channels
// (spo2_red@68 / spo2_ir@70 on the v24 layout), but NOOP nulls spo2Pct for WHOOP on purpose: computing a
// calibrated % from the raw ADC needs the dense dual-wavelength waveform plus WHOOP's proprietary
// calibration curve, and guessing it would manufacture a plausible-but-wrong health number - the exact
// trap that withdrew the #194 PPG->HR attempt. The ONLY honest path to a reliable value is to find out
// whether the strap already BANKS a computed SpO2 in a record field we have not mapped.
//
// So this dumps a handful of FULL historical records (hex) alongside their mapped SpO2 channels, log-only
// and gated behind the Test Centre Connection mode, so an offline pass can correlate a byte (or the
// red/IR pair) against the SpO2 % the WHOOP app shows for the same nights. Records dump whether or not
// they carry SpO2 channels, so "the strap banks nothing" is provable too - in which case the honest
// outcome is a capability label, never a fabricated number. NO user-facing SpO2 value comes from this.
//
// Pure formatter: no I/O, no state, no em-dashes, no PII (a record is sensor payload; the serial never
// rides in it). The Kotlin twin is Spo2ReTrace.kt; the emitted line is byte-identical on both platforms.
public enum Spo2ReTrace {

    /// Max records dumped per offload session. A handful is enough for an offline correlation pass and
    /// keeps the strap log bounded; the Backfiller counter spans chunks and resets per session.
    public static let maxSamples = 8

    /// whoop-rs gate for v18 absolute `@82` (== GEN5 inner 74): 70..100 candidate only.
    /// Bit-7 saturation sentinels (e.g. 0x80) and sub-70 codes fall out — same filter as whoop-rs.
    /// Product path never banks DailyMetric.spo2Pct from open BLE — research helper.
    public static func whoopRsSpo2PctCandidate(_ auxByte82: Int?) -> Int? {
        guard let v = auxByte82, (70...100).contains(v) else { return nil }
        return v
    }

    /// Prefer nz `@82` / whoop-rs gate / band-asleep within [maxSamples]; keep [baselineSamples]
    /// for awake+aux0 so always-zero nights stay provable. Never a product %.
    public static let baselineSamples = 2

    public static func isResearchInteresting(auxByte82: Int?, sleepState: Int?) -> Bool {
        if let a = auxByte82, a != 0 { return true }
        if whoopRsSpo2PctCandidate(auxByte82) != nil { return true }
        if sleepState == 2 { return true }
        return false
    }

    /// One record's RE line: the mapped SpO2 channels + timestamp + layout version, then sleep_state /
    /// aux_byte_82 (v18 research fields — raw, NEVER SpO2 %), then the FULL frame hex. Absent channels
    /// render "null" so a channel-less record still proves what it lacks.
    /// Takes already-extracted ints (ConnectionTrace's primitive style) so this package stays free of a
    /// WhoopProtocol dependency; the caller reads them off its parsed frame.
    public static func recordLine(frame: [UInt8], version: Int?, unix: Int?,
                                  red: Int?, ir: Int?, skinRaw: Int?,
                                  sleepState: Int? = nil, auxByte82: Int? = nil) -> String {
        let hex = frame.map { String(format: "%02x", $0) }.joined()
        func f(_ v: Int?) -> String { v.map(String.init) ?? "null" }
        let gate: String
        if auxByte82 == nil {
            gate = ""
        } else if whoopRsSpo2PctCandidate(auxByte82) != nil {
            gate = " whooprs_pct_gate=in70_100"
        } else {
            gate = " whooprs_pct_gate=out"
        }
        return "spo2re v=\(f(version)) unix=\(f(unix)) red=\(f(red)) ir=\(f(ir)) "
            + "skinRaw=\(f(skinRaw)) sleep_state=\(f(sleepState)) aux82=\(f(auxByte82))\(gate) "
            + "len=\(frame.count) raw=\(hex)"
    }
}
