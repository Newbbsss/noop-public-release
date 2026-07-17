# Alarm / Wake — 50 concrete style improvements

Surfaces: Sleep → **Alarm** (`WhoopLikeQuickAlarmCard` in `SleepScreen.kt`) + full **Wake settings** (`SmartAlarmScreen.kt`).  
Baseline: **8.6.110 / 380**. Legend: **SHIP** = **8.6.111**. Keep Armed / may-drift / exact honesty. Soft Rest lacquer — not purple AI sludge, no glow, no nested cards.

---

## Quick Alarm glance (1–14)

1. Hero is deadline clock — bedtime should own the first glance. **SHIP**
2. Preset wake chips fight the new bedtime composition. **SHIP** (removed from quick)
3. Window preset chip row (15–90) is a second dump under presets. **SHIP** (removed from quick)
4. “Earliest” mid-page + presets = three wake editors. **SHIP** → one wake edit at bottom
5. Aim / bed / window / custom / extras crammed into footnote soup again. **SHIP** (bed hero + short captions)
6. Status pill OK but sits above a styleless clock stack — pair with Rest overline. **SHIP**
7. Range title (earliest→deadline) competes with bedtime hero — demote under wake row. **SHIP**
8. Rest bridge hairline good; bridge copy can sit under bed, not under deadline. **SHIP**
9. Arm CTA flanked by ±15m looks utilitarian — keep Arm primary, window as caption. **SHIP**
10. Tertiary toggle grid (Strap / Turn-back / Wake rested / Wind-down) equal weight. **SHIP** (quieter secondary row)
11. Off-state essay at bottom still long — one short line. **SHIP**
12. Wake settings CTA uses accent weight equal to Arm — demote to footnote link. **SHIP**
13. No Bedtime icon / Rest scenic wash on quick summary. **SHIP**
14. Countdown color = wakeAccent; bedtime number should use Rest bright. **SHIP**

## Wake settings full page (15–28)

15. WindowCard leads with earliest→deadline — add recommended bedtime display. **SHIP**
16. PersonalSleepPlanCard title2 is good; bump bedtime to number(36f) hero. **SHIP**
17. ExplanationCard flat footnote — add Rest overline “How it works”. **SHIP**
18. AlarmSettingsCard gold Alarm icon while Rest domain owns wake — use Rest tint. **SHIP**
19. Scaffold subtitle static — show Off / Armed / may-drift like Sleep Alarm. **SHIP**
20. Cards stack with identical 20dp padding — vary section gaps (12 / 20). **SHIP**
21. Custom / Strap / Wind-down cards look like three twins — distinct overlines already; strengthen Wind-down evening overline color. **SHIP**
22. Exact-alarm early warning is plain text — soft warning pill border (no glow). **SHIP**
23. Why-an-alarm empty paragraph lacks Rest hairline separator from WindowCard. **SHIP**
24. TimeChip gold on Wake settings is fine; bedtime hero should not also be gold. **SHIP**
25. Window stepper row denser than plan card — align vertical rhythm. **IN-TREE**
26. ToggleRow help text tertiary; when Armed, one positive Rest caption under master switch. **IN-TREE**
27. Remove duplicate Shield + Alarm icon noise at top of consecutive cards. **SHIP** (settings icon Rest)
28. Lazy scaffold items need clearer visual chapters (overline between plan / settings / custom). **SHIPPED** (8.6.116 — Custom + Extras)

## Color, type, chrome (29–40)

29. `alarmWakeAccent()` fallback blue on gold packs — keep for may-drift; bedtime uses Rest. **SHIP**
30. ScenicHeroBackground on WindowCard — extend soft Rest wash behind bedtime on quick. **SHIP**
31. Hairline under status → bed → captions (consistent 12dp spacedBy). **SHIP**
32. Large bedtime: NoopType.number(48–56) Bold + heading semantics. **SHIP**
33. Overline “RECOMMENDED BEDTIME” / “WAKE UP” for the bottom editor. **SHIP**
34. May-drift Effort blue only on honesty lines, never on bedtime digits. **SHIP**
35. Light theme: Rest bright on raised for bed clock contrast. **IN-TREE** (`alarmBedChromeColor`)
36. No nested NoopCard inside WhoopLikeQuickAlarmCard. **SHIP**
37. No glow / bloom under Arm or bed number. **SHIP**
38. Soft style: surfaceInset 0.55 wash + Rest hairline, not purple gradient. **SHIP**
39. Segment Sleep|Alarm already polished 8.6.110 — don’t re-add underline glow.
40. Fold: bottom wake row single line (label + chip + settings) without wrap clip. **SHIP**

## Honesty preserved, chrome simplified (41–46)

41. Keep Guaranteed by / May drift / Exact tap — shorter, under Arm. **SHIP**
42. Notifications-off warning stays when Armed (don’t style away). **SHIP**
43. Strap offline / Test buzz ACK — caption under secondary row, not hero. **SHIP**
44. Boot re-arm + muted channel — one combined tertiary line when Armed. **SHIP**
45. Custom + soonest caption only if custom enabled (avoid empty noise). **SHIP**
46. Never claim stages/SpO2 from soft window — copy already honest; keep.

## Motion & a11y (47–50)

47. Arm settle retained on status pill. **SHIP**
48. Bedtime TalkBack heading includes aim + wake. **SHIP**
49. Wake TimeChip 48dp hit via padding; contentDescription “Wake up time”. **SHIP**
50. ≥20 AppChangelog items covering Quick redesign + Wake style. **SHIP**

---

## Why Alarm felt styleless (diagnosis)

| Cause | Effect |
|-------|--------|
| Deadline-as-hero + chip dumps | Looks like a picker warehouse, not a bedtime ritual |
| Equal tertiary buttons | No primary composition |
| Wake settings = stacked identical cards | Functional but lacquer-less |
| Style budget spent on IA honesty (8.6.108–110) | Craft next: hierarchy + Rest domain |

Honesty stays; chrome gets quiet and bedtime-first.
