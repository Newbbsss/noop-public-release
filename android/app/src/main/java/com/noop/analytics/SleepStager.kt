package com.noop.analytics

import com.noop.data.GravitySample
import com.noop.data.HrSample
import com.noop.data.RespSample
import com.noop.data.RrInterval
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/*
 * SleepStager.kt — sleep/wake detection + APPROXIMATE 4-class staging.
 *
 * Faithful Kotlin port of StrandAnalytics/SleepStager.swift (verified on macOS),
 * itself ported from server/ingest/app/analysis/sleep.py and sleep_features.py.
 *
 * HONEST HEDGING: these stages are APPROXIMATIONS, not PSG-validated, not medical
 * advice. The EEG-free 4-class ceiling is ~65–73% epoch agreement (Walch 2019).
 * Light/deep separation is the weakest link — deep-minute estimates are the least
 * reliable output.
 *
 * Pipeline (30 s epochs):
 *   Stage 0  gravity-stillness sleep/wake spine → in-bed sessions. Cole–Kripke
 *            (te Lindert 30 s) computed as a citable cross-check; HR confirms runs.
 *   Stage 1  per-epoch cardiorespiratory features over a rolling 5-min window
 *            (mean HR, DoG-HR variability, RMSSD/SDNN from RR, resp rate + RRV).
 *   Stage 2  transparent percentile-band classifier → {wake, light, deep, rem}.
 *   Stage 3  median smoothing + physiology re-imposition (no early REM, deep in
 *            the first third of the night).
 *
 * NOTE: frequency-domain HRV features (HF, LF/HF) are omitted (no neurokit2/scipy
 * on-device); the parasympathetic-tone signal is RMSSD only. Respiration rate +
 * RRV are derived from the raw 1 Hz resp channel with a simple peak detector. The
 * classifier seam, percentile bands, smoothing, and physiology rules are
 * reproduced exactly.
 *
 * Types:
 *   - The detected-sleep type is [DetectedSleep] (AnalyticsModels.kt), NOT a Room
 *     entity. Stage segments are [StageSegment] (AnalyticsModels.kt, fields var).
 *   - [HypnogramMetrics] (AnalyticsModels.kt) is returned by [hypnogramMetrics].
 *
 * All `ts` / `start` / `end` are wall-clock unix SECONDS (Long); the Swift source
 * uses Int seconds. Math is done in Double throughout, matching the Swift port.
 */
object SleepStager {

    /**
     * Optional profile soft priors for V1 staging (8.6.235). Null fields = no change.
     * Set only around [AnalyticsEngine.analyzeDay] (serialized analyze path); never invents sensors.
     */
    data class SoftPriors(val ageYears: Double? = null, val bmi: Double? = null)

    @Volatile
    var softPriors: SoftPriors = SoftPriors()


    // ── Stage 0 constants (sleep.py) ─────────────────────────────────────────

    /** Per-sample gravity change (g) at/below which a sample is "still". */
    const val gravityStillThresholdG: Double = 0.01

    /** Rolling stillness window (minutes). */
    const val stillWindowMin: Int = 15

    /** Fraction of still samples to call the window-center "sleep". */
    const val stillFraction: Double = 0.70

    /** Data gap (minutes) that always breaks a run. */
    const val maxGapMin: Int = 20

    /** Runs shorter than this (minutes) are absorbed into neighbours. */
    const val mergeMin: Int = 15

    /** A sleep run must exceed this (minutes) to count as a session. */
    const val minSleepMin: Int = 60

    /** Assumed sample interval (seconds) when not inferable. */
    const val defaultIntervalS: Double = 60.0

    // ── Daytime false-sleep guard (#90) ──────────────────────────────────────
    //
    // A long, still, sedentary daytime stretch (reading, a desk, a sofa) is gravity-
    // indistinguishable from a real nap, so the gravity spine alone misclassifies it as
    // sleep. The fix is NOT to drop daytime sleep — real naps are legitimate sessions —
    // but to hold a window whose CENTER falls in the local daytime band to a stricter bar:
    // it must be long enough to be a real nap AND show a genuine cardiac dip (a sedentary
    // stretch keeps a near-baseline HR). Overnight windows are UNCHANGED. Mirrors Swift.

    /** Local hour (inclusive) at which the stricter daytime bar begins. */
    const val daytimeBandStartHour: Int = 11

    /**
     * Local hour (exclusive) at which the stricter daytime bar ends. A window whose center
     * is in [start, end) local hours is "daytime"; everything else is "overnight".
     */
    const val daytimeBandEndHour: Int = 20

    /**
     * A still sleep run that resumes within this gap of an overnight sleep chain is the
     * night's TAIL — a late wake past the daytime-band start, or a brief morning stir then
     * back to sleep — not an isolated daytime nap, so it skips the daytime guard. Without
     * this, a real sleep that ran past ~11:00 local had its tail rejected as a "nap" and the
     * displayed wake time was truncated to late morning (late sleepers / shift workers).
     * Reimplemented from @vulnix0x4's PR #353.
     */
    const val nightContinuationGapMin: Int = 90

    /**
     * A daytime window must run at least this long (minutes) to count — short still daytime
     * stretches are the dominant false-positive and are rejected outright. Aligned with
     * [NapDetector.DEFAULT_MIN_NAP_MIN] (20) plus a small honesty pad; was 90 and silently
     * dropped real 30–70 min naps from Sleep UI + Rest duration.
     */
    const val daytimeMinSleepMin: Int = 25

    /**
     * A daytime window's resting HR (lowest 5-min rolling mean) must be at or below
     * baseline × this to confirm a real cardiac dip. Stricter than the overnight 1.05:
     * a true nap dips BELOW the waking-day median, sedentary stillness does not.
     */
    const val daytimeRestingHRMult: Double = 0.95

    // ── H4 physiological in-bed span cap (#547 / #531 / #509 / tail) ───────────
    //
    // Maximum plausible in-bed span (seconds) for a SINGLE assembled main-sleep run. No real single night
    // runs longer than this: a 12 h+ "sleep" is a bad-clock artefact (a stale/duplicated timestamp range,
    // or a strap that banked one frozen still stretch under a wrong clock) reading as one enormous still
    // block — which then reports a 12 h sleep and poisons Rest / the debt ledger / the headline. 16 h is
    // well above any genuine night yet below the clock-artefact range. A run whose span exceeds this is
    // DROPPED (not silently truncated to 16 h, which would fabricate a wake time): an over-long block is
    // not trustworthy enough to assert a span for at all. Mirrors Swift `maxMainSleepSpanS`.
    const val maxMainSleepSpanS: Long = 16L * 60L * 60L

    // ── H7 morning-stillness nap suppression (#531) ───────────────────────────
    //
    // After a real overnight wake the wrist is often still (sitting with coffee, back in bed scrolling, a
    // sofa) for a stretch that the gravity spine reads as a fresh "nap" — #531's 9 am phantom nap right after
    // the night ended. It is NOT a night-tail continuation (handled by nightContinuationGapMin and exempted),
    // and it can clear the ordinary daytime guard (long + the post-wake HR is still low), so it slipped
    // through. H7 holds a daytime block that BEGINS within morningStillnessWindowMin of the just-detected
    // overnight wake to a STRONGER bar: it must show a genuine SUSTAINED re-onset — a real second sleep dips
    // clearly below the day median, not merely near it. Mirrors Swift.

    /** A daytime block whose onset falls within this many minutes AFTER an overnight chain's wake is treated
     *  as suspected morning residual stillness and held to the stronger re-onset bar below. ~3 h covers the
     *  post-wake window where residual stillness masquerades as a nap; a genuine afternoon nap (hours later)
     *  is past it and faces only the ordinary daytime guard. Mirrors Swift `morningStillnessWindowMin`. (#531) */
    const val morningStillnessWindowMin: Int = 180

    /** The stronger resting-HR bar (× day baseline) a suspected-morning-stillness block must clear to be kept
     *  as a real re-onset. Stricter than the ordinary daytime [daytimeRestingHRMult] (0.95): residual waking
     *  stillness keeps a near-waking HR, so only a block that dips clearly (a true second sleep) survives.
     *  Mirrors Swift `morningReonsetRestingHRMult`. (#531) */
    const val morningReonsetRestingHRMult: Double = 0.90

    /** The persisted v18 BAND sleep_state value that means "asleep" (Interpreter's `(sb>>4)&3`: 0 wake /
     *  1 still / 2 asleep / 3 up). The strap's OWN scored band state — an independent anchor we CONSUME to
     *  confirm a borderline morning re-onset (H7). Mirrors Swift `bandStateAsleep`. (#531 / H8 consume) */
    const val bandStateAsleep: Int = 2

    /** Fraction of a suspected-morning-stillness block's epochs whose persisted band sleep_state must read
     *  "asleep" ([bandStateAsleep]) for the strap's OWN signal to CONFIRM a genuine re-onset and KEEP the
     *  block even when its HR dip is borderline. A real second sleep the strap itself scored asleep is a
     *  strong, honest anchor; a residual-stillness false nap reads "still"/"up", not "asleep". ≥0.6 keeps
     *  this conservative. Mirrors Swift `morningReonsetBandAsleepFrac`. (H8 consume) */
    const val morningReonsetBandAsleepFrac: Double = 0.6

    /**
     * Soft-snap bed/wake to the strap's longest band-asleep stretch when it overlaps the gravity
     * window (Fable Sleep #27/#28). Slack ±90 min; never invents a night without ≥90 min band asleep.
     */
    const val bandBoundSlackSec: Long = 90L * 60L
    const val bandBoundMinAsleepSec: Long = 90L * 60L
    /** Max gap between consecutive asleep samples still counted as one stretch. */
    const val bandBoundSampleGapSec: Long = 5L * 60L

    // ── Primary-bout alignment (WHOOP compare 2026-07-17) ─────────────────────
    //
    // Gravity stillness often keeps a morning still/sit stretch inside the SAME in-bed run as the
    // overnight sleep (coffee, scrolling, residual stillness). Staging correctly labels that tail
    // WAKE, but leaving it inside [start,end] inflates TIB and awake% (Fold: 03:44–12:20 with ~40%
    // awake vs WHOOP's 5:05–9:16 primary bout). Align the scored window to the longest sleep
    // cluster — split on sustained wake — without inventing Deep/REM. Honesty: HR-only / sparse
    // MG motion stages stay approximate; this only fixes the WINDOW.

    /** Sustained wake (minutes) that splits sleep clusters / ends the primary bout. */
    const val primaryBoutSplitWakeMin: Int = 45

    /** Keep up to this many minutes of pre-onset wake as sleep-onset latency pad. */
    const val primaryBoutSolPadMin: Int = 20

    /** Keep up to this many minutes of post-final-sleep wake for a clean wake edge. */
    const val primaryBoutWakePadMin: Int = 10

    /**
     * Rejoin a following sleep cluster when it has at least this many minutes asleep.
     *
     * User reports (WHOOP-class false morning wake ~6:15–6:22 while real wake ~8:15): Cole–Kripke /
     * HR-rise can label a mid-morning stretch as sustained wake (≥ [primaryBoutSplitWakeMin]), so
     * primary-bout keeps only the first cluster and cuts the scored wake early — even though the
     * wearer returned to substantial sleep. Rejoin when that return-to-sleep is real; do **not**
     * invent Deep/REM. Trailing post-final stillness with **no** return sleep still clips (Jul 17).
     */
    const val primaryBoutRejoinMinAsleepMin: Int = 45

    /**
     * Max wake gap (minutes) across which a substantial return-to-sleep may rejoin the primary bout.
     * Longer gaps stay split (true early wake + later nap → nap card / separate cluster).
     */
    const val primaryBoutRejoinGapMaxMin: Int = 90

    /** Seconds in a calendar day (for local-hour-of-day arithmetic). */
    const val secondsPerDay: Long = 86_400L

    /** Floor on the rolling-window size in samples. */
    const val minWindowSamples: Int = 3

    /** A run is HR-confirmed only if mean HR ≤ baseline × this. */
    const val hrSleepBaselineMult: Double = 1.05

    /** Skip HR refinement (trust gravity) when fewer than this many HR samples. */
    const val hrRefineMinSamples: Int = 30

    /** Consecutive sleep epochs required to declare onset.
     *  Was 3 (~90s on the 30s grid). Screenshot nights opened early vs WHOOP when a brief
     *  pre-sleep HR dip satisfied the short persist bar; 4 epochs (~2 min) still opens on a real
     *  descent without inventing stages. */
    const val onsetPersistEpochs: Int = 5

    // ── Off-wrist backstop (#500) ─────────────────────────────────────────────
    //
    // A wrist-OFF stretch reads as perfectly still gravity with no contrary motion, so the
    // gravity spine classifies it as sleep — and because the off-wrist epochs carry zero/missing
    // HR the daytime guard treats them as "missing data" and lets them through (a daytime desk-off
    // strap logged a phantom sleep). The backstop measures OFF-WRIST COVERAGE: while worn the strap
    // emits ~1 Hz HR, so a long CONTIGUOUS gap in the HR samples spanning part of a candidate sleep
    // run is a strong off-wrist proxy that works even when explicit WRIST_OFF events are absent;
    // explicit WRIST_OFF→WRIST_ON intervals (when the store surfaces them) sharpen it. A run is dropped
    // only when that coverage reaches maxOffWristSleepFraction of its duration (the FRACTIONAL rule from
    // j0b-dev's #504), so a real night that over-extends into a SHORT off-wrist tail survives. Independent
    // of the daytime band — off-wrist is off-wrist day OR night, and a night-tail continuation does NOT
    // exempt it. Mirrors Swift.

    /**
     * A contiguous HR-sample gap of at least this many minutes contributes to a candidate run's
     * off-wrist coverage. Sized at maxGapMin so a real worn night (dense ~1 Hz HR, or PPG-derived HR
     * on a 5/MG) contributes ~no gap, but a wrist-off stretch (HR flatlines to no samples) contributes
     * its whole span. The edges of the run count too: a run that begins/ends far from its nearest HR
     * sample is partially uncovered.
     */
    const val offWristHRGapMin: Int = 20

    /**
     * FRACTIONAL off-wrist rejection (#500), design credited to j0b-dev's #504 analysis. A candidate
     * sleep run is dropped ONLY when its off-wrist coverage — the UNION of its long HR-gap spans and
     * any WRIST_OFF→WRIST_ON intervals overlapping it — is at least this fraction of its duration. The
     * earlier guard dropped the WHOLE run on ANY contiguous HR gap or ANY single WRIST_OFF blip, which
     * nuked a real night that over-extended into a SHORT off-wrist morning tail (strap removed shortly
     * after waking) or that contained one stray WRIST_OFF event. 0.5 keeps such a night (<50% off-wrist)
     * while still dropping an all-day desk strap (≈100% gap) or a session genuinely spent off-wrist.
     * Mirrors Swift.
     */
    const val maxOffWristSleepFraction: Double = 0.5

    /**
     * Minimum average HR-stream density for the off-wrist HR-gap proxy to be trusted (#507). The proxy
     * reads a >[offWristHRGapMin]-minute hole in HR as "off the wrist" — valid only when HR is otherwise
     * dense. A WHOOP 4.0's SYNCED night is reconstructed mostly from MOTION with sparse, derived HR, whose
     * natural gaps would otherwise read as off-wrist and wrongly DROP a real night. So if the HR stream
     * averages fewer than one sample per this many seconds, we don't assert off-wrist from gaps at all
     * (WRIST_OFF events still apply). Self-consistent: a night sparse enough to be >50% gap-covered is, by
     * definition, below this density, so it is spared. Measured over the whole stream, so an off-wrist HOLE
     * inside an otherwise dense, worn day (#500) is still caught. Mirrors Swift.
     */
    const val hrDenseSpacingS: Int = 600   // one HR sample per 10 minutes, averaged over the stream

    // ── Sparse-gravity robustness (#308) ──────────────────────────────────────
    //
    // On an un-unlocked WHOOP 5.0 the strap backfills mostly v18/v26 records where gravity is
    // sparse/clumped (~25% coverage), so the gravity-only Stage-0 spine fragments the night at
    // every >maxGapMin gravity gap and detectSleep drops every <minSleepMin fragment — collapsing
    // a ~6 h night to ~1 h. The fix derives the in-bed spine from a sustained low-HR stretch and
    // uses gravity stillness only to REFINE it, but is GATED ENTIRELY behind a "gravity is sparse"
    // condition so dense WHOOP-4.0 nights stay BYTE-IDENTICAL (a 4.0 regression is unacceptable).
    // Mirrors Swift.

    /**
     * Gravity is "sparse" when its timespan covers less than this fraction of the HR-sample
     * timespan. A dense 4.0 night has gravity spanning the whole HR window (≈1.0) and never trips
     * this; a 5.0 backfill clumps gravity into a fraction of the night.
     */
    const val sparseGravitySpanFrac: Double = 0.5

    /**
     * When sparse, HR drives the in-bed spine: an HR sample is "sleep-band" when its bpm ≤
     * baseline × this. Reuses the overnight HR-confirmation multiplier so the band is the same one
     * detectSleep already trusts to confirm a run.
     */
    const val hrSleepBandMult: Double = hrSleepBaselineMult

    /**
     * When sparse, two adjacent sleep runs separated ONLY by a gravity gap up to this many minutes
     * are merged if the intervening HR stays in the sleep band — so a real night is not shredded
     * into sub-minSleepMin fragments by gravity dropouts. Sized at the daytime-nap floor (a real
     * continuous night never has a true >90 min wake bridge mid-sleep).
     */
    const val sparseBridgeGapMin: Int = 90

    // ── HR-only spine for gravity-DEAD holes (Fable #201/#204) ────────────────
    //
    // The #308 sparse branches can only BRIDGE runs the gravity spine already seeded. A night whose
    // gravity coverage is entirely ABSENT over the sleep itself seeds NOTHING: the Fold 2026-07-12
    // night (04:56–12:54, WHOOP 06:21–12:57) carried dense 1 Hz HR + R-R but ZERO gravity records
    // from 04:46 to 15:00, so buildRuns produced zero candidates, the whole real night scored
    // nothing, and Today's Rest read "no scored sleep" against a Sleep tab that plainly had the
    // night. When a long gravity HOLE has DENSE HR, derive candidate sleep runs from sustained
    // sleep-band HR inside the hole alone — the same band the detector already trusts to confirm
    // and to bridge (#308). Every synthesized run still faces the FULL gate ladder (minSleepMin,
    // span cap, HR confirm, off-wrist fraction, daytime/morning guards), so this widens recall on
    // motion-dead nights without loosening any acceptance bar. Off-wrist can't slip in: off-wrist
    // means NO HR, and the spine requires the hole to be HR-dense end to end. Gated on [sparse]
    // so a dense-gravity night stays byte-identical. Mirrors nothing in Swift yet — iOS parity
    // pending (tagged in docs/SLEEP_FACTORS_AND_LITERATURE.md).

    /** A gravity inter-sample gap (or leading/trailing gap vs the HR span) at least this long is a
     *  DEAD hole eligible for the HR-only spine. Well above sparseBridgeGapMin so anything the
     *  bridge could have stitched stays the bridge's job. */
    const val hrOnlyHoleMin: Int = 120

    /** Bucket width (seconds) for classifying a hole's HR into sleep-band / not. 5 min matches the
     *  detector's rolling resting-HR window. */
    const val hrOnlyBucketS: Long = 300L

    /** The lowest bucket-mean HR inside a synthesized run must dip to baseline × this — a real
     *  sleep dips clearly below the day median; a quiet sedentary stretch hovers near it. Same bar
     *  as the daytime guard's genuine-dip multiplier. */
    const val hrOnlyDipMult: Double = daytimeRestingHRMult

    /** Consecutive dip buckets (≤ baseline × [hrOnlyDipMult]) required to OPEN a synthesized run —
     *  15 min of sustained sub-dip HR, the bucket twin of [onsetPersistEpochs]. One transient dip
     *  bucket amid quiet pre-sleep rest must not drag onset an hour early (Fold replay: a lone
     *  03:14 dip vs the ~04:30 sustained descent the night actually began with). */
    const val hrOnlyOnsetPersistBuckets: Int = 4

    /**
     * Candidate sleep runs derived from HR alone inside gravity-DEAD holes. Empty unless [sparse].
     * For each hole ≥ [hrOnlyHoleMin] between consecutive gravity samples (plus the leading/trailing
     * holes against the HR span): bucket the hole's HR into [hrOnlyBucketS] buckets; a bucket is
     * sleep-band when its mean bpm ≤ baseline × [hrSleepBandMult]; contiguous sleep-band buckets
     * (empty-bucket gaps ≤ [maxGapMin] tolerated, an above-band bucket always breaks) form a run.
     * A run must span ≥ [minSleepMin], be HR-dense (mean spacing ≤ [hrDenseSpacingS] — off-wrist
     * emits no HR), and contain a genuine dip (min bucket ≤ baseline × [hrOnlyDipMult]).
     */
    internal fun hrOnlySleepRuns(
        grav: List<GravitySample>, hr: List<HrSample>, baseline: Double?, sparse: Boolean,
    ): List<Period> {
        if (!sparse || baseline == null || grav.isEmpty() || hr.size < hrRefineMinSamples) return emptyList()
        val holeS = hrOnlyHoleMin * 60L
        val gts = grav.map { it.ts }
        val holes = ArrayList<Pair<Long, Long>>()
        if (gts.first() - hr.first().ts >= holeS) holes.add(hr.first().ts to gts.first())
        for (i in 0 until gts.size - 1) {
            if (gts[i + 1] - gts[i] >= holeS) holes.add(gts[i] to gts[i + 1])
        }
        if (hr.last().ts - gts.last() >= holeS) holes.add(gts.last() to hr.last().ts)
        if (holes.isEmpty()) return emptyList()

        val out = ArrayList<Period>()
        val minSpanS = (minSleepMin * 60).toLong()
        val maxEmptyBuckets = (maxGapMin * 60L / hrOnlyBucketS).toInt()
        var lo = 0
        for ((a, b) in holes) {
            // Pointer-walk the sorted HR into this hole (holes are ascending, so `lo` only advances).
            while (lo < hr.size && hr[lo].ts < a) lo++
            var hi = lo
            while (hi < hr.size && hr[hi].ts <= b) hi++
            val n = hi - lo
            if (n < hrRefineMinSamples) continue
            if ((b - a).toDouble() / n > hrDenseSpacingS) continue // hole not HR-dense: could be off-wrist

            // Classify per bucket, emitting runs as we go. runFirst/runLast are SAMPLE timestamps, so a
            // run never asserts sleep beyond the data that vouches for it.
            var i = lo
            var runFirst = -1L
            var runLast = -1L
            var runMinBucket = Double.MAX_VALUE
            var emptyStreak = 0
            // Onset persistence (the bucket twin of onsetPersistEpochs): a run OPENS only after
            // hrOnlyOnsetPersistBuckets CONSECUTIVE dip buckets (≤ baseline × hrOnlyDipMult), anchored
            // at the streak's first sample — quiet pre-sleep rest hovers between the dip and band bars
            // and a lone transient dip must not drag onset an hour early (Fold replay: 03:14 vs the
            // ~04:30 sustained descent). Once open, band-level buckets (≤ hrSleepBandMult) CONTINUE
            // the run, so normal mid-sleep HR wander doesn't shred the night.
            var dipStreak = 0
            var dipStreakFirst = -1L
            fun flush() {
                if (runFirst >= 0 && runLast - runFirst >= minSpanS &&
                    runMinBucket <= baseline * hrOnlyDipMult
                ) {
                    out.add(Period(stage = "sleep", start = runFirst, end = runLast))
                }
                runFirst = -1L; runLast = -1L; runMinBucket = Double.MAX_VALUE
                dipStreak = 0; dipStreakFirst = -1L
            }
            var t = a
            while (t < b) {
                val tEnd = minOf(t + hrOnlyBucketS, b)
                var sum = 0.0
                var cnt = 0
                var firstTs = -1L
                var lastTs = -1L
                while (i < hi && hr[i].ts < tEnd) {
                    sum += hr[i].bpm
                    cnt++
                    if (firstTs < 0) firstTs = hr[i].ts
                    lastTs = hr[i].ts
                    i++
                }
                if (cnt == 0) {
                    emptyStreak++
                    if (emptyStreak > maxEmptyBuckets) flush()
                } else {
                    val dips = sum / cnt <= baseline * hrOnlyDipMult
                    val sustains = sum / cnt <= baseline * hrSleepBandMult
                    val mean = sum / cnt
                    if (runFirst >= 0) {
                        if (sustains) {
                            runLast = lastTs
                            if (mean < runMinBucket) runMinBucket = mean
                        } else {
                            flush()
                        }
                    } else if (dips) {
                        if (dipStreak == 0) dipStreakFirst = firstTs
                        dipStreak++
                        if (dipStreak >= hrOnlyOnsetPersistBuckets) {
                            runFirst = dipStreakFirst
                            runLast = lastTs
                            runMinBucket = mean
                        }
                    } else {
                        dipStreak = 0
                        dipStreakFirst = -1L
                    }
                    emptyStreak = 0
                }
                t = tEnd
            }
            flush()
        }
        return out
    }

    // ── Stage 1–3 constants (sleep_features.py) ──────────────────────────────

    const val epochS: Double = 30.0
    const val featureWindowS: Double = 5 * 60.0
    const val ckCountDivisor: Double = 100.0
    const val ckCountClip: Double = 300.0
    const val moveDeltaThresholdG: Double = 0.01
    const val hrDogSigma1S: Double = 120.0
    const val hrDogSigma2S: Double = 600.0

    // Jul-13 pack (MAIN 32/66/2 vs WHOOP ~24/29/31/16): widen HR-low + still so sparse
    // PPG nights reopen Deep; lower HR-high so late still+elevated HR can reopen REM.
    // Jul-20 Light-bias pack (Gilbert): widen Deep/REM eligibility further — Light is the
    // classifyOne default when Wake/Deep/REM fail. Percentile bands stay relative — never
    // invent Deep/REM from HC TIB / empty gravity. Per-epoch RMSSD still gates Deep via
    // parasympOK; sessionAvgHRV is Charge-only (do not confuse).
    const val stageHRLowPct: Double = 48.0
    const val stageHRHighPct: Double = 60.0
    const val stageHRVHighPct: Double = 58.0
    const val stageHRVarHighPct: Double = 62.0
    const val stageRRVHighPct: Double = 65.0
    const val stageRRVLowPct: Double = 50.0
    const val stageWakeMoveFrac: Double = 0.15
    const val stageStillMoveFrac: Double = 0.21

    /**
     * Fraction of sleep-period epochs that must carry a MISSING per-epoch RMSSD (sparse R-R) for the
     * session's cardiac signal to count as PPG-DERIVED / sparse-cardiac. On a WHOOP 5/MG the PPG-derived
     * HR feeds a noisier per-epoch HR-variance, which inflates `hrVar` on otherwise still, low-HR sleep
     * epochs and was tripping the Stage-2 WAKE rule (which keys on the `hrvarHigh` percentile), so a
     * whole night over-reported WAKE. We already trust `!rmssd.isFinite()` as a PPG/sparse tell for the
     * pro-deep RMSSD handling (#127/#129); at this share across the night it also down-weights the
     * HR-variance half of the WAKE rule. ~50% keeps a real worn 4.0 night (dense R-R) on the strict
     * path and only relaxes nights whose cardiac signal is genuinely sparse/derived. (#705)
     */
    const val cardiacSparseEpochFrac: Double = 0.5

    const val smoothEpochs: Int = 5
    const val noREMAfterOnsetMin: Double = 15.0
    const val deepFirstFraction: Double = 1.0 / 3.0

    /**
     * Fragment-merge threshold (#274). A staged run shorter than this is "noise": the
     * WHOOP 5/MG banks sparse motion, so the stager emits lots of sub-minute stage flecks
     * and the hypnogram reads choppier than WHOOP's. mergeFragments (a DISPLAY/scoring
     * smoothing applied AFTER staging, never to the underlying detection) absorbs runs
     * below this into their neighbours. 3 min is conservative — long enough to clear the
     * fleck noise, short enough to leave a genuine stage transition (a real deep or REM
     * block runs many minutes) untouched. Mirrors Swift.
     */
    const val fragmentMergeMin: Double = 3.0

    /** fragmentMergeMin expressed in 30 s epochs (6). A run with < this many epochs merges. */
    val fragmentMergeEpochs: Int = (fragmentMergeMin * 60.0 / epochS).roundToInt()

    /** te Lindert 30 s Cole–Kripke weights [A₋₄..A₊₂]. SI = 0.001·Σ wᵢ·Aᵢ; sleep iff SI<1. */
    val ckWeights: List<Double> = listOf(106.0, 54.0, 58.0, 76.0, 230.0, 74.0, 67.0)
    const val ckScale: Double = 0.001
    const val ckBack: Int = 4
    const val ckFwd: Int = 2

    // ── Gravity deltas ───────────────────────────────────────────────────────

    /**
     * Per-record movement proxy = L2 magnitude of the gravity change vs the
     * previous record. First record → 0. (No dropout sentinel needed: GravitySample
     * always carries finite x/y/z.)
     */
    internal fun gravityDeltas(grav: List<GravitySample>): List<Double> {
        val deltas = ArrayList<Double>(grav.size)
        var prev: GravitySample? = null
        for ((i, r) in grav.withIndex()) {
            if (i == 0) {
                deltas.add(0.0)
            } else {
                val p = prev
                if (p != null) {
                    val dx = p.x - r.x
                    val dy = p.y - r.y
                    val dz = p.z - r.z
                    deltas.add(sqrt(dx * dx + dy * dy + dz * dz))
                } else {
                    deltas.add(0.0)
                }
            }
            prev = r
        }
        return deltas
    }

    /** Median spacing between consecutive timestamps, restricted to (0, 300 s). */
    internal fun medianIntervalS(times: List<Long>): Double {
        if (times.size < 2) return defaultIntervalS
        val gaps = ArrayList<Double>(times.size)
        for (i in 0 until times.size - 1) {
            val g = (times[i + 1] - times[i]).toDouble()
            if (g > 0 && g < 300) gaps.add(g)
        }
        if (gaps.isEmpty()) return defaultIntervalS
        gaps.sort()
        return maxOf(gaps[gaps.size / 2], 1.0)
    }

    internal fun windowSize(times: List<Long>): Int {
        val interval = medianIntervalS(times)
        return maxOf(minWindowSamples, (stillWindowMin * 60 / interval).toInt())
    }

    // ── Sparse-gravity gate (#308) ─────────────────────────────────────────────

    /**
     * Largest spacing between consecutive timestamps (seconds), NO upper cap; 0.0 for <2 samples.
     * Used to detect clumped/sparse gravity where the dropouts themselves are the signal: a few
     * long dropouts in otherwise-dense (clumped) motion keep the MEDIAN gap small but still break
     * runs, so the largest gap — not the median — is the right signal (#28).
     */
    internal fun largestGapS(times: List<Long>): Double {
        if (times.size < 2) return 0.0
        var mx = 0.0
        for (i in 0 until times.size - 1) {
            val g = (times[i + 1] - times[i]).toDouble()
            if (g > mx) mx = g
        }
        return mx
    }

    /**
     * True when gravity is too sparse for the gravity-only spine to be trusted across gaps: the
     * gravity timespan covers < sparseGravitySpanFrac of the HR-sample timespan, OR the LARGEST
     * gravity inter-sample gap exceeds maxGapMin. The largest-gap test (not just the median) catches
     * CLUMPED motion — dense bursts split by a few long dropouts, the typical WHOOP 4.0 backfill
     * (#28) — whose median gap stays small yet which still hides run-breaking gaps. Requires a real
     * HR span to compare against — with no/degenerate HR the dense path is kept (false), so a 4.0
     * with absent HR is never reclassified as sparse.
     */
    internal fun isGravitySparse(grav: List<GravitySample>, hr: List<HrSample>): Boolean {
        if (grav.size < 2 || hr.size < 2) return false
        val hrSpan = (hr[hr.size - 1].ts - hr[0].ts).toDouble()
        if (hrSpan <= 0) return false
        val gravSpan = (grav[grav.size - 1].ts - grav[0].ts).toDouble()
        if (gravSpan < sparseGravitySpanFrac * hrSpan) return true
        // #28: clumped 4.0 motion keeps a SMALL median gap yet still contains >maxGapMin dropouts
        // the gravity-only spine shreds the night on. The largest gap catches what a median would
        // miss (largest >= median, so this subsumes the old median check). Flagging sparse only
        // ENABLES buildRuns' HR-vouched bridge — a real wake (HR above the sleep band) still breaks.
        return largestGapS(grav.map { it.ts }) > (maxGapMin * 60).toDouble()
    }

    /**
     * True when HR stays in the sleep band (≤ baseline × hrSleepBandMult) across (a, b], used to
     * decide whether a pure gravity gap is a real wake or just a dropout. With no baseline or no HR
     * in the interval, the answer is false (cannot vouch for the gap → treat as a real break).
     */
    internal fun hrSleepBandAcross(a: Long, b: Long, hr: List<HrSample>, baseline: Double?): Boolean {
        if (baseline == null) return false
        val seg = hr.filter { it.ts > a && it.ts <= b }
        if (seg.isEmpty()) return false
        val meanHR = seg.sumOf { it.bpm }.toDouble() / seg.size.toDouble()
        return meanHR <= baseline * hrSleepBandMult
    }

    /** Per-record sleep flags from a rolling fraction of "still" samples. */
    internal fun classifyStill(grav: List<GravitySample>, deltas: List<Double>): List<Boolean> {
        val n = grav.size
        if (n < 2) return List(n) { false }
        val half = windowSize(grav.map { it.ts }) / 2
        // stillPrefix[i] = number of still samples among deltas[0 until i]. Turns each per-sample
        // window count from O(window) into O(1), so the whole scan is O(n) not O(n×window). The old
        // nested loop ran ~n×window times per night and — ×21 nights, on the MAIN THREAD — froze the
        // app into ANRs after a few nights of 1 Hz history. Output is byte-identical. (#125)
        val stillPrefix = IntArray(n + 1)
        for (i in 0 until n) {
            stillPrefix[i + 1] = stillPrefix[i] + if (deltas[i] < gravityStillThresholdG) 1 else 0
        }
        val flags = ArrayList<Boolean>(n)
        for (i in 0 until n) {
            val lo = maxOf(0, i - half)
            val hi = minOf(n, i + half + 1)
            val stillCount = stillPrefix[hi] - stillPrefix[lo]
            flags.add(stillCount.toDouble() / (hi - lo).toDouble() >= stillFraction)
        }
        return flags
    }

    /** A contiguous sleep/active run. `stage` ∈ {"sleep", "active"}. */
    internal data class Period(val stage: String, val start: Long, val end: Long)

    /**
     * Collapse per-record flags into contiguous runs, breaking on class change
     * or a gap > maxGapMin minutes.
     *
     * When [sparse] (gravity is too clumped to bridge gaps — #308), a PURE gravity data-gap (no
     * contrary motion) does NOT close a SLEEP run while HR stays in the sleep band across the gap:
     * the strap simply banked no motion there, not a wake. A class change always still closes the
     * run, and the dense path ([sparse] == false) is byte-identical to the original. Mirrors Swift.
     */
    internal fun buildRuns(
        grav: List<GravitySample>, flags: List<Boolean>,
        sparse: Boolean = false, hr: List<HrSample> = emptyList(), baseline: Double? = null,
    ): List<Period> {
        val n = grav.size
        if (n == 0) return emptyList()
        val times = grav.map { it.ts }
        val maxGapS = (maxGapMin * 60).toLong()
        val periods = ArrayList<Period>()
        var runStart = 0
        for (i in 1..n) {
            val atEnd = (i == n)
            val close: Boolean
            if (atEnd) {
                close = true
            } else {
                val classChanged = flags[i] != flags[runStart]
                var gapExceeded = (times[i] - times[i - 1]) > maxGapS
                // Sparse override: a pure gravity gap (no class change) does not break a sleep run
                // when HR stays in the sleep band across it — the gap is a dropout, not a wake.
                if (sparse && gapExceeded && !classChanged && flags[runStart] &&
                    hrSleepBandAcross(times[i - 1], times[i], hr, baseline)
                ) {
                    gapExceeded = false
                }
                close = classChanged || gapExceeded
            }
            if (close) {
                periods.add(
                    Period(
                        stage = if (flags[runStart]) "sleep" else "active",
                        start = times[runStart],
                        end = times[i - 1],
                    )
                )
                runStart = i
            }
        }
        return periods
    }

    /** Absorb runs shorter than mergeMin minutes into their neighbours. */
    internal fun mergePeriods(periods: List<Period>, mergeMinutes: Int = mergeMin): List<Period> {
        if (periods.isEmpty()) return emptyList()
        val pending = periods.toMutableList()
        val thresholdS = (mergeMinutes * 60).toLong()
        val merged = ArrayList<Period>()
        var i = 0
        while (i < pending.size) {
            val current = pending[i]
            val tooShort = (current.end - current.start) < thresholdS
            if (!tooShort) {
                merged.add(current)
                i += 1
                continue
            }

            val hasPrev = i > 0 && merged.isNotEmpty()
            val hasNext = i + 1 < pending.size
            val bridgesSame = hasPrev && hasNext && pending[i - 1].stage == pending[i + 1].stage

            if (bridgesSame) {
                val prev = merged.removeAt(merged.size - 1)
                merged.add(Period(stage = prev.stage, start = prev.start, end = pending[i + 1].end))
                i += 2
            } else if (hasNext) {
                pending[i + 1] = Period(
                    stage = pending[i + 1].stage,
                    start = current.start,
                    end = pending[i + 1].end,
                )
                i += 1
            } else if (hasPrev) {
                val prev = merged.removeAt(merged.size - 1)
                merged.add(Period(stage = prev.stage, start = prev.start, end = current.end))
                i += 1
            } else {
                i += 1
            }
        }
        return merged
    }

    /**
     * Sparse-gravity bridge (#308): merge two adjacent SLEEP runs separated ONLY by a gap up to
     * sparseBridgeGapMin minutes when the intervening HR stays in the sleep band — so a real night
     * fragmented by gravity dropouts is re-stitched into one continuous in-bed span BEFORE the
     * minSleepMin gate drops the pieces. Active runs and over-threshold gaps are left untouched; the
     * span between two bridged sleep runs (an "active"/gap run, if present) is absorbed. A no-op when
     * [sparse] == false, so the dense 4.0 path is unchanged. Mirrors Swift.
     */
    internal fun bridgeSparseSleep(
        periods: List<Period>, sparse: Boolean, hr: List<HrSample>, baseline: Double?,
    ): List<Period> {
        if (!sparse || periods.isEmpty()) return periods
        val bridgeGapS = (sparseBridgeGapMin * 60).toLong()
        val out = ArrayList<Period>()
        for (p in periods) {
            val last = out.lastOrNull()
            if (last != null && last.stage == "sleep" && p.stage == "sleep") {
                val gap = p.start - last.end
                if (gap in 0..bridgeGapS && hrSleepBandAcross(last.end, p.start, hr, baseline)) {
                    out[out.size - 1] = Period(stage = "sleep", start = last.start, end = p.end)
                    continue
                }
            }
            out.add(p)
        }
        return out
    }

    // ── HR refinement ────────────────────────────────────────────────────────

    private inline fun <T> rowsBetween(rows: List<T>, start: Long, end: Long, ts: (T) -> Long): List<T> =
        rows.filter { ts(it) in start..end }

    /** Day HR baseline = median bpm over all HR samples; null if none. */
    internal fun hrBaseline(hr: List<HrSample>): Double? {
        val vals = hr.map { it.bpm.toDouble() }
        if (vals.isEmpty()) return null
        return HrvAnalyzer.median(vals)
    }

    internal fun confirmSleepWithHR(p: Period, hr: List<HrSample>, baseline: Double?): Boolean {
        if (baseline == null) return true
        val seg = rowsBetween(hr, p.start, p.end) { it.ts }
        if (seg.size < hrRefineMinSamples) return true
        val meanHR = seg.sumOf { it.bpm }.toDouble() / seg.size.toDouble()
        return meanHR <= baseline * hrSleepBaselineMult
    }

    /**
     * True when the run's CENTER, shifted to LOCAL time by [tzOffsetSeconds], lands in the
     * daytime band [daytimeBandStartHour, daytimeBandEndHour). The center (not the edges) is
     * used so a window straddling a band edge is classified once, by where it mostly is.
     * Math.floorMod keeps the local-shifted time in [0, secondsPerDay) for any sign.
     */
    internal fun isDaytimeCenter(p: Period, tzOffsetSeconds: Long): Boolean {
        val center = p.start + (p.end - p.start) / 2
        val secOfDay = Math.floorMod(center + tzOffsetSeconds, secondsPerDay)
        val hour = (secOfDay / 3_600L).toInt()
        return hour >= daytimeBandStartHour && hour < daytimeBandEndHour
    }

    /**
     * True when a run's ONSET (start), in LOCAL time, falls OUTSIDE the daytime band — i.e. the
     * sleep began at night, not during the day. Anchors a continuous-sleep chain: only a chain
     * that began overnight may carry its tail past the daytime-band start (a late wake).
     * Reimplemented from @vulnix0x4's PR #353.
     */
    internal fun isOvernightOnset(start: Long, tzOffsetSeconds: Long): Boolean {
        val secOfDay = Math.floorMod(start + tzOffsetSeconds, secondsPerDay)
        val hour = (secOfDay / 3_600L).toInt()
        return !(hour >= daytimeBandStartHour && hour < daytimeBandEndHour)
    }

    /**
     * Stricter bar for a daytime-centered window (#90). A real daytime nap clears it; a long
     * sedentary still stretch (the false-positive this guards) does not, because it is either
     * too short or never shows a genuine cardiac dip below the day median. Overnight windows
     * never reach here. Returns true = keep, false = reject.
     *
     * [restingHR] is the window's own lowest 5-min rolling-mean HR (the sleep-depth proxy
     * detectSleep already computes); [baseline] is the day's median HR. With no usable HR
     * evidence (null baseline OR null restingHR) a daytime stretch cannot be confirmed as a
     * real nap, so it is rejected — sedentary daytime stillness without a measured HR dip is
     * far more likely than an unmonitored nap, and this path can never touch the night.
     */
    internal fun passesDaytimeGuard(p: Period, restingHR: Int?, baseline: Double?): Boolean {
        val daytimeMinSleepS = (daytimeMinSleepMin * 60).toLong()
        if ((p.end - p.start) < daytimeMinSleepS) return false
        if (baseline == null || restingHR == null) return false
        return restingHR.toDouble() <= baseline * daytimeRestingHRMult
    }

    /**
     * H7 morning-stillness nap suppression (#531). Returns true = KEEP, false = REJECT, for a daytime block
     * [p] that begins shortly after a real overnight wake. [morningWakeEnd] is the end of the just-detected
     * OVERNIGHT chain (null when the prior chain was not overnight, or there was none) — when [p].start is
     * within [morningStillnessWindowMin] of it, the block is suspected morning residual stillness and must
     * clear the ORDINARY daytime guard AND show a SUSTAINED re-onset: its resting HR must dip below the
     * stronger [morningReonsetRestingHRMult] × baseline bar (a true second sleep, not near-waking stillness).
     * Outside the morning window this is a no-op (returns the plain daytime-guard result), so a genuine
     * afternoon nap is unaffected. Mirrors Swift `passesMorningStillnessGuard`. (#531)
     */
    internal fun passesMorningStillnessGuard(
        p: Period,
        restingHR: Int?,
        baseline: Double?,
        morningWakeEnd: Long?,
        bandSleepState: List<Pair<Long, Int>> = emptyList(),
    ): Boolean {
        // Only a daytime block beginning within the post-wake window of an overnight chain is suspected.
        if (morningWakeEnd == null || p.start < morningWakeEnd ||
            (p.start - morningWakeEnd) > (morningStillnessWindowMin * 60).toLong()
        ) {
            return passesDaytimeGuard(p, restingHR, baseline)
        }
        // Suspected morning stillness needs at least the ordinary daytime guard (long enough + a real dip).
        if (!passesDaytimeGuard(p, restingHR, baseline)) return false
        // CONSUME the strap's OWN banked band sleep_state (#531 / H8): if the strap itself scored this block
        // predominantly "asleep", that is a strong independent re-onset anchor — KEEP it even on a borderline
        // HR dip. This only ever RESCUES a block the strap says was real sleep; it never fabricates one.
        if (bandStateConfirmsAsleep(p, bandSleepState)) return true
        // Otherwise require the clearly-deeper cardiac dip of a true second sleep.
        if (baseline == null || restingHR == null) return false
        return restingHR.toDouble() <= baseline * morningReonsetRestingHRMult
    }

    /**
     * CONSUME-side helper (#531 / H8): true when the strap's OWN persisted v18 band sleep_state over the
     * block [p.start, p.end] reads predominantly "asleep" ([bandStateAsleep]), at/above
     * [morningReonsetBandAsleepFrac] of the in-block samples — an independent confirmation of a real
     * re-onset. Empty/absent band state → false (no anchor → fall back to the HR bar); we never invent an
     * "asleep" reading the strap did not bank. Pure + deterministic. Mirrors Swift `bandStateConfirmsAsleep`.
     * (#531 / H8 consume)
     */
    internal fun bandStateConfirmsAsleep(p: Period, bandSleepState: List<Pair<Long, Int>>): Boolean {
        val inBlock = bandSleepState.filter { it.first in p.start..p.end }
        if (inBlock.isEmpty()) return false
        val asleep = inBlock.count { it.second == bandStateAsleep }
        return asleep.toDouble() / inBlock.size.toDouble() >= morningReonsetBandAsleepFrac
    }

    /**
     * Off-wrist HR-gap spans (#500). The contiguous HR-coverage gaps of at least [offWristHRGapMin]
     * minutes WITHIN [p.start, p.end], as concrete [start, end) sub-intervals — a strong wrist-OFF
     * proxy. Worn, the strap streams ~1 Hz HR (or PPG-derived HR on a 5/MG), so a real night yields no
     * long gap; an off-wrist stretch flatlines to no HR samples and yields a span. The leading edge
     * ([p.start] → first in-run sample) and trailing edge (last in-run sample → [p.end]) count too,
     * and a run with NO in-run HR at all is one full-period gap. With NO HR data at all this returns
     * [] (the gravity-only path is left to the existing guards — we can't assert off-wrist without HR).
     * These spans are UNIONed with the WRIST_OFF intervals by [offWristFraction]. Mirrors Swift.
     */
    internal fun offWristHRGapSpans(p: Period, hr: List<HrSample>): List<Pair<Long, Long>> {
        if (hr.isEmpty() || p.end <= p.start) return emptyList()
        // Density gate (#507): only trust the HR-gap off-wrist proxy when the HR STREAM is dense enough
        // that a long gap is anomalous. A WHOOP 4.0 synced night is motion-reconstructed with sparse HR,
        // so its natural gaps must NOT read as off-wrist (that wrongly dropped a real night). Judge over
        // the whole stream so an off-wrist HOLE inside an otherwise dense, worn day (#500) is still caught.
        val sortedAll = hr.sortedBy { it.ts }
        val streamSpan = sortedAll.last().ts - sortedAll.first().ts
        if (streamSpan >= hrDenseSpacingS && hr.size < streamSpan / hrDenseSpacingS) return emptyList()
        val gapS = (offWristHRGapMin * 60).toLong()
        val seg = hr.filter { it.ts in p.start..p.end }.sortedBy { it.ts }
        // No HR anywhere inside a run long enough to matter → the whole period is one gap.
        if (seg.isEmpty()) return if ((p.end - p.start) >= gapS) listOf(p.start to p.end) else emptyList()
        val spans = ArrayList<Pair<Long, Long>>()
        // Leading edge: run start to first sample.
        if (seg[0].ts - p.start >= gapS) spans.add(p.start to seg[0].ts)
        // Interior: any gap between consecutive in-run samples.
        for (i in 1 until seg.size) if (seg[i].ts - seg[i - 1].ts >= gapS) spans.add(seg[i - 1].ts to seg[i].ts)
        // Trailing edge: last sample to run end.
        if (p.end - seg[seg.size - 1].ts >= gapS) spans.add(seg[seg.size - 1].ts to p.end)
        return spans
    }

    /**
     * Fractional off-wrist coverage of a candidate run [p.start, p.end] in [0, 1] (#500).
     * Design credited to j0b-dev's #504 analysis: instead of a binary drop on ANY HR gap or ANY single
     * WRIST_OFF blip, we measure how much of the run is off-wrist and let the caller drop it only past
     * [maxOffWristSleepFraction]. Coverage = (length of the UNION of) the HR-gap spans ([offWristHRGapSpans])
     * AND the supplied WRIST_OFF→WRIST_ON [wristOff] intervals, clipped to the run, divided by duration.
     * Unioning avoids double-counting overlapping gap+event time. A real night with a small (<50%)
     * off-wrist tail scores low and is kept; an all-day desk strap (HR-gap ≈100%, no events needed) or a
     * session genuinely spent off the wrist scores high and is dropped. Mirrors Swift.
     */
    internal fun offWristFraction(p: Period, hr: List<HrSample>, wristOff: List<Pair<Long, Long>>): Double {
        val dur = p.end - p.start
        if (dur <= 0) return 0.0
        // Collect every off-wrist span, clipped to the run: HR-gap proxy spans + explicit wrist-off events.
        val spans = ArrayList(offWristHRGapSpans(p, hr))
        for (w in wristOff) {
            val s = maxOf(w.first, p.start); val e = minOf(w.second, p.end)
            if (e > s) spans.add(s to e)
        }
        if (spans.isEmpty()) return 0.0
        // Union the spans so overlapping gap+event time is counted once, then sum the covered length.
        spans.sortBy { it.first }
        var covered = 0L; var curStart = spans[0].first; var curEnd = spans[0].second
        for (i in 1 until spans.size) {
            val sp = spans[i]
            if (sp.first <= curEnd) {
                curEnd = maxOf(curEnd, sp.second)        // overlapping/adjacent → extend
            } else {
                covered += curEnd - curStart             // disjoint → bank the run
                curStart = sp.first; curEnd = sp.second
            }
        }
        covered += curEnd - curStart
        return covered.toDouble() / dur.toDouble()
    }

    // ── detectSleep (public) ──────────────────────────────────────────────────

    /**
     * One folded Long per sample stream for the [detectSleep] memo key: folds the COUNT plus every
     * (ts, quantised value) with large odd multipliers — the same every-element discipline as
     * [StagerCache.fingerprint] — so an in-place interior edit (a re-import correcting a value), a
     * truncation, or an append all re-key to a fresh compute and a stale hit is never served.
     * Internal so the fingerprint-completeness test pins those properties directly.
     */
    internal fun <T> streamFingerprint(samples: List<T>, ts: (T) -> Long, quant: (T) -> Long): Long {
        var h = samples.size.toLong() * 2_654_435_761L
        for (e in samples) h = (h * 1_000_003L + ts(e)) * 1_000_003L + quant(e)
        return h
    }

    /** Full memo key for [detectSleep] — every input that steers detection or staging, nothing else. */
    private data class DetectKey(
        val grav: Long, val hr: Long, val rr: Long, val resp: Long,
        val tz: Long, val wristOff: Long, val band: Long, val v2: Boolean,
    )

    /** Bound ≈ the distinct days of a scoring window (matches the Swift detectSleepCache capacity, 40).
     *  Access-order LRU so the hottest nights survive a long session; an entry is a handful of small
     *  sessions — the multi-hour raw streams are never retained (#707 rule: the cache must not be the
     *  next leak). */
    private const val DETECT_CACHE_MAX_DAYS = 40
    private val detectCache = object : LinkedHashMap<DetectKey, List<DetectedSleep>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DetectKey, List<DetectedSleep>>): Boolean =
            size > DETECT_CACHE_MAX_DAYS
    }
    private val detectCacheLock = Any()

    /** Deep copy across the memo boundary. [DetectedSleep] itself is immutable, but its `stages` are
     *  mutable [StageSegment]s — the exact reason [StagerCache] deep-copies hypnograms — so the memo
     *  holds a private copy and hands every hit a fresh one; a caller reshaping a segment in place can
     *  never poison the cache. Swift needs no copy (value-type structs); Kotlin's mutable StageSegment
     *  does. O(stages), a rounding error next to the detection spine the memo skips. */
    private fun copyDetected(sessions: List<DetectedSleep>): List<DetectedSleep> =
        sessions.map { it.copy(stages = StagerCache.copyOf(it.stages)) }

    /**
     * Detect sleep sessions from biometric streams. Empty/absent gravity → [].
     * Gravity-only input degrades gracefully (HR/RR/resp refinements skipped).
     *
     * [tzOffsetSeconds] is the wall-clock UTC offset (TimeZone.getDefault().getOffset)
     * used ONLY to place each window's center on a LOCAL clock for the daytime false-sleep
     * guard (#90). It defaults to 0 so the pure function and its tests stay UTC; the live
     * call site (IntelligenceEngine) passes the device's real offset.
     *
     * [wristOff] is an optional list of off-wrist [start, end) intervals (unix seconds), paired from
     * the strap's WRIST_OFF/WRIST_ON events by `AnalyticsEngine.offWristIntervals`. When the call site
     * has them (IntelligenceEngine reads `repo.events`), they sharpen the always-on HR-gap off-wrist
     * backstop: a candidate run is dropped when its off-wrist coverage (HR-gap spans UNION these
     * intervals) reaches [maxOffWristSleepFraction] of its duration — the FRACTIONAL rule from #504, so
     * a real night with a short off-wrist tail survives (#500). Defaults to empty (HR-gap proxy only),
     * so the pure function and its tests stay event-free.
     *
     * PERF (#707 parity, mirrors the Swift detectSleepCache): this is the single heaviest analytics
     * call — it sorts the dense full-day gravity stream, builds the delta/still spine, and stages every
     * accepted run — and [IntelligenceEngine.analyzeRecent] re-runs it per day across the window every
     * 15-min tick, so an idempotent re-pass (or a sync that never touched a given day) redid all of it
     * for an identical result; the exact path that ANR'd before #125. Android had only the staging-layer
     * [StagerCache], so the detection spine still re-ran uncached ~21× per pass. The public entry now
     * memoizes the RESULT on a FULL input key (every argument that steers detection or staging: the four
     * streams, tz offset, #500 off-wrist intervals, #531 band state, the V2 toggle) in a bounded LRU. A
     * hit is byte-identical to recomputing — the memo only ever skips work, never changes it.
     */
    fun detectSleep(
        hr: List<HrSample> = emptyList(),
        rr: List<RrInterval> = emptyList(),
        resp: List<RespSample> = emptyList(),
        gravity: List<GravitySample>,
        tzOffsetSeconds: Long = 0L,
        wristOff: List<Pair<Long, Long>> = emptyList(),
        // The strap's OWN persisted v18 BAND sleep_state per timestamp (Interpreter's `(sb shr 4) and 3`:
        // 0 wake / 1 still / 2 asleep / 3 up), consumed ONLY to confirm a borderline H7 morning re-onset
        // (#531): a daytime block the strap itself scored predominantly "asleep" is kept even on a borderline
        // HR dip. Default empty keeps pure-function callers/tests free of it; IntelligenceEngine passes the
        // night window's persisted band state. It can only RESCUE a real-sleep block, never fabricate. Mirrors Swift.
        bandSleepState: List<Pair<Long, Int>> = emptyList(),
        // V7 / #690: when true, each accepted night is staged by the experimental cardiorespiratory recipe
        // [SleepStagerV2.stageSession] instead of V1's [stageSession]. DETECTION is unchanged (same accepted
        // windows); only the per-epoch hypnogram differs. Default false keeps V1 the byte-identical default
        // (frozen-golden tests stay green). The live call site threads the experimentalSleepV2 flag so the
        // Settings toggle now affects normal detected nights, not just the self-heal restage path. Mirrors Swift.
        useSleepStagerV2: Boolean = false,
        // Sleep & Rest test mode (E10): when non-null, each candidate run emits ONE gate verdict line
        // and the sparse-gravity bridge records its result. Side-effect-only; the returned list is
        // byte-identical to the untraced call. Default null = no work, byte-identical. Mirrors Swift.
        traceSink: ((String) -> Unit)? = null,
    ): List<DetectedSleep> {
        // Test mode ONLY: a requested trace MUST run the live gate ladder so every verdict emits for THIS
        // night — never a silent memo replay. The trace is side-effect-only (the returned list is
        // byte-identical to the untraced call), so every real call still memoizes below. Mirrors the
        // Swift detectSleep traceSink bypass (#707).
        if (traceSink != null) {
            return detectSleepUncached(hr, rr, resp, gravity, tzOffsetSeconds, wristOff,
                bandSleepState, useSleepStagerV2, traceSink)
        }
        val key = DetectKey(
            // Fold the three gravity axes SEPARATELY (raw IEEE-754 bits, like StagerCache.fingerprint)
            // rather than quantising the x+y+z SUM: two different postures sharing a component sum must
            // not alias. DELIBERATELY harder than the Swift key's summed quant — a fingerprint difference
            // can only cost one platform an extra recompute, never change a value, so this is free
            // collision hardening, not a parity break.
            grav = streamFingerprint(gravity, { it.ts }) { s ->
                var q = java.lang.Double.doubleToRawLongBits(s.x)
                q = q * 1_000_003L + java.lang.Double.doubleToRawLongBits(s.y)
                q = q * 1_000_003L + java.lang.Double.doubleToRawLongBits(s.z)
                q
            },
            hr = streamFingerprint(hr, { it.ts }) { it.bpm.toLong() },
            rr = streamFingerprint(rr, { it.ts }) { it.rrMs.toLong() },
            resp = streamFingerprint(resp, { it.ts }) { it.raw.toLong() },
            tz = tzOffsetSeconds,
            wristOff = streamFingerprint(wristOff, { it.first }) { it.second },
            band = streamFingerprint(bandSleepState, { it.first }) { it.second.toLong() },
            v2 = useSleepStagerV2,
        )
        synchronized(detectCacheLock) { detectCache[key] }?.let { return copyDetected(it) }
        // Compute OUTSIDE the lock (the Swift AnalyticsMemoCache does the same): a slow night never
        // serialises another thread's lookup, and a racing duplicate compute just overwrites the entry
        // with an identical value — benign, the function is deterministic.
        val sessions = detectSleepUncached(hr, rr, resp, gravity, tzOffsetSeconds, wristOff,
            bandSleepState, useSleepStagerV2, null)
        synchronized(detectCacheLock) { detectCache[key] = copyDetected(sessions) }
        return sessions
    }

    /** The unchanged detection + staging pipeline, split out verbatim so [detectSleep] can memoize in
     *  front (#707 parity). Parameters are documented on the public wrapper. */
    private fun detectSleepUncached(
        hr: List<HrSample>,
        rr: List<RrInterval>,
        resp: List<RespSample>,
        gravity: List<GravitySample>,
        tzOffsetSeconds: Long,
        wristOff: List<Pair<Long, Long>>,
        bandSleepState: List<Pair<Long, Int>>,
        useSleepStagerV2: Boolean,
        traceSink: ((String) -> Unit)?,
    ): List<DetectedSleep> {
        val grav = gravity.sortedBy { it.ts }
        if (grav.size < 2) return emptyList()

        val hrS = hr.sortedBy { it.ts }
        val rrS = rr.sortedBy { it.ts }
        val respS = resp.sortedBy { it.ts }

        val baseline = hrBaseline(hrS)
        // Sparse-gravity gate (#308): an un-unlocked WHOOP 5.0 backfills mostly v18/v26 records
        // where gravity is clumped (~25% coverage), so the gravity-only spine fragments the night.
        // ONLY when sparse do the three robustness branches engage; a dense 4.0 night is `false`
        // here and follows the exact original path (byte-identical).
        val sparse = isGravitySparse(grav, hrS)

        val deltas = gravityDeltas(grav)
        val flags = classifyStill(grav, deltas)
        var runs = buildRuns(grav, flags, sparse = sparse, hr = hrS, baseline = baseline)
        runs = mergePeriods(runs)
        // Re-stitch sleep runs fragmented by pure gravity dropouts (sparse only) before minSleepMin.
        val runsBeforeBridge = if (traceSink == null) 0 else runs.count { it.stage == "sleep" }
        runs = bridgeSparseSleep(runs, sparse = sparse, hr = hrS, baseline = baseline)
        // Sleep & Rest test mode (E10): record the sparse-gravity bridge result, so a sparse 5.0 night
        // rescued from fragmentation is visible. Only when gravity is sparse and only when tracing.
        if (traceSink != null && sparse) {
            val runsAfterBridge = runs.count { it.stage == "sleep" }
            traceSink(SleepStagerTrace.runLine(-1, 0, 0,
                if (runsAfterBridge < runsBeforeBridge) SleepStagerTrace.Verdict.KEPT else SleepStagerTrace.Verdict.DROPPED,
                "sparseBridge", "sparse=true gapMin=$sparseBridgeGapMin runsBefore=$runsBeforeBridge runsAfter=$runsAfterBridge"))
        }
        // HR-only spine for gravity-DEAD holes (Fable #201/#204): a real night whose gravity coverage
        // is entirely absent (dense HR + R-R, zero motion records — the Fold 2026-07-12 04:46→15:00
        // hole) seeds no gravity runs at all, so it never even reached the gates. Synthesize sleep-band
        // HR runs inside such holes and feed them through the SAME gate ladder below. Sparse-gated;
        // dense nights are byte-identical. Re-run the sparse bridge so a synthesized run stitches to an
        // adjacent gravity-seeded fragment exactly like two gravity fragments would.
        val hrOnly = hrOnlySleepRuns(grav, hrS, baseline, sparse)
        if (hrOnly.isNotEmpty()) {
            runs = bridgeSparseSleep(
                (runs + hrOnly).sortedBy { it.start },
                sparse = sparse, hr = hrS, baseline = baseline,
            )
            traceSink?.invoke(SleepStagerTrace.runLine(-1, 0, 0,
                SleepStagerTrace.Verdict.KEPT, "hrOnlySpine",
                "sparse=true holesMin=$hrOnlyHoleMin synthesized=${hrOnly.size}"))
        }

        val minSleepS = (minSleepMin * 60).toLong()

        val sessions = ArrayList<DetectedSleep>()
        // Continuous-sleep chain tracking so a real overnight sleep that runs PAST the daytime-band
        // start (a late wake, or a brief morning stir then back to sleep that leaves the tail as its
        // own daytime-centered run) is NOT mistaken for an isolated daytime nap and rejected — which
        // truncated the displayed wake time to ~late morning. A daytime run skips the nap guard ONLY
        // when it directly continues (≤ nightContinuationGap) a chain that BEGAN overnight; isolated
        // daytime stillness (hours after waking) still faces the full guard.
        // Reimplemented from @vulnix0x4's PR #353.
        val continuationGapS = (nightContinuationGapMin * 60).toLong()
        var chainPrevEnd: Long? = null       // end of the last accepted sleep run
        var chainFromOvernight = false       // did the current contiguous chain begin overnight?
        // Sleep & Rest test mode (E10): each candidate sleep run emits ONE verdict line naming the gate
        // that kept or dropped it. The decisions below are byte-identical to the untraced path; the
        // traceSink calls are the only addition and never alter `sessions`. `runIndex` counts only
        // sleep-stage runs so the trace numbers match the candidate ordinal.
        var runIndex = -1
        for (p in runs) {
            if (p.stage != "sleep") continue
            runIndex += 1
            val spanMin = ((p.end - p.start) / 60).toInt()
            // Daytime nap floor is lower than overnight minSleepMin so real 25–60 min naps survive
            // the first gate (they still must clear daytime HR-dip / morning-stillness guards).
            val isDaytimeCandidate = isDaytimeCenter(p, tzOffsetSeconds)
            val spanFloorS = if (isDaytimeCandidate) {
                (daytimeMinSleepMin * 60).toLong()
            } else {
                minSleepS
            }
            if ((p.end - p.start) <= spanFloorS) {
                val floorLabel = if (isDaytimeCandidate) daytimeMinSleepMin else minSleepMin
                traceSink?.invoke(SleepStagerTrace.runLine(runIndex, p.start, p.end,
                    SleepStagerTrace.Verdict.DROPPED, "minSleepMin",
                    "spanMin=$spanMin minSleepMin=$floorLabel daytime=$isDaytimeCandidate"))
                continue
            }
            // H4 physiological in-bed span cap (#547/#531/#509 tail): a single assembled main-sleep run
            // longer than ~16 h is a bad-clock artefact (a frozen still stretch banked under a stale/wrong
            // clock), not a real night. Drop it rather than report (or truncate to) a 12 h+ "sleep" — an
            // over-long block can't be trusted to assert a span at all, and truncating would fabricate a
            // wake time. Checked before staging so the artefact never reaches the aggregate. Mirrors Swift.
            if ((p.end - p.start) > maxMainSleepSpanS) {
                traceSink?.invoke(SleepStagerTrace.runLine(runIndex, p.start, p.end,
                    SleepStagerTrace.Verdict.DROPPED, "maxMainSleepSpanS", "spanMin=$spanMin maxMainSleepSpanMin=${maxMainSleepSpanS / 60}"))
                continue
            }
            if (!confirmSleepWithHR(p, hrS, baseline)) {
                traceSink?.invoke(SleepStagerTrace.runLine(runIndex, p.start, p.end,
                    SleepStagerTrace.Verdict.DROPPED, "hrConfirm", "hrSleepBaselineMult=$hrSleepBaselineMult baseline=${baseline?.toInt() ?: -1}"))
                continue
            }
            // Off-wrist backstop (#500), FRACTIONAL rule (design credited to j0b-dev's #504 analysis):
            // a wrist-OFF stretch is still gravity with no HR, so it slips past both the gravity spine
            // and the daytime guard's "missing data" path. Measure off-wrist COVERAGE — the union of the
            // run's long HR-coverage gaps (the must-have proxy) and any WRIST_OFF→WRIST_ON intervals
            // overlapping it — and drop the run only when that reaches maxOffWristSleepFraction of its
            // duration. This no longer nukes a real night that over-extends into a SHORT (<50%) off-wrist
            // morning tail, or that holds a single stray WRIST_OFF blip, while an all-day desk strap
            // (≈100% gap) is still dropped. Checked BEFORE the night-tail exemption: off-wrist time is
            // off-wrist day or night and must NOT ride a continuation chain. It does NOT re-anchor the
            // chain (the run is simply skipped).
            val offFrac = offWristFraction(p, hrS, wristOff)
            if (offFrac >= maxOffWristSleepFraction) {
                traceSink?.invoke(SleepStagerTrace.runLine(runIndex, p.start, p.end,
                    SleepStagerTrace.Verdict.DROPPED, "offWrist", "offWristFrac=${SleepStagerTrace.round2(offFrac)} max=$maxOffWristSleepFraction"))
                continue
            }
            // Daytime false-sleep guard (#90): a window centered in the local daytime band
            // must clear a stricter bar (≥daytimeMinSleepMin AND a real resting-HR dip).
            // Overnight windows skip this entirely. restingHR is computed here (reused below).
            val resting = sessionRestingHR(start = p.start, end = p.end, hr = hrS)
            val continuesChain = chainPrevEnd?.let { p.start - it <= continuationGapS } ?: false
            val isNightTail = continuesChain && chainFromOvernight   // the night's tail, not a nap
            // H7 (#531): when the prior accepted chain BEGAN overnight, its wake (chainPrevEnd) anchors the
            // morning-stillness window. A daytime block beginning within it that is NOT a night-tail must
            // clear the STRONGER re-onset bar — killing the 9 am phantom nap of residual post-wake stillness
            // while keeping a genuine second sleep. Outside the window the guard is the ordinary daytime bar.
            val morningWakeEnd = if (chainFromOvernight) chainPrevEnd else null
            val isDaytime = isDaytimeCenter(p, tzOffsetSeconds)
            // Evaluate the morning-stillness guard ONLY when the run is daytime-centered, preserving the
            // original short-circuit (overnight runs never call it). The boolean used to drop below is
            // identical to the original combined condition.
            val passesMorning = if (isDaytime)
                passesMorningStillnessGuard(p, resting, baseline, morningWakeEnd, bandSleepState)
            else true
            if (isDaytime && !passesMorning && !isNightTail) {
                val gateName = if (morningWakeEnd != null) "morningStillness" else "daytimeGuard"
                traceSink?.invoke(SleepStagerTrace.runLine(runIndex, p.start, p.end,
                    SleepStagerTrace.Verdict.DROPPED, gateName,
                    "daytime=true restingHR=${resting ?: -1} baseline=${baseline?.toInt() ?: -1} nightTail=false"))
                continue
            }
            // Fable Sleep #27/#28: soft-snap bed/wake to longest band-asleep stretch when present.
            val (bed0, wake0) = refineBoundsFromBandState(p.start, p.end, bandSleepState)
            val rawStages = if (useSleepStagerV2) {
                SleepStagerV2.stageSession(start = bed0, end = wake0, grav = grav,
                    hr = hrS, rr = rrS, resp = respS)
            } else {
                stageSession(start = bed0, end = wake0, grav = grav,
                    hr = hrS, rr = rrS, resp = respS)
            }
            // Align scored TIB to the primary sleep bout (drop long post-wake stillness / pre-onset
            // fluff that gravity merged into one run). Does not invent stages — clips only.
            val aligned = alignToPrimaryBout(bed0, wake0, rawStages)
            val bed = aligned.start
            val wake = aligned.end
            val stages = aligned.stages
            val eff = efficiency(start = bed, end = wake, stages = stages)
            val avgHrv = sessionAvgHRV(start = bed, end = wake, rr = rrS)
            val avgSdnn = sessionAvgSdnn(start = bed, end = wake, rr = rrS)
            val restingFinal = if (bed != p.start || wake != p.end) {
                sessionRestingHR(start = bed, end = wake, hr = hrS)
            } else {
                resting
            }
            sessions.add(
                DetectedSleep(
                    start = bed, end = wake, efficiency = eff,
                    stages = stages, restingHR = restingFinal, avgHRV = avgHrv, avgSdnn = avgSdnn,
                )
            )
            traceSink?.invoke(SleepStagerTrace.runLine(runIndex, bed, wake,
                SleepStagerTrace.Verdict.KEPT, "accepted",
                "spanMin=${(wake - bed) / 60} eff=${SleepStagerTrace.round2(eff)} restingHR=${restingFinal ?: -1} daytime=$isDaytime primaryBout=${bed != bed0 || wake != wake0}"))
            // A run that does NOT continue the chain re-anchors it on this run's onset.
            if (!continuesChain) chainFromOvernight = isOvernightOnset(bed, tzOffsetSeconds)
            chainPrevEnd = wake
        }
        sessions.sortBy { it.start }
        return sessions
    }

    /**
     * Clip a staged in-bed window to the **primary sleep bout**: the cluster of sleep epochs with
     * the most asleep time, split when sustained wake ≥ [primaryBoutSplitWakeMin], then **rejoin**
     * a following cluster when return-to-sleep is substantial within [primaryBoutRejoinGapMaxMin]
     * (false morning wake). Trims long leading wake (keep ≤ [primaryBoutSolPadMin]) and long
     * trailing wake (keep ≤ [primaryBoutWakePadMin]). Returns the original window when there is no
     * sleep content or the clip would be empty. Never invents Deep/REM — only reshapes bounds +
     * clips segments.
     */
    internal fun alignToPrimaryBout(
        start: Long,
        end: Long,
        stages: List<StageSegment>,
        splitWakeMin: Int = primaryBoutSplitWakeMin,
        solPadMin: Int = primaryBoutSolPadMin,
        wakePadMin: Int = primaryBoutWakePadMin,
        rejoinMinAsleepMin: Int = primaryBoutRejoinMinAsleepMin,
        rejoinGapMaxMin: Int = primaryBoutRejoinGapMaxMin,
    ): DetectedSleep {
        val segs = stages.sortedBy { it.start }.filter { it.end > it.start }
        if (segs.isEmpty() || end <= start) {
            return DetectedSleep(start = start, end = end, efficiency = 0.0, stages = stages,
                restingHR = null, avgHRV = null)
        }
        val splitS = splitWakeMin * 60L
        val solPadS = solPadMin * 60L
        val wakePadS = wakePadMin * 60L
        val rejoinMinS = rejoinMinAsleepMin * 60L
        val rejoinGapS = rejoinGapMaxMin * 60L

        data class Cluster(var start: Long, var end: Long, var asleepS: Long)

        val clusters = ArrayList<Cluster>()
        var cur: Cluster? = null
        var pendingWakeStart: Long? = null
        var pendingWakeEnd: Long? = null

        fun flushWakeIntoOrSplit(wakeStart: Long, wakeEnd: Long) {
            val wakeDur = wakeEnd - wakeStart
            val c = cur
            if (c == null) return // leading wake handled after cluster pick
            if (wakeDur >= splitS) {
                // Sustained wake ends the cluster; do not absorb it.
                cur = null
            } else {
                // Short WASO stays inside the bout.
                c.end = wakeEnd
            }
        }

        for (s in segs) {
            val isSleep = s.stage == "light" || s.stage == "deep" || s.stage == "rem"
            if (isSleep) {
                    if (pendingWakeStart != null && pendingWakeEnd != null) {
                        flushWakeIntoOrSplit(pendingWakeStart, pendingWakeEnd)
                        pendingWakeStart = null
                        pendingWakeEnd = null
                    }
                val c = cur
                if (c == null) {
                    val neu = Cluster(start = s.start, end = s.end, asleepS = s.end - s.start)
                    clusters.add(neu)
                    cur = neu
                } else {
                    c.end = s.end
                    c.asleepS += s.end - s.start
                }
            } else {
                // wake
                if (cur == null) continue // leading wake — trim later via SOL pad
                if (pendingWakeStart == null) pendingWakeStart = s.start
                pendingWakeEnd = s.end
            }
        }
        // Trailing short wake may still be pending; absorb only if under split threshold.
        if (pendingWakeStart != null && pendingWakeEnd != null) {
            flushWakeIntoOrSplit(pendingWakeStart, pendingWakeEnd)
        }

        // Rejoin false-morning-wake splits: substantial return-to-sleep within the gap cap.
        if (clusters.size >= 2) {
            val merged = ArrayList<Cluster>()
            var acc = clusters[0]
            for (i in 1 until clusters.size) {
                val nxt = clusters[i]
                val gap = nxt.start - acc.end
                if (gap in 1..rejoinGapS && nxt.asleepS >= rejoinMinS) {
                    acc.end = nxt.end
                    acc.asleepS += nxt.asleepS
                } else {
                    merged.add(acc)
                    acc = nxt
                }
            }
            merged.add(acc)
            clusters.clear()
            clusters.addAll(merged)
        }

        val primary = clusters.maxByOrNull { it.asleepS }
            ?: return DetectedSleep(
                start = start, end = end,
                efficiency = efficiency(start, end, segs),
                stages = segs.map { StageSegment(it.start, it.end, it.stage) },
                restingHR = null, avgHRV = null,
            )

        // First / last sleep edges inside the primary cluster (for SOL / wake pads).
        val sleepSegs = segs.filter {
            (it.stage == "light" || it.stage == "deep" || it.stage == "rem") &&
                it.start < primary.end && it.end > primary.start
        }
        val firstSleep = sleepSegs.minOfOrNull { it.start } ?: primary.start
        val lastSleep = sleepSegs.maxOfOrNull { it.end } ?: primary.end

        // Drop long pre-onset wake (keep ≤ SOL pad) and long post-wake stillness (keep ≤ wake pad).
        // Short WASO already absorbed into [primary] stays inside the bout.
        val newStart = maxOf(start, firstSleep - solPadS)
        val newEnd = when {
            primary.end > lastSleep -> minOf(end, primary.end) // absorbed short trailing WASO
            else -> minOf(end, lastSleep + wakePadS)
        }
        if (newEnd <= newStart) {
            return DetectedSleep(
                start = start, end = end,
                efficiency = efficiency(start, end, segs),
                stages = segs.map { StageSegment(it.start, it.end, it.stage) },
                restingHR = null, avgHRV = null,
            )
        }

        // No-op when the gravity/band window already matches the primary bout.
        if (newStart == start && newEnd == end) {
            return DetectedSleep(
                start = start, end = end,
                efficiency = efficiency(start, end, segs),
                stages = segs.map { StageSegment(it.start, it.end, it.stage) },
                restingHR = null, avgHRV = null,
            )
        }

        val clipped = ArrayList<StageSegment>()
        for (s in segs) {
            val a = maxOf(s.start, newStart)
            val b = minOf(s.end, newEnd)
            if (b <= a) continue
            val last = clipped.lastOrNull()
            if (last != null && last.stage == s.stage) {
                last.end = b
            } else {
                clipped.add(StageSegment(start = a, end = b, stage = s.stage))
            }
        }
        if (clipped.isEmpty()) {
            return DetectedSleep(
                start = start, end = end,
                efficiency = efficiency(start, end, segs),
                stages = segs.map { StageSegment(it.start, it.end, it.stage) },
                restingHR = null, avgHRV = null,
            )
        }
        // Ensure the clipped stages tile [newStart, newEnd]: pad edges with wake if needed.
        if (clipped.first().start > newStart) {
            clipped.add(0, StageSegment(newStart, clipped.first().start, "wake"))
        }
        if (clipped.last().end < newEnd) {
            clipped.add(StageSegment(clipped.last().end, newEnd, "wake"))
        }
        val eff = efficiency(newStart, newEnd, clipped)
        return DetectedSleep(
            start = newStart, end = newEnd, efficiency = eff,
            stages = clipped, restingHR = null, avgHRV = null,
        )
    }

    /**
     * Soft-align gravity bed/wake to the longest contiguous band-asleep stretch overlapping the
     * candidate window (Fable Sleep #27/#28). Returns the original bounds when band is sparse or
     * the stretch is too short / too far from the gravity window. Never invents a night.
     */
    internal fun refineBoundsFromBandState(
        start: Long,
        end: Long,
        bandSleepState: List<Pair<Long, Int>>,
        slackSec: Long = bandBoundSlackSec,
        minAsleepSec: Long = bandBoundMinAsleepSec,
        gapSec: Long = bandBoundSampleGapSec,
    ): Pair<Long, Long> {
        if (bandSleepState.isEmpty() || end <= start) return start to end
        val lo = start - slackSec
        val hi = end + slackSec
        val asleep = bandSleepState
            .asSequence()
            .filter { (ts, st) -> st == bandStateAsleep && ts in lo..hi }
            .map { it.first }
            .sorted()
            .toList()
        if (asleep.size < 3) return start to end

        // Longest contiguous stretch (gaps ≤ gapSec).
        var bestA = asleep[0]
        var bestB = asleep[0]
        var runA = asleep[0]
        var runB = asleep[0]
        for (i in 1 until asleep.size) {
            val t = asleep[i]
            if (t - runB <= gapSec) {
                runB = t
            } else {
                if (runB - runA > bestB - bestA) {
                    bestA = runA
                    bestB = runB
                }
                runA = t
                runB = t
            }
        }
        if (runB - runA > bestB - bestA) {
            bestA = runA
            bestB = runB
        }
        val span = bestB - bestA
        if (span < minAsleepSec) return start to end
        // Must overlap the gravity window and stay within slack of original edges.
        if (bestB < start || bestA > end) return start to end
        if (kotlin.math.abs(bestA - start) > slackSec) return start to end
        if (kotlin.math.abs(bestB - end) > slackSec) return start to end
        // Never shrink below half the gravity span (keeps night from collapsing on sparse band).
        val gravSpan = end - start
        if (span < gravSpan / 2) return start to end
        val newStart = bestA.coerceIn(lo, hi)
        val newEnd = (bestB + 60L).coerceIn(lo, hi) // +1 min pad past last asleep sample
        if (newEnd - newStart < minAsleepSec) return start to end
        return newStart to newEnd
    }

    /** asleep / in-bed in [0, 1]; asleep = in-bed − wake. */
    internal fun efficiency(start: Long, end: Long, stages: List<StageSegment>): Double {
        val inBed = (end - start).toDouble()
        if (inBed <= 0) return 0.0
        val wake = stages.filter { it.stage == "wake" }.sumOf { (it.end - it.start).toDouble() }
        val asleep = maxOf(0.0, inBed - wake)
        return minOf(1.0, asleep / inBed)
    }

    // ── Stage 1–3: staging over a 30 s epoch grid ────────────────────────────

    /** First persistent-sleep epoch (onset) and last sleep epoch (final wake). */
    internal fun onsetAndFinalWake(ckFlags: List<Boolean>): Pair<Int, Int> {
        val n = ckFlags.size
        if (n == 0) return Pair(0, 0)
        var onset: Int? = null
        var run = 0
        for ((i, s) in ckFlags.withIndex()) {
            run = if (s) run + 1 else 0
            if (run >= onsetPersistEpochs) {
                onset = i - onsetPersistEpochs + 1
                break
            }
        }
        var final: Int? = null
        for (i in n - 1 downTo 0) {
            if (ckFlags[i]) {
                final = i
                break
            }
        }
        val o = onset ?: 0
        var f = final ?: (n - 1)
        if (f < o) f = n - 1
        return Pair(o, f)
    }

    /**
     * Build a 30 s hypnogram for [start, end] and return StageSegments.
     *
     * PERF (v7.0.2 / #707): staging is the heaviest per-night step on the model path and it was being
     * re-run for EVERY detected night on EVERY [IntelligenceEngine.analyzeRecent] — ~21× per post-sync
     * pass, again per sleep edit, and up to thousands of nights on the one-shot full-history Effort rescore
     * (maxDays=4000). It is a pure function of (start, end, samples), so this is a thin cache veneer over
     * [stageSessionUncached]: each distinct night stages AT MOST ONCE, peak heap stays flat across repeated
     * passes, and the output is byte-identical (the cached list is the same one the recipe produced, handed
     * back as a fresh copy so a caller extending a segment in place can never poison the cache). Edits
     * invalidate naturally — a moved bed/wake time changes start/end → new key; newly-banked samples change
     * the per-stream count/edge-ts/checksum → new key (see [StagerCache.fingerprint]).
     */
    internal fun stageSession(
        start: Long, end: Long, grav: List<GravitySample>,
        hr: List<HrSample>, rr: List<RrInterval>, resp: List<RespSample>,
    ): List<StageSegment> {
        val key = StagerCache.fingerprint(StagerCache.Version.V1, start, end, grav, hr, rr, resp)
        StagerCache.get(key)?.let { return StagerCache.copyOf(it) }
        val segments = stageSessionUncached(start, end, grav, hr, rr, resp)
        StagerCache.put(key, segments)
        return StagerCache.copyOf(segments)
    }

    /** The pure recipe, exactly as before — extracted so [stageSession] can memoize it. */
    private fun stageSessionUncached(
        start: Long, end: Long, grav: List<GravitySample>,
        hr: List<HrSample>, rr: List<RrInterval>, resp: List<RespSample>,
    ): List<StageSegment> {
        val gSeg = rowsBetween(grav, start, end) { it.ts }
        if (gSeg.size < 2) return listOf(StageSegment(start = start, end = end, stage = "light"))

        val gDeltas = gravityDeltas(gSeg)
        val gTimes = gSeg.map { it.ts }

        val hrSeg = rowsBetween(hr, start, end) { it.ts }
        val rrSeg = rowsBetween(rr, start, end) { it.ts }
        val respSeg = rowsBetween(resp, start, end) { it.ts }

        val grid = buildEpochGrid(
            start = start.toDouble(), end = end.toDouble(),
            gravTimes = gTimes, gravDeltas = gDeltas,
            hr = hrSeg, rr = rrSeg, resp = respSeg,
        )
        if (grid.nEpochs == 0) return listOf(StageSegment(start = start, end = end, stage = "light"))

        val rescaled = rescaleCounts(grid.counts)
        val ckFlags = coleKripke(rescaled)
        val (onsetIdx, finalWakeIdx) = onsetAndFinalWake(ckFlags)

        val dogHR = dogHRVariability(grid.hr)
        val feats = extractFeatures(grid = grid, ckFlags = ckFlags, dogHR = dogHR,
            onsetIdx = onsetIdx, finalWakeIdx = finalWakeIdx)

        var labels = classifyEpochs(feats)
        labels = smoothLabels(labels)
        labels = reimposePhysiology(labels, features = feats,
            onsetIdx = onsetIdx, finalWakeIdx = finalWakeIdx)
        // Mid-bout WASO honesty (Track B / 8.6.237): high-motion or Cole–Kripke wake epochs
        // inside the sleep period → stage "wake" BEFORE merge so quiet arousals are not
        // swallowed as Light. Never invents Deep/REM. Pre-/post-bout wake still forced below.
        labels = promoteMidBoutWake(labels, features = feats,
            onsetIdx = onsetIdx, finalWakeIdx = finalWakeIdx)
        // Conservative fragment merge (#274): absorb sub-3-min stage flecks (the WHOOP 5/MG
        // sparse-motion artefact) so the hypnogram stops reading choppier than WHOOP's,
        // without erasing genuine multi-minute transitions. Display/scoring only — the
        // per-epoch detection above is unchanged.
        labels = mergeFragments(labels)

        // Pre-onset and post-final-wake epochs are not sleep → force wake.
        val mutLabels = labels.toMutableList()
        for (i in mutLabels.indices) {
            if (i < onsetIdx || i > finalWakeIdx) mutLabels[i] = "wake"
        }

        // Merge consecutive same-stage epochs into segments tiling [start, end].
        val segments = ArrayList<StageSegment>()
        for ((i, stage) in mutLabels.withIndex()) {
            val segStart = grid.edges[i].roundToLong()
            val segEnd = grid.edges[i + 1].roundToLong()
            val last = segments.lastOrNull()
            if (last != null && last.stage == stage) {
                segments[segments.size - 1].end = segEnd
            } else {
                segments.add(StageSegment(start = segStart, end = segEnd, stage = stage))
            }
        }
        if (segments.isNotEmpty()) segments[segments.size - 1].end = end
        return segments
    }

    // ── Per-epoch motion (H8 — persisted beside stagesJSON) ───────────────────

    /**
     * The per-epoch MOTION magnitudes for a session window, on the SAME 30 s epoch grid as [stageSession]'s
     * `stagesJSON` (one entry per epoch, in order). Each value is the epoch's summed |Δgravity| (the raw
     * pre-rescale Cole–Kripke activity count) — the strap's own motion signal, banked so later passes and
     * the UI can read per-epoch movement without re-reading the raw gravity stream. Returns `[]` when the
     * window has too little gravity to grid (mirrors [stageSession]'s degenerate fallback), so the caller
     * persists NULL (no fabricated zero series). Pure + deterministic; shares [buildEpochGrid] with staging
     * so the grids align epoch-for-epoch. Mirrors Swift `sessionEpochMotion`. (H8)
     */
    fun sessionEpochMotion(start: Long, end: Long, grav: List<GravitySample>): List<Double> {
        val gSeg = rowsBetween(grav, start, end) { it.ts }
        if (gSeg.size < 2) return emptyList()
        val gDeltas = gravityDeltas(gSeg)
        val gTimes = gSeg.map { it.ts }
        val grid = buildEpochGrid(
            start = start.toDouble(), end = end.toDouble(),
            gravTimes = gTimes, gravDeltas = gDeltas,
            hr = emptyList(), rr = emptyList(), resp = emptyList(),
        )
        return grid.counts
    }

    /**
     * #175: the strap's OWN band sleep_state (0 wake/1 still/2 asleep/3 up) gridded onto the SAME 30 s epoch
     * grid `stagesJSON` / [sessionEpochMotion] use, so the caller can persist it via
     * [com.noop.data.WhoopRepository.persistSessionSleepState] and the H7 re-onset CONFIRM guard can read it
     * back as timestamped `(startTs + i*epochS, state)` samples. Returns EMPTY when the session carries no
     * band-state samples (a WHOOP 4.0, or an unbanded window) — an absent signal stays absent, never a
     * fabricated array. When present, each epoch takes the band's LAST reported state within its
     * `[start + i*30, start + (i+1)*30)` window; an epoch with no sample of its own CARRIES FORWARD the
     * previous epoch's state (band state is a step function). Leading epochs before the first sample take the
     * first sample's state. The band code is carried VERBATIM — this never converts an unproven code into a
     * derived stage; consumers decide meaning. Mirrors Swift `sessionEpochSleepState`. (#175)
     */
    fun sessionEpochSleepState(start: Long, end: Long, sleepState: List<Pair<Long, Int>>): List<Int> {
        val seg = rowsBetween(sleepState, start, end) { it.first }.sortedBy { it.first }
        if (seg.isEmpty() || end <= start) return emptyList()
        val nEpochs = maxOf(1, kotlin.math.ceil((end - start).toDouble() / epochS).toInt())
        val out = IntArray(nEpochs) { seg[0].second }   // lead-in = first sample's state
        var last = seg[0].second
        var si = 0
        for (i in 0 until nEpochs) {
            val epochEnd = start + ((i + 1) * epochS).toLong()
            // Advance through every sample that falls in/at-or-before this epoch's window; the LAST one wins.
            while (si < seg.size && seg[si].first < epochEnd) {
                last = seg[si].second
                si++
            }
            out[i] = last   // carry-forward when the epoch had no sample of its own
        }
        return out.toList()
    }

    // ── Epoch grid ────────────────────────────────────────────────────────────

    internal class EpochGrid(
        val start: Double,
        val end: Double,
        val edges: List<Double>,
        /** per-epoch summed |Δgravity| (raw, pre-rescale). */
        val counts: List<Double>,
        /** scale-robust per-epoch moving-sample fraction. */
        val moveFrac: List<Double>,
        /** per-epoch mean HR (bpm) or NaN. */
        val hr: List<Double>,
        /** per-epoch RR intervals (ms). */
        val rr: List<List<Double>>,
        /** per-epoch raw respiration samples. */
        val resp: List<List<Double>>,
    ) {
        val nEpochs: Int get() = counts.size
        fun epochMid(i: Int): Double = edges[i] + epochS / 2.0
    }

    internal fun buildEpochGrid(
        start: Double, end: Double,
        gravTimes: List<Long>, gravDeltas: List<Double>,
        hr: List<HrSample>, rr: List<RrInterval>, resp: List<RespSample>,
    ): EpochGrid {
        if (end <= start) {
            return EpochGrid(
                start = start, end = end, edges = listOf(start), counts = emptyList(),
                moveFrac = emptyList(), hr = emptyList(), rr = emptyList(), resp = emptyList(),
            )
        }
        val nEpochs = maxOf(1, ceil((end - start) / epochS).toInt())
        val edges = DoubleArray(nEpochs + 1) { start + it.toDouble() * epochS }
        edges[nEpochs] = maxOf(edges[nEpochs], end)

        val counts = DoubleArray(nEpochs)
        val moveN = IntArray(nEpochs)
        val gravN = IntArray(nEpochs)
        val hrSum = DoubleArray(nEpochs)
        val hrCnt = IntArray(nEpochs)
        val rrBuckets = Array(nEpochs) { ArrayList<Double>() }
        val respBuckets = Array(nEpochs) { ArrayList<Double>() }

        fun idx(ts: Double): Int? {
            if (ts < start || ts >= end) {
                if (ts == end) return nEpochs - 1
                return null
            }
            val i = ((ts - start) / epochS).toInt()
            return minOf(i, nEpochs - 1)
        }

        for (k in gravTimes.indices) {
            val i = idx(gravTimes[k].toDouble()) ?: continue
            counts[i] += gravDeltas[k]
            gravN[i] += 1
            if (gravDeltas[k] >= moveDeltaThresholdG) moveN[i] += 1
        }
        for (r in hr) {
            val i = idx(r.ts.toDouble()) ?: continue
            hrSum[i] += r.bpm.toDouble()
            hrCnt[i] += 1
        }
        for (r in rr) {
            val i = idx(r.ts.toDouble()) ?: continue
            rrBuckets[i].add(r.rrMs.toDouble())
        }
        for (r in resp) {
            val i = idx(r.ts.toDouble()) ?: continue
            respBuckets[i].add(r.raw.toDouble())
        }

        val hrMean = List(nEpochs) { if (hrCnt[it] > 0) hrSum[it] / hrCnt[it].toDouble() else Double.NaN }
        // TOP 2026-07-13 sleep Light flood: missing gravity used to force moveFrac=1.0 ("moving")
        // which collapsed staging into wake/light mush on sparse MG nights. Prefer an HR sleep-band
        // proxy so Deep/REM cardiac cues can still fire; never invent Deep/REM — only stillness.
        // Unknown (no grav + no HR): stay STILL (0.0), not 0.5 — 0.5 sat above stageStillMoveFrac and
        // blocked Deep/REM even when a later cardiac sample arrived mid-grid; without HR, classifyOne
        // still cannot invent Deep/REM (hrLow / cardiac gates require finite HR).
        val hrFinite = hrMean.filter { !it.isNaN() }
        val hrMed = if (hrFinite.isEmpty()) Double.NaN else {
            val s = hrFinite.sorted()
            s[s.size / 2]
        }
        val moveFrac = List(nEpochs) { i ->
            if (gravN[i] > 0) {
                moveN[i].toDouble() / gravN[i].toDouble()
            } else {
                val hr = hrMean[i]
                when {
                    hr.isNaN() || hrMed.isNaN() -> 0.0 // unknown → still; cardiac gates still gate Deep/REM
                    hr <= hrMed * hrSleepBandMult -> 0.0 // sleep-band HR → still
                    else -> 1.0 // elevated → wake
                }
            }
        }

        return EpochGrid(
            start = start, end = end, edges = edges.toList(), counts = counts.toList(),
            moveFrac = moveFrac, hr = hrMean,
            rr = rrBuckets.map { it.toList() }, resp = respBuckets.map { it.toList() },
        )
    }

    // ── Cole–Kripke ────────────────────────────────────────────────────────────

    internal fun rescaleCounts(counts: List<Double>): List<Double> =
        counts.map { minOf(it / ckCountDivisor, ckCountClip) }

    internal fun coleKripke(rescaled: List<Double>): List<Boolean> {
        val n = rescaled.size
        val flags = ArrayList<Boolean>(n)
        for (i in 0 until n) {
            var si = 0.0
            for ((k, w) in ckWeights.withIndex()) {
                val j = i - ckBack + k
                val a = if (j in 0 until n) rescaled[j] else 0.0
                si += w * a
            }
            si *= ckScale
            flags.add(si < 1.0)
        }
        return flags
    }

    // ── Walch difference-of-Gaussians HR variability ─────────────────────────

    internal fun gaussianKernel(sigmaS: Double, dtS: Double = epochS): List<Double> {
        val sigma = maxOf(sigmaS / dtS, 1e-6) // σ in epochs
        val radius = maxOf(1, ceil(3 * sigma).toInt())
        val k = ArrayList<Double>(2 * radius + 1)
        for (x in -radius..radius) {
            k.add(exp(-0.5 * (x.toDouble() / sigma).pow(2)))
        }
        val sum = k.sum()
        return k.map { it / sum }
    }

    /** Same-length convolution with reflect padding (edge-stable). */
    internal fun convolveReflect(x: List<Double>, kernel: List<Double>): List<Double> {
        val r = kernel.size / 2
        // A signal shorter than the kernel radius can't be reflect-padded (the mirror reads x[r]
        // and x[x.size-2-i]) — return it unchanged rather than indexing out of bounds. In practice
        // the only caller is gated by the 60-min session floor, so this is defensive.
        if (r == 0 || x.size <= r) return x
        // Reflect padding: numpy 'reflect' mirrors WITHOUT repeating the edge sample.
        val padded = ArrayList<Double>(x.size + 2 * r)
        for (i in 0 until r) padded.add(x[r - i]) // x[r], x[r-1], ... x[1]
        padded.addAll(x)
        for (i in 0 until r) padded.add(x[x.size - 2 - i]) // x[n-2], x[n-3], ...
        // Valid convolution, then take the first x.count outputs.
        val out = ArrayList<Double>(x.size)
        val m = kernel.size
        // np.convolve(padded, kernel, 'valid') has length padded.count - m + 1.
        for (i in 0..(padded.size - m)) {
            var acc = 0.0
            for (j in 0 until m) acc += padded[i + j] * kernel[m - 1 - j]
            out.add(acc)
            if (out.size == x.size) break
        }
        return out
    }

    /**
     * DoG-filtered HR (σ1=120 s minus σ2=600 s). NaNs linearly interpolated first;
     * all-NaN → zeros.
     */
    internal fun dogHRVariability(hrPerEpoch: List<Double>): List<Double> {
        val n = hrPerEpoch.size
        if (n == 0) return emptyList()
        val maskIdx = (0 until n).filter { !hrPerEpoch[it].isNaN() }
        if (maskIdx.isEmpty()) return List(n) { 0.0 }

        // Linear interpolation over the grid (numpy.interp semantics: clamp at edges).
        val filled = DoubleArray(n)
        val first = maskIdx.first()
        val last = maskIdx.last()
        for (i in 0 until n) {
            if (!hrPerEpoch[i].isNaN()) {
                filled[i] = hrPerEpoch[i]
                continue
            }
            // find surrounding known points
            if (i <= first) {
                filled[i] = hrPerEpoch[first]
                continue
            }
            if (i >= last) {
                filled[i] = hrPerEpoch[last]
                continue
            }
            var lo = first
            var hi = last
            for (m in maskIdx) {
                if (m <= i) lo = m
                if (m >= i) {
                    hi = m
                    break
                }
            }
            if (hi == lo) {
                filled[i] = hrPerEpoch[lo]
            } else {
                val frac = (i - lo).toDouble() / (hi - lo).toDouble()
                filled[i] = hrPerEpoch[lo] + frac * (hrPerEpoch[hi] - hrPerEpoch[lo])
            }
        }

        val k1 = gaussianKernel(sigmaS = hrDogSigma1S)
        val k2 = gaussianKernel(sigmaS = hrDogSigma2S)
        val g1 = convolveReflect(filled.toList(), k1)
        val g2 = convolveReflect(filled.toList(), k2)
        return List(n) { g1[it] - g2[it] }
    }

    // ── Respiration rate + RRV (raw 1 Hz) ────────────────────────────────────

    /**
     * Estimate respiratory rate (breaths/min) and RRV (s) from a raw resp window.
     * Detrend → peak-pick (≥2 s apart) → breath intervals (1.5–12 s) → rate =
     * 60/median interval, RRV = std of intervals. (NaN, NaN) when too few samples.
     *
     * Faithful port of sleep_features.resp_rate_and_rrv using a simple local-maxima
     * peak finder. Returned as a Pair(rate, rrv).
     */
    internal fun respRateAndRRV(respRaw: List<Double>, dtS: Double = 1.0): Pair<Double, Double> {
        val nan = Double.NaN
        if (respRaw.size < 8) return Pair(nan, nan)
        val mean = respRaw.sum() / respRaw.size.toDouble()
        val x = respRaw.map { it - mean }
        if (x.all { abs(it) < 1e-12 }) return Pair(nan, nan)

        val std = standardDeviation(x)
        if (std <= 0) return Pair(nan, nan)

        val minDistance = maxOf(2, (2.0 / dtS).roundToInt())
        val peaks = findPeaks(x, distance = minDistance, height = 0.0)
        if (peaks.size < 3) return Pair(nan, nan)

        val intervals = ArrayList<Double>()
        for (i in 1 until peaks.size) {
            val iv = (peaks[i] - peaks[i - 1]).toDouble() * dtS
            if (iv in 1.5..12.0) intervals.add(iv)
        }
        if (intervals.size < 2) return Pair(nan, nan)
        val rate = 60.0 / HrvAnalyzer.median(intervals)
        val rrv = standardDeviation(intervals) // population std (numpy default)
        return Pair(rate, rrv)
    }

    // ── Respiration rate from R-R (RSA) — WHOOP5 on-wire path ────────────────

    /** RSA tachogram resample rate (Hz). 4 Hz is the standard HRV resample grid. */
    private const val rsaResampleHz: Double = 4.0

    /** Moving-mean detrend window for the RSA tachogram (seconds). */
    private const val rsaDetrendWindowS: Double = 8.0

    /** Minimum spacing between breath peaks on the tachogram (seconds) → ≤24 bpm. */
    private const val rsaMinPeakDistanceS: Double = 2.5

    /** Per-window length for the per-window rate estimate (seconds). */
    private const val rsaWindowS: Double = 300.0

    /** Physiologic breath-interval band (seconds): 0.1–0.4 Hz = 6–24 breaths/min. */
    private const val rsaMinBreathIntervalS: Double = 2.5  // 24 bpm
    private const val rsaMaxBreathIntervalS: Double = 10.0 // 6 bpm

    /**
     * THE canonical plausible sleeping-respiratory-rate band (bpm). The RSA peak-pick above can yield
     * 6–8 bpm at its noise floor, but every consumer (ReadinessEngine illness/readiness) only acts on
     * 8–25 — so a sub-8 estimate used to be persisted-then-silently-ignored. respRateFromRR now clamps
     * its output to this band (NaN outside it), and ReadinessEngine references this same range, so the
     * stored value can never disagree with what's acted on. (#78) */
    val respPlausibleRangeBpm: ClosedFloatingPointRange<Double> = 8.0..25.0

    /**
     * APPROXIMATE respiratory rate (breaths/min) from the R-R interval stream via
     * respiratory sinus arrhythmia (RSA), for use when no raw resp ADC channel is
     * available (WHOOP5 v18 wire is RR-only; resp ADC is WHOOP4 / cloud-only).
     *
     * This is an ON-DEVICE ESTIMATE, NOT a cloud/clinical respiration measurement.
     * It recovers the breathing-modulation of beat-to-beat timing, which tracks but
     * does not equal a chest-band / capnography rate.
     *
     * Pipeline (per matched in-bed session [start, end], unix SECONDS):
     *   1. Restrict RR rows to ts in [start, end]; range-filter the RR values
     *      (HrvAnalyzer.rangeFilter) to drop dropouts/ectopics.
     *   2. Reconstruct beat times by cumulatively summing the kept RR intervals
     *      from the first in-bed beat, yielding an (irregular) tachogram
     *      t_k = Σ rr, value_k = rr_k (ms).
     *   3. Resample the tachogram onto a uniform ~4 Hz grid by linear interpolation.
     *   4. Detrend: subtract a centered moving mean (rsaDetrendWindowS).
     *   5. Per ~5-min window: findPeaks (min distance rsaMinPeakDistanceS) on the
     *      detrended grid, keep peak-to-peak intervals in the 6–24 bpm band, rate =
     *      60 / median(intervals). Take the median across windows.
     * Returns NaN when too few intervals survive (honest no-data).
     */
    internal fun respRateFromRR(rr: List<RrInterval>, start: Long, end: Long): Double {
        val nan = Double.NaN
        if (end <= start) return nan

        // 1. In-bed RR rows in chronological order, range-filtered.
        val inBed = rr.asSequence()
            .filter { it.ts in start..end }
            .sortedBy { it.ts }
            .map { it.rrMs.toDouble() }
            .toList()
        val filtered = HrvAnalyzer.rangeFilter(inBed)
        if (filtered.size < 30) return nan // need enough beats for any RSA estimate

        // 2. Reconstruct beat times (seconds from session start) by cumulative sum.
        val beatTimes = DoubleArray(filtered.size)
        var acc = 0.0
        for (i in filtered.indices) {
            acc += filtered[i] / 1000.0
            beatTimes[i] = acc
        }
        val totalSpanS = beatTimes[beatTimes.size - 1]
        if (totalSpanS < rsaWindowS / 2.0) return nan // < ~2.5 min of beats

        // 3. Resample onto a uniform grid by linear interpolation.
        val dt = 1.0 / rsaResampleHz
        val nGrid = (totalSpanS / dt).toInt() + 1
        if (nGrid < 8) return nan
        val grid = DoubleArray(nGrid)
        var seg = 0
        for (g in 0 until nGrid) {
            val t = g * dt
            // advance segment so beatTimes[seg] <= t <= beatTimes[seg+1]
            while (seg < beatTimes.size - 2 && beatTimes[seg + 1] < t) seg += 1
            val t0 = beatTimes[seg]
            val t1 = beatTimes[seg + 1]
            val v0 = filtered[seg]
            val v1 = filtered[seg + 1]
            grid[g] = if (t1 <= t0) v0 else {
                val frac = ((t - t0) / (t1 - t0)).coerceIn(0.0, 1.0)
                v0 + frac * (v1 - v0)
            }
        }

        // 4. Detrend: subtract a centered moving mean (removes slow LF/baseline drift).
        val halfW = maxOf(1, (rsaDetrendWindowS * rsaResampleHz / 2.0).roundToInt())
        val detrended = DoubleArray(nGrid)
        for (i in 0 until nGrid) {
            val lo = maxOf(0, i - halfW)
            val hi = minOf(nGrid - 1, i + halfW)
            var sum = 0.0
            for (j in lo..hi) sum += grid[j]
            val mean = sum / (hi - lo + 1).toDouble()
            detrended[i] = grid[i] - mean
        }
        if (standardDeviation(detrended.toList()) <= 1e-9) return nan // flat → no RSA

        // 5. Per ~5-min window peak-pick → 60/median(breath interval); median across.
        val minDistSamples = maxOf(2, (rsaMinPeakDistanceS * rsaResampleHz).roundToInt())
        val windowSamples = maxOf(minDistSamples * 3, (rsaWindowS * rsaResampleHz).roundToInt())
        val perWindowRates = ArrayList<Double>()
        var w = 0
        while (w < nGrid) {
            val wEnd = minOf(nGrid, w + windowSamples)
            if (wEnd - w >= minDistSamples * 3) {
                val winSeg = ArrayList<Double>(wEnd - w)
                for (k in w until wEnd) winSeg.add(detrended[k])
                // findPeaks with height = 0.0 selects the positive RSA peaks (one per
                // breath) on the zero-mean detrended tachogram.
                val peaks = findPeaks(winSeg, distance = minDistSamples, height = 0.0)
                if (peaks.size >= 3) {
                    val intervals = ArrayList<Double>(peaks.size - 1)
                    for (i in 1 until peaks.size) {
                        val ivS = (peaks[i] - peaks[i - 1]).toDouble() * dt
                        if (ivS in rsaMinBreathIntervalS..rsaMaxBreathIntervalS) intervals.add(ivS)
                    }
                    if (intervals.size >= 2) {
                        val med = HrvAnalyzer.median(intervals)
                        if (med > 0.0) perWindowRates.add(60.0 / med)
                    }
                }
            }
            w += windowSamples
        }
        if (perWindowRates.isEmpty()) return nan
        // Reject estimates outside the canonical consumer band (NaN = "no usable estimate") so the
        // persisted value never silently disagrees with ReadinessEngine's plausibility gate. (#78)
        val median = HrvAnalyzer.median(perWindowRates)
        return if (median in respPlausibleRangeBpm) median else nan
    }

    /**
     * Local-maxima peak finder mirroring scipy.find_peaks(distance, height):
     * a sample is a peak if strictly greater than both neighbours and ≥ height;
     * peaks closer than `distance` are resolved by keeping the taller.
     */
    internal fun findPeaks(x: List<Double>, distance: Int, height: Double): List<Int> {
        val n = x.size
        if (n < 3) return emptyList()
        val candidates = ArrayList<Int>()
        var i = 1
        while (i < n - 1) {
            if (x[i] > x[i - 1] && x[i] >= height) {
                // handle flat plateaus: find right edge of the plateau
                var j = i
                while (j + 1 < n && x[j + 1] == x[i]) j += 1
                if (j + 1 < n && x[j + 1] < x[i]) {
                    candidates.add((i + j) / 2) // plateau midpoint
                }
                i = j + 1
            } else {
                i += 1
            }
        }
        if (distance <= 1 || candidates.isEmpty()) return candidates
        // Enforce minimum distance: greedily keep tallest, scipy-style.
        val byHeight = candidates.sortedByDescending { x[it] }
        val keep = BooleanArray(candidates.size) { true }
        val indexOf = HashMap<Int, Int>(candidates.size)
        for ((off, c) in candidates.withIndex()) indexOf[c] = off
        for (p in byHeight) {
            val pi = indexOf[p] ?: continue
            if (!keep[pi]) continue
            for ((qi, q) in candidates.withIndex()) {
                if (qi != pi && keep[qi]) {
                    if (abs(q - p) < distance) keep[qi] = false
                }
            }
        }
        return candidates.filterIndexed { off, _ -> keep[off] }.sorted()
    }

    // ── Per-epoch features ──────────────────────────────────────────────────

    internal class EpochFeatures(
        val index: Int,
        val midTs: Double,
        /** rescaled Cole–Kripke activity count. */
        val count: Double,
        val moveFrac: Double,
        val ckSleep: Boolean,
        /** mean HR over the feature window. */
        val hr: Double,
        /** Walch DoG-HR windowed std. */
        val hrVar: Double,
        /** ms. */
        val rmssd: Double,
        /** ms. */
        val sdnn: Double,
        /** breaths/min. */
        val respRate: Double,
        /** respiratory-rate variability (s). */
        val rrv: Double,
        /** normalized time since onset, 0..1. */
        val clock: Double,
    )

    internal fun extractFeatures(
        grid: EpochGrid, ckFlags: List<Boolean>, dogHR: List<Double>,
        onsetIdx: Int, finalWakeIdx: Int,
    ): List<EpochFeatures> {
        val n = grid.nEpochs
        val rescaled = rescaleCounts(grid.counts)
        val halfW = (featureWindowS / epochS / 2).roundToInt()
        val span = maxOf(1, finalWakeIdx - onsetIdx).toDouble()

        val feats = ArrayList<EpochFeatures>(n)
        for (i in 0 until n) {
            val lo = maxOf(0, i - halfW)
            val hi = minOf(n, i + halfW + 1)

            val winHR = (lo until hi).map { grid.hr[it] }.filter { !it.isNaN() }
            val hrMean = if (winHR.isEmpty()) Double.NaN else winHR.sum() / winHR.size.toDouble()

            val winDog = (lo until hi).map { if (dogHR.isEmpty()) 0.0 else dogHR[it] }
            val hrVar = if (winDog.size >= 2) standardDeviation(winDog) else Double.NaN

            // RMSSD/SDNN over the pooled RR window (range-filtered, like the
            // Python per-epoch hrv_from_rr which uses RAW range-filtered RR).
            val winRR = ArrayList<Double>()
            for (j in lo until hi) winRR.addAll(grid.rr[j])
            val filteredRR = HrvAnalyzer.rangeFilter(winRR)
            val rmssd = if (filteredRR.size >= 5) (HrvAnalyzer.rmssdRaw(filteredRR) ?: Double.NaN) else Double.NaN
            val sdnn = if (filteredRR.size >= 5) (HrvAnalyzer.sdnnRaw(filteredRR) ?: Double.NaN) else Double.NaN

            val winResp = ArrayList<Double>()
            for (j in lo until hi) winResp.addAll(grid.resp[j])
            val (respRate, rrv) = respRateAndRRV(winResp)

            val clock = minOf(1.0, maxOf(0.0, (i - onsetIdx).toDouble() / span))

            feats.add(
                EpochFeatures(
                    index = i, midTs = grid.epochMid(i), count = rescaled[i],
                    moveFrac = grid.moveFrac[i],
                    ckSleep = if (i < ckFlags.size) ckFlags[i] else true,
                    hr = hrMean, hrVar = hrVar, rmssd = rmssd, sdnn = sdnn,
                    respRate = respRate, rrv = rrv, clock = clock,
                )
            )
        }
        return feats
    }

    // ── Percentile helper ─────────────────────────────────────────────────────

    /** numpy-style linear-interpolated percentile over finite values; null if none. */
    internal fun percentile(values: List<Double>, pct: Double): Double? {
        val vals = values.filter { it.isFinite() }.sorted()
        if (vals.isEmpty()) return null
        return percentileSorted(vals, pct)
    }

    /**
     * Linear-interpolated percentile of an already-sorted sequence (numpy-style).
     * Inlined from Swift `StrainScorer.percentile` (not yet ported to Kotlin); same
     * algorithm so a later StrainScorer port stays consistent.
     */
    private fun percentileSorted(sortedValues: List<Double>, pct: Double): Double {
        val n = sortedValues.size
        if (n == 0) return 0.0
        if (n == 1) return sortedValues[0]
        val position = (pct / 100.0) * (n - 1).toDouble()
        val lower = position.toInt()
        val upper = minOf(lower + 1, n - 1)
        val frac = position - lower.toDouble()
        return sortedValues[lower] + frac * (sortedValues[upper] - sortedValues[lower])
    }

    // ── Classifier seam (Stage 2) ─────────────────────────────────────────────

    internal fun classifyEpochs(features: List<EpochFeatures>): List<String> {
        val n = features.size
        if (n == 0) return emptyList()

        // Session-relative reference distributions over SLEEP-PERIOD epochs.
        val sleepFeats = if (features.any { it.ckSleep }) features.filter { it.ckSleep } else features
        val hrLo = percentile(sleepFeats.map { it.hr }, stageHRLowPct)
        val hrHi = percentile(sleepFeats.map { it.hr }, stageHRHighPct)
        val rmssdHi = percentile(sleepFeats.map { it.rmssd }, stageHRVHighPct)
        val hrvarHi = percentile(sleepFeats.map { it.hrVar }, stageHRVarHighPct)
        val rrvHi = percentile(sleepFeats.map { it.rrv }, stageRRVHighPct)
        val rrvLo = percentile(sleepFeats.map { it.rrv }, stageRRVLowPct)
        val cardiacSparse = isCardiacSparse(sleepFeats)

        return features.map {
            classifyOne(it, hrLo = hrLo, hrHi = hrHi, rmssdHi = rmssdHi,
                hrvarHi = hrvarHi, rrvHi = rrvHi, rrvLo = rrvLo,
                cardiacSparse = cardiacSparse)
        }
    }

    /**
     * Session-level PPG-derived / sparse-cardiac tell: most sleep-period epochs carry NO finite
     * per-epoch RMSSD (sparse R-R). On those nights the HR is PPG-derived and its windowed variance
     * (`hrVar`) is noisier, so the percentile `hrvarHigh` bar fires on genuinely still, low-HR sleep —
     * which the WAKE rule must NOT treat as cardiac activation. Same `!rmssd.isFinite()` signal already
     * trusted for the pro-deep RMSSD handling (#127/#129), aggregated across the night. (#705)
     */
    internal fun isCardiacSparse(sleepFeats: List<EpochFeatures>): Boolean {
        if (sleepFeats.isEmpty()) return false
        val sparse = sleepFeats.count { !it.rmssd.isFinite() }
        return sparse.toDouble() >= cardiacSparseEpochFrac * sleepFeats.size.toDouble()
    }

    internal fun classifyOne(
        f: EpochFeatures, hrLo: Double?, hrHi: Double?,
        rmssdHi: Double?, hrvarHi: Double?, rrvHi: Double?, rrvLo: Double?,
        cardiacSparse: Boolean = false,
    ): String {
        val hasHR = f.hr.isFinite()
        val hrLow = hasHR && hrLo != null && f.hr <= hrLo
        val hrHigh = hasHR && hrHi != null && f.hr >= hrHi

        // NOTE: HF omitted (no neurokit2). Parasympathetic tone = RMSSD only.
        // Dense nights (finite R-R): missing per-epoch RMSSD is NOT pro-deep — require the
        // high-tone bar so Light↔Deep is HRV-dependent. Sparse cardiac nights still treat
        // missing RMSSD as OK (BLE-offload reopen, #127/#129).
        val parasympOK = when {
            f.rmssd.isFinite() -> rmssdHi != null && f.rmssd >= rmssdHi
            cardiacSparse -> true
            else -> false
        }

        val hrvarHigh = f.hrVar.isFinite() && hrvarHi != null && f.hrVar >= hrvarHi
        val cardiacActivated = hrHigh || hrvarHigh

        // WAKE-specific cardiac vetting. On a PPG-derived / sparse-cardiac night the per-epoch HR-variance
        // is noisy, so `hrvarHigh` fires on still, low-HR sleep and used to flip those epochs to WAKE. When
        // the session is sparse we DOWN-WEIGHT hrVar for the wake promotion and require a real elevated HR
        // (`hrHigh`) — the down-weighting mirrors how sparse R-R is trusted for the pro-deep RMSSD handling.
        // Dense 4.0 nights keep the full `hrHigh || hrvarHigh` signal, so their behaviour is unchanged. (#705)
        val cardiacActivatedForWake = if (cardiacSparse) hrHigh else cardiacActivated

        val rrvIrregular = f.rrv.isFinite() && rrvHi != null && f.rrv >= rrvHi
        // Missing respiration (NaN RRV) treated as "regular" (pro-deep bias).
        val rrvRegular = (!f.rrv.isFinite()) || (rrvLo != null && f.rrv <= rrvLo)

        val stillBar = if ((softPriors.bmi ?: 0.0) >= 35.0) {
            stageStillMoveFrac * 1.15
        } else {
            stageStillMoveFrac
        }
        val still = f.moveFrac <= stillBar
        val moving = f.moveFrac >= stageWakeMoveFrac

        // WAKE: sustained motion + activated cardiac (or no HR to vet motion). On a sparse/PPG night the
        // cardiac half is vetted by HR only (see `cardiacActivatedForWake`), so noisy hrVar no longer
        // over-promotes still sleep to wake. (#705)
        if (moving && (cardiacActivatedForWake || !hasHR)) return "wake"
        // DEEP: still + low HR + regular respiration, with high parasympathetic tone when measurable.
        // On cardiac-sparse nights (missing RMSSD / RRV common on BLE-offload), also accept still +
        // low HR alone when resp is absent — reopen deep without inventing from HC.
        if (still && parasympOK && hrLow && rrvRegular) return "deep"
        if (cardiacSparse && still && hrLow && !f.rrv.isFinite()) return "deep"
        // Mid-HR + high-RMSSD Deep reopen: high parasymp can tip still epochs that miss hrLow
        // (classic Light flood with deep physiology on HRV). Never when hrHigh (REM territory).
        if (still && parasympOK && rrvRegular && hasHR && !hrHigh && f.rmssd.isFinite()) return "deep"
        // REM: still body + activated cardiac + irregular respiration.
        if (still && cardiacActivated && rrvIrregular) return "rem"
        // REM fallback when respiration unavailable: require BOTH cardiac signals.
        if (still && hrHigh && hrvarHigh && !f.rrv.isFinite()) return "rem"
        // Sparse / no-resp late-night REM: still + elevated HR alone after the first third of the night.
        if (cardiacSparse && still && hrHigh && !f.rrv.isFinite() && f.clock > deepFirstFraction) return "rem"
        // Dense-but-thin REM reopen (13 Jul pack: WHOOP 16% REM, NOOP 0%): still + hrHigh mid/late night
        // when RRV missing — does not invent REM from HC, only from strap HR.
        if (still && hrHigh && !f.rrv.isFinite() && f.clock > deepFirstFraction) return "rem"
        return "light"
    }

    // ── Post-processing (Stage 3) ─────────────────────────────────────────────

    internal fun smoothLabels(labels: List<String>, window: Int = smoothEpochs): List<String> {
        val n = labels.size
        if (n == 0 || window <= 1) return labels
        var w = window
        if (w % 2 == 0) w += 1
        val half = w / 2
        val out = ArrayList<String>(n)
        for (i in 0 until n) {
            val lo = maxOf(0, i - half)
            val hi = minOf(n, i + half + 1)
            val counts = HashMap<String, Int>()
            val order = ArrayList<String>()
            for (idx in lo until hi) {
                val s = labels[idx]
                if (counts[s] == null) order.add(s)
                counts[s] = (counts[s] ?: 0) + 1
            }
            val best = counts.values.maxOrNull()
            if (best == null) { out.add(labels[i]); continue }
            val winners = order.filter { counts[it] == best } // insertion order preserved
            out.add(if (winners.contains(labels[i])) labels[i] else winners[0])
        }
        return out
    }

    internal fun reimposePhysiology(
        labels: List<String>, features: List<EpochFeatures>,
        onsetIdx: Int, finalWakeIdx: Int,
    ): List<String> {
        val out = labels.toMutableList()
        val noREMEpochs = (noREMAfterOnsetMin * 60.0 / epochS).roundToInt()
        // "Deep is front-loaded" is a soft prior, not a hard wipe. Jul-13 WHOOP Deep ~33% of the night
        // spans past the first third; stripping every late deep epoch collapsed MAIN to ~2%. Only demote
        // late deep when early deep is already ADEQUATE (≥15% of the sleep window) AND the epoch fails
        // parasympathetic tone — clock alone must not turn HRV-supported late Deep into Light.
        val sleepLen = (finalWakeIdx - onsetIdx + 1).coerceAtLeast(1)
        val deepFrac = if ((softPriors.ageYears ?: 0.0) >= 65.0) {
            (deepFirstFraction * 1.15).coerceAtMost(0.45)
        } else {
            deepFirstFraction
        }
        val earlyDeepN = labels.indices.count {
            it in onsetIdx..finalWakeIdx && labels[it] == "deep" && features[it].clock <= deepFrac
        }
        val earlyDeepAdequate = earlyDeepN.toDouble() >= 0.15 * sleepLen.toDouble()
        val sleepFeats = features.filterIndexed { i, _ -> i in onsetIdx..finalWakeIdx }
        val rmssdHi = percentile(sleepFeats.map { it.rmssd }, stageHRVHighPct)
        val cardiacSparse = isCardiacSparse(sleepFeats)
        for ((i, f) in features.withIndex()) {
            if (i < onsetIdx || i > finalWakeIdx) continue
            if (out[i] == "rem" && (i - onsetIdx) < noREMEpochs) out[i] = "light"
            if (out[i] == "deep" && f.clock > deepFrac && earlyDeepAdequate) {
                val parasympOK = when {
                    f.rmssd.isFinite() -> rmssdHi != null && f.rmssd >= rmssdHi
                    cardiacSparse -> true
                    else -> false
                }
                // Keep late Deep when HRV still supports it; demote only when parasymp fails.
                if (!parasympOK) out[i] = "light"
            }
        }
        return out
    }

    /**
     * Mid-bout WASO promotion (Track B): inside [onsetIdx, finalWakeIdx], force stage `"wake"`
     * when the epoch is high-motion (`moveFrac ≥ stageWakeMoveFrac`) or Cole–Kripke wake
     * (`!ckSleep`). Runs after smooth/reimpose and **before** [mergeFragments] so sustained
     * arousals survive into TST/WASO accounting instead of falling through to Light.
     * Does not invent Deep/REM; does not touch pre-onset / post-final epochs (forced wake later).
     */
    internal fun promoteMidBoutWake(
        labels: List<String>,
        features: List<EpochFeatures>,
        onsetIdx: Int,
        finalWakeIdx: Int,
    ): List<String> {
        if (labels.isEmpty() || features.size != labels.size) return labels
        val out = labels.toMutableList()
        val lo = onsetIdx.coerceAtLeast(0)
        val hi = finalWakeIdx.coerceAtMost(labels.lastIndex)
        if (lo > hi) return labels
        for (i in lo..hi) {
            val f = features[i]
            if (f.moveFrac >= stageWakeMoveFrac || !f.ckSleep) {
                out[i] = "wake"
            }
        }
        return out
    }

    // ── REM-funnel diagnostic (#688) ──────────────────────────────────────────

    // 0% REM over a whole night is physiologically implausible (healthy adults cycle ~20–25% REM),
    // so a 0%-REM hypnogram — common on WHOOP 4.0 nights staged WITHOUT a respiration channel —
    // points at the STAGER, not the sleeper. The REM path in [classifyOne] is gated by three
    // predicates (still body + activated cardiac + irregular respiration), with a no-resp fallback
    // (still + high HR + high HR-variability), and any surviving early-REM is then stripped by the
    // no-REM-after-onset re-imposition. This pure, READ-ONLY diagnostic re-runs that exact funnel and
    // counts where REM was lost — WITHOUT changing a single label or score. It is a triage surface,
    // logged by the caller, never a scoring change. Mirrors Swift `remFunnelDiagnostic`. (#688)

    /**
     * Why REM funneled toward zero for one staged session window. Counts are over the SLEEP-PERIOD
     * epochs (onset…finalWake) the classifier actually ranges; pure + deterministic; shares the exact
     * classifier seam with [stageSession]. Mirrors Swift `SleepStager.REMFunnelDiagnostic`. (#688)
     */
    data class REMFunnelDiagnostic(
        /** Sleep-period epochs considered (onset…finalWake inclusive). */
        val sleepEpochs: Int,
        /** Epochs the classifier labelled "rem" BEFORE smoothing / re-imposition. */
        val remAtClassify: Int,
        /** "rem" epochs surviving the no-REM-after-onset re-imposition (the final hypnogram's REM). */
        val remAfterReimpose: Int,
        /** Classified-REM epochs stripped specifically by the 15-min onset guard. */
        val remStrippedByOnsetGuard: Int,
        /**
         * Whether ANY epoch carried a finite respiration-variability feature (the resp channel was
         * usable). False ⇒ the whole night ran the no-resp REM fallback — the dominant 4.0 cause.
         */
        val respChannelPresent: Boolean,
        /** Body not still enough (moveFrac above the still bar). */
        val blockedNotStill: Int,
        /** Neither HR-high nor HR-variability-high. */
        val blockedNoCardiacActivation: Int,
        /** Resp present but NOT irregular (regular breathing). */
        val blockedRespRegular: Int,
        /** Resp absent and the stricter no-resp REM bar unmet. */
        val blockedNoRespFallbackBar: Int,
        /** Won a non-REM stage outright (wake/deep/light) before any REM gate — not a REM rejection. */
        val wonOtherStage: Int,
    ) {
        /** True when the final hypnogram carries no REM at all — the case this diagnostic triages. */
        val isZeroREM: Boolean get() = remAfterReimpose == 0

        /** One human-readable line for the caller to LOG. No I/O here — the engine stays pure. */
        val summary: String
            get() = "REM-funnel: $sleepEpochs sleep-epochs, classify=$remAtClassify rem, " +
                "final=$remAfterReimpose rem (onset-guard stripped $remStrippedByOnsetGuard); " +
                "resp=${if (respChannelPresent) "present" else "ABSENT"}; " +
                "blocked[notStill=$blockedNotStill, noCardiac=$blockedNoCardiacActivation, " +
                "respRegular=$blockedRespRegular, noRespBar=$blockedNoRespFallbackBar], " +
                "otherStage=$wonOtherStage"
    }

    /**
     * Per-epoch reason REM was rejected, evaluated in classifier precedence order. `REM_ELIGIBLE`
     * means the epoch WOULD be labelled REM. Internal — drives [remFunnelDiagnostic].
     */
    internal enum class REMRejectReason {
        REM_ELIGIBLE, WON_OTHER_STAGE, NOT_STILL, NO_CARDIAC_ACTIVATION, RESP_REGULAR, NO_RESP_FALLBACK_BAR
    }

    /**
     * Classify a single epoch's REM-eligibility AND, when not eligible, the FIRST reason it failed —
     * using the exact predicates and precedence of [classifyOne] so the diagnostic can never diverge
     * from the real classifier. Read-only. Mirrors Swift `remRejectReason`. (#688)
     */
    internal fun remRejectReason(
        f: EpochFeatures, hrLo: Double?, hrHi: Double?,
        rmssdHi: Double?, hrvarHi: Double?, rrvHi: Double?, rrvLo: Double?,
        cardiacSparse: Boolean = false,
    ): REMRejectReason {
        // Mirror classifyOne's derived predicates exactly.
        val hasHR = f.hr.isFinite()
        val hrLow = hasHR && hrLo != null && f.hr <= hrLo
        val hrHigh = hasHR && hrHi != null && f.hr >= hrHi
        val parasympOK = when {
            f.rmssd.isFinite() -> rmssdHi != null && f.rmssd >= rmssdHi
            cardiacSparse -> true
            else -> false
        }
        val hrvarHigh = f.hrVar.isFinite() && hrvarHi != null && f.hrVar >= hrvarHi
        val cardiacActivated = hrHigh || hrvarHigh
        val cardiacActivatedForWake = if (cardiacSparse) hrHigh else cardiacActivated
        val rrvIrregular = f.rrv.isFinite() && rrvHi != null && f.rrv >= rrvHi
        val rrvRegular = (!f.rrv.isFinite()) || (rrvLo != null && f.rrv <= rrvLo)
        val still = f.moveFrac <= stageStillMoveFrac
        val moving = f.moveFrac >= stageWakeMoveFrac

        // classifyOne precedence: WAKE, then DEEP, then REM (then REM fallback), else LIGHT.
        // An epoch that wins WAKE or DEEP was never a REM candidate.
        if (moving && (cardiacActivatedForWake || !hasHR)) return REMRejectReason.WON_OTHER_STAGE  // wake
        if (still && parasympOK && hrLow && rrvRegular) return REMRejectReason.WON_OTHER_STAGE // deep
        if (cardiacSparse && still && hrLow && !f.rrv.isFinite()) return REMRejectReason.WON_OTHER_STAGE // sparse deep
        if (still && parasympOK && rrvRegular && hasHR && !hrHigh && f.rmssd.isFinite()) {
            return REMRejectReason.WON_OTHER_STAGE // mid-HR + high-RMSSD deep
        }
        // From here the epoch did NOT win wake/deep; it is either REM or falls through to LIGHT.
        if (still && cardiacActivated && rrvIrregular) return REMRejectReason.REM_ELIGIBLE
        if (still && hrHigh && hrvarHigh && !f.rrv.isFinite()) return REMRejectReason.REM_ELIGIBLE
        if (cardiacSparse && still && hrHigh && !f.rrv.isFinite() && f.clock > deepFirstFraction) {
            return REMRejectReason.REM_ELIGIBLE
        }
        if (still && hrHigh && !f.rrv.isFinite() && f.clock > deepFirstFraction) {
            return REMRejectReason.REM_ELIGIBLE
        }
        // Not REM — attribute to the FIRST unmet REM precondition (in REM-rule order).
        if (!still) return REMRejectReason.NOT_STILL
        if (!cardiacActivated) return REMRejectReason.NO_CARDIAC_ACTIVATION
        if (f.rrv.isFinite()) return REMRejectReason.RESP_REGULAR  // resp present but not irregular
        return REMRejectReason.NO_RESP_FALLBACK_BAR                 // resp absent and no-resp bar unmet
    }

    /**
     * Read-only REM-funnel triage for ONE in-bed window [start, end] (#688). Re-runs the SAME
     * Stage-0→3 staging seam [stageSession] uses (epoch grid → Cole–Kripke → features → classify →
     * smooth → re-impose), but instead of emitting a hypnogram it COUNTS where REM was lost. Changes
     * NOTHING: no label, no score, no session. Returns null only when the window has too little gravity
     * to grid (mirroring [stageSession]'s degenerate fallback, which carries no REM to explain). The
     * caller logs `.summary`; tests assert the counts. Pure + deterministic. Mirrors Swift. (#688)
     */
    fun remFunnelDiagnostic(
        start: Long, end: Long, grav: List<GravitySample>,
        hr: List<HrSample>, rr: List<RrInterval>, resp: List<RespSample>,
    ): REMFunnelDiagnostic? {
        val gSeg = rowsBetween(grav, start, end) { it.ts }
        if (gSeg.size < 2) return null
        val gDeltas = gravityDeltas(gSeg)
        val gTimes = gSeg.map { it.ts }
        val hrSeg = rowsBetween(hr, start, end) { it.ts }
        val rrSeg = rowsBetween(rr, start, end) { it.ts }
        val respSeg = rowsBetween(resp, start, end) { it.ts }

        val grid = buildEpochGrid(
            start = start.toDouble(), end = end.toDouble(),
            gravTimes = gTimes, gravDeltas = gDeltas,
            hr = hrSeg, rr = rrSeg, resp = respSeg,
        )
        if (grid.nEpochs == 0) return null

        val rescaled = rescaleCounts(grid.counts)
        val ckFlags = coleKripke(rescaled)
        val (onsetIdx, finalWakeIdx) = onsetAndFinalWake(ckFlags)
        val dogHR = dogHRVariability(grid.hr)
        val feats = extractFeatures(grid = grid, ckFlags = ckFlags, dogHR = dogHR,
            onsetIdx = onsetIdx, finalWakeIdx = finalWakeIdx)

        // The SAME session-relative reference percentiles classifyEpochs derives.
        val sleepFeats = if (feats.any { it.ckSleep }) feats.filter { it.ckSleep } else feats
        val hrLo = percentile(sleepFeats.map { it.hr }, stageHRLowPct)
        val hrHi = percentile(sleepFeats.map { it.hr }, stageHRHighPct)
        val rmssdHi = percentile(sleepFeats.map { it.rmssd }, stageHRVHighPct)
        val hrvarHi = percentile(sleepFeats.map { it.hrVar }, stageHRVarHighPct)
        val rrvHi = percentile(sleepFeats.map { it.rrv }, stageRRVHighPct)
        val rrvLo = percentile(sleepFeats.map { it.rrv }, stageRRVLowPct)
        val cardiacSparse = isCardiacSparse(sleepFeats)

        // Classify + post-process exactly as stageSession does, so we explain the SAME hypnogram.
        val labels = classifyEpochs(feats)
        val smoothed = smoothLabels(labels)
        val reimposed = reimposePhysiology(smoothed, features = feats,
            onsetIdx = onsetIdx, finalWakeIdx = finalWakeIdx)

        val noREMEpochs = (noREMAfterOnsetMin * 60.0 / epochS).roundToInt()
        var sleepEpochs = 0; var remAtClassify = 0; var remAfterReimpose = 0; var remStrippedByOnsetGuard = 0
        var blockedNotStill = 0; var blockedNoCardiacActivation = 0; var blockedRespRegular = 0
        var blockedNoRespFallbackBar = 0; var wonOtherStage = 0
        var respChannelPresent = false

        for (i in onsetIdx..maxOf(onsetIdx, finalWakeIdx)) {
            if (i >= feats.size) break
            val f = feats[i]
            sleepEpochs += 1
            if (f.rrv.isFinite()) respChannelPresent = true
            // Per-epoch REM reason at the raw classifier seam (pre-smoothing) — the funnel's mouth.
            when (remRejectReason(f, hrLo = hrLo, hrHi = hrHi, rmssdHi = rmssdHi,
                hrvarHi = hrvarHi, rrvHi = rrvHi, rrvLo = rrvLo,
                cardiacSparse = cardiacSparse)) {
                REMRejectReason.REM_ELIGIBLE -> remAtClassify += 1
                REMRejectReason.WON_OTHER_STAGE -> wonOtherStage += 1
                REMRejectReason.NOT_STILL -> blockedNotStill += 1
                REMRejectReason.NO_CARDIAC_ACTIVATION -> blockedNoCardiacActivation += 1
                REMRejectReason.RESP_REGULAR -> blockedRespRegular += 1
                REMRejectReason.NO_RESP_FALLBACK_BAR -> blockedNoRespFallbackBar += 1
            }
            // Final-hypnogram REM (post smooth + re-impose) and the onset-guard strip.
            if (reimposed[i] == "rem") remAfterReimpose += 1
            // The re-imposition strips a SMOOTHED "rem" epoch inside the onset guard → light; count
            // the strip off the smoothed labels reimpose actually sees (exact, not the raw seam).
            if (smoothed[i] == "rem" && (i - onsetIdx) < noREMEpochs) remStrippedByOnsetGuard += 1
        }

        return REMFunnelDiagnostic(
            sleepEpochs = sleepEpochs, remAtClassify = remAtClassify, remAfterReimpose = remAfterReimpose,
            remStrippedByOnsetGuard = remStrippedByOnsetGuard, respChannelPresent = respChannelPresent,
            blockedNotStill = blockedNotStill, blockedNoCardiacActivation = blockedNoCardiacActivation,
            blockedRespRegular = blockedRespRegular, blockedNoRespFallbackBar = blockedNoRespFallbackBar,
            wonOtherStage = wonOtherStage,
        )
    }

    // ── Light-funnel diagnostic (Track C / measure-before-knobs, 2026-07-20) ─────

    // Light is the classifyOne FALLTHROUGH: every sleep-period epoch that does not win wake /
    // deep / rem. A Light-flood night is usually "Deep gates unmet" (notStill / midHR /
    // parasympFail / respIrregular), then inflated further by reimpose (REM-onset→light,
    // late-deep→light) and mergeFragments (tie→lighter). This READ-ONLY diagnostic re-runs
    // the same seam as [stageSession] / [remFunnelDiagnostic] and COUNTS where Light came
    // from — WITHOUT changing knobs, labels, or scores. Measure first; do not touch
    // stageHRLowPct / stageStillMoveFrac / stageHRVHighPct from this surface.

    /**
     * Why Light dominated one staged session window. Counts are over SLEEP-PERIOD epochs
     * (onset…finalWake). Classifier-mouth buckets partition those epochs; demote counters
     * explain post-classify Light inflation. Pure + deterministic. Mirrors [REMFunnelDiagnostic].
     */
    data class LightFunnelDiagnostic(
        /** Sleep-period epochs considered (onset…finalWake inclusive). */
        val sleepEpochs: Int,
        /** Epochs labelled "light" at the raw classifier seam (pre-smooth). */
        val lightAtClassify: Int,
        /** "light" after smooth + [reimposePhysiology] (before WASO promote / merge). */
        val lightAfterReimpose: Int,
        /** "light" after [promoteMidBoutWake] (before [mergeFragments]). */
        val lightAfterWaso: Int,
        /** "light" after [mergeFragments] — the final hypnogram's Light inside the sleep period. */
        val lightAfterMerge: Int,
        /** Classifier wins (not Light). */
        val wonWake: Int,
        val wonDeep: Int,
        val wonRem: Int,
        /** Light fallthrough — first unmet Deep gate (Deep is the primary alternative to Light). */
        val blockedNotStill: Int,
        val blockedParasympFail: Int,
        val blockedMidHR: Int,
        val blockedRespIrregular: Int,
        /** Reimpose demotes → light (counted off smoothed labels, matching reimpose inputs). */
        val demotedRemOnset: Int,
        val demotedLateDeep: Int,
        /** [promoteMidBoutWake] flipped a non-wake epoch to wake (often draining Light). */
        val promotedWasoWake: Int,
        /** mergeFragments flipped a non-light epoch to light inside the sleep period. */
        val demotedByMerge: Int,
        /** Whether ANY sleep-period epoch carried finite RMSSD (dense R-R vs sparse/PPG). */
        val rmssdChannelPresent: Boolean,
    ) {
        /** True when the final sleep-period hypnogram is entirely Light (classic flood). */
        val isMonoLight: Boolean get() = sleepEpochs > 0 && lightAfterMerge == sleepEpochs

        /** One human-readable line for the caller to LOG. No I/O — engine stays pure. */
        val summary: String
            get() = "Light-funnel: $sleepEpochs sleep-epochs, classify=$lightAtClassify light, " +
                "reimpose=$lightAfterReimpose, waso=$lightAfterWaso, final=$lightAfterMerge light; " +
                "won[wake=$wonWake, deep=$wonDeep, rem=$wonRem]; " +
                "blocked[notStill=$blockedNotStill, parasympFail=$blockedParasympFail, " +
                "midHR=$blockedMidHR, respIrregular=$blockedRespIrregular]; " +
                "demote[remOnset=$demotedRemOnset, lateDeep=$demotedLateDeep, merge=$demotedByMerge]; " +
                "wasoPromote=$promotedWasoWake; " +
                "rmssd=${if (rmssdChannelPresent) "present" else "ABSENT"}"
    }

    /**
     * Per-epoch classify outcome for the Light funnel. `LIGHT_*` means fallthrough to light,
     * attributed to the FIRST unmet Deep precondition (Deep-rule order). Internal.
     */
    internal enum class LightClassifyReason {
        WON_WAKE, WON_DEEP, WON_REM,
        NOT_STILL, PARASYMP_FAIL, MID_HR, RESP_IRREGULAR,
    }

    /**
     * Classify a single epoch's Light-fallthrough reason using the exact predicates and
     * precedence of [classifyOne] (including BMI still-bar and sparse-cardiac Deep/REM paths).
     * Read-only — must never diverge from the real classifier.
     */
    internal fun lightFallthroughReason(
        f: EpochFeatures, hrLo: Double?, hrHi: Double?,
        rmssdHi: Double?, hrvarHi: Double?, rrvHi: Double?, rrvLo: Double?,
        cardiacSparse: Boolean = false,
    ): LightClassifyReason {
        val hasHR = f.hr.isFinite()
        val hrLow = hasHR && hrLo != null && f.hr <= hrLo
        val hrHigh = hasHR && hrHi != null && f.hr >= hrHi
        val parasympOK = when {
            f.rmssd.isFinite() -> rmssdHi != null && f.rmssd >= rmssdHi
            cardiacSparse -> true
            else -> false
        }
        val hrvarHigh = f.hrVar.isFinite() && hrvarHi != null && f.hrVar >= hrvarHi
        val cardiacActivated = hrHigh || hrvarHigh
        val cardiacActivatedForWake = if (cardiacSparse) hrHigh else cardiacActivated
        val rrvIrregular = f.rrv.isFinite() && rrvHi != null && f.rrv >= rrvHi
        val rrvRegular = (!f.rrv.isFinite()) || (rrvLo != null && f.rrv <= rrvLo)
        val stillBar = if ((softPriors.bmi ?: 0.0) >= 35.0) {
            stageStillMoveFrac * 1.15
        } else {
            stageStillMoveFrac
        }
        val still = f.moveFrac <= stillBar
        val moving = f.moveFrac >= stageWakeMoveFrac

        // Mirror classifyOne precedence exactly.
        if (moving && (cardiacActivatedForWake || !hasHR)) return LightClassifyReason.WON_WAKE
        if (still && parasympOK && hrLow && rrvRegular) return LightClassifyReason.WON_DEEP
        if (cardiacSparse && still && hrLow && !f.rrv.isFinite()) return LightClassifyReason.WON_DEEP
        if (still && parasympOK && rrvRegular && hasHR && !hrHigh && f.rmssd.isFinite()) {
            return LightClassifyReason.WON_DEEP
        }
        if (still && cardiacActivated && rrvIrregular) return LightClassifyReason.WON_REM
        if (still && hrHigh && hrvarHigh && !f.rrv.isFinite()) return LightClassifyReason.WON_REM
        if (cardiacSparse && still && hrHigh && !f.rrv.isFinite() && f.clock > deepFirstFraction) {
            return LightClassifyReason.WON_REM
        }
        if (still && hrHigh && !f.rrv.isFinite() && f.clock > deepFirstFraction) {
            return LightClassifyReason.WON_REM
        }
        // Light fallthrough — first unmet Deep gate. Sparse no-resp Deep only needs still+hrLow
        // (already checked above), so a sparse no-resp fallthrough here is mid-HR.
        if (!still) return LightClassifyReason.NOT_STILL
        if (cardiacSparse && !f.rrv.isFinite()) return LightClassifyReason.MID_HR
        if (!parasympOK) return LightClassifyReason.PARASYMP_FAIL
        if (!hrLow) return LightClassifyReason.MID_HR
        return LightClassifyReason.RESP_IRREGULAR
    }

    /**
     * Read-only Light-funnel triage for ONE in-bed window [start, end]. Re-runs the SAME
     * Stage-0→3 seam [stageSession] uses, counting where Light was produced (classifier
     * fallthrough + reimpose/merge demotes). Changes NOTHING. Returns null only when the
     * window has too little gravity to grid. Caller logs `.summary`.
     */
    fun lightFunnelDiagnostic(
        start: Long, end: Long, grav: List<GravitySample>,
        hr: List<HrSample>, rr: List<RrInterval>, resp: List<RespSample>,
    ): LightFunnelDiagnostic? {
        val gSeg = rowsBetween(grav, start, end) { it.ts }
        if (gSeg.size < 2) return null
        val gDeltas = gravityDeltas(gSeg)
        val gTimes = gSeg.map { it.ts }
        val hrSeg = rowsBetween(hr, start, end) { it.ts }
        val rrSeg = rowsBetween(rr, start, end) { it.ts }
        val respSeg = rowsBetween(resp, start, end) { it.ts }

        val grid = buildEpochGrid(
            start = start.toDouble(), end = end.toDouble(),
            gravTimes = gTimes, gravDeltas = gDeltas,
            hr = hrSeg, rr = rrSeg, resp = respSeg,
        )
        if (grid.nEpochs == 0) return null

        val rescaled = rescaleCounts(grid.counts)
        val ckFlags = coleKripke(rescaled)
        val (onsetIdx, finalWakeIdx) = onsetAndFinalWake(ckFlags)
        val dogHR = dogHRVariability(grid.hr)
        val feats = extractFeatures(grid = grid, ckFlags = ckFlags, dogHR = dogHR,
            onsetIdx = onsetIdx, finalWakeIdx = finalWakeIdx)

        val sleepFeats = if (feats.any { it.ckSleep }) feats.filter { it.ckSleep } else feats
        val hrLo = percentile(sleepFeats.map { it.hr }, stageHRLowPct)
        val hrHi = percentile(sleepFeats.map { it.hr }, stageHRHighPct)
        val rmssdHi = percentile(sleepFeats.map { it.rmssd }, stageHRVHighPct)
        val hrvarHi = percentile(sleepFeats.map { it.hrVar }, stageHRVarHighPct)
        val rrvHi = percentile(sleepFeats.map { it.rrv }, stageRRVHighPct)
        val rrvLo = percentile(sleepFeats.map { it.rrv }, stageRRVLowPct)
        val cardiacSparse = isCardiacSparse(sleepFeats)

        val labels = classifyEpochs(feats)
        val smoothed = smoothLabels(labels)
        val reimposed = reimposePhysiology(smoothed, features = feats,
            onsetIdx = onsetIdx, finalWakeIdx = finalWakeIdx)
        // Same seam as stageSession: WASO promote before merge (Track B).
        val waso = promoteMidBoutWake(reimposed, features = feats,
            onsetIdx = onsetIdx, finalWakeIdx = finalWakeIdx)
        val merged = mergeFragments(waso)

        val noREMEpochs = (noREMAfterOnsetMin * 60.0 / epochS).roundToInt()
        val deepFrac = if ((softPriors.ageYears ?: 0.0) >= 65.0) {
            (deepFirstFraction * 1.15).coerceAtMost(0.45)
        } else {
            deepFirstFraction
        }
        val sleepLen = (finalWakeIdx - onsetIdx + 1).coerceAtLeast(1)
        val earlyDeepN = smoothed.indices.count {
            it in onsetIdx..finalWakeIdx && smoothed[it] == "deep" && feats[it].clock <= deepFrac
        }
        val earlyDeepAdequate = earlyDeepN.toDouble() >= 0.15 * sleepLen.toDouble()

        var sleepEpochs = 0
        var lightAtClassify = 0; var lightAfterReimpose = 0
        var lightAfterWaso = 0; var lightAfterMerge = 0
        var wonWake = 0; var wonDeep = 0; var wonRem = 0
        var blockedNotStill = 0; var blockedParasympFail = 0
        var blockedMidHR = 0; var blockedRespIrregular = 0
        var demotedRemOnset = 0; var demotedLateDeep = 0
        var promotedWasoWake = 0; var demotedByMerge = 0
        var rmssdChannelPresent = false

        for (i in onsetIdx..maxOf(onsetIdx, finalWakeIdx)) {
            if (i >= feats.size) break
            val f = feats[i]
            sleepEpochs += 1
            if (f.rmssd.isFinite()) rmssdChannelPresent = true
            when (lightFallthroughReason(f, hrLo = hrLo, hrHi = hrHi, rmssdHi = rmssdHi,
                hrvarHi = hrvarHi, rrvHi = rrvHi, rrvLo = rrvLo,
                cardiacSparse = cardiacSparse)) {
                LightClassifyReason.WON_WAKE -> wonWake += 1
                LightClassifyReason.WON_DEEP -> wonDeep += 1
                LightClassifyReason.WON_REM -> wonRem += 1
                LightClassifyReason.NOT_STILL -> { blockedNotStill += 1; lightAtClassify += 1 }
                LightClassifyReason.PARASYMP_FAIL -> { blockedParasympFail += 1; lightAtClassify += 1 }
                LightClassifyReason.MID_HR -> { blockedMidHR += 1; lightAtClassify += 1 }
                LightClassifyReason.RESP_IRREGULAR -> { blockedRespIrregular += 1; lightAtClassify += 1 }
            }
            if (reimposed[i] == "light") lightAfterReimpose += 1
            if (waso[i] == "light") lightAfterWaso += 1
            if (merged[i] == "light") lightAfterMerge += 1
            // Reimpose demotes (exact predicates reimposePhysiology applies to smoothed).
            if (smoothed[i] == "rem" && (i - onsetIdx) < noREMEpochs) demotedRemOnset += 1
            if (smoothed[i] == "deep" && f.clock > deepFrac && earlyDeepAdequate) demotedLateDeep += 1
            if (reimposed[i] != "wake" && waso[i] == "wake") promotedWasoWake += 1
            if (waso[i] != "light" && merged[i] == "light") demotedByMerge += 1
        }

        return LightFunnelDiagnostic(
            sleepEpochs = sleepEpochs,
            lightAtClassify = lightAtClassify,
            lightAfterReimpose = lightAfterReimpose,
            lightAfterWaso = lightAfterWaso,
            lightAfterMerge = lightAfterMerge,
            wonWake = wonWake, wonDeep = wonDeep, wonRem = wonRem,
            blockedNotStill = blockedNotStill, blockedParasympFail = blockedParasympFail,
            blockedMidHR = blockedMidHR, blockedRespIrregular = blockedRespIrregular,
            demotedRemOnset = demotedRemOnset, demotedLateDeep = demotedLateDeep,
            promotedWasoWake = promotedWasoWake, demotedByMerge = demotedByMerge,
            rmssdChannelPresent = rmssdChannelPresent,
        )
    }

    /**
     * Cheap wake concordance: Cole–Kripke sleep flag vs final staged label inside the
     * sleep period. Surfaces CK↔stager disagreement without a second full pipeline beyond
     * the same seam [lightFunnelDiagnostic] already runs. Read-only.
     */
    data class WakeConcordance(
        val sleepEpochs: Int,
        /** Epochs where CK-sleep matches staged non-wake (or CK-wake matches staged wake). */
        val agree: Int,
        /** CK says wake but stager kept sleep (light/deep/rem). */
        val ckWakeStagedSleep: Int,
        /** CK says sleep but stager labelled wake. */
        val ckSleepStagedWake: Int,
    ) {
        val agreementFrac: Double
            get() = if (sleepEpochs == 0) 1.0 else agree.toDouble() / sleepEpochs.toDouble()

        val summary: String
            get() = "wake-concordance: $sleepEpochs sleep-epochs, agree=$agree " +
                "(${(agreementFrac * 100.0).roundToInt()}%), " +
                "ckWake∧stagedSleep=$ckWakeStagedSleep, ckSleep∧stagedWake=$ckSleepStagedWake"
    }

    /**
     * Read-only CK vs staged-wake concordance for ONE in-bed window. Null when gravity is
     * insufficient to grid. Uses final post-merge labels inside onset…finalWake.
     */
    fun wakeConcordance(
        start: Long, end: Long, grav: List<GravitySample>,
        hr: List<HrSample>, rr: List<RrInterval>, resp: List<RespSample>,
    ): WakeConcordance? {
        val gSeg = rowsBetween(grav, start, end) { it.ts }
        if (gSeg.size < 2) return null
        val gDeltas = gravityDeltas(gSeg)
        val gTimes = gSeg.map { it.ts }
        val hrSeg = rowsBetween(hr, start, end) { it.ts }
        val rrSeg = rowsBetween(rr, start, end) { it.ts }
        val respSeg = rowsBetween(resp, start, end) { it.ts }

        val grid = buildEpochGrid(
            start = start.toDouble(), end = end.toDouble(),
            gravTimes = gTimes, gravDeltas = gDeltas,
            hr = hrSeg, rr = rrSeg, resp = respSeg,
        )
        if (grid.nEpochs == 0) return null

        val rescaled = rescaleCounts(grid.counts)
        val ckFlags = coleKripke(rescaled)
        val (onsetIdx, finalWakeIdx) = onsetAndFinalWake(ckFlags)
        val dogHR = dogHRVariability(grid.hr)
        val feats = extractFeatures(grid = grid, ckFlags = ckFlags, dogHR = dogHR,
            onsetIdx = onsetIdx, finalWakeIdx = finalWakeIdx)
        var labels = classifyEpochs(feats)
        labels = smoothLabels(labels)
        labels = reimposePhysiology(labels, features = feats,
            onsetIdx = onsetIdx, finalWakeIdx = finalWakeIdx)
        labels = promoteMidBoutWake(labels, features = feats,
            onsetIdx = onsetIdx, finalWakeIdx = finalWakeIdx)
        labels = mergeFragments(labels)

        var sleepEpochs = 0; var agree = 0
        var ckWakeStagedSleep = 0; var ckSleepStagedWake = 0
        for (i in onsetIdx..maxOf(onsetIdx, finalWakeIdx)) {
            if (i >= labels.size || i >= ckFlags.size) break
            sleepEpochs += 1
            val ckSleep = ckFlags[i]
            val stagedWake = labels[i] == "wake"
            when {
                ckSleep && !stagedWake -> agree += 1
                !ckSleep && stagedWake -> agree += 1
                !ckSleep && !stagedWake -> ckWakeStagedSleep += 1
                else -> ckSleepStagedWake += 1
            }
        }
        return WakeConcordance(
            sleepEpochs = sleepEpochs, agree = agree,
            ckWakeStagedSleep = ckWakeStagedSleep, ckSleepStagedWake = ckSleepStagedWake,
        )
    }

    /**
     * Sleep-depth rank, lighter → deeper: wake 0, light 1, rem 2, deep 3. Used by
     * mergeFragments to bias an ambiguous merge toward the LIGHTER stage so smoothing
     * can never inflate deep/REM. Unknown labels rank lightest (0) — they never win deep.
     */
    internal fun stageDepthRank(stage: String): Int = when (stage) {
        "light" -> 1
        "rem" -> 2
        "deep" -> 3
        else -> 0 // "wake" and any unexpected label
    }

    /** A contiguous run of one stage spanning [len] epochs. */
    private data class StageRun(val stage: String, var len: Int)

    /**
     * Display/scoring smoothing of the staged label sequence (#274). Absorbs sub-threshold
     * "noise" runs WITHOUT erasing real transitions — applied AFTER staging, it never
     * touches the underlying per-epoch detection.
     *
     * Per run shorter than [thresholdEpochs]:
     *   • bridged by two SAME-stage neighbours → absorbed into them (the fleck was a blip
     *     inside one continuous stage);
     *   • between DIFFERENT stages → relabelled to the dominant (longer) neighbour. On a tie
     *     — or when the longer neighbour is the deeper one and the shorter is lighter and of
     *     comparable length — it biases toward the LIGHTER neighbour so a stray fleck can
     *     never inflate deep/REM (the least-reliable, most-overcountable classes).
     *
     * Single left-to-right pass over runs, mirroring mergePeriods' control flow so the
     * Swift and Kotlin ports stay byte-identical. A run already ≥ threshold is a real
     * transition and is always preserved.
     */
    internal fun mergeFragments(labels: List<String>, thresholdEpochs: Int = fragmentMergeEpochs): List<String> {
        val n = labels.size
        if (n == 0 || thresholdEpochs <= 1) return labels

        // Collapse the per-epoch labels into contiguous runs of (stage, length).
        val runs = ArrayList<StageRun>()
        for (s in labels) {
            val last = runs.lastOrNull()
            if (last != null && last.stage == s) last.len += 1
            else runs.add(StageRun(s, 1))
        }
        if (runs.size < 2) return labels

        val merged = ArrayList<StageRun>()
        var i = 0
        while (i < runs.size) {
            val current = runs[i]
            if (current.len >= thresholdEpochs) {
                merged.add(StageRun(current.stage, current.len))
                i += 1
                continue
            }

            val hasPrev = merged.isNotEmpty()
            val hasNext = i + 1 < runs.size

            if (hasPrev && hasNext && merged[merged.size - 1].stage == runs[i + 1].stage) {
                // Same-stage bridge: absorb the fleck and the next run into the previous one.
                merged[merged.size - 1].len += current.len + runs[i + 1].len
                i += 2
            } else if (hasPrev && hasNext) {
                // Between two DIFFERENT stages: relabel to the dominant neighbour, biasing
                // toward the lighter stage when the two neighbours are tied in length.
                val prev = merged[merged.size - 1]
                val next = runs[i + 1]
                val winner: String = when {
                    prev.len > next.len -> prev.stage
                    next.len > prev.len -> next.stage
                    // Tie → lighter (smaller depth rank) wins; never inflate deep/REM.
                    else -> if (stageDepthRank(prev.stage) <= stageDepthRank(next.stage)) prev.stage else next.stage
                }
                if (winner == prev.stage) {
                    merged[merged.size - 1].len += current.len
                    i += 1
                } else {
                    // Becomes part of the NEXT run: extend next, drop current.
                    runs[i + 1] = StageRun(next.stage, next.len + current.len)
                    i += 1
                }
            } else if (hasNext) {
                // No previous run (leading fleck): fold forward into the next run.
                runs[i + 1] = StageRun(runs[i + 1].stage, runs[i + 1].len + current.len)
                i += 1
            } else if (hasPrev) {
                // No next run (trailing fleck): fold back into the previous run.
                merged[merged.size - 1].len += current.len
                i += 1
            } else {
                // Single sub-threshold run with no neighbours — nothing to merge into.
                merged.add(StageRun(current.stage, current.len))
                i += 1
            }
        }

        // Re-expand the runs back into a per-epoch label sequence of the same length.
        val out = ArrayList<String>(n)
        for (r in merged) {
            for (k in 0 until r.len) out.add(r.stage)
        }
        return out
    }

    // ── Per-session HR / HRV ─────────────────────────────────────────────────

    /** Lowest 5-min rolling-mean HR during the session (bpm), or null. */
    internal fun sessionRestingHR(start: Long, end: Long, hr: List<HrSample>): Int? {
        val seg = hr.filter { it.ts in start..end }
        if (seg.isEmpty()) return null
        val windowS = 5 * 60L
        val means = ArrayList<Double>()
        var t = start
        while (t < end) {
            val win = seg.filter { it.ts >= t && it.ts < t + windowS }
            if (win.isNotEmpty()) means.add(win.sumOf { it.bpm }.toDouble() / win.size.toDouble())
            t += windowS
        }
        val m = means.minOrNull()
        if (m != null) return m.roundToInt()
        val all = seg.sumOf { it.bpm }.toDouble() / seg.size.toDouble()
        return all.roundToInt()
    }

    /**
     * Mean RMSSD over 5-min tumbling windows across the session (ms), or null.
     * Uses the same range-filter + ≥2-valid-interval rule as hrv.rmssd().
     */
    internal fun sessionAvgHRV(start: Long, end: Long, rr: List<RrInterval>): Double? {
        val vals = sessionHrvWindowMeans(start, end, rr) { HrvAnalyzer.rmssdRaw(it) }
        if (vals.isEmpty()) return null
        return vals.sum() / vals.size.toDouble()
    }

    /** Mean SDNN over the same 5-min windows as [sessionAvgHRV] (ms), or null. */
    internal fun sessionAvgSdnn(start: Long, end: Long, rr: List<RrInterval>): Double? {
        val vals = sessionHrvWindowMeans(start, end, rr) { HrvAnalyzer.sdnnRaw(it) }
        if (vals.isEmpty()) return null
        return vals.sum() / vals.size.toDouble()
    }

    private fun sessionHrvWindowMeans(
        start: Long,
        end: Long,
        rr: List<RrInterval>,
        metric: (List<Double>) -> Double?,
    ): List<Double> {
        val seg = rr.filter { it.ts in start..end }
        if (seg.isEmpty()) return emptyList()
        val windowS = 5 * 60L
        val vals = ArrayList<Double>()
        var t = start
        while (t < end) {
            val bucket = seg.filter { it.ts >= t && it.ts < t + windowS }.map { it.rrMs.toDouble() }
            // Full clean (range + Malik ectopic rejection), not just range — matches the
            // analyze() pipeline. The 0x2A37 RR on a WHOOP 5/MG is PPG-derived and noisier
            // than a 4.0's; rMSSD is built from SUCCESSIVE differences, so an un-rejected
            // jitter spike inflates the session HRV. Ectopic rejection drops those (#262/#235).
            val cleaned = HrvAnalyzer.cleanRR(bucket)
            if (cleaned.size >= 2) {
                val r = metric(cleaned)
                if (r != null) vals.add(r)
            }
            t += windowS
        }
        return vals
    }

    // ── AASM hypnogram metrics ───────────────────────────────────────────────

    /** AASM-style metrics from a session's stage segments. */
    fun hypnogramMetrics(session: DetectedSleep): HypnogramMetrics {
        val segs = session.stages.sortedBy { it.start }
        val tib = maxOf(0.0, (session.end - session.start).toDouble())

        fun dur(s: StageSegment): Double = (s.end - s.start).toDouble()
        val sleepSegs = segs.filter { it.stage == "light" || it.stage == "deep" || it.stage == "rem" }
        val tst = sleepSegs.sumOf { dur(it) }
        val deepS = segs.filter { it.stage == "deep" }.sumOf { dur(it) }
        val remS = segs.filter { it.stage == "rem" }.sumOf { dur(it) }
        val lightS = segs.filter { it.stage == "light" }.sumOf { dur(it) }

        val onset: Double
        val sptEnd: Double
        val sol: Double
        val first = sleepSegs.firstOrNull()
        val last = sleepSegs.lastOrNull()
        if (first != null && last != null) {
            onset = first.start.toDouble()
            sptEnd = last.end.toDouble()
            sol = maxOf(0.0, onset - session.start.toDouble())
        } else {
            onset = session.end.toDouble()
            sptEnd = session.end.toDouble()
            sol = tib
        }

        val remSegs = segs.filter { it.stage == "rem" }
        val remLatency = remSegs.firstOrNull()?.let { it.start.toDouble() - onset } ?: Double.NaN

        var waso = 0.0
        var disturbances = 0
        for (s in segs) {
            if (s.stage != "wake") continue
            val w0 = maxOf(s.start.toDouble(), onset)
            val w1 = minOf(s.end.toDouble(), sptEnd)
            if (w1 > w0) {
                waso += (w1 - w0)
                disturbances += 1
            }
        }

        val se = if (tib > 0) tst / tib else 0.0
        fun pct(x: Double): Double = if (tst > 0) x / tst * 100.0 else 0.0

        return HypnogramMetrics(
            tibS = tib, tstS = tst, sptS = maxOf(0.0, sptEnd - onset), solS = sol,
            remLatencyS = remLatency, wasoS = waso, efficiency = minOf(1.0, se),
            disturbances = disturbances, deepMin = deepS / 60.0, remMin = remS / 60.0,
            lightMin = lightS / 60.0, deepPct = pct(deepS), remPct = pct(remS), lightPct = pct(lightS),
        )
    }

    // ── Small stats helpers ───────────────────────────────────────────────────

    /** Population standard deviation (numpy default, ddof=0). */
    internal fun standardDeviation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.sum() / values.size.toDouble()
        var ss = 0.0
        for (v in values) {
            val d = v - mean
            ss += d * d
        }
        return sqrt(ss / values.size.toDouble())
    }
}
