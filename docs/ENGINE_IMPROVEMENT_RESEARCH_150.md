# Intelligence and analytics engines: 150 improvement research prompts

Durable research backlog for NOOP’s on-device engines. Audited against the Kotlin implementation on 2026-07-13.

## How to use this backlog

Each item is a falsifiable study, implementation experiment, or protocol review. A change should not ship merely because it matches a paper: validate it on NOOP’s sensor cadence, device families, missingness, and user population. Preserve deterministic replay, source provenance, non-medical wording, and “no number rather than a fabricated number.”

The `★` items are the ten highest-leverage starting points. Suggested validation hierarchy:

- PSG/ECG/indirect-calorimetry or manually labelled reference where feasible.
- Time-aligned WHOOP-app/Health Connect labels as an external comparison, never assumed ground truth.
- Within-user repeated measures and leave-one-user-out evaluation.
- Synthetic/property tests only for invariants, not accuracy claims.

Count: SleepStager 22; Effort 16; Charge 16; Rest 14; DaytimeStress 16; WorkoutDetector 14; Steps 12; orchestration 14; baselines 12; Health Connect fusion 14. Total: 150.

## SleepStager — 22 prompts

1. ★ **Device-family stage models.** Hypothesis: separate WHOOP 4.0 and 5/MG feature calibrations outperform the shared percentile rules because gravity and PPG cadence differ. Replay nights stratified by family against manually reviewed/HC-staged sleep; require lower onset/offset MAE and no worse nap false-positive rate.
2. **Weakly supervised HC calibration.** Test whether learning per-user threshold offsets from nights with Health Connect stages improves later NOOP-only nights. Train on the first N paired nights, freeze, and evaluate on held-out nights; report TST MAE, stage macro-F1, and calibration drift.
3. **Cole–Kripke ensemble weighting.** Compare current use of Cole–Kripke as a cross-check with a calibrated ensemble of gravity spine, Cole–Kripke, HR sleep band, and band sleep-state. Use nested cross-validation to prevent choosing weights on test nights.
4. **Circadian prior without shift-worker harm.** Add a soft sleep-onset prior from recent local sleep midpoint, then evaluate separately for regular sleepers, rotating shifts, travel, and late sleepers. Accept only if false daytime sleep falls without truncating genuine irregular nights.
5. **Adaptive stillness threshold.** Hypothesis: a per-device gravity-noise floor estimated from worn quiet periods beats fixed `0.01 g`. Compare session detection precision/recall and test invariance under sensor-axis scaling.
6. **Sampling-cadence normalization.** Resample or time-weight gravity deltas before stillness scoring so bursty 5/MG history does not vote more heavily than sparse intervals. Validate identical decisions under artificial downsampling and jitter.
7. **Change-point onset/offset.** Compare persistence rules with Bayesian online change-point detection or PELT over HR, motion, and band state. Primary endpoints: sleep onset latency and final-wake MAE; cap CPU/memory for on-device use.
8. **Missing-not-at-random handling.** Model HR/gravity dropout as a feature rather than neutral evidence. Inject realistic off-wrist, BLE-gap, and backfill patterns into complete nights; measure phantom sleep and truncated-night rates.
9. **Off-wrist posterior.** Replace the binary 50% coverage rejection with a calibrated off-wrist probability using HR density, explicit wrist events, skin contact, temperature, and frozen gravity. Require better discrimination of desk-off-strap versus sparse real sleep.
10. **Nap protocol.** Build a labelled nap corpus with 20–30, 30–60, 60–90, and >90 minute naps plus sedentary controls. Test whether the current 90-minute daytime minimum systematically misses restorative short naps; optimize precision at a declared recall floor.
11. **Morning residual-stillness study.** Collect post-wake coffee/reading/back-in-bed cases and true second sleeps. Tune the 180-minute/0.90 HR gates using precision-recall curves rather than single anecdotes.
12. **Fragment bridge evidence.** For candidate gaps, compare current fixed bridge windows with evidence from HR level, motion, band state, and duration. Test whether a probabilistic bridge reduces both shattered nights and merged split sleeps.
13. **Stage-duration semi-Markov model.** Compare median smoothing plus hard physiology rules with a four-state hidden semi-Markov model whose duration priors reflect typical wake/light/deep/REM bouts. Evaluate macro-F1 and impossible-transition frequency.
14. **Age-conditioned architecture.** Test age-dependent REM/deep duration priors from sleep literature while keeping priors soft. Require benefit in age-stratified held-out data and no systematic score inflation for older adults.
15. **REM latency personalization.** Hypothesis: a personal trailing distribution of REM latency is more accurate than a universal “no REM in first 15 minutes” rule. Evaluate after enough staged nights and retain the universal guard during cold start.
16. **Deep-late-night rule audit.** Quantify how often verified deep sleep occurs after the first third of the night; compare hard demotion with a decaying prior. Primary metric is deep recall with unchanged wake precision.
17. **Respiration feature robustness.** Benchmark the simple peak detector against autocorrelation, zero-crossing after band-pass filtering, and spectral peak methods on motion-contaminated 1 Hz respiration. Measure rate MAE and downstream REM/deep classification.
18. **Frequency-domain HRV feasibility.** Evaluate Lomb–Scargle or interpolated FFT HF power on RR windows, following Task Force caveats. Ship only if artifact-controlled HF adds held-out stage information beyond RMSSD and stays computationally bounded.
19. **Artifact-aware RR features.** Compare Malik local-median cleaning with Lipponen–Tarvainen-inspired correction or confidence weighting. Report retained-beat fraction, RMSSD bias on injected ectopy, and stage impact.
20. **Signal-quality confidence per epoch.** Train or hand-calibrate an epoch confidence from sample density, artifacts, source family, and disagreement among classifiers. Test whether abstaining on low-confidence epochs improves reported-stage reliability.
21. **PSG validation protocol.** Define a small prospective home-PSG study using AASM 30-second labels, blinded alignment, Cohen’s kappa, macro-F1, TST/WASO/SOL/REM-latency errors, and Bland–Altman plots. Pre-register acceptance criteria before tuning.
22. **Edit-as-label learning.** Treat repeated user bedtime/wake edits as noisy boundary labels. Test a private on-device offset model, with minimum-label and outlier gates, against future edits; never reinterpret stage labels from a boundary edit.

## StrainScorer / Effort — 16 prompts

23. ★ **Time-weighted irregular cadence integration.** Hypothesis: integrating each sample over its actual forward interval, capped at a gap threshold, reduces Effort bias versus using a segment’s first cadence. Downsample complete days at 1 s, 5 s, and 30 s and require score stability.
24. **Personal HRmax evidence hierarchy.** Compare fixed Tanaka floor/current 99.5th percentile with a robust model using verified maximal tests, high-intensity session plateaus, age prediction intervals, and decay. Evaluate zone-time agreement and prevent one artifact from raising HRmax.
25. **Resting-HR input timing.** Test sleeping RHR, waking resting HR, rolling 7-day RHR, and day-specific 10th percentile as HRR denominator choices. Evaluate repeatability and association with session RPE/load.
26. **Edwards versus Banister within user.** On users with session-RPE or training logs, compare Edwards, sex-coefficient Banister, and individualized exponential coefficient. Use blocked time-series validation and report monotonicity, not just correlation.
27. **Below-zone movement load.** Validate the steps/active-kcal floor against long walks, strength work, yoga, chores, and cycling. Require that movement raises Effort when appropriate without letting phone-step noise dominate cardiovascular load.
28. **Resistance-training load.** Study whether heart-rate-only TRIMP underestimates strength sessions; test adding duration, set density, volume load, or user session-RPE when available. Keep cardiovascular and muscular load separately explainable.
29. **EPOC/recovery tail.** Test a bounded post-exercise elevated-HR load term against current raw integration. Verify it captures physiological cost without double-counting the workout itself or illness-related tachycardia.
30. **Artifact and cadence quality weighting.** Estimate per-segment confidence from duplicate removal, gap fraction, plausible-HR rejection, and source cadence; test confidence-aware undercount bounds alongside the point score.
31. **TRIMP denominator calibration target.** Fit the log denominator against independent outcomes such as session-RPE load or weekly monotony, not solely proprietary strain labels. Use user-level cross-validation and inspect saturation.
32. **Scale shape audit.** Compare logarithmic, Box–Cox, and monotone spline maps from TRIMP to 0–100. Require improved interpretability across easy/moderate/hard days while preserving rarity of 90+.
33. **Sport-specific HR lag.** Quantify HR lag for intervals, lifting, swimming, and heat. Test whether sport-aware integration windows improve alignment with perceived exertion without requiring a sport label for baseline operation.
34. **Environmental modifiers.** Test whether heat, altitude, dehydration, and caffeine cause systematic Effort inflation relative to external workload. Prefer explanatory context or confidence changes over subtracting genuine cardiovascular strain.
35. **Medication/profile sensitivity.** Research beta blockers, stimulants, pregnancy, and autonomic conditions as profile modifiers. Prototype opt-in HR-zone calibration rather than medical inference; evaluate with simulated and consented cohorts.
36. **Partial-day nowcast.** Calibrate an “Effort so far” confidence based on covered waking time and wear fraction. Test that morning values are not interpreted as final-day scores and converge monotonically as data accumulates.
37. **Cross-device cadence parity.** Replay identical HR trajectories through WHOOP 4.0, 5/MG sparse, standard BLE HR, Oura, and HC sampling patterns. Require bounded Effort differences after cadence normalization.
38. **Training-load derivatives.** Compare 7/28-day exponentially weighted load, monotony, strain, and ACWR-style summaries. Evaluate predictive utility for self-reported fatigue while explicitly avoiding injury-risk claims from ACWR alone.

## RecoveryScorer / Charge — 16 prompts

39. ★ **Outcome-calibrated driver weights.** Hypothesis: fixed 0.55/0.20/0.15/0.05/0.05 weights are suboptimal across users. Fit regularized hierarchical models against next-day self-rated readiness/performance, with global priors and personal adaptation; compare calibration and stability.
40. ★ **Uncertainty propagation.** Propagate baseline spread uncertainty, HRV beat quality, sleep-stage confidence, and missing drivers into a Charge interval or reliability score. Test empirical coverage: nominal 80% intervals should contain held-out outcomes about 80% of the time.
41. **ln-RMSSD versus raw RMSSD.** Following Plews et al., compare baseline z-scores on RMSSD and ln-RMSSD. Evaluate normality, within-user coefficient of variation, outlier sensitivity, and readiness association.
42. **Morning-window HRV protocol.** Compare whole-night average HRV, first stable deep/light window, last 5 minutes before wake, and standardized morning resting measurements. Test repeatability and predictive value, stratified by device/source.
43. **RHR estimator comparison.** Compare minimum qualified 5-minute bin, lowest rolling 5-minute mean, nocturnal median, and stage-conditioned floor. Evaluate artifact sensitivity and night-to-night reliability.
44. **Respiration deviation shape.** Test linear “lower is better” against asymmetric penalties where elevated respiration matters more than low respiration. Use illness-tagged and healthy nights; guard against medical claims.
45. **Skin-temperature nonlinear penalty.** Compare absolute linear penalty with robust Huber or thresholded deviation based on personal sensor noise. Evaluate false alerts after ambient-temperature changes and menstrual-cycle shifts.
46. **SpO₂ as quality/context, not silent penalty.** For trusted calibrated SpO₂ only, test threshold, duration-below-threshold, and confidence-aware terms. Require incremental outcome value and preserve absence neutrality.
47. **Prior-day load interaction.** Test whether Effort modifies the meaning of HRV/RHR deviations: the same low HRV after hard training versus after rest may need different interpretation. Compare additive and interaction models on held-out days.
48. **Multi-day trend term.** Test whether 3-day slopes in HRV, RHR, respiration, and skin temperature improve next-day readiness beyond one-night z-scores. Use blocked validation to avoid leakage.
49. **Menstrual-cycle context.** In opted-in users, test cycle-phase-aware baselines versus one global baseline for temperature, RHR, and HRV. Measure calibration by phase; never infer phase without consent/data.
50. **Travel/altitude adaptation.** Detect context shifts from timezone/altitude inputs when available and compare temporary baseline freeze, faster adaptation, or explanatory confidence downgrade.
51. **Logistic anchor calibration.** Audit the `z=0 → 58%` population anchor against NOOP’s own outcome and score distributions. Compare global versus personal intercepts while preserving transparent 0–100 semantics.
52. **Missing-driver behavior.** Simulate all driver-missing patterns and compare current weight renormalization with uncertainty penalties or minimum-driver sets. Ensure a lone weak signal cannot produce falsely precise Charge.
53. **Acute illness holdout.** Build a symptom-tagged evaluation set and measure whether Charge direction and confidence respond before/during illness without overreacting to isolated outliers. Report sensitivity at a fixed false-positive rate.
54. **Actionability trial.** Randomize whether users see Charge alone or Charge plus top drivers and uncertainty. Test comprehension and decision quality, not whether users merely like the richer card.

## RestScorer — 14 prompts

55. ★ **Personal sleep need model.** Replace “recent average with 7.5 h floor” with a regularized model using age, recent sleep debt, naps, prior wake performance, and long-run achieved sleep. Evaluate against next-day sleepiness/readiness and avoid learning chronic restriction as “need.”
56. **Source-confidence weighted Rest.** Hypothesis: stage-derived restorative share should contribute less when staging confidence is low or stages are imported/reconstructed. Compare confidence-weighted components with fixed weights; require better calibration without hiding duration/efficiency.
57. **Sleep-need debt feedback.** Test whether accumulated debt should raise tonight’s duration target and by how much. Compare Borbély two-process-inspired decay models against the fixed need and existing debt ledger.
58. **Oversleep shape.** Current duration clamps at 100. Test whether very long sleep should remain neutral, signal catch-up, or carry a small context-dependent penalty. Evaluate illness and recovery contexts before changing.
59. **Efficiency threshold sensitivity.** Compare linear efficiency scoring with a plateau above 90% and steeper penalty below 75%. Assess association with next-day sleepiness and avoid overrewarding short but efficient sleep.
60. **WASO/SOL decomposition.** Test replacing or supplementing efficiency with separate sleep-onset latency and wake-after-sleep-onset terms. Evaluate explanatory value and multicollinearity.
61. **Restorative target personalization.** Compare fixed deep+REM target share with age-conditioned and personal stable-night distributions. Require held-out benefit and explicit stage-confidence gating.
62. **Deep floor factor audit.** Quantify whether the 0.5 deep adequacy floor prevents implausible Rest inflation or unfairly penalizes valid low-deep nights. Compare PSG/HC-aligned subsets and device families.
63. **REM/deep asymmetric value.** Test separate REM and deep terms rather than one restorative sum. Evaluate whether each adds independent next-day cognitive/physical outcome signal.
64. **Consistency metric design.** Compare standard deviation of sleep/wake time, Sleep Regularity Index, social jetlag, and circular-statistics distance. Evaluate robustness to naps, shift work, DST, and travel.
65. **Consistency neutral default.** Current missing consistency becomes 50. Compare neutral inclusion, dropped-and-renormalized weight, and explicit uncertainty. Measure cold-start score bias.
66. **Nap contribution.** Test how naps repay duration debt and whether nap timing/duration should affect Rest. Keep main sleep architecture separate and validate against next-day sleepiness.
67. **Split-sleep protocol.** Define how two legitimate sleep blocks combine for duration, efficiency, restorative share, and consistency. Test biphasic sleepers and avoid treating fragmentation artifacts as planned split sleep.
68. **Rest calibration study.** Compare Rest against Karolinska Sleepiness Scale, PROMIS sleep disturbance, psychomotor vigilance, and user-rated restoration. Use repeated within-user analysis; proprietary Sleep Performance is a comparator, not ground truth.

## DaytimeStress — 16 prompts

69. ★ **State-space stress model.** Replace independent 15-minute scoring plus debounce with a robust state-space model that separates latent autonomic load from noisy HR/RMSSD observations. Evaluate event timing, false spikes, and stability under sparse cadence.
70. ★ **Activity-conditioned personal baselines.** Build separate calm references for seated, standing, walking, post-workout, and sleep states. Test whether this reduces “exercise equals stress” leakage while retaining non-exercise autonomic peaks.
71. **RR quality gate.** Calibrate bucket-level RR confidence from clean-beat count, ectopic fraction, cadence, and motion. Evaluate whether low-quality abstention improves stress-label agreement.
72. **ln-RMSSD and heart-rate residualization.** Compare raw RMSSD z-scores with ln-RMSSD and HR-adjusted HRV residuals. Measure reduction in mathematical coupling between HR and HRV terms.
73. **Bucket duration comparison.** Evaluate 5-, 10-, 15-, and adaptive-duration buckets against labelled stress events. Report detection latency, variance, coverage, and battery/CPU.
74. **Waking-window personalization.** Learn sleep/wake boundaries from detected sleep instead of fixed 06:00–22:00/07:00–21:00. Test shift workers, naps, and travel; fall back safely when sleep is unknown.
75. **Workout recovery curve.** Replace the fixed overlap damp with a post-workout recovery-state model based on elapsed time and HR recovery. Distinguish training load from lingering psychosocial stress.
76. **Respiration coupling.** Test respiratory sinus arrhythmia and breathing-rate context so paced breathing does not appear as an abrupt unexplained HRV jump. Validate during Breathe sessions and spontaneous slow breathing.
77. **Electrodermal/temperature optional fusion.** Review whether available device-family signals add value to stress timing. Require real decoded signals, consent, and incremental validation; never infer from unsupported channels.
78. **Self-report ecological momentary assessment.** Design randomized low-burden prompts after predicted low/medium/high windows and control windows. Estimate precision, recall, and user-specific calibration while limiting notification bias.
79. **Event-lag analysis.** Determine whether autonomic peaks lead, coincide with, or follow self-reported stress. Test lagged labels rather than forcing same-bucket agreement.
80. **Circadian autonomic pattern.** Fit a personal time-of-day baseline so normal morning activation and evening decline are not mislabelled. Evaluate with blocked days and preserve acute deviations.
81. **Caffeine/meal context.** With journal labels, quantify systematic post-caffeine and postprandial effects. Prefer explanations to subtractive corrections until causal evidence exists.
82. **Sustained-high rule.** Compare 3 consecutive hours over 2.0 with run-length, area-under-curve, and change-point alerts. Optimize for meaningful user events at a fixed weekly alert burden.
83. **Calibration-night target.** Test whether four prior calm nights are enough across within-user variability. Plot error versus N and choose confidence tiers from empirical learning curves.
84. **Daily aggregate definition.** Compare simple mean, waking time-weighted mean, 90th percentile, high-zone minutes, and area above personal baseline as the daily stress summary. Validate against daily self-report and keep the intraday curve unchanged.

## WorkoutDetector — 14 prompts

85. ★ **Multi-sensor bout probability.** Replace hard HR+motion gates with a calibrated bout probability using HR reserve, motion, steps/activity class, cadence, and context. Evaluate event-level precision/recall and boundary IoU across sports.
86. **Sport-stratified corpus.** Collect labelled running, cycling, rowing, strength, swimming, yoga, walking, team sports, and chores. Publish per-sport miss/false-positive rates; aggregate accuracy can hide systematic failures.
87. **Personal HR threshold.** Compare `RHR + 15` with HRR percentage, personal activity/rest distributions, and age/medication-aware thresholds. Evaluate low-HR responders and heat-induced HR.
88. **Adaptive motion threshold.** Normalize gravity intensity by per-device noise and placement. Test cross-family and dominant/non-dominant wrist stability.
89. **No-motion cardio path.** For cycling and static machines, test HR-only detection with longer persistence and contextual safeguards. Require low false positives during illness, sauna, or anxiety.
90. **Low-HR movement path.** For yoga, walking, and strength, test motion/step/set-pattern detection without elevated HR. Keep these as activity bouts if cardiovascular evidence is weak.
91. **Bridge-gap model.** Current no-HR gaps automatically bridge. Compare a dropout-confidence model using surrounding cadence and motion to avoid merging two workouts separated by sensor absence.
92. **Boundary refinement.** After detecting a core bout, expand boundaries using HR rise/recovery and motion change points. Evaluate start/end MAE and avoid swallowing warm-up chores.
93. **Duplicate arbitration.** Test overlap matching by interval IoU, sport compatibility, source trust, and edit status. Ensure manual/imported workouts win without discarding distinct adjacent bouts.
94. **Sport classification confidence.** Couple `SportClassifier` only after detection and surface “workout” when confidence is low. Evaluate top-1/top-3 accuracy and calibration, not forced labels.
95. **Strength set detection.** Explore periodic wrist-motion bursts plus HR recovery to identify sets/rests. Validate against manually tapped sets and keep false positives during household activity low.
96. **Energy expenditure validation.** Compare Keytel/Harris–Benedict estimates with indirect calorimetry datasets or trusted reference devices by sport and sex. Report bias and limits of agreement, not just correlation.
97. **User correction loop.** Use accepted, deleted, resized, and relabelled workouts as private weak labels. Test whether a bounded personal threshold adapter reduces future corrections.
98. **Detection latency versus retroactive quality.** Compare live early detection with end-of-bout retrospective detection. Define separate metrics and allow retroactive boundary correction without notification spam.

## Steps / StepMotionCounter / StepsEstimateEngine — 12 prompts

99. **Ground-truth walking protocol.** Run controlled 100/500/1,000-step trials at slow, normal, fast, stairs, stroller/cart, and dominant-hand tasks. Fit and test ticks-per-step by condition, device family, and wrist.
100. **Counter-wrap and reboot properties.** Generate arbitrary 16-bit wraps, resets, duplicates, gaps, and reordering. Assert accepted steps are non-negative, bounded by physiology, and invariant to harmless chunking.
101. **Rate-cap calibration.** Validate `MAX_TICKS_PER_SECOND=4` and `MAX_DELTA=256` against running/sprinting and bursty backfill. Measure true-step rejection versus artifact rejection.
102. **Activity-class reliability.** Build confusion matrices for class 0/1/2 against labelled still/walk/run. Use them to learn weights instead of fixed 0.15/0.50/0.75/1.05 values.
103. **Noise-floor sessions.** Compare shake-learned noise floor across wrist gestures, driving, typing, cooking, and off-wrist transport. Test day-specific versus persistent floors.
104. **Robust coefficient fit.** Compare current volume-weighted calibration with Huber regression, Theil–Sen, and hierarchical per-user/device models. Use leave-one-day-out step MAE and protect against bad phone reference days.
105. **Calibration confidence.** Derive confidence from number of days, motion volume, condition diversity, residual spread, and coefficient stability rather than days alone. Test empirical error bands.
106. **Source arbitration by interval.** Current display chooses strap, then HC, then estimate for the whole day. Test interval-level de-overlap/source coverage so a partial strap day can combine with non-overlapping phone periods without double-counting.
107. **Phone-carry bias.** Quantify HC reference undercount when the phone is not carried and watch-source duplication. Exclude or downweight poor reference days using source metadata and coverage.
108. **Gait-personalization features.** Test whether height, cadence, arm swing, wrist side, and mobility aids explain residual coefficient variance. Keep optional and avoid sensitive inference.
109. **Daily plausibility model.** Add soft warnings for impossible jumps, long zero plateaus during active motion, and >60k caps. Evaluate detection of decoder failures without silently rewriting totals.
110. **Free-living validation.** Compare NOOP, manual clicker/validated pedometer, and HC over 7–14 days. Report mean absolute percentage error by activity level and source availability.

## IntelligenceEngine orchestration — 14 prompts

111. ★ **Incremental dependency graph.** Hypothesis: recomputing only days whose raw inputs, source ownership, profile, baseline version, or algorithm version changed cuts CPU/battery without stale scores. Persist input fingerprints and compare outputs with full replay.
112. **Snapshot consistency.** Read each day’s streams and metadata under a coherent repository snapshot/version. Stress-test concurrent BLE writes, imports, edits, and rescoring; forbid mixed pre/post-update outputs.
113. **Coalescing scheduler.** Replace queued duplicate 21-day passes with keyed coalescing while preserving full-history and edit-specific requests. Simulate all caller interleavings and assert no requested scope is lost.
114. **Algorithm-version provenance.** Persist scorer version/config hash with every derived metric and sleep session. Test selective migration/replay and expose “computed with older model” without blind rescaling.
115. **Idempotence contract.** Property-test that identical snapshots produce byte-equivalent daily metrics, sessions, workouts, and source rows after repeated runs.
116. **Crash-safe transaction boundary.** Persist a day’s daily metric, metric series, sleep sessions, and workouts atomically or with a generation marker. Kill the process at each write step and verify recovery.
117. **Timezone/DST ownership.** Build a matrix for DST jumps, timezone travel, UTC±14, and nights crossing local midnight. Assert one owner/day, correct wake-day attribution, and no double-scored hours.
118. **Late-arriving data invalidation.** Backfill chunks can improve an old night after it was scored. Track stream watermarks and automatically rescore affected days plus dependent baselines/future Charge.
119. **Edit dependency propagation.** Formalize which outputs change after sleep edit, workout edit, profile/HRmax change, baseline reset, source lock, and HC permission change. Add end-to-end dependency tests.
120. **Memory-bounded streaming features.** Benchmark streaming/chunked feature extraction versus loading up to 200k rows per stream. Require numerically equivalent results and lower peak memory on 21-day/full-history passes.
121. **Day-owner confidence and conflict report.** Extend single-owner resolution with reasons and conflict diagnostics when multiple devices have data. Test remove/re-add identities, locked owners, imports, and active-write mismatch.
122. **Import-computed precedence specification.** Encode per-field arbitration in executable tests, including partial imported rows. Ensure an imported missing field does not erase a valid computed field and computed values do not overwrite trusted imports.
123. **Performance budget.** Establish device-class budgets for one day, 21 days, and 4,000-day migration: wall time, CPU time, allocations, DB reads/writes, and battery estimate. Fail regressions in benchmarks.
124. **Golden cross-platform corpus.** Maintain privacy-safe raw/aggregate fixtures replayed through Kotlin and Swift. Compare all scores, stages, confidence, provenance, and rounding with declared tolerances.

## Baselines — 12 prompts

125. **Hierarchical cold start.** Use population/device/age priors with wide uncertainty that shrink toward personal data, instead of no score or midpoint seeds alone. Evaluate first-14-night calibration and ensure priors cannot look “Solid.”
126. **Robust baseline family comparison.** Compare winsorized EWMA, rolling median/MAD, exponentially weighted quantiles, and robust Kalman filters on stable, trend, illness, and artifact periods.
127. **Half-life tuning by metric.** Estimate HRV, RHR, respiration, and temperature adaptation rates separately using rolling-origin validation. Current 14/21-night values should be treated as hypotheses.
128. **Outlier mixture model.** Distinguish sensor artifact from real physiological excursion using signal quality and multi-metric concordance. Test whether true illness nights are retained for context without permanently shifting baseline.
129. **Spread calibration.** Verify `1.253 × EWMA absolute deviation` yields calibrated z-scores under skewed/lognormal HRV. Compare metric-specific transforms and MAD scaling.
130. **Missingness semantics.** Separate “not worn,” “source missing,” “rejected artifact,” and “no sleep” rather than one null update. Test stale-state and confidence behavior for each.
131. **Regime-shift detection.** Detect sustained changes after training, medication, pregnancy, illness recovery, or device replacement. Compare baseline reset, dual-speed adaptation, and change-point models.
132. **Seasonality and cycle.** Test optional weekday, seasonal, and menstrual-phase baseline components with enough data. Use out-of-sample improvement and avoid overfitting sparse histories.
133. **Device-change bridge.** Quantify systematic offsets when source/device family changes. Test overlap calibration or temporary uncertainty instead of abruptly mixing scales.
134. **Early-adaptation constants.** Simulate high/low first-night anchors and replay real onboarding cohorts to tune 8 nights, 3-night half-life, and 2.5× spread inflation. Optimize convergence without chasing noise.
135. **Recalibration UX semantics.** Compare hard epoch reset with “ignore selected bad nights,” partial metric reset, and guided rebaseline. Verify historical scores remain reproducible and provenance records the reset.
136. **Baseline audit metrics.** Log privacy-safe baseline age, valid count, spread, rejected count/reason, source-family transitions, and last update. Test whether these diagnostics predict score errors and stale complaints.

## Health Connect fusion / source arbitration — 14 prompts

137. ★ **Probabilistic source fusion.** Replace fixed 65% HC sleep blending with source-quality weights from device origin, stage presence, overlap, edit status, recency, and historical agreement. Evaluate on held-out paired nights and preserve provenance.
138. **Session-level temporal alignment.** Match HC and NOOP sleep intervals by overlap/IoU before fusing daily totals. Test main sleep, naps, split sleep, and duplicate records; prevent fusing unrelated sessions on the same wake-day.
139. **Clock-offset estimation.** Detect stable timestamp offsets between sources using overlapping HR/sleep transitions. Correct only with strong repeated evidence and retain raw timestamps/provenance.
140. **Stage taxonomy mapping.** Validate mappings for `core`, `light`, `deep`, `REM`, `awake`, `unknown`, and vendor-specific values. Test totals conservation and never map unknown to light by default.
141. **Asleep-versus-in-bed semantics.** HC sources differ in record meaning. Infer from record/stage metadata and test that efficiency is not computed from two asleep totals or invented without in-bed evidence.
142. **Fusion threshold sensitivity.** Sweep the 25-minute trigger and 65% weight by source pair. Report TST MAE and discontinuities around the threshold; consider continuous disagreement-based weighting.
143. **Manual edit supremacy.** Define whether a user-edited NOOP bedtime/wake should outrank later HC sync. Test sync-after-edit and deletion/tombstone durability.
144. **Cross-source uncertainty.** Surface agreement bands or confidence when HC and NOOP differ materially. Test whether users understand “sources disagree” better than a silently blended number.
145. **Steps interval de-overlap.** Use HC data-origin metadata and time ranges to de-duplicate phone/watch/strap intervals. Validate partial-day coverage and avoid both whole-day winner-take-all loss and naive summation.
146. **HR de-duplication.** When HC and BLE HR overlap, compare source-priority, quality scoring, and median fusion. Verify no duplicate cadence inflates Effort or workout detection.
147. **Write-back loop prevention.** Property-test origin/package identifiers so NOOP never reimports its own HC writes, including reinstall, package suffixes, and staging/debug variants.
148. **Permission-transition replay.** Grant, revoke, and re-grant HC permissions around imports. Ensure derived metrics invalidate appropriately, no stale fused values masquerade as current, and user edits survive.
149. **Source-specific calibration ledger.** Track historical bias/variance between each HC origin and NOOP per metric. Test whether learned reliability improves future fusion while resetting safely after device/source changes.
150. **Fusion gold-standard protocol.** Build a three-way corpus: NOOP raw-derived, HC/vendor-derived, and manual/PSG/reference. Evaluate each source and fusion independently with MAE, bias, limits of agreement, missingness, and subgroup results; never declare fusion better from agreement with one input alone.

## Recommended first experimental sequence

- Start with the golden cross-platform/replay corpus (#124) and algorithm-version provenance (#114).
- Then fix cadence integration (#23) and evaluate device-family sleep models (#1).
- Add uncertainty propagation (#40) and source-confidence weighted Rest (#56).
- Validate personal sleep need (#55) and hierarchical cold start (#125).
- Prototype activity-conditioned Stress (#70), multi-sensor workout probability (#85), and controlled step protocol (#99).
- Fit probabilistic HC fusion (#137) only after source-specific errors can be measured.
