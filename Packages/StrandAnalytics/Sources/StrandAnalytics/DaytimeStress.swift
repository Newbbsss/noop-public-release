import Foundation
import WhoopProtocol

// DaytimeStress.swift — intraday autonomic stress proxy from banked HR + R-R, with gravity
// motion + step activity-class + sedentary-bout calm gates.
//
// CONTINUED 2026-07-12 WHOOP Stress Monitor match (byte-twin of Android DaytimeStress.kt):
//   • 15-minute buckets (was 1h) — closer to WHOOP’s continuous curve without inventing beats.
//   • Quiet HR p10/p25; calm logistic raw=0 → ~0.5 LOW.
//   • Gravity L2 + step walk/run → stiller BPM subset; sedentary / still → calm bias.
//   • Night scored near floor; waking-only calm reference (#357).
//   • SD floors so flat calm days cannot explode z-scores.
//
// APPROXIMATE / non-clinical — not WHOOP’s proprietary Stress Monitor.

public enum DaytimeStress {

    // MARK: - Tunables

    /// 15-minute buckets — WHOOP chart is continuous; 1h was too coarse for tip/peak shape.
    public static let bucketSeconds: Int = 900
    /// ~4 buckets per clock hour (for UI hour counts).
    public static let bucketsPerHour: Int = 4

    /// Min HR samples per 15-min bucket (~75s at 1 Hz). Sparse MG offload may use [minHourHRSamplesSparse].
    public static let minHourHRSamples: Int = 75
    /// Adaptive floor when the day has few scored windows (Fable Stress #32).
    public static let minHourHRSamplesSparse: Int = 50
    public static let minHourHRSpanSeconds: Int = 60
    public static let sparseDayBucketCap: Int = 8
    public static let minPlausibleBpm: Int = 30
    public static let maxPlausibleBpm: Int = 220
    public static let highBandFloor: Double = 2.0
    /// ~3 waking hours of consecutive HIGH 15-min buckets.
    public static let sustainedHours: Int = 3
    public static var sustainedBuckets: Int { sustainedHours * bucketsPerHour }

    public static let wakingStartHour: Int = 6
    public static let wakingEndHour: Int = 22
    /// Optional tighter waking window (Fable Stress #31).
    public static let tightWakingStartHour: Int = 7
    public static let tightWakingEndHour: Int = 21

    /// raw=0 → ~0.36 LOW (8.6.77 WHOOP match twin of Android).
    public static let calmAnchorOffset: Double = 2.00
    public static let motionBusyFloor: Double = 0.030
    /// Soft damp when gravity/step marks the bucket busy — cuts motion false highs.
    public static let motionBusyDamp: Double = 0.58
    public static let sedentaryCalmBias: Double = 0.65
    public static let nightCalmBias: Double = 1.35
    /// When waking quiet HR is within this many bpm of overnight quiet, pull toward LOW (#12).
    public static let overnightAnchorSlackBpm: Double = 5.0
    public static let overnightCalmBias: Double = 0.55
    /// Soft tip ceiling outside clock waking (Android Now twin). Allows WHOOP overnight awake ~1.5.
    public static let nightTipCeiling: Double = 1.55
    /// Tighter ceiling inside sleep window / band asleep.
    public static let sleepTipCeiling: Double = 0.95
    /// Soft blend of prior days' resting/calm HR into today's calm reference (#11).
    public static let priorCalmBlendMax: Int = 14
    public static let baevskyCalmSi: Double = 80.0
    public static let baevskyHighSi: Double = 200.0
    public static let baevskyCalmBias: Double = 0.30
    public static let baevskyHighBump: Double = 0.15
    public static let hfCalmShare: Double = 0.40
    public static let hfCalmBias: Double = 0.25
    public static let workoutOverlapBias: Double = 0.62
    public static let ectopicMotionThreshold: Double = 0.15
    /// WHOOP-style personalization: nights of prior calm needed (Fable #50).
    public static let calibrationNightsTarget: Int = 4
    /// Band sleep_state == asleep (HistoricalStreams @81 nibble).
    public static let sleepStateAsleep: Int = 2
    public static let skinElevatedAbsC: Double = 0.5
    public static let skinElevatedBias: Double = 0.20
    public static let respElevatedBpm: Double = 18.0
    public static let respElevatedBias: Double = 0.15

    public static let stepClassStill: Int = 0
    public static let stepClassWalk: Int = 1
    public static let stepClassRun: Int = 2

    // MARK: - Output

    public struct HourPoint: Equatable, Sendable {
        public let hour: Int
        public let startTs: Int
        public let level: Double?
        public let meanHR: Double?
        public let rmssd: Double?
        /// Gravity/step walk-run busy — UI glyph + scrub caption.
        public let motionBusy: Bool

        public var hasData: Bool { level != nil }

        public init(hour: Int, startTs: Int, level: Double?, meanHR: Double?, rmssd: Double?,
                    motionBusy: Bool = false) {
            self.hour = hour
            self.startTs = startTs
            self.level = level
            self.meanHR = meanHR
            self.rmssd = rmssd
            self.motionBusy = motionBusy
        }
    }

    public struct Result: Equatable, Sendable {
        public let hours: [HourPoint]
        public let sustainedHigh: Bool
        public let sustainedRun: Int
        public let dayMean: Double?
        public let peak: HourPoint?
        /// Distinct prior days that contributed to multi-day calm (Fable #50).
        public let priorCalmDayCount: Int

        public init(hours: [HourPoint], sustainedHigh: Bool, sustainedRun: Int,
                    dayMean: Double?, peak: HourPoint?, priorCalmDayCount: Int = 0) {
            self.hours = hours
            self.sustainedHigh = sustainedHigh
            self.sustainedRun = sustainedRun
            self.dayMean = dayMean
            self.peak = peak
            self.priorCalmDayCount = priorCalmDayCount
        }

        public var scored: [HourPoint] { hours.filter { $0.level != nil } }

        /// Nights still needed for a personal calm baseline; 0 when ready.
        public var calibrationNightsRemaining: Int {
            max(0, calibrationNightsTarget - priorCalmDayCount)
        }

        /// Scored coverage in clock-hours (15-min buckets ÷ 4).
        public var scoredHoursApprox: Double {
            Double(scored.count) / Double(bucketsPerHour)
        }

        /// Exact high-zone minutes: scored buckets with level ≥ highBandFloor × 15.
        public var highZoneMinutes: Int {
            Result.minutesForBuckets(scored.filter { ($0.level ?? 0) >= highBandFloor }.count)
        }

        public var calmZoneMinutes: Int {
            Result.minutesForBuckets(scored.filter { ($0.level ?? 0) < 1.0 }.count)
        }

        public var moderateZoneMinutes: Int {
            Result.minutesForBuckets(scored.filter {
                guard let l = $0.level else { return false }
                return l >= 1.0 && l < highBandFloor
            }.count)
        }

        public static let empty = Result(hours: [], sustainedHigh: false, sustainedRun: 0,
                                         dayMean: nil, peak: nil, priorCalmDayCount: 0)

        public static func minutesForBuckets(_ buckets: Int) -> Int {
            guard buckets > 0 else { return 0 }
            return buckets * Int(bucketSeconds / 60)
        }

        public static func formatZoneDuration(_ minutes: Int) -> String {
            if minutes <= 0 { return "0 min" }
            let hr = minutes / 60
            let min = minutes % 60
            if hr > 0 && min > 0 { return "\(hr) hr \(min) min" }
            if hr > 0 { return "\(hr) hr" }
            return "\(min) min"
        }

        public static func formatZoneCompact(_ minutes: Int) -> String {
            if minutes <= 0 { return "—" }
            let hr = minutes / 60
            let min = minutes % 60
            if hr > 0 && min > 0 { return "\(hr)h \(min)m" }
            if hr > 0 { return "\(hr)h" }
            return "\(min)m"
        }
    }

    // MARK: - Shared stress math

    static func mean(_ xs: [Double]) -> Double? {
        guard !xs.isEmpty else { return nil }
        return xs.reduce(0, +) / Double(xs.count)
    }

    static func std(_ xs: [Double], mean m: Double?) -> Double {
        guard let m, xs.count > 1 else { return 0 }
        let v = xs.map { ($0 - m) * ($0 - m) }.reduce(0, +) / Double(xs.count)
        return v.squareRoot()
    }

    static func rawScore(hr: Double?, meanHR: Double?, sdHR: Double,
                         rmssd: Double?, meanRMSSD: Double?, sdRMSSD: Double) -> Double {
        var sum = 0.0
        if let h = hr, let m = meanHR, sdHR > 0.0001 {
            sum += (h - m) / sdHR
        }
        if let r = rmssd, let m = meanRMSSD, sdRMSSD > 0.0001 {
            sum += (m - r) / sdRMSSD
        }
        return sum
    }

    static func squash(_ raw: Double) -> Double {
        let s = 3.0 / (1.0 + exp(-(raw - calmAnchorOffset)))
        return min(max(s, 0), 3)
    }

    // MARK: - Public API

    public static func analyze(hr: [HRSample], rr: [RRInterval],
                               tzOffsetSeconds: Int = 0,
                               motion: [WorkoutDetector.ActivityPoint] = [],
                               steps: [StepSample] = [],
                               sedentaryBouts: [InactivityPeriod] = [],
                               wakingStart: Int = wakingStartHour,
                               wakingEnd: Int = wakingEndHour,
                               priorCalmHrs: [Double] = [],
                               daySi: Double? = nil,
                               hfVagalTrusted: Bool = false,
                               workoutWindows: [(Int, Int)] = [],
                               sleepState: [(Int, Int)] = [],
                               skinElevated: Bool = false,
                               respElevated: Bool = false,
                               priorCalmDayCount: Int = 0) -> Result {
        let key = StressKey(
            hr: StreamFingerprint.of(hr, ts: { $0.ts }, quant: { Int($0.bpm) }),
            rr: StreamFingerprint.of(rr, ts: { $0.ts }, quant: { Int($0.rrMs) }),
            motion: StreamFingerprint.of(motion, ts: { $0.ts }, quant: { Int(($0.intensity * 1000).rounded()) }),
            steps: StreamFingerprint.of(steps, ts: { $0.ts }, quant: { ($0.activityClass ?? -1) * 10_000 + $0.counter }),
            sedentary: sedentaryBouts.count + sedentaryBouts.reduce(0) { $0 &+ $1.start &+ $1.end },
            tz: tzOffsetSeconds,
            waking: wakingStart * 100 + wakingEnd,
            prior: priorCalmHrs.count + Int((priorCalmHrs.reduce(0, +) * 10).rounded()),
            si: Int(((daySi ?? -1) * 10).rounded()),
            hf: hfVagalTrusted ? 1 : 0,
            workouts: workoutWindows.count,
            sleep: sleepState.count + sleepState.reduce(0) { $0 &+ $1.0 &+ $1.1 },
            vitals: (skinElevated ? 1 : 0) * 2 + (respElevated ? 1 : 0),
            priorDays: priorCalmDayCount)
        return analyzeCache.value(key) {
            analyzeUncached(hr: hr, rr: rr, tzOffsetSeconds: tzOffsetSeconds,
                            motion: motion, steps: steps, sedentaryBouts: sedentaryBouts,
                            wakingStart: wakingStart, wakingEnd: wakingEnd,
                            priorCalmHrs: priorCalmHrs, daySi: daySi,
                            hfVagalTrusted: hfVagalTrusted, workoutWindows: workoutWindows,
                            sleepState: sleepState, skinElevated: skinElevated,
                            respElevated: respElevated, priorCalmDayCount: priorCalmDayCount)
        }
    }

    private struct StressKey: Hashable {
        let hr: StreamFingerprint; let rr: StreamFingerprint
        let motion: StreamFingerprint; let steps: StreamFingerprint
        let sedentary: Int; let tz: Int; let waking: Int
        let prior: Int; let si: Int; let hf: Int; let workouts: Int
        let sleep: Int; let vitals: Int; let priorDays: Int
    }
    private static let analyzeCache = AnalyticsMemoCache<StressKey, Result>(capacity: 8)

    private static func analyzeUncached(hr: [HRSample], rr: [RRInterval],
                                        tzOffsetSeconds: Int,
                                        motion: [WorkoutDetector.ActivityPoint],
                                        steps: [StepSample],
                                        sedentaryBouts: [InactivityPeriod],
                                        wakingStart: Int,
                                        wakingEnd: Int,
                                        priorCalmHrs: [Double],
                                        daySi: Double?,
                                        hfVagalTrusted: Bool,
                                        workoutWindows: [(Int, Int)],
                                        sleepState: [(Int, Int)],
                                        skinElevated: Bool,
                                        respElevated: Bool,
                                        priorCalmDayCount: Int) -> Result {
        let usable = hr.filter { $0.bpm >= minPlausibleBpm && $0.bpm <= maxPlausibleBpm }
        guard !usable.isEmpty else { return .empty }

        var asleepCountByBucket: [Int: Int] = [:]
        for (ts, state) in sleepState where state == sleepStateAsleep {
            let local = ts + tzOffsetSeconds
            let bucket = floorDiv(local, bucketSeconds) * bucketSeconds
            asleepCountByBucket[bucket, default: 0] += 1
        }
        func bandAsleep(_ bucketLocal: Int) -> Bool {
            (asleepCountByBucket[bucketLocal] ?? 0) >= 3
        }
        func waking(_ bucketLocal: Int) -> Bool {
            let h = localHourOfDay(bucketLocal)
            if h < wakingStart || h >= wakingEnd { return false }
            if bandAsleep(bucketLocal) { return false }
            return true
        }
        func inWorkout(_ wallMid: Int) -> Bool {
            workoutWindows.contains { wallMid >= $0.0 && wallMid <= $0.1 }
        }

        var hrByBucket: [Int: [HRSample]] = [:]
        for s in usable {
            let local = s.ts + tzOffsetSeconds
            let bucket = floorDiv(local, bucketSeconds) * bucketSeconds
            hrByBucket[bucket, default: []].append(s)
        }
        var rrByBucket: [Int: [Double]] = [:]
        for s in rr {
            let local = s.ts + tzOffsetSeconds
            let bucket = floorDiv(local, bucketSeconds) * bucketSeconds
            rrByBucket[bucket, default: []].append(Double(s.rrMs))
        }
        var motionByBucket: [Int: [Double]] = [:]
        for p in motion {
            let local = p.ts + tzOffsetSeconds
            let bucket = floorDiv(local, bucketSeconds) * bucketSeconds
            motionByBucket[bucket, default: []].append(p.intensity)
        }
        var stepClassByBucket: [Int: [Int]] = [:]
        for s in steps {
            guard let cls = s.activityClass else { continue }
            let local = s.ts + tzOffsetSeconds
            let bucket = floorDiv(local, bucketSeconds) * bucketSeconds
            stepClassByBucket[bucket, default: []].append(cls)
        }

        struct HourAgg {
            let bucket: Int
            let meanHR: Double?
            let rmssd: Double?
            let motionBusy: Bool
            let sedentaryCalm: Bool
            let workoutOverlap: Bool
            let bandAsleep: Bool
        }
        let orderedBuckets = hrByBucket.keys.sorted()
        var aggs: [HourAgg] = []
        aggs.reserveCapacity(orderedBuckets.count)
        for b in orderedBuckets {
            let hrs = Array(Dictionary(grouping: hrByBucket[b] ?? [], by: \.ts).compactMap { $0.value.first })
            let span = (hrs.map(\.ts).max() ?? 0) - (hrs.map(\.ts).min() ?? 0)
            let motionMean = mean(motionByBucket[b] ?? []) ?? 0
            let classes = stepClassByBucket[b] ?? []
            let walkRun = classes.filter { $0 == stepClassWalk || $0 == stepClassRun }.count
            let stillN = classes.filter { $0 == stepClassStill }.count
            // Strict walk/run majority (not tie) — twin of Android 8.6.77.
            let stepBusy = !classes.isEmpty && walkRun > stillN && walkRun >= 3
            let stepStill = !classes.isEmpty && stillN > walkRun
            let motionBusy = motionMean >= motionBusyFloor || stepBusy
            let wallMid = (b - tzOffsetSeconds) + bucketSeconds / 2
            let inSedentary = sedentaryBouts.contains { wallMid >= $0.start && wallMid <= $0.end }
            let sedentaryCalm = inSedentary || stepStill
            let workoutOverlap = inWorkout(wallMid)
            let asleep = bandAsleep(b)
            let quiet: Double?
            if hrs.count >= minHourHRSamplesSparse && span >= minHourHRSpanSeconds {
                quiet = quietHourHR(hrs.map { Double($0.bpm) }, motionBusy: motionBusy)
            } else {
                quiet = nil
            }
            let rrRes = HRVAnalyzer.analyze(
                rawRR: rrByBucket[b] ?? [],
                ectopicThreshold: motionBusy ? ectopicMotionThreshold : nil)
            aggs.append(HourAgg(bucket: b, meanHR: quiet, rmssd: rrRes.rmssd,
                                motionBusy: motionBusy, sedentaryCalm: sedentaryCalm,
                                workoutOverlap: workoutOverlap, bandAsleep: asleep))
        }

        let referenceAggs = aggs.filter { waking($0.bucket) }
        let hrMeans = referenceAggs.compactMap { $0.meanHR }
        let overnightQuiet = calmReference(
            aggs.filter { !waking($0.bucket) }.compactMap { $0.meanHR },
            calmIsLow: true)
        let prior = Array(priorCalmHrs
            .filter { $0 >= Double(minPlausibleBpm) && $0 <= Double(maxPlausibleBpm) }
            .suffix(priorCalmBlendMax))
        let calmPool = hrMeans + prior
        let rmssdVals = referenceAggs.compactMap { $0.rmssd }
        let refHR = calmReference(calmPool.isEmpty ? hrMeans : calmPool, calmIsLow: true)
        let refRMSSD = calmReference(rmssdVals, calmIsLow: false)
        let sdPool = hrMeans.isEmpty ? calmPool : hrMeans
        let sdHR = max(std(sdPool, mean: mean(sdPool)), 3.0)
        let sdRMSSD = max(std(rmssdVals, mean: mean(rmssdVals)), 5.0)
        let priorDays = min(max(priorCalmDayCount, 0), priorCalmBlendMax)

        var points: [HourPoint] = []
        points.reserveCapacity(aggs.count)
        for a in aggs {
            let hourOfDay = localHourOfDay(a.bucket)
            let wallStart = a.bucket - tzOffsetSeconds
            let level: Double?
            if let quiet = a.meanHR {
                var raw = rawScore(hr: quiet, meanHR: refHR, sdHR: sdHR,
                                   rmssd: a.rmssd, meanRMSSD: refRMSSD, sdRMSSD: sdRMSSD)
                if !waking(a.bucket) || a.bandAsleep { raw -= nightCalmBias }
                if a.motionBusy { raw -= motionBusyDamp }
                if a.sedentaryCalm { raw -= sedentaryCalmBias }
                if waking(a.bucket), !a.motionBusy, let overnight = overnightQuiet,
                   quiet <= overnight + overnightAnchorSlackBpm {
                    raw -= overnightCalmBias
                }
                if waking(a.bucket), let si = daySi {
                    if si < baevskyCalmSi { raw -= baevskyCalmBias }
                    else if si > baevskyHighSi { raw += baevskyHighBump }
                }
                if waking(a.bucket), hfVagalTrusted { raw -= hfCalmBias }
                if a.workoutOverlap { raw -= workoutOverlapBias }
                if waking(a.bucket), skinElevated { raw += skinElevatedBias }
                if waking(a.bucket), respElevated { raw += respElevatedBias }
                level = squash(raw)
            } else {
                level = nil
            }
            points.append(HourPoint(hour: hourOfDay, startTs: wallStart,
                                    level: level, meanHR: a.meanHR, rmssd: a.rmssd,
                                    motionBusy: a.motionBusy))
        }

        let wakingScored = points.compactMap { p -> (HourPoint, Double)? in
            guard let lvl = p.level,
                  p.hour >= wakingStart && p.hour < wakingEnd else { return nil }
            let wallMid = p.startTs + bucketSeconds / 2
            if inWorkout(wallMid) { return nil }
            let localBucket = p.startTs + tzOffsetSeconds
            let bucket = floorDiv(localBucket, bucketSeconds) * bucketSeconds
            if bandAsleep(bucket) { return nil }
            return (p, lvl)
        }
        guard !wakingScored.isEmpty || points.contains(where: { $0.level != nil }) else {
            return points.isEmpty ? .empty
                : Result(hours: points, sustainedHigh: false, sustainedRun: 0,
                         dayMean: nil, peak: nil, priorCalmDayCount: priorDays)
        }

        var run = 0
        for (_, lvl) in wakingScored.reversed() {
            if lvl >= highBandFloor { run += 1 } else { break }
        }
        let sustained = run >= sustainedBuckets

        let wakingLevels = points.compactMap { p -> Double? in
            guard let l = p.level, p.hour >= wakingStart && p.hour < wakingEnd else { return nil }
            return l
        }
        let dayMean = mean(wakingLevels.isEmpty ? points.compactMap(\.level) : wakingLevels)
        let peak = points.compactMap { p -> (HourPoint, Double)? in
            guard let l = p.level, p.hour >= wakingStart && p.hour < wakingEnd else { return nil }
            return (p, l)
        }.max { $0.1 < $1.1 }?.0
            ?? points.compactMap { p -> (HourPoint, Double)? in
                guard let l = p.level else { return nil }
                return (p, l)
            }.max { $0.1 < $1.1 }?.0

        return Result(hours: points, sustainedHigh: sustained, sustainedRun: run,
                      dayMean: dayMean, peak: peak, priorCalmDayCount: priorDays)
    }

    // MARK: - Helpers

    static func floorDiv(_ a: Int, _ b: Int) -> Int {
        let q = a / b, r = a % b
        return (r != 0 && (r < 0) != (b < 0)) ? q - 1 : q
    }

    /// Local clock hour 0–23 from a local-epoch bucket start (works for any bucketSeconds).
    public static func localHourOfDay(_ bucketLocal: Int) -> Int {
        floorDiv(((bucketLocal % 86_400) + 86_400) % 86_400, 3_600)
    }

    static func isWakingBucket(_ bucketLocal: Int) -> Bool {
        let h = localHourOfDay(bucketLocal)
        return h >= wakingStartHour && h < wakingEndHour
    }

    /// Whether a local hour-bucket start falls inside the waking window.
    static func isWakingHour(_ bucket: Int) -> Bool { isWakingBucket(bucket) }

    static func quietHourHR(_ bpms: [Double], motionBusy: Bool = false) -> Double? {
        guard !bpms.isEmpty else { return nil }
        let pool: [Double]
        if motionBusy && bpms.count >= 8 {
            let mid = bpms.sorted()[bpms.count / 2]
            let lower = bpms.filter { $0 <= mid }
            pool = lower.isEmpty ? bpms : lower
        } else {
            pool = bpms
        }
        guard pool.count >= 4 else { return mean(pool) }
        let q: Double
        if motionBusy && pool.count >= 16 {
            q = 0.05
        } else if pool.count >= 20 {
            q = 0.10
        } else {
            q = 0.25
        }
        return quantile(pool.sorted(), q)
    }

    static func calmReference(_ xs: [Double], calmIsLow: Bool) -> Double? {
        guard !xs.isEmpty else { return nil }
        guard xs.count >= 4 else { return mean(xs) }
        let s = xs.sorted()
        return calmIsLow ? quantile(s, 0.25) : quantile(s, 0.75)
    }

    static func quantile(_ sorted: [Double], _ q: Double) -> Double {
        let n = sorted.count
        guard n > 0 else { return 0 }
        if n == 1 { return sorted[0] }
        let pos = q * Double(n - 1)
        let lo = Int(pos), hi = min(lo + 1, n - 1)
        let frac = pos - Double(lo)
        return sorted[lo] + frac * (sorted[hi] - sorted[lo])
    }
}
