# DEBUG vs MAIN parity checklist

Audited against the Android source on 2026-07-13. In this document:

- **DEBUG** means a `fullDebug` build (`BuildConfig.DEBUG == true`).
- **MAIN** means a normal `fullRelease` build (`BuildConfig.DEBUG == false`).
- **DEMO** is a separate product flavor (`BuildConfig.ENABLE_DEMO == true`) and is not evidence of a DEBUG/MAIN difference. Demo-only synthetic seeding must not leak into MAIN.
- “Keep on MAIN” means user-facing intelligence, honest empty-state UX, or a real device capability.
- “Debug-only” means developer instrumentation, forced preview, trace/report tooling, or annotation UI.

## Executive finding

Most apparent DEBUG/MAIN differences are **data-state differences, not screen implementations**. Sleep, Stress, Alarm, the three score vessels, and real charging use the same production code. Do not strip those from MAIN.

The verified build gates are:

1. `WhoopAlgoCompare` navigation and the Today compare block.
2. Test Centre, its UI demo lab, and the Test Centre row in Settings.
3. Forced charging preview entry points.
4. Review-pin annotation overlay.
5. An honest no-reading SpO₂ card/metric placeholder.

The first and fifth contain user value and should be reviewed for MAIN promotion. The middle three are true debug tooling.

## Global shell and navigation

- [x] **Bottom tabs: Today, Trends, optional Cycle, Sleep, More** — same implementation in DEBUG and MAIN.
- [x] **Cycle tab** — preference-gated, not build-gated. Keep on MAIN when cycle tracking is enabled.
- [x] **Quick actions and Updates inbox** — same in both builds. Keep on MAIN.
- [x] **Real charging host** — `StrapChargingHost(viewModel)` mounts for every build and reacts to real `LiveState.charging`. Keep on MAIN.
- [ ] **Review-pin overlay** — DEBUG adds a floating annotation layer on every route. This is true debug clutter; keep DEBUG-only.
- [ ] **NOOP vs WHOOP route registration** — the destination is registered only inside `if (BuildConfig.DEBUG)`, despite comments calling it a FullRelease product feature. Promote the user-facing comparison route to MAIN, while keeping raw traces and diagnostics out.
- [ ] **Goals navigation defect on MAIN** — one release branch sends “open Test Centre” to `whoop_algo_compare`, but that route is absent in MAIN. Another release branch sends “open compare” back to Today. Resolve before claiming parity.

## Today tab

- [x] **Charge, Effort, Rest vessels** — same production composables, state resolution, confidence tiers, carried-score behavior, and tap behavior. Keep all three on MAIN.
- [x] **Vessel intelligence** — Charge drivers, Effort explanation, Rest-to-Sleep deep link, calibration copy, prior-night freshness, and WHOOP-label fallbacks are not debug clutter.
- [x] **Charging state in header/hero** — real battery and charging state are shared. Runtime estimate hiding while charging is production behavior.
- [ ] **Forced charging animation** — DEBUG can invoke an artificial 67% charging overlay from More, Test Centre, intent extras, or a broadcast. Keep these triggers DEBUG-only; do not remove the real charging overlay.
- [x] **Alarm affordance** — Today’s next-alarm footnote/deep link is wired for both builds. Keep on MAIN.
- [x] **Sleep affordance** — Rest vessel, carried-sleep note, Sleep card, and empty-state link are shared. Keep on MAIN.
- [x] **Stress card and live stress refresh** — shared. Keep on MAIN.
- [x] **Health/vital tiles** — HRV, RHR, respiratory rate, skin temperature, Fitness Age, Vitality, Steps, and calories share the same code.
- [ ] **SpO₂ with no calibrated sample** — DEBUG keeps the Blood Oxygen card and tile visible with an honest no-data state; MAIN removes it until `spo2Sample != null`. This is not developer instrumentation. Consider showing the same honest “No calibrated reading yet” state on MAIN, especially when raw red/IR ADC is banked.
- [x] **SpO₂ with a real calibrated sample** — visible in both builds. Never invent a percentage from raw ADC.
- [ ] **NOOP algo vs WHOOP app block** — DEBUG-only on Today. This is user-facing calibration/explainability, not inherently debug clutter. Promote a cleaned, provenance-labelled version to MAIN or provide a valid MAIN route from More/Goals.
- [x] **Live sessions, hydration, active-workout continuation, devices, sources, workouts** — shared.

## Trends tab

- [x] Same route and implementation in DEBUG and MAIN.
- [x] Charge/Effort/Rest history, metric trends, ranges, and reports are user-facing intelligence. Keep on MAIN.
- [x] Empty histories are data-state behavior, not build stripping.
- [ ] Add a release smoke test that opens Trends after a computed-only night, an imported-only day, and a fused day; compare series provenance rather than screenshots alone.

## Cycle tab / Period Calendar

- [x] Same implementation; availability is controlled by the user’s cycle-tracking preference, not DEBUG.
- [x] Sleep, SpO₂/raw-ADC explanatory copy, skin temperature, and phase context are production UX. Keep on MAIN.
- [ ] Verify MAIN displays the raw-SpO₂ disclaimer when raw ADC exists without a calibrated percentage; do not hide the whole health context just because calibration is unavailable.

## Sleep tab

- [x] Same `SleepScreen` route and implementation in both builds.
- [x] Night browsing, naps/split sleep, edit/delete/undo, real hypnogram, reconstructed imported-stage display, Rest, efficiency, consistency, hours-vs-needed, restorative share, respiration, debt, and stage trends belong on MAIN.
- [x] Alarm deep link from Sleep is shared and should stay.
- [x] Data Sources and Trends links are shared and should stay.
- [x] Experimental Sleep Staging V2 toggle exists in shared Settings diagnostics; the engine itself is hardware-family gated, not build-gated.
- [ ] Test Centre sleep traces and UI demo controls are instrumentation. Keep their trace/report controls DEBUG-only, but keep all resulting production-grade sleep explainability on MAIN.
- [ ] Add screenshot/data parity fixtures for: no night, duration-only HC night, imported staged night, computed V1 night, computed V2 5/MG night, edited night, nap, and low-confidence restorative share.

## More tab

- [x] Insights, Body, Data, and App groups are shared.
- [ ] DEBUG inserts `WhoopAlgoCompare` in Insights. MAIN lacks the row. Promote the cleaned comparison experience or remove broken release deep links that imply it exists.
- [ ] DEBUG inserts Test Centre in App. Keep DEBUG-only.
- [ ] DEBUG adds a prominent “UI demo” charging-preview card above normal More content. True debug clutter; keep DEBUG-only.
- [x] Smart Alarm remains under App in both builds.
- [x] Stress, Sleep-adjacent body tools, Health, Workouts, Live, Lab Book, Intervals, and Breathe are shared.

## Intelligence

- [x] Same screen and orchestration entry point in both builds.
- [x] On-device recompute, score confidence, provenance, and recalibration are product intelligence; keep on MAIN.
- [ ] Test-mode trace sinks are zero-cost when inactive and should remain inaccessible from MAIN unless a deliberate support mode is designed.
- [ ] MAIN needs an end-to-end test that recomputes, persists under the computed source, and refreshes Today/Sleep/Stress without Test Centre.

## Insights Hub, Journal Insights, Explore, Compare, and Coach

- [x] Routes and implementations are shared.
- [x] Correlations, behavior effects, period comparisons, and coaching are user-facing. Do not classify statistical detail as debug clutter merely because it explains an algorithm.
- [ ] Ensure MAIN exposes confidence/sample-size caveats wherever DEBUG test data made a card appear more complete.

## NOOP vs WHOOP comparison

- [ ] DEBUG has the route, More row, and Today block.
- [ ] MAIN has neither a registered route nor a row, even though source comments describe it as a FullRelease feature.
- [ ] MAIN Goals callbacks can target the absent route or fall back to Today, creating inconsistent behavior.
- [ ] Recommendation: register the route in both builds; show only user-entered/imported WHOOP labels, sample count, error/correlation validity, and provenance on MAIN. Keep raw logs, trace toggles, capture internals, and synthetic fixtures in DEBUG/Test Centre.

## Live

- [x] Same screen and real BLE behavior.
- [x] Real charging, battery, HR, HRV snapshot, workout start, and device-management affordances belong on MAIN.
- [ ] Forced preview states belong in DEBUG only.
- [ ] Confirm MAIN still exposes a dismissible real charging full-screen experience and a way to reopen it from the real charge hero.

## Workouts and live workout

- [x] Same screen and persisted workout behavior.
- [x] Manual logging/editing, auto-detected bouts, zone summaries, and honest approximate labels belong on MAIN.
- [ ] Workouts/GPS trace and report controls are Test Centre instrumentation; keep DEBUG-only.
- [ ] A sparse production dataset may look “less complete” than demo/debug data. Treat that as a provenance/empty-state issue, not a reason to remove the UI.

## Health, Vital Signs, and vital detail

- [x] Same routes in both builds.
- [x] Fused record, Lab Book, cycle link, illness heads-up, body-clock result, vital trends, and provenance are user-facing intelligence.
- [ ] MAIN should preserve honest unknown states for unavailable sensors. In particular, hiding the SpO₂ row until a percentage exists loses the explanation that raw data may be present but uncalibrated.
- [x] Raw SpO₂ red/IR means are intentionally not converted to a percentage. Keep that safety invariant.

## Lab Book

- [x] Same implementation.
- [x] Health observations, source labels, and history are production features.
- [ ] Verify MAIN does not depend on Test Centre activation to surface ordinary records.

## Stress

- [x] Same `StressScreen` in both builds.
- [x] 0–3 autonomic load, 15-minute curve, live tip, zone durations, motion context, baseline/calibration copy, breathe link, and “How computed” content belong on MAIN.
- [x] Charging freezes the volatile live tip while retaining history in both builds; this is production correctness, not preview behavior.
- [ ] Test Centre can add stress traces/log volume, but it does not own the Stress UX. Keep traces DEBUG-only and the model/explanations MAIN.
- [ ] Add parity fixtures for no baseline, sparse HR, dense HR+RR, workout overlap, sleep-state overlap, charging freeze, and sustained high stress.

## Breathe

- [x] Same screen and engine behavior.
- [x] Paced breathing and HR-down feedback are user-facing. Keep on MAIN.

## Intervals

- [x] Same route and UI.
- [x] Training interval guidance and zone behavior belong on MAIN.

## Rhythm

- [x] Same route and consent gate.
- [x] Honest “no clear reading yet” is a production empty state, not debug clutter.
- [ ] Keep experimental consent and non-diagnostic wording on MAIN; keep raw probe/report plumbing in DEBUG.

## Period Calendar

- [x] Same as the optional Cycle tab route.
- [x] See Cycle parity above.

## Fused Record

- [x] Same route.
- [x] Source arbitration and “your data, fused” provenance are core MAIN intelligence.
- [ ] Verify MAIN source chips do not disappear when Test Centre is absent.

## Apple Health, Data Sources, and Backup/Sync

- [x] Same routes in both builds.
- [x] Import, permission, source priority, and backup UX belong on MAIN.
- [ ] Test Centre export bundles are separate from user backup/export. Do not remove user-controlled data portability while stripping debug reports.

## Devices and Add Device

- [x] Same route and scanning/connection flows.
- [x] Model-aware controls are based on selected/detected hardware, not DEBUG.
- [x] The old DEBUG escape that exposed all 5/MG controls was removed; keep that model gate.
- [ ] Diagnostic log sharing may remain in MAIN as a support feature if clearly labelled and privacy-reviewed; verbose live tracing belongs in DEBUG/Test Centre.

## Automations

- [x] Same route.
- [x] User automations remain on MAIN.
- [x] Strap alarm controls were consolidated into Alarm; do not mistake their absence here for DEBUG/MAIN drift.

## Alarm

- [x] Same `SmartAlarmScreen` in both builds.
- [x] Phone wake window, guaranteed OS deadline, strap firmware buzz, wind-down reminder, exact-alarm permission recovery, and personal sleep plan all belong on MAIN.
- [x] Alarm availability depends on OS permission, bond state, and strap generation, not build type.
- [ ] Add MAIN tests for permission denied/granted, strap offline, WHOOP 4.0, WHOOP 5/MG, exact deadline, and early smart wake. DEBUG preview success is not sufficient.

## Notifications

- [x] Same route and settings.
- [x] User notifications and battery alerts belong on MAIN.
- [ ] Debug export scheduling is not a normal notification feature; keep it under advanced/support tooling.

## Goals

- [x] Goals board itself is shared.
- [ ] DEBUG callbacks can open Test Centre and NOOP-vs-WHOOP.
- [ ] MAIN substitutes inconsistent destinations, including one unregistered route. Fix destination contracts so a release build never offers a dead action.
- [ ] Goal descriptions may remain user-facing, but internal test instructions and implementation status should not masquerade as finished product UX.

## Settings

- [x] Most of Settings is identical in both builds.
- [x] Appearance, profile, devices, behavior, cycle, units, data, privacy, and user experiments belong on MAIN.
- [x] WHOOP 5/MG controls are hardware-gated, not DEBUG-forced. Keep this.
- [x] Experimental Sleep V2 is shared and defaults off; keep it model-gated and clearly experimental.
- [x] “Debug logging,” strap-log sharing, raw sensor CSV, scheduled debug export, and diagnostics currently exist in shared production Settings. They are not DEBUG/MAIN differences today.
- [ ] Decide explicitly whether each shared diagnostic is a supported user export or developer clutter:
  - Keep raw-data export and privacy-reviewed support log sharing if users own and can inspect the output.
  - Move verbose logcat mirroring and scheduled debug bundles behind an advanced support mode if MAIN simplification is desired.
- [ ] Test Centre Settings section is DEBUG-only. Keep it DEBUG-only.

## Test Centre

- [ ] Entire route and navigation row are DEBUG-only.
- [ ] UI demo lab, forced overlays, domain test modes, capture counters, report assembly, diagnostic tools, advanced probes, and goals/test readouts are true diagnostic tooling.
- [ ] Keep Test Centre DEBUG-only unless a separately designed, consented support mode is introduced.
- [ ] Do not remove the production feature being tested when removing its Test Centre control: Sleep, Stress, Steps, Workouts, Charge, battery, display, storage, and connectivity must remain functional on MAIN.

## Quick Start and Step Training

- [x] Same routes.
- [x] Quick Start is user onboarding.
- [x] Step Training is user calibration, not Test Centre. Keep on MAIN.
- [ ] Preserve honest “estimate” source labels on MAIN.

## Charging behavior: explicit decision

- [x] Keep on MAIN: real charging detection, full-screen animation, tone, battery percentage, dismiss/reopen behavior, foreground safety, and charging-aware Stress/battery logic.
- [ ] Keep DEBUG-only: intent/broadcast forcing, emulator preview, the More demo card, and Test Centre preview controls.
- [ ] Release test: drive a real `charging=true → false` transition or a deterministic production-state unit/instrumentation seam. Do not infer production parity from the DEBUG broadcast.

## SpO₂ behavior: explicit decision

- [x] Keep on MAIN: real calibrated SpO₂ percentages from trusted imports/sources.
- [x] Keep on MAIN: raw red/IR ADC with an explicit “not a calibrated percentage” label.
- [ ] Promote from DEBUG: the honest empty Blood Oxygen tile/card so users understand capability and missing prerequisites.
- [ ] Never promote: synthetic percentages, proprietary-curve guesses, or demo values presented without a DEMO badge.

## Sleep and Stress: explicit decision

- [x] Both full screens and all user explainability are MAIN features already.
- [ ] Keep DEBUG-only: trace sinks, capture/report mode, synthetic/UI demo scenarios.
- [ ] Preserve on MAIN: confidence, calibration, sparse-data, source, stale/fresh, and charging states. Those are product truth, not clutter.

## Release acceptance checklist

- [ ] Build `fullDebug` and `fullRelease` from the same commit.
- [ ] Seed each with the same non-synthetic fixture or import; do not compare DEBUG test data to an empty MAIN database.
- [ ] Walk every route listed above and record route-open success, source token, value, confidence, and empty-state copy.
- [ ] Assert that only Test Centre, forced charging preview, review pins, and developer traces are absent on MAIN.
- [ ] Decide and test promotion of NOOP-vs-WHOOP.
- [ ] Decide and test promotion of the honest empty SpO₂ state.
- [ ] Verify real charging, Alarm, all three vessels, Sleep, and Stress on MAIN.
- [ ] Verify Goals contains no dead release destination.
- [ ] Verify demo seeding never runs in `fullRelease`.
- [ ] Keep screenshots secondary to data/provenance assertions; identical pixels can still hide source-selection bugs.

## 2026-07-13 overnight follow-up: current-source corrections

This addendum supersedes any broader statement above that source parity alone
proves runtime correctness. The current source is 8.6.67-fable / 338. No build,
test, install, publish, emulator, or Fold action was performed during this
follow-up. Existing test files were inspected but not run.

### MAIN Effort vessel

- [x] There is no MAIN-only Effort vessel gate. Both variants show a value when
  either stored `DailyMetric.strain` or `liveTodayStrain` is non-null
  (`TodayScreen.kt:1300-1326,3205-3239`).
- [ ] The live value is not reactive to raw-HR revisions; its effect keys are
  daily/day-selection state (`TodayScreen.kt:875-908`). A clean MAIN database
  can remain blank while DEBUG's separate database masks the defect with a
  stored score.
- [ ] `AppViewModel` captures the active device ID once, so an in-process
  remove/re-add or switch can leave BLE writes and Today/recent-day reads on
  different IDs (`AppViewModel.kt:92-119,308,505-518`).
- [ ] The analysis fingerprint omits PPG-derived HR even though scoring can read
  it (`WhoopDao.kt:179-193,581-584`; `WhoopRepository.kt:234-237`).
- [ ] The engine checks 200 rows in a broad night window before it reads valid
  full-calendar-day HR for Effort (`IntelligenceEngine.kt:465-530`). Split
  Effort eligibility from sleep-analysis eligibility if the affected fixture
  confirms this cause.
- [ ] Compare per-package active ID, measured/PPG HR counts, computed owner,
  analysis watermark and v324 flag before calling this a DEBUG/MAIN algorithm
  difference. Do not modify `StrainScorer` in the current ownership lane.

### Sleep Light bias

- [x] V1/V2 staging implementations are shared between fullDebug and MAIN.
  Different package preferences, data, source arbitration and restage times can
  produce different results without a build branch.
- [ ] A session with fewer than two gravity samples returns one all-Light
  segment before the build-337 missing-epoch fallback runs
  (`SleepStager.kt:1318-1324,1507-1526`). Therefore build 337 does not close the
  documented fully gravity-dead Fold case.
- [ ] V1 falls through to Light whenever Wake/Deep/REM conjunctions fail, and
  later smoothing favors lighter neighbors
  (`SleepStager.kt:1994-2038,2068-2083,2277-2364`). Validate against
  labelled/reviewed nights; current
  synthetic tests are not accuracy evidence.
- [ ] Edited-night healing bypasses the normal WHOOP-4 V2 family gate
  (`IntelligenceEngine.kt:40-49,576-580,736-743`;
  `SleepStageHealer.kt:94-111`).
- [ ] Settings incorrectly says V2 leaves scores unchanged; changed stage totals
  feed Rest and can feed Charge (`SettingsScreen.kt:1903-1910`;
  `AnalyticsEngine.kt:348-389`).

### BLE chrome under Today vessels

- [x] Current-source direct-under-hero status is the `scores_as_of` item
  (`TodayScreen.kt:1362-1399`), plus conditional `SyncingHistoryNote` at
  `TodayScreen.kt:1450-1457`.
- [x] `RecordingStatusChip` remains defined at `TodayScreen.kt:5235-5344` but
  has no production call. An installed `Recording` chip would indicate an older
  or different binary, not this source.
- [ ] Do not edit Today while Fold/Effort owns it. Do not suppress the shared
  `Components.SyncingHistoryNote`, because Health/Sleep/Intelligence use it.
- [ ] After ownership is released, remove only the confirmed Today call site;
  preserve the functional header battery/Devices affordance and lower Data
  Sources section unless explicitly included.

### Charging correction

- [x] Real charging source parity is confirmed; runtime MAIN verification is
  not. Build-337 emulator/forced-preview success cannot prove a real MAIN
  `charging=false/null -> true -> false` sequence.
- [ ] Add a production-state host test and later real-hardware release matrix.
  Existing charging tests cover event/policy predicates only.
- [x] Route explicit `CHARGING_ON/OFF` events or document why battery/console
  inference is authoritative (`Enums.kt:48-50`;
  `WhoopBleClient.kt:3982-4031`).

### Rest/Sleep correction

- [ ] Build 338's RestDrivers claim is incomplete: the UI omits consistency
  even though it shaped persisted Rest (`RestDrivers.kt:24-28,120-135`;
  `SleepScreen.kt:4882-4887`).
- [x] Clamp blank WHOOP `sleep_performance` fallback to its 0-100 contract;
  keep `hours_vs_needed_pct` free to exceed 100 if intended
  (`WhoopCsvImporter.kt:388-398`; `SleepScreen.kt:959-966`).
- [ ] Preserve per-metric provenance. Today can call imported Rest `NOOP`, while
  Sleep can call computed resolved Rest `WHOOP app`
  (`TodayScreen.kt:3144-3165,3370-3378`;
  `SleepScreen.kt:246-267,1370-1377`).
- [ ] Align HC sessions temporally before fusion and make visible stage/Rest
  provenance explicit (`IntelligenceEngine.kt:764-815`;
  `HcNoopAlign.kt:88-106`).

### Alarm correction

- [x] P0/P1: prevent an early smart wake from rearming today's original
  deadline (`SmartAlarmReceiver.kt:37-44`;
  `SmartAlarmScheduler.kt:50-64,155-162`).
- [ ] P0/P1: the receiver's only audible path is its notification channel; the
  `guaranteed wake` copy is not valid when notifications/channel delivery is
  unavailable (`SmartAlarmReceiver.kt:55-116`).
- [ ] Custom alarms must not appear enabled when exact scheduling silently
  returns (`CustomAlarmScheduler.kt:19-28`).
- [ ] Add missed-deadline boot, timezone/manual-clock, permission, notification,
  and real strap-family verification. Payload tests are not physical-wake proof.

### Today chrome/navigation correction

- [ ] MAIN Goals G2/G5 labels do not match their destination; release returns to
  Today and a fallback references an unregistered compare route
  (`GoalsBoardScreen.kt:40-65`; `AppRoot.kt:596-604,664-680`).
- [ ] Updates is currently unreachable: Today accepts but does not invoke
  `onQuickActions`/`onOpenUpdates`, and the bottom `+` opens a different sheet
  (`TodayScreen.kt:260-265`; `AppRoot.kt:514-526,725-827,1144-1202`).
- [ ] Add semantic click/tab-selection coverage for custom bottom chrome
  (`AppRoot.kt:1722-1814,1857-1931`).
- [ ] Today chevrons remain explicitly out of scope.

### IntelligenceEngine research linkage

The following are concrete current-code priorities grounded in
`ENGINE_IMPROVEMENT_RESEARCH_150.md`:

1. [x] Reconcile computed workouts by stable ownership, not inferred sport text
   (`IntelligenceEngine.kt:896-910,1274-1278`; `WhoopDao.kt:533-534`).
2. Implement research #112/#116 with an atomic derived generation; current
   daily/Rest delete-then-upsert is non-transactional
   (`IntelligenceEngine.kt:1051-1065`).
3. [x] Eliminate temporal baseline leakage by chronological replay and add prefix
   invariance tests (`IntelligenceEngine.kt:610-693,792-815`).
4. [x] Calculate skin-temperature deviation before Charge and persist the exact
   inputs that shaped the score
   (`IntelligenceEngine.kt:795-809,861-862,1349-1366`).
5. Use one deduplicated, bounded Rest context for persisted Rest, Charge,
   drivers, widgets and notifications
   (`IntelligenceEngine.kt:393-404,810-815,1349-1356`).
6. Implement durable field-level provenance, algorithm/config version, input
   fingerprint and confidence (research #111/#114/#121/#124).
7. Research #23 remains open, but `StrainScorer` is protected in this lane.
8. Replace fixed-current-offset/86400 stepping with historical timezone rules
   and the #117 ownership matrix (`IntelligenceEngine.kt:364-370,435-466`).

No current test establishes sleep-stage accuracy, clinical validity, or a
full `analyzeRecent -> Room -> merged Flow -> Today/Sleep` production path.
