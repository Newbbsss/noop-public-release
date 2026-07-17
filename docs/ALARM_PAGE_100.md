# Alarm page — 100 concrete improvements

Surface: Sleep → **Alarm** tab (`WhoopLikeQuickAlarmCard` in `SleepScreen.kt`) plus full editor (`SmartAlarmScreen.kt`).
Baseline when written: **8.6.107 / 377**. First ship batch: **8.6.108 / 378**.

Legend: **SHIPPED** = closed in 8.6.108 unless noted.

---

## Glance / hierarchy (1–15)

1. Status is plain overline text — not a glanceable Armed/Off pill. **SHIPPED**
2. Hero clock shows deadline only — earliest→deadline pairing missing. **SHIPPED**
3. No countdown (“in 6h 12m”) under the hero when armed. **SHIPPED**
4. Off state still shows a big clock that looks armed. **SHIPPED** (Off pill + calm empty)
5. Aim / bed / soft / custom / extras crammed into one footnote. **SHIPPED**
6. Soft window minutes not shown between ±15m controls. **SHIPPED** (Window Xm + presets)
7. ±15m UI allowed up to 90 while store was 60 — silent lie. **SHIPPED** (store max 90; toast at bounds)
8. Preset wake chips (6:30 / 7 / 7:30 / 8) missing on Alarm summary. **SHIPPED**
9. Preset selection invisible when wake is Off. **SHIPPED** (border marks target)
10. No earliest wake edit on the summary. **SHIPPED** (TimeChip)
11. Primary Arm competes with four tertiary buttons. **SHIPPED** (cleaner labels + kind)
12. Long honesty paragraph stacks strap + exact + MG. **SHIPPED**
13. Exact-permission failure is Toast-only. **SHIPPED**
14. “Armed · may drift” not tappable to Settings. **SHIPPED**
15. Scaffold subtitle ignores live armed state. **SHIPPED** (live Armed/Off/may-drift subtitle)

## Controls & copy (16–30)

16. “Strap buzz on/off” awkward labels. **SHIPPED**
17. “Turn-back on/off” / “Wake rested on/off” awkward. **SHIPPED**
18. No haptic on strap-buzz toggle. **SHIPPED**
19. Wind-down only in full editor. **SHIPPED** (summary toggle)
20. Test buzz Toast is engineering jargon. **SHIPPED**
21. Strap offline line buries Arm CTA when Off. **SHIPPED** (8.6.110 — strap honesty under Test buzz when Off)
22. No next custom time on summary. **SHIPPED**
23. Empty custom list lacks teaching empty state. **SHIPPED**
24. Full editor duplicates Wake switch beside wake time. **SHIPPED**
25. Full editor vs summary window ranges disagreed. **SHIPPED** (both 5–90 / summary quick 15–90)
26. “More detail · custom times” undersells extras. **SHIPPED** (8.6.110 — Wake settings CTA)
27. Soft window −15m not dimmed at floor. **SHIPPED**
28. Soft window +15m not dimmed at ceiling. **SHIPPED**
29. Arming with exact denied looks half-armed. **SHIPPED**
30. Help copy still says “WHOOP 3/4/MG” in places. **SHIPPED** (8.6.110)

## Sleep | Alarm chrome (31–40)

31. Selected tab text uses glow shadow. **SHIPPED**
32. Underline + wash + shadow is triple emphasis. **SHIPPED** (8.6.110 — wash only)
33. Inactive tab ash on some packs. **SHIPPED** (8.6.110)
34. Tab contentDescription “Sleep tab Alarm” noun order. **SHIPPED** (8.6.110)
35. No swipe between Sleep|Alarm (tap only). **SHIPPED** (8.6.114 — swipe on segment)
36. AnimatedVisibility exit can blank briefly. **SHIPPED** (8.6.110 — direction-aware)
37. Alarm early-return skips Sleep TZ travel note. **SHIPPED** (8.6.110)
38. Reduce Motion still leaves segment breath wash. **SHIPPED** (8.6.110)
39. Segment padding wastes Fold cover width. **SHIPPED** (8.6.110)
40. Page enter always `forward=true`. **SHIPPED** (8.6.110)

## Full editor / SmartAlarmScreen (41–55)

41. WindowCard and settings restate the same safety promise. **SHIPPED** (8.6.110)
42. Personal sleep plan can disagree with Alarm aim if samples differ. **IN-TREE**
43. Strap wake-alarm vs “Buzz connected strap” two models. **IN-TREE**
44. Turn-back steppers lack tonight preview. **SHIPPED** (8.6.110)
45. Wake-when-rested Charge threshold unexplained. **SHIPPED** (8.6.110)
46. Custom “Remove” easy to fat-finger — ≥48dp shipped; confirm still open. **SHIPPED** (8.6.110 — confirm)
47. Custom labels default “Alarm N” — no rename. **SHIPPED** (8.6.110)
48. Weekday picker lacks weekdays/weekends presets. **SHIPPED** (8.6.110)
49. Day-override picker invisible until smart alarm on. **SHIPPED** (8.6.110 — tip when off)
50. Wind-down card has enable only — no fire time. **SHIPPED** (8.6.110)
51. ExplanationCard buried below three cards. **SHIPPED** (8.6.110)
52. Exact-alarm warning late for first-time armers. **SHIPPED** (8.6.110)
53. “Why an alarm helps” essay too long. **SHIPPED** (summary Off one-liner)
54. Icon Alarm repeated on three cards. **SHIPPED** (8.6.110 — Schedule / Watch / Alarm)
55. Title “Alarm” collides with Sleep|Alarm tab when pushed from More. **SHIPPED** (8.6.110 — Wake settings)

## Honesty / BLE / OS (56–70)

56. MG wake experimental — short clause. **SHIPPED**
57. Soft window vs strap firmware desync when phone offline (#439). **IN-TREE**
58. Notifications permission denied → silent miss. **SHIPPED** (8.6.110)
59. OEM battery / exact quirks undocumented. **SHIPPED** (8.6.110 — docs/ALARM_OEM_QUIRKS.md)
60. TZ change honesty missing on Alarm page. **SHIPPED** (8.6.110)
61. Soonest countdown uses window-start candidate — verify vs deadline fire. **IN-TREE** (deadline named in soonestLabel)
62. Test buzz vs “Strap buzz armed” can briefly disagree. **IN-TREE**
63. Alongside / exclusive not next to Test buzz. **IN-TREE**
64. Command family “framing ?” — removed from summary. **SHIPPED**
65. No last buzz ACK on Alarm. **SHIPPED** (8.6.110)
66. Boot re-arm never surfaces. **SHIPPED** (8.6.109)
67. Custom + smart window — which rings first only on Today. **SHIPPED** (8.6.109)
68. Exact deep-link OEM fallback shared. **SHIPPED**
69. DND / alarm channel not mentioned. **SHIPPED** (8.6.109)
70. Fold cover density may clip tertiary row. **SHIPPED** (8.6.116 — narrow gap)

## Accessibility (71–80)

71. ±15m undershoot 48dp. **SHIPPED** (heightIn on presets / Remove)
72. Preset chips need “Wake preset …” description. **SHIPPED**
73. Status announces armed/off/may-drift. **SHIPPED**
74. Hero time not marked heading for TalkBack. **SHIPPED** (8.6.110)
75. Color-only armed state — pill text helps. **SHIPPED**
76. Large font aim/bed wraps — two lines. **SHIPPED**
77. Contrast: inactive presets on sky. **SHIPPED** (8.6.110)
78. Focus order lacks grouping headers. **SHIPPED** (8.6.110)
79. Remove lacks confirmation. **SHIPPED** (8.6.110)
80. Reduce Motion: segment breath not fully frozen. **SHIPPED** (8.6.110)

## Consistency / IA (81–90)

81. Today + Alarm share wake preset list. **SHIPPED**
82. Today “Full editor” vs Alarm “More detail” naming drift. **SHIPPED** (8.6.110 — Wake settings)
83. Settings still a third alarm door. **IN-TREE** (Automations → Wake settings door)
84. Automations leftover copy may point wrong. **SHIPPED** (8.6.110)
85. iOS SmartAlarmView parity gaps.
86. Changelog once claimed presets while summary lacked them. **SHIPPED**
87. Rest bridge vs Alarm aim can disagree by minutes. **SHIPPED** (8.6.110)
88. Soft vs Window vocabulary. **SHIPPED** (Window on summary)
89. Wake-rested should link to Charge vessel. **SHIPPED** (8.6.114)
90. Sleep tools don’t mention Alarm tab. **SHIPPED** (8.6.109)

## Delight / polish (91–100)

91. Arm success settle motion on pill. **SHIPPED** (8.6.109)
92. Preset chip haptic. **SHIPPED**
93. Window step no-op toast at bounds. **SHIPPED**
94. Off empty one calm sentence. **SHIPPED**
95. Optional Rest hairline under clock. **SHIPPED** (8.6.109)
96. Gold wake accent vs gold theme pack (#385). **IN-TREE** (Wake settings uses alarmWakeAccent)
97. Screenshot golden Frame (L37).
98. Widget next-alarm (L9). **IN-TREE**
99. Buzz-the-time from Alarm (#340+). **IN-TREE**
100. ≥20 changelog items per Alarm MAIN ship. **SHIPPED**

---

## Top shipped in 8.6.110

Wake settings naming; Sleep|Alarm chrome; Alarm honesty (notifs, TZ, buzz ACK); editor polish (exact early, rename/remove, weekday presets, wind-down fire time, OEM doc).

---

## Top 20 shipped in 8.6.108

1. Armed / Off / may-drift status pill
2. Earliest → deadline under hero
3. Countdown via NextAlarmDisplay
4. Guaranteed / may-drift caption + Settings deep link
5. Earliest TimeChip on summary
6. Wake presets 6:30 / 7 / 7:30 / 8
7. Window presets 15–90 + store max 90
8. ±15m dim + toast at bounds
9. Aim / bed two-line split
10. Clean Strap buzz / Turn-back / Wake rested / Wind-down labels
11. Wind-down toggle on summary
12. Softened Test buzz toast + MG experimental
13. Next custom on More detail CTA
14. Custom empty teaching + Add a fixed time
15. Full editor: no duplicate Wake switch
16. Sleep|Alarm: no glow text-shadow
17. Off empty calm sentence
18. Shared WAKE_PRESET_MINUTES helper
19. Exact-permission tappable footnote
20. Arm failure opens Settings
