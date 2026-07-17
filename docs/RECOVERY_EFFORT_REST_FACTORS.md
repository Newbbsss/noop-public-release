# Recovery / Charge / Effort / Rest / Workout factors and literature

**NEW 2026-07-14.** Charge/Effort/Rest and workout auto-detection never had a dedicated doc — they were
scattered across Fable list items and code comments. This is the code-truth map, the first precise
numeric walkthrough of why Rest can outscore a badly-staged night, and the improvement backlog for all
four systems (Charge, Effort, Rest, workout detection). Mirrors the format of `SLEEP_FACTORS_AND_LITERATURE.md`
and `STRESS_FACTORS_AND_LITERATURE.md`. Docs-only — nothing in this doc has been coded yet.

---

## How each number is actually calculated (user-facing explainer, verified against code)

### Charge (NOOP's name for WHOOP "Recovery"), `RecoveryScorer.kt`

A logistic squash of a weighted z-score composite, NOT a lookup table:

```
z = Σ(termZ × weight) / Σ(weight)      — missing terms drop out and renormalize
Charge = 100 / (1 + e^(−1.6 × (z − (−0.20))))     — anchored so z=0 → 58% (WHOOP's published population mean)
```

Terms and weights: HRV vs personal baseline **0.55** (dominant), resting HR vs baseline **0.20**
(lower is better), sleep performance/Rest **0.15**, respiration vs baseline **0.05**, skin-temp deviation
**0.05** (symmetric — illness OR overreach both hurt, unlike the "lower is better" terms). Requires an
HRV baseline built from real nights (`hrvBaselineUsable`) or it returns **null** rather than a guess.

### Effort (NOOP's name for WHOOP "Strain")

Comes from `WorkoutDetector` / `StrainScorer` cumulative HR-zone time across the day (Edwards zone
time-percent + %HRR), displayed on both a 0–100 NOOP scale and the dual 0–21 WHOOP-style scale — the two
scales are shown side-by-side on the compare card specifically so "29.5" (NOOP) and "10.2" (WHOOP-style)
aren't mistaken for a bug.

### Rest (NOOP's name for WHOOP "Sleep Performance")

```
Rest = 0.50·duration + 0.20·efficiency + 0.20·restorative + 0.10·consistency
```
(`RestScorer.rest`, `AnalyticsEngine.kt:780`). Each sub-term is itself 0–100:

- **duration** = min(100, asleep hours ÷ personal need × 100) — personal need defaults to 8h, refined to
  the recent average, floored at 7.5h.
- **efficiency** = asleep ÷ time-in-bed × 100.
- **restorative** = (deep + REM) share of asleep time, normalized against a 50%-share target, THEN
  multiplied by a `deepFactor` that ranges from 1.0 (deep share ≥ 13% of asleep) down to a **floor of 0.5**
  as deep → 0 — deliberately never zeroed, "so a low-deep night reads honestly without the whole night
  tanking" (code comment).
- **consistency** = sleep/wake regularity 0–1, or a neutral 50 when there's no history yet.

### Worked example — why the 13 Jul night scored Rest 73 "Strong" with 2% deep / 0% REM

Using the on-device stage breakdown numbers from the 2026-07-14 export (asleep ≈ 7h19m = 439 min,
efficiency 69%, deep 10 min, REM 0 min, need ≈ 7.5h floor):

| Term | Raw score | Weight | Contribution |
|---|---|---|---|
| Duration | min(100, 7.32/7.5×100) ≈ **97.5** | 0.50 | 48.8 |
| Efficiency | **69** | 0.20 | 13.8 |
| Restorative | share = 10/439 = 2.3%; deepAdequacy = (2.3/13)=0.175; deepFactor = 0.5+0.5×0.175=0.588; score = min(100, 2.3/50×100)×0.588 ≈ **2.7** | 0.20 | 0.5 |
| Consistency | neutral **50** (no history) | 0.10 | 5.0 |
| **Total** | | | **≈ 68** (screenshot showed 73 — the gap is explained by which of NOOP's two conflicting duration numbers fed the live compute; see `SLEEP_FACTORS_AND_LITERATURE.md` 2026-07-14 update) |

**The mechanism, plainly:** duration and efficiency alone can supply up to 62.6 of the 68–73 points even
when the restorative term is nearly zero, because those two terms together hold 70% of the weight and
this particular night happened to have decent raw hours and a middling-but-not-terrible efficiency. WHOOP
scored the same shape of night 64% — lower, but not catastrophically — which suggests WHOOP's proprietary
model also doesn't crater a score purely on stage percentages; the interesting question isn't "NOOP is
broken", it's "NOOP's floor for the restorative term (0.5×) may be more forgiving than WHOOP's equivalent,
and/or duration is weighted a bit high relative to how WHOOP appears to weight it here." Both are tunable
without inventing any new data.

---

## Recovery/Rest improvement backlog (grounded, non-padded)

**Rest re-weighting**
1. Lower `deepFloorFactor` from 0.5 toward ~0.3 so a nearly-zero-deep night's restorative term can fall
   further, while still never hitting a literal zero (keeps the "honest, not tanking" intent).
2. Make `deepShareTarget` (13%) do double duty: below HALF of target (i.e. <6.5% deep share, this night's
   2.3% qualifies), apply a steeper penalty curve than the current linear ramp — the literature gap between
   "a little short on deep" and "essentially no deep" is not linear in health significance.
3. Consider capping `durationScore` itself by an efficiency/restorative joint gate — WHOOP's proprietary
   score likely does NOT let raw hours alone carry 50% of the composite when the underlying sleep quality
   is poor; a duration score that's discounted when restorative is very low would blunt exactly this
   night's failure mode without a wholesale re-weight.
4. Surface the FULL per-term breakdown (the table above) directly in the Rest drill-in (`RestDrivers.kt`
   already computes each term/delta) — right now `restDrivers` returns exactly this data but the worked
   math above had to be reconstructed from the source; make sure the UI actually surfaces "Deep+REM: −10
   points" as prominently as "Sleep duration: +25 points" rather than requiring an expand.
5. Add a per-night "Rest confidence" flag when the restorative term's deep-gate percentile spread was
   degenerate (ties into Sleep backlog item #9 above) — a Rest score built on unreliable staging shouldn't
   present with the same visual confidence as one built on a clean night.

**Charge (Recovery) refinements**
6. `wSleep = 0.15` feeds Rest INTO Charge — meaning a Rest-score bug (like the one above) doubly affects
   the user: once directly as "Rest 73", and again indirectly inside "Charge 47". Fixing Rest weighting
   will also quietly improve Charge accuracy; don't tune Charge's own weights until Rest is fixed, or
   you'll be compensating for the wrong layer.
7. `restingHRMinPlausibleBpm = 25.0` and `restingHRMinBinSamples = 5` (artifact hardening, #686) are solid
   guards — consider the SAME class of guard for the HRV term: a single anomalously high RMSSD reading
   from a motion artifact could currently pull the whole HRV z-score up; add an analogous plausibility
   floor/ceiling on nightly HRV before it enters the z-score.
8. Skin-temp term (`wSkinTemp = 0.05`, symmetric) is a good model already — cross-reference against Stress
   backlog item #11 (Stress's skin-temp handling is NOT symmetric) so the two systems interpret the same
   sensor consistently.
9. `logisticZ0 = -0.20` anchors z=0 to 58%, matching WHOOP's published population average — good practice;
   periodically re-validate this anchor doesn't drift as more real user data accumulates (a per-user
   recalibration of the anchor, not just the baseline mean/spread, could reduce systematic over/under
   scoring for people far from the population center).

---

## Workout auto-detection improvement backlog (`AutoWorkoutDetector.kt`, `WorkoutDetector.kt`)

**Current state, precisely:** `AutoWorkoutDetector` is a stateless, re-run-every-time suggestion engine —
sustained HR ≥ resting+30bpm for ≥12 min (dips ≤90s tolerated, windows within 5 min merged), optionally
confirmed by motion (mean gravity-L2 ≥ 0.05) when a motion series is supplied. It NEVER persists a "this is
what a basketball session looks like for this user" signature — every suggestion starts from the same
generic thresholds, so it can't get smarter about a specific person's specific sports over time, and it
can't distinguish "basketball" from "generic workout" (that label comes from a SEPARATE confirm-sheet
classifier, `WorkoutSportConfirmSheet.kt`, unrelated to detection itself).

10. Persist a per-sport HR/motion "signature" (mean bpm, peak bpm, HR variability during the session,
    mean/peak motion intensity, typical duration) keyed by the sport label the user confirms — the next
    time a detected window's signature is close to a stored "basketball" signature, pre-select basketball
    in the confirm sheet instead of defaulting to generic "Workout".
11. Track signature drift over time (a user's basketball sessions in month 6 may look different from month
    1 as fitness changes) — an EWMA-updated signature per sport, not a static one-shot average.
12. `elevatedMarginBPM = 30` and `minSustainedMin = 12.0` are deliberately conservative (low false-positive
    tolerance) per the file's own comment — this means SHORT intense sessions (e.g. HIIT intervals with
    frequent dips) are the documented blind spot; a signature-aware detector could apply a LOWER bar for a
    window that already resembles a known high-intensity-interval signature, without loosening the global
    default.
13. `motionConfirmMean = 0.05` is a single flat threshold for ALL sports — a swim (accelerometer motion is
    very different underwater/in different watch orientation) vs a bike ride (motion mostly in legs) vs
    basketball (whole-body, bursty) plausibly need different motion-confirmation bars; per-sport-signature
    motion ranges would let each sport self-calibrate.
14. `WorkoutDetector`'s separate durable-detection path has its own `motionThreshold = 0.20` (vs
    `AutoWorkoutDetector`'s suggestion-only 0.05) — confirm this intentional asymmetry (suggestion errs
    permissive/dismissible, durable detection errs strict) is documented somewhere a future maintainer
    won't "fix" as an inconsistency.
15. `bridgeGapS = 300.0` (endurance-session bridging across a motion gap, #303) is HR-only gated — no
    signature-awareness of "this sport typically has motion gaps" (cycling coasting, swimming turns) vs
    "this sport doesn't" (continuous running) — a sport-specific bridge-gap tolerance would reduce both
    false splits (endurance sports) and false merges (stop-start sports like basketball, which SHOULD show
    real rest breaks as separate bouts sometimes).
16. No feedback loop from a user's EDITS (`WorkoutEditing.kt`, `ManualWorkoutRescore.kt`) back into the
    signature store — if a user consistently re-labels detected "Workout" as "Basketball" and adjusts the
    start/end time, that correction should update the stored basketball signature and boundary-timing
    tendencies (does this user's basketball typically start a few minutes before HR crosses the elevated
    gate — e.g. warmup — and the detector should learn to backdate the suggested start).
17. Steps activity-class (`stepClassWalk`/`stepClassRun`, already wired into `DaytimeStress`) is NOT
    currently fed into workout detection at all — cross-referencing step cadence class could sharpen sport
    classification (a "run" step-class during a detected HR-elevated window is a strong basketball/running
    discriminator vs a "still" class during the same HR elevation, which would suggest a stationary effort
    like weightlifting instead).

---

## Related paths

- `android/app/src/main/java/com/noop/analytics/RecoveryScorer.kt`
- `android/app/src/main/java/com/noop/analytics/AnalyticsEngine.kt` (`RestScorer`, line ~716)
- `android/app/src/main/java/com/noop/analytics/RestDrivers.kt`
- `android/app/src/main/java/com/noop/analytics/AutoWorkoutDetector.kt`
- `android/app/src/main/java/com/noop/analytics/WorkoutDetector.kt`
- `android/app/src/main/java/com/noop/analytics/WorkoutSport.kt`
- `android/app/src/main/java/com/noop/ui/WorkoutSportConfirmSheet.kt`, `WorkoutEditing.kt`, `ManualWorkoutRescore.kt`
- Fable: `docs/FABLE_200_UI_IMPROVEMENTS.md`, `docs/FABLE5_300_NOT_INTUITIVE.md`
- Handoff: `ANY_MODEL_CONTINUE.md` → 2026-07-14 entry
