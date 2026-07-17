# iOS â†” Android parity â€” ship bar **8.6.164-fable / 434**

**Scope:** Gilbert fork (`noop-v8.4.0-src`), Android private store **8.6.164-fable / 434**.  
**Date:** 2026-07-17  
**Policy:** document debt; claim Swift parity only under Done.  
**Public:** no `noop-public-release` this pass. Private GitHub IPA OK.

## Inventory vs Android MAIN (8.6.151â€“164)

| Feature | Android | iOS / Swift | Parity |
|---------|---------|-------------|--------|
| Sleep stages (hypnogram, bars, main night) | `SleepScreen.kt` + shared stager | `SleepView.swift` + StrandAnalytics | **Aligned** (shared engine) |
| mainSleepReason â€œthis nightâ€ | night-scoped copy | `onlyBlock` â†’ â€œthis nightâ€ | **Aligned** |
| Bedtime \| Wake dual + Arm bottom | Alarm glance card | `SmartAlarmView` Bedtime\|Wake hero + Cues (Band/Wind-down); phone wake Android-only (honest) | **Partial** â€” cues list thinner; no Turn-back / Wake-rested |
| NoopTimePicker (non-radial) | `NoopTimePickerDialog` | System `DatePicker` `.hourAndMinute` | **Partial** â€” usable; no custom Â± columns |
| Connect multi-bond preference | pin â†’ last-saved â†’ live GATT â†’ MG-named | pin + drop non-preferred; `WhoopModel.fromBleName` + `resolveForEstimates` | **Partial** â€” identity/ETA solid; last-saved/MG-named order thinner |
| FA soft frost (no liquid) | Key tile frac 0.5 frost | Health FA soft frost | **Aligned** (Health hero) |
| Age in profile | editable in pfp / profile | `ProfileStore.age` + Settings/profile | **Verify** on iPhone shell |
| Wear N/4 prior-calm | `DaytimeStress.priorCalmDayCount` | Shared analytics + Charge N/4 | **Partial** â€” stress Wear footnote verify |
| 6-axis band-only tester | Settings + Test Centre, no phone IMU | `SixAxisMotion` + `SixAxisMotionDotTester` + `BLEManager.latestStrapImu` (offload/live honesty) | **Aligned** (UI + publisher; live type-51 still firmware-gated) |
| Health under-bar soft frost | `LocalUnderBarInset` / no black slab | `FloatingTabBar` + content clearance | **Verify** |
| Alarm Band + Cues switches | Band buzz + Turn-back / Wake-rested / Wind-down list | Band + Wind-down cue card; Turn-back / Wake-rested Android-only | **Partial** |
| Cycle Replay setup/reset | Settings â†’ Replay Cycle â†’ onboarding | No Period calendar on iOS â€” awareness-only (`CyclePhaseEngine`) | **Android-only** until Period lands |
| Recovery vitals card removed | Gone; HRV/RHR/resp in Key Metrics | Removed from classic + liquid Today | **Aligned** |
| Bug report â†’ GitHub `user-bug` | `BugReportScreen` + More/Settings | `BugReportView` + More/Settings + Test Centre; issues â†’ **Newbbsss/noop-public-release** | **Aligned** |
| MG/5 identity + battery ETA | `fromBleName` / `resolveForEstimates` / rated life by family | Same APIs on `WhoopModel` + `BatteryEstimator.ratedLifeHours(whoop5Family:)` | **Aligned** |
| Charging flap policy | `ChargingOverlayPolicy` session latch | `ChargingOverlayPolicy` Swift twin + tests; full-screen overlay still Android-first | **Partial** |
| Today clouds / explain | `TodayCloudExplain` Charge/Effort/Rest | Liquid: `LiquidTodayCloudExplain` lacquer overlay on vessel/label tap; Reduce Motion â†’ ScoringGuide sheet; classic sheets kept | **Aligned** (liquid) / classic Partial |
| Strength Trainer | `StrengthTrainerUi` dense bank | Slim `StrengthPlanStore` + `StrengthTrainerSection` (presets, logâ†’manual, duplicate/delete). Muscle-heat dial Android-first | **Partial** (slim port) |
| Effort â‰¤0 honesty | `effortDisplayOrEmpty` | Same helper + Live/Workouts/Liquid/WeeklyDigest call sites | **Aligned** |
| Haptic Clock digit-hold | `Style.DIGIT_HOLD` default (6:30 â†’ 6s pause 3s) | `HapticClock` digit-hold default + `buzzTimeNow` holdLoops | **Aligned** |
| Alarm math dismiss | `AlarmMathChallenge` + `AlarmRingActivity` | Pure `AlarmMathChallenge` + tests; phone ring UI Android-only (no critical-alert wake) | **Partial** (logic ported; ring UI Android) |

## IPA pipeline

| Path | Status |
|------|--------|
| `Tools/build-ipa.sh` | **Ready** â€” Mac: unsigned `dist/NOOP-v<VER>-ios.ipa` |
| `Tools/build-ipa.ps1` | Windows honest exit 2 + instructions |
| `.github/workflows/ios-ipa.yml` | **Stamped** â€” defaults `8.6.164-fable` / `v8.6.164-fable` (no Android clobber) |
| `.github/workflows/fork-release.yml` `ios` job | Full release (also rebuilds Android â€” sync source first) |
| **This agent host** | **Windows** â€” IPA via CI |

**IPA download:** https://github.com/Newbbsss/noop-public-release/releases/download/v8.6.164-fable/NOOP-ios-unsigned-v8.6.164-fable.ipa  
**CI run:** https://github.com/Newbbsss/noop-public-release/actions/runs/29555005812  
**Mac one-liner:** `Tools/build-ipa.sh 8.6.164-fable` â†’ `dist/NOOP-v8.6.164-fable-ios.ipa`  
**CI re-dispatch:** `gh workflow run ios-ipa.yml -f version=8.6.164-fable -f release_tag=v8.6.164-fable --ref ios-parity-164 --repo Newbbsss/noop-public-release`

## Done this session (Swift Â· 164 bar)

- [x] `project.yml` â†’ **8.6.164-fable / 434**
- [x] Effort â‰¤0 `effortDisplayOrEmpty` twin + key call sites
- [x] HapticClock digit-hold default + Morse retained + tests
- [x] `BLEManager.buzzTimeNow` digit-hold / holdLoops
- [x] `AlarmMathChallenge` pure Swift + unit tests (ring UI remains Android)
- [x] Liquid Today cloud explain overlay (`LiquidTodayCloudExplain`)
- [x] 6-axis band-only tester (Settings Advanced + Test Centre) + `latestStrapImu`
- [x] Strength Trainer slim (`StrengthPlanStore` + Workouts section)
- [x] Cycle Replay documented **Android-only** (no Period calendar on iOS)
- [x] `ios-ipa.yml` defaults stamped to 164

## Remaining / Android-only

1. Cycle Period calendar + Replay Cycle setup (Android until Period lands)
2. Strength dense muscle-heat dial / a11y bank (Android-first; slim port shipped)
3. AlarmRing math dismiss UI + guaranteed phone wake (needs critical-alert / Android ring)
4. Multi-bond last-saved / MG-named preference order
5. Turn-back / Wake-rested alarm cues

## Related

- `ANY_MODEL_CONTINUE.md` â€” store bar + leftovers  
- `docs/DEBUG_VS_MAIN_PARITY.md` â€” Android DEBUG vs MAIN  
- `docs/IOS.md` â€” sideload / AltStore  
- `docs/BUILDSTORE_GITHUB_PACKAGES.md` â€” Android GitHub APK URLs  
