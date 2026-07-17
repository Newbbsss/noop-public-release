# Alarm + buzz time — 20 ways to be more effective

NOOP-specific: phone smart alarm (soft window + deadline), exact-alarm permission, strap buzz (WHOOP 4 / 5 / MG), Test buzz, turn-back, custom fixed alarms, Rest↔Alarm bridge. Baseline: **8.6.108 / 378**.

Legend: **SHIP** = implemented in the same MAIN ship as this doc (8.6.109).

---

1. **Don’t hide Sleep tracker when Alarm is open.** Alarm page early-return blanked nights/stages/ledger/tools — felt “gone.” Keep a **Last night peek** + one tap back to Sleep. **SHIP**

2. **Put stages above Rest drivers / phone compare.** Hypnogram and stage minutes must appear in the first scroll beat, not after two honesty blocks. **SHIP**

3. **Night map chips** (Stages · Ledger · Trends · Tools) under Sleep|Alarm so tracker surfaces are findable without hunting. **SHIP**

4. **Tools strip must name Alarm** and switch the Sleep|Alarm tab (not only open the full editor). **SHIP**

5. **Empty “Set Alarm”** should open the Alarm *tab*, not jump straight into SmartAlarmScreen. **SHIP**

6. **Live Alarm subtitle** — scaffold shows Armed / Off / may-drift, not a static “Wake window” line. **SHIP**

7. **Today Quick alarm** matches Alarm glance: Off/Armed cue, selection border when Off, countdown when armed. **SHIP**

8. **Arm success settle** on the status pill (short scale/alpha settle; Reduce Motion = instant). **SHIP**

9. **Rest bridge hairline** under the Alarm hero clock when Rest % / need / wake clock can be stated honestly. **SHIP**

10. **Test buzz sits with Strap buzz** and says phone deadline still fires alongside (or without) strap buzz — no exclusive mystery. **SHIP**

11. **Boot re-arm honesty** one-liner when Armed: phone deadline survives reboot; strap buzz needs the strap connected again. **SHIP**

12. **Exact permission after grant** — ON_RESUME already refreshes; show a calm “Exact alarms allowed · Arm again if still Off” when returning from Settings with permission now true. **SHIP**

13. **Stages empty ≠ history empty** — when this night has no stage data, say so and tip ◀ older nights; never blank the whole Sleep tab. **SHIP**

14. **Ledger always reachable** — jump chip + section overline “Sleep debt ledger” so the 14-night balance isn’t mistaken for a missing feature. **SHIP**

15. **Custom + smart which-rings-first** — one caption on Alarm summary: soonest of soft window vs next custom (uses NextAlarmDisplay). **SHIP**

16. **Wake-rested ↔ Charge** — short caption when Wake rested is on: early cue uses Charge / sleep-need; hard deadline still stands; open More detail to tune. **SHIP** (extends existing turn-back/wake-rested help)

17. **Notification channel mute** — if alarm may be silent, one footnote: check NOOP alarm notification isn’t muted in system settings. **SHIP** (short honesty line when Armed)

18. **Window vocabulary** — keep “Window Xm” (not soft) on Today Quick when showing armed state. **SHIP**

19. **MG / Whoop5 Test buzz** — keep experimental copy; after Test buzz, point at Buzz ACK on Live/status (already toast; reinforce on summary strap line). **SHIP**

20. **Ship cadence** — every Alarm/Sleep MAIN batch carries ≥20 `AppChangelog` items covering tracker findability + buzz/alarm clarity. **SHIP**

---

## Why tracker felt gone (diagnosis)

| Cause | Effect |
|-------|--------|
| `if (sleepPage == Alarm) return@LazyScreenScaffold` | Stages, ledger, trends, tools **not composed** on Alarm tab |
| Stage `Hero` after Rest drivers + HC compare | Hypnogram below the fold on Sleep |
| Tools / marks at list bottom | “Tracker” marks feel missing |
| Empty Set Alarm → full editor | Leaves Sleep IA; Alarm tab unused |
| StagesVsTypical gated on `model != null` | Stage-less browsed night hides “vs typical” even when history exists |

No staging-algorithm change was required — presentation / IA / wiring only.
