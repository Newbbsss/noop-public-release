package com.noop.analytics

import com.noop.data.HrSample
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

/*
 * StrainScorer.kt — cardiovascular load (NOOP "Effort") on a 0–100 logarithmic scale.
 *
 * Faithful Kotlin port of StrandAnalytics/StrainScorer.swift (verified on macOS),
 * itself ported from server/ingest/app/analysis/strain.py. INDEPENDENT implementation
 * of published exercise-physiology methods (WHOOP-*like*, not a reproduction of the
 * proprietary algorithm; not medical advice).
 *
 * SCALE: the internal metric key stays `strain`, but the published axis is now 0–100
 * ("Effort"). This is a pure RESCALE — `maxStrain` went 21.0 → 100.0 while the
 * denominator D = 7201 is UNCHANGED, so the log curve and its saturation point
 * (TRIMP 7200 ≈ max) are preserved: a max-Effort day stays exactly as rare as a 21.0
 * day was. trimpToStrain now returns 0–100.
 *
 * Pipeline:
 *   1. Heart-Rate Reserve (Karvonen): HRR = HRmax − RHR.
 *   2. Per-sample intensity as %HRR = (HR − RHR) / HRR × 100, clamped 0..100.
 *   3. TRIMP accumulated over the window:
 *        a. Edwards 5-zone summation (default): sample contributes its zone weight
 *           (1..5 at 50/60/70/80/90 %HRR cut-offs) × duration.
 *        b. Banister exponential: sample contributes duration × x × 0.64 × e^(b·x).
 *   4. Logarithmic compression onto [0, 100]:
 *        effort = 100 × ln(TRIMP + 1) / ln(D),  D = STRAIN_DENOMINATOR.
 *
 * References: Karvonen 1957 (%HRR); Edwards 1993 (5-zone TRIMP); Banister 1991
 * (exponential TRIMP, b = 1.92 men / 1.67 women); Tanaka 2001 (HRmax = 208 − 0.7×age).
 *
 * Operates on the Room [HrSample] (ts:Long unix seconds, bpm:Int). The HRR-based
 * zone math here is INDEPENDENT of the %HRmax display zones in [HrZones]; this port
 * uses [HrZones] only where the Swift used HRZones (none in this file — strain has
 * its own Edwards %HRR thresholds).
 */
object StrainScorer {

    // ---- Constants (strain.py) ----

    /** Minimum HR readings before computing strain on a DENSE stream (≈10 min at 1 Hz). */
    const val minReadings: Int = 600
    /**
     * Sparse-stream acceptance (#482/#480): a low-cadence strap — the WHOOP 5/MG sends live
     * standard HR only ~every 30 s — would need ~5 h of continuous wear to reach [minReadings], so
     * Effort sat un-scored (null → a stale prior-day value on the gauge) for most of the day. Also
     * accept once the HR series SPANS at least [minSpanSeconds] of wall-clock with a small sample
     * floor. This never fabricates load: TRIMP still integrates honestly, so a genuine low-HR day
     * scores 0 either way — it just lets the live gauge reflect TODAY. A dense 1 Hz stream is
     * unaffected (it clears [minReadings] first).
     */
    const val minSparseReadings: Int = 20
    /** Wall-clock coverage (seconds) qualifying a sparse stream. 600 s = 10 min, matching the dense
     *  gate's ≈10 min of 600 × 1 Hz samples, so both cadences trust the number at the same age. */
    const val minSpanSeconds: Int = 600
    /** Live wrist PPG outside this range is a transport/artifact candidate, not Effort evidence. */
    const val minPlausibleBpm: Int = 30
    const val maxPlausibleBpm: Int = 220
    /** A gap beyond this splits the stream into separate worn segments (Fable #322: it used to NULL
     *  the whole day; a gap can only under-count TRIMP, never fabricate, so segmenting is honest). */
    const val maxSampleGapSeconds: Long = 90L

    /** Top of the Effort scale (was 21.0 — rescaled to 0–100 for "Effort"). */
    const val maxStrain: Double = 100.0

    /**
     * Logarithmic-map denominator D. Chosen so the Edwards daily ceiling
     * (top zone weight 5 sustained 24 h = 7200) maps to exactly maxStrain:
     * D = 7200 + 1 = 7201 makes ln(7201)/ln(7201) = 1, so the curve shape and
     * its saturation point are independent of maxStrain (the 21→100 rescale is a
     * pure linear scaling of the whole curve).
     */
    const val strainDenominator: Double = 7201.0
    val lnStrainDenominator: Double get() = ln(strainDenominator)

    /** Fallback per-sample duration (minutes) — 1 s at 1 Hz. */
    const val fallbackSampleMin: Double = 1.0 / 60.0

    const val defaultAge: Int = 30
    const val defaultRestingHR: Double = 60.0

    /** Minimum HR samples before the observed high-percentile HRmax is trusted. */
    const val hrmaxMinSamples: Int = 600

    /** Upper percentile for the observed-HRmax estimate. */
    const val hrmaxPercentile: Double = 99.5

    /** Banister coefficients. */
    const val banisterScale: Double = 0.64
    const val banisterBMen: Double = 1.92
    const val banisterBWomen: Double = 1.67

    /**
     * Edwards zone cut-offs as (%HRR threshold, weight), highest-first.
     * No weight below **60% HRR** (classic Edwards zone-1 at 50% dropped): ambulatory daytime HR
     * on rest/walk mornings was minting Effort ~40 while WHOOP Strain stayed ~0.1 with 0:00 zone
     * time (Fold compare 2026-07-17). Easy days then sit on the steps movement floor instead.
     */
    val edwardsZones: List<Pair<Double, Int>> = listOf(
        90.0 to 5, 80.0 to 4, 70.0 to 3, 60.0 to 2,
    )
    /** Minimum %HRR that contributes any Edwards weight. */
    const val edwardsMinPctHrr: Double = 60.0

    /** TRIMP accumulation method. */
    enum class Method { EDWARDS, BANISTER }

    /** Strain calibration / fit errors. Mirrors Swift `StrainError`. */
    enum class StrainError { TOO_FEW_PAIRS, DEGENERATE }

    /** Thrown by [fitStrainDenominator] when the fit is impossible. */
    class StrainException(val error: StrainError) : Exception("Strain fit failed: $error")

    // ---- HRmax helpers ----

    /** Tanaka (2001): HRmax = 208 − 0.7 × age (gender-independent). */
    fun tanakaHRmax(age: Double): Double = 208.0 - 0.7 * age

    /** Classic 220 − age. Last-resort fallback only. */
    fun defaultMaxHR(age: Int = defaultAge): Int = 220 - age

    /** Linear-interpolated percentile of an already-sorted sequence (numpy-style). */
    fun percentile(sortedValues: List<Double>, pct: Double): Double {
        val n = sortedValues.size
        if (n == 0) return 0.0
        if (n == 1) return sortedValues[0]
        val position = (pct / 100.0) * (n - 1).toDouble()
        val lower = position.toInt()
        val upper = minOf(lower + 1, n - 1)
        val frac = position - lower.toDouble()
        return sortedValues[lower] + frac * (sortedValues[upper] - sortedValues[lower])
    }

    /**
     * Estimate a personalized HRmax from a trailing HR series.
     * Returns (hrmax bpm, source) where source ∈ {"observed", "tanaka", "unknown"}.
     */
    fun estimateHRmax(hrHistory: List<Double>, age: Double?): Pair<Double, String> {
        val n = hrHistory.size
        val tanaka = age?.let { tanakaHRmax(it) }

        if (n >= hrmaxMinSamples) {
            val observed = percentile(hrHistory.sorted(), hrmaxPercentile)
            if (tanaka == null) return observed to "observed"
            return if (observed >= tanaka) observed to "observed" else tanaka to "tanaka"
        }
        if (tanaka != null) return tanaka to "tanaka"
        return 0.0 to "unknown"
    }

    // ---- Karvonen %HRR and Edwards zone weight ----

    /** Karvonen %HRR, clamped [0, 100]. */
    fun pctHRR(bpm: Double, restingHR: Double, hrReserve: Double): Double {
        val pct = (bpm - restingHR) / hrReserve * 100.0
        if (pct < 0) return 0.0
        if (pct > 100) return 100.0
        return pct
    }

    /**
     * Edwards 5-zone weight (0–5) from %HRR (unclamped; extremes agree with
     * the clamped path at both ends).
     */
    fun zoneWeight(bpm: Double, restingHR: Double, hrReserve: Double): Int {
        val pct = (bpm - restingHR) / hrReserve * 100.0
        for ((threshold, weight) in edwardsZones) {
            if (pct >= threshold) return weight
        }
        return 0
    }

    // ---- TRIMP accumulation ----

    /**
     * Infer per-sample duration (minutes) from the first two timestamps. Falls
     * back to 1 s when fewer than two samples or coincident timestamps.
     */
    fun sampleDurationMinutes(hr: List<HrSample>): Double {
        if (hr.size < 2) return fallbackSampleMin
        val deltaS = abs((hr[1].ts - hr[0].ts).toDouble())
        return if (deltaS > 0) deltaS / 60.0 else fallbackSampleMin
    }

    fun edwardsTRIMP(
        hr: List<HrSample>,
        restingHR: Double,
        hrReserve: Double,
        sampleDurationMin: Double,
    ): Double {
        var weighted = 0
        for (s in hr) {
            weighted += zoneWeight(s.bpm.toDouble(), restingHR, hrReserve)
        }
        return weighted.toDouble() * sampleDurationMin
    }

    fun banisterTRIMP(
        hr: List<HrSample>,
        restingHR: Double,
        hrReserve: Double,
        sampleDurationMin: Double,
        b: Double,
    ): Double {
        var acc = 0.0
        for (s in hr) {
            val x = pctHRR(s.bpm.toDouble(), restingHR, hrReserve) / 100.0
            if (x > 0) acc += sampleDurationMin * x * banisterScale * exp(b * x)
        }
        return acc
    }

    // ---- Logarithmic map ----

    /**
     * Map accumulated TRIMP onto [0, 100] via 100 × ln(TRIMP+1) / ln(D), 2 dp.
     * TRIMP ≤ 0 → 0.
     */
    fun trimpToStrain(trimp: Double, denominator: Double = strainDenominator): Double {
        if (trimp <= 0) return 0.0
        val value = maxStrain * ln(trimp + 1.0) / ln(denominator)
        return (value * 100).roundToLong() / 100.0
    }

    // ---- Denominator calibration ----

    /**
     * Calibrate D from (TRIMP, reference_strain) pairs via the through-origin
     * least-squares line: ln(D) = maxStrain × Σ(x²) / Σ(xy), x = ln(TRIMP+1).
     * Reference strains are on the maxStrain (0–100) scale. Throws [StrainException]
     * when fewer than 2 usable pairs (TRIMP>0, strain>0) or degenerate.
     */
    fun fitStrainDenominator(pairs: List<Pair<Double, Double>>): Double {
        val usable = pairs.filter { it.first > 0 && it.second > 0 }
        if (usable.size < 2) throw StrainException(StrainError.TOO_FEW_PAIRS)
        var sumXX = 0.0
        var sumXY = 0.0
        for ((trimp, strain) in usable) {
            val x = ln(trimp + 1.0)
            sumXX += x * x
            sumXY += x * strain
        }
        if (!(sumXY > 0 && sumXX > 0)) throw StrainException(StrainError.DEGENERATE)
        return exp(maxStrain * sumXX / sumXY)
    }

    // ---- Steps floor (Gilbert P0 2026-07-17; retuned WHOOP-like; kcal removed 2026-07-17b) ----
    //
    // Docs (ANALYTICS.md / Scoring Guide) promise that a long walk with little cardio still moves
    // Effort via a movement-derived floor. Cardio TRIMP stays primary when higher; the floor only
    // raises calm/zero walks. Shape is CONVEX in steps (small at low–mid daily steps, steeper as
    // steps accumulate) so a desk/rest morning stays near 0 like WHOOP Strain ~0.1, while a very
    // high step day can matter more — without the old near-linear climb that hit ~22 at ~12k steps.
    //
    // activeKcalEst in DailyMetric is whole-day active+resting energy (often ~1.5–2k) — feeding it
    // into the floor saturated the cap every calm day. Floor is **steps-only** until true active
    // kcal exists.

    /** Steps below this contribute 0 to the movement floor (desk / noise). */
    const val movementStepsNoiseFloor: Int = 5_000
    /** @deprecated Kept for API compat; kcal no longer raises the floor (total-day kcal was wrong). */
    const val movementKcalNoiseFloor: Double = 250.0
    /** Hard cap — steps alone never invent high cardio Effort. Moderate days land nearer ~11. */
    const val movementFloorCap: Double = 12.0
    /**
     * Excess-steps scale for the convex curve: floor ≈ cap × (excess / scale)^exponent,
     * clipped to [0, cap]. ~22k excess (~27k total steps) approaches the cap.
     */
    const val movementStepsScale: Double = 22_000.0
    /** Convex exponent (>1): accelerating contribution as steps accumulate. */
    const val movementStepsExponent: Double = 1.85
    const val movementKcalScale: Double = 1_000.0
    const val movementKcalExponent: Double = 1.85

    /**
     * Movement-derived Effort floor (0…[movementFloorCap]) from band steps.
     * Band / day steps only — never phone steps as primary (caller must pass strap or estimate).
     * Rest / desk morning (under noise floors) → 0. Mid walk (~10–12k) → low single digits.
     * High step days climb toward ~11–12 (was ~22); cardio TRIMP still wins when higher.
     *
     * [activeKcal] is accepted for call-site compat but **ignored** — `DailyMetric.activeKcalEst`
     * is whole-day energy, not active-only, and was pinning Effort near the cap on rest mornings.
     */
    fun movementFloor(steps: Int?, @Suppress("UNUSED_PARAMETER") activeKcal: Double?): Double {
        fun convex(excess: Double, scale: Double, exponent: Double): Double {
            if (excess <= 0.0 || scale <= 0.0) return 0.0
            val u = (excess / scale).coerceAtLeast(0.0)
            val shaped = u.pow(exponent).coerceAtMost(1.0)
            return movementFloorCap * shaped
        }
        val fromSteps = if (steps == null || steps < movementStepsNoiseFloor) {
            0.0
        } else {
            convex((steps - movementStepsNoiseFloor).toDouble(), movementStepsScale, movementStepsExponent)
        }
        val raw = fromSteps.coerceIn(0.0, movementFloorCap)
        return (raw * 100.0).roundToLong() / 100.0
    }

    /**
     * Combine cardio TRIMP Effort with the steps/kcal floor: `max(trimp, floor)`.
     * - Both absent / zero → null when [trimpEffort] is null and floor is 0 (honest no-data).
     * - Floor alone (walk, thin HR) → non-null floor.
     * - TRIMP wins when higher.
     */
    fun withMovementFloor(trimpEffort: Double?, steps: Int?, activeKcal: Double?): Double? {
        val floor = movementFloor(steps, activeKcal)
        if (trimpEffort == null) return if (floor > 0.0) floor else null
        return maxOf(trimpEffort, floor)
    }

    // ---- Public API ----

    /**
     * Cardiovascular Effort (0–100) from an HR series. APPROXIMATE.
     *
     * Returns null when there isn't yet enough data to trust the number — fewer than [minReadings]
     * samples AND less than [minSpanSeconds] of HR coverage (the sparse-strap path, #482) — or when
     * maxHR ≤ restingHR (invalid HRR).
     *
     * Does **not** apply the steps/kcal floor — callers that have day steps / active kcal should
     * wrap with [withMovementFloor] (see [AnalyticsEngine.analyzeDay] and live Today).
     *
     * @param hr time-ordered [HrSample] list.
     * @param maxHR HRmax (bpm). Defaults to 220 − defaultAge when null.
     * @param restingHR resting HR (bpm) for the HRR denominator (default 60).
     * @param method [Method.EDWARDS] (default) or [Method.BANISTER].
     * @param sex "male"/"female" — selects the Banister coefficient (ignored by Edwards).
     * @param denominator log-map D (default [strainDenominator]).
     */
    fun strain(
        hr: List<HrSample>,
        maxHR: Double? = null,
        restingHR: Double = defaultRestingHR,
        method: Method = Method.EDWARDS,
        sex: String = "male",
        denominator: Double = strainDenominator,
    ): Double? {
        val effMax = maxHR ?: defaultMaxHR().toDouble()
        // Do not let duplicate timestamps or implausible PPG values fabricate load.
        val usable = hr.asSequence()
            .filter { it.bpm in minPlausibleBpm..maxPlausibleBpm }
            .sortedBy { it.ts }
            .distinctBy { it.ts }
            .toList()
        // Disconnected gaps (Fable #322): a gap > maxSampleGapSeconds used to NULL the WHOLE day —
        // one 2-min BLE dropout (or a shower) blanked Effort for a full day of 1 Hz wear, while
        // WHOOP happily scored it (the Fold 2026-07-10..12 rows: 70k+ HR samples/day, strain=null).
        // Nulling was over-defensive: TRIMP is per-sample × cadence, so a gap can only UNDER-count,
        // never fabricate. Segment at the gaps instead and integrate each worn stretch with its OWN
        // inferred cadence; the gaps contribute exactly zero. A gapless stream is one segment —
        // byte-identical to the old path.
        val segments = ArrayList<List<HrSample>>()
        var segStart = 0
        for (i in 1..usable.size) {
            if (i == usable.size || usable[i].ts - usable[i - 1].ts > maxSampleGapSeconds) {
                segments.add(usable.subList(segStart, i))
                segStart = i
            }
        }
        // Enough data to trust the score: a dense stream (≥ minReadings) OR a sparse-but-sustained
        // one covering ≥ minSpanSeconds with a sample floor (#482 — the 5/MG's ~30 s HR cadence).
        // Coverage sums WORN segment spans only, so a disconnected day can't count its holes.
        val enoughData = when {
            usable.size >= minReadings -> true
            usable.size >= minSparseReadings -> {
                segments.sumOf { seg -> if (seg.size < 2) 0L else seg.last().ts - seg.first().ts } >= minSpanSeconds
            }
            else -> false
        }
        if (!enoughData || effMax <= restingHR) return null

        val hrReserve = effMax - restingHR
        var trimp = 0.0
        for (seg in segments) {
            val sampleDur = sampleDurationMinutes(seg)
            trimp += when (method) {
                Method.BANISTER -> {
                    val b = if (sex.lowercase().startsWith("f")) banisterBWomen else banisterBMen
                    banisterTRIMP(seg, restingHR, hrReserve, sampleDur, b)
                }
                Method.EDWARDS -> {
                    edwardsTRIMP(seg, restingHR, hrReserve, sampleDur)
                }
            }
        }
        return trimpToStrain(trimp, denominator)
    }
}
