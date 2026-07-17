# Today life anim + floating cloud explain

Ship: **8.6.145** source bank (clouds epic) · prior life-anim notes below.

## Floating cloud explain (SHIP_IMPROVE_400 finale epic)

**Gilbert ask:** Today list floating clouds; bubbles animate **forward**, get **big**, **blur the back**, full **explanation inside** (replace static explain sheets as the motion language).

### Workshop (picked C)

| Variant | Idea | Verdict |
|--------|------|---------|
| **A** | Ambient cloud wash only; ModalBottomSheet / Dialog explains stay | Rejected — explanation not *in* the bubble |
| **B** | Key Metrics tile morphs in-grid | Rejected as primary — 3-col can't hold Charge breakdown |
| **C** | Forward-grow cloud portal + idle float + list wash | **Shipped** |

### Feel (how Gilbert should experience it)

1. Open Today — soft Effort-tinted clouds drift in the **top band only** (Reduce Motion = static); they **fade as you scroll** (hero sink) and never paint mid-list.
2. Key Metrics tiles are **static** (no bob/shake — Dense Today retired idle float).
3. Tap **Charge** vessel or Charge tile → Today softens/blurs + scales back slightly; a lacquer cloud grows forward (0.78→1, EaseOutQuint ~420ms) with **What shaped your Charge** + full drivers/contributors/readiness inside.
4. Tap **Effort** vessel/tile → same portal with dual-scale honesty (0–100 vs 0–21).
5. Tap **Rest** Key Metrics tile → Rest cloud (Open Sleep + How Rest). Rest **vessel** still deep-links Sleep.
6. Mist scrim + close / Back dismisses; deep-dive "How X is calculated" still opens ScoringGuide.
7. Hero vessels read **Effort · Charge · Rest** (Charge↔Effort swap); no polarity / "fills up" chrome under the bubbles.
8. Past-day Charge uses `mayShowChargeVessel` (allows Whoop CSV / On-device; blanks WHOOP-app / Apple / HC) so yesterday's Charge isn't empty while today's carry still works.
9. Hold-+ dial bloom uses `NoopMotion.press()` spring (not 100ms snap); spokes fade with EaseOutQuint ~220ms after a mid-bloom gate — fluid pre-152 feel, still responsive. Spoke label is **Workout** (no "Up =").

### Files

- `TodayCloudExplain.kt` — portal, wash, float, backdrop blur
- `TodayScreen.kt` — wire Charge/Effort/Rest; ambient wash
- `LifeMotes.kt` — `LifeChapterLacquer` Charge/Rest cloud copy
- `AppRoot.kt` — hold-+ dial spring bloom + Workout spoke label

---

## Reduce Motion

`rememberReduceMotion()` (Android animator duration scale = 0) → **static** poses, no float/spin. Cloud portal → instant crossfade (duration 0).

| Surface | Motion on | Reduce Motion |
|--------|-----------|---------------|
| Today cloud portal | Forward-grow + backdrop blur | Instant mist + bubble |
| Today list cloud wash | Soft drift in top band; fades with scroll | Static clouds at top |
| Key Metrics idle float | Off (identity — no shake) | Static |
| Cycle four-point stars | Spawn → longer hover → soft break (`CycleStarLifeMotes`, `CYCLE_INTENSITY`); Learning = dimmed | Static stars at rest poses |
| Hydration droplets | Bob + soft cyan bloom (`HydrationSipLifeMotes`, `SIP_INTENSITY`) | Static droplets, no bob |
| Today / Nutrition Sip | Droplets + tube + optional `HydrationGoalBloom` | Static; no burst |
| Today / Nutrition Sip +250 | One-shot `HydrationSipBurstSpark` (`SIP_BURST_MS`) | No burst |
| Nutrition meals hero | Charge diamonds (`FUEL_HERO_INTENSITY`) + `NutritionMealBurstSpark` on log | Static motes; no burst |
| Today Fuel peek | Same motes at `FUEL_PEEK_INTENSITY` | Static motes |
| Quick meal chips | Quiet Charge mote wash (`FUEL_CHIP_INTENSITY`) | Static wash |
| Alarm / Today bedtime | Moon crescents (5); Today `TODAY_MOON_INTENSITY` 0.82 · Alarm 1.0 · Wake WindowCard `ALARM_WINDOW_MOON_INTENSITY` 0.82 | Static moons; Off = calmer |
| Creatine Taken spark | Soft Rest pulse when logged today | Static spark |
| Supplement catalog log | `SupplementLogBurstSpark` one-shot (`SIP_BURST_MS − 80`) | No burst |
| Today Quick Alarm / WindowCard Arm pill | `ARM_SETTLE_MS` 280ms settle | Instant |
| Nav / Sleep↔Alarm | 140ms fade · whisper slide | Instant cut |
| Live / Health / Today optical lock | `OpticalLockHairline` + `opticalLockCaption` (`OPTICAL_LOCK_LEAD`) | Static fill at current % |
| Live / Health / Today R-R feel | `RrFeelProgressHairline` + `rrFeelClimbCaption` while 1..<3 beats | Static fill at beat/3 |

## Shared file

`LifeMotes.kt` — meal / moon / sip / sip-burst / goal-bloom / meal-burst / supplement burst / **CycleStarLifeMotes** / **LifeChapterLacquer** / **OpticalLockHairline** / **RrFeelProgressHairline** / optical + alarm DRY helpers. Token-alpha bloom only (≤0.22) — no purple glow, no nested cards.

`TodayCloudExplain.kt` — floating-cloud explain portal (finale epic).

## LifeChapterLacquer (8.6.124+)

- `SURFACE_ALPHA` 0.50 · `CORNER_DP` 16 · `PAD_V_DP` 11 · `BORDER_ALPHA` 0.22 · `CHAPTER_MIN_HEIGHT_DP` 52
- Intensities: Today moons 0.82 · Alarm 1.0 · Fuel peek 0.72 · Fuel hero 1.0 · Fuel chip 0.55 · Sip 0.88 · Cycle 0.90
- Frost wash: Glance 1.05 · Today Quick 0.95 · Arm 1.15 · Extras 1.10 · Window 1.00 · Wake-edit 1.15
- Clocks: `ALARM_GLANCE_CLOCK_SP` 22 · `TODAY_ALARM_CLOCK_SP` 18
- Status: `ALARM_STATUS_ARMED` / `OFF` / `MAY_DRIFT`
- Armed glance: pill one word · aim = duration±window only · foot = bare soonest · deadline `By HH:mm` (skipped when May drift)

## Alarm regions (MAIN)

- Sleep Alarm: Glance / Arm / Extras / Wake frost + Rest hairline border
- Wake settings: Window / Plan / Arm settings / Explain / Evening frost + border
- Shared LifeMotes captions stay DRY across Today Quick + Alarm glance + WindowCard

## Related

- `LifeMotes.kt`, `TodayCloudExplain.kt`, `TodayScreen`, `SleepScreen`, `SmartAlarmScreen`, `docs/FABLE_DO_NOW.md`
