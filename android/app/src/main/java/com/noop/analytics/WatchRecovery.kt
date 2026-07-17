package com.noop.analytics

/**
 * Recovery/Charge from DAILY aggregates (Apple Watch / Health Connect / an Oura/Fitbit/Garmin export).
 * Kotlin twin of the Swift `WatchRecovery`.
 *
 * A WHOOP strap gives dense overnight R-R intervals, so RecoveryScorer runs off raw-derived nightly RMSSD.
 * A daily-aggregate source does NOT: it gives a daily HRV (SDNN-ish) reading plus a resting HR. So this is
 * a genuinely lower-density computation.
 *
 * We do NOT invent a new formula. Recovery is HRV-and-RHR-vs-personal-baseline, and because every term is
 * relative to the person's OWN baseline, the metric scale cancels out: SDNN-vs-SDNN-baseline behaves like
 * RMSSD-vs-RMSSD-baseline. So we build HRV and RHR baselines through the existing [Baselines] machinery and
 * feed them straight into the SAME [RecoveryScorer.recovery] the strap uses. Source-only recovery and strap
 * recovery therefore land on the same 0-100 scale and read against the same bands.
 *
 * What we drop vs the strap path: the respiration, sleep-performance and skin-temp terms are not supplied
 * here, so RecoveryScorer renormalises the remaining HRV + RHR weights. The HRV term stays dominant.
 *
 * Honesty rule: return null recovery + CALIBRATING when today's HRV is missing, OR the HRV baseline isn't
 * usable yet (same [Baselines.minNightsSeed] gate as strap Charge). We NEVER fabricate a number. Thin
 * history (seed..<~7 nights) still scores, but [ScoreConfidence.forCharge] marks it BUILDING — that is
 * the warm-up honesty, not a second longer null gate (Fable Rest #8 unify).
 */
object WatchRecovery {

    /** Result: the score (null while calibrating) and its confidence tier. */
    data class Result(val recovery: Double?, val confidence: ScoreConfidence)

    /**
     * Minimum nights of HRV history before we score recovery from a daily-aggregate source.
     * Unified with strap Charge ([Baselines.minNightsSeed] = 4). Sparse daily HRV still warms up
     * honestly via BUILDING confidence until [ScoreConfidence.buildingNightsThreshold], not by
     * staying blank until night 7. Mirrors the Swift constant.
     */
    const val minBaselineNights = Baselines.minNightsSeed

    /**
     * Compute recovery/Charge from a daily HRV + resting HR vs the person's own baseline.
     *
     * @param todayHrv today's HRV reading (ms), or null if the source logged none.
     * @param todayRhr today's resting HR (bpm), or null to drop the RHR term.
     * @param hrvHistory ordered nightly HRV values (oldest -> newest), the baseline input.
     * @param rhrHistory ordered nightly resting-HR values (oldest -> newest).
     */
    fun compute(
        todayHrv: Double?,
        todayRhr: Int?,
        hrvHistory: List<Double>,
        rhrHistory: List<Double>,
    ): Result {
        // Build both baselines through the production model (Winsorized EWMA + cold-start gating), exactly
        // as the strap path does. HRV feeds the HRV config; resting HR feeds the RHR config.
        val hrvBase = Baselines.foldHistory(hrvHistory, Baselines.hrvCfg)
        val rhrBase = Baselines.foldHistory(rhrHistory, Baselines.restingHRCfg)

        // Honesty gate: same seed as strap Charge — today's HRV + usable baseline (+ history length
        // matching the seed). Thin weeks score with BUILDING confidence, never a fabricated value.
        if (todayHrv == null || !hrvBase.usable || hrvHistory.size < minBaselineNights) {
            return Result(recovery = null, confidence = ScoreConfidence.CALIBRATING)
        }

        // Reuse the canonical Charge engine. Drop the resp / sleep / skin-temp terms (the daily aggregate
        // doesn't carry them here) -> RecoveryScorer renormalises to HRV + RHR. RHR is optional: a missing
        // resting HR today passes the at-baseline value (z~0, neutral term) and drops the RHR term entirely.
        val recovery = RecoveryScorer.recovery(
            hrv = todayHrv,
            rhr = todayRhr?.toDouble() ?: rhrBase.baseline,
            resp = null,
            hrvBaseline = hrvBase,
            rhrBaseline = if (todayRhr != null) rhrBase else null,
            respBaseline = null,
            sleepPerf = null,
        ) ?: return Result(recovery = null, confidence = ScoreConfidence.CALIBRATING)

        // Confidence from the REAL score + baseline density (calibrating / building / solid) — same
        // helper the strap Charge uses.
        val conf = ScoreConfidence.forCharge(recovery, hrvBase)
        return Result(recovery = recovery, confidence = conf)
    }
}
