# Today — 50 concrete style improvements

Surface: Home / **Today** (`TodayScreen.kt`). Baseline when written: **8.6.110 / 380**.  
Legend: **SHIP** = closed in **8.6.111** (this batch). Soft lacquer + Rest/Charge/Effort tokens; no glow, no nested cards, no invented vitals.

---

## Quick alarm composition (1–12)

1. Quick alarm is a preset-chip dump — no bedtime story. **SHIP** → bedtime hero + wake edit.
2. Hero clock missing — presets steal the first glance. **SHIP**
3. Recommended bedtime not computed on Today (only on Sleep Alarm). **SHIP**
4. “Quick alarm” label is bland subhead — needs Rest overline + status pill. **SHIP**
5. Off/Armed pill competes with four equal chips. **SHIP**
6. Wake settings link is tiny trailing text — bury secondary, elevate Arm. **SHIP**
7. No “aim for Xh” line tied to personal sleep need. **SHIP**
8. Window Xm only when armed — Off should still show soft plan. **SHIP**
9. Flat `surfaceRaised 0.40` wash reads unfinished vs vessel hero. **SHIP** (hairline Rest card)
10. No bedtime icon / Rest domain cue on the maker card. **SHIP**
11. Today next-alarm footnote duplicates Quick without bedtime language. **SHIP**
12. Preset selection when Off looks like Armed — remove presets from Today Quick. **SHIP**

## Hero / vessels / compare (13–24)

13. Score vessels sit strong; below-fold blocks feel styleless vs liquid hero. **IN-TREE**
14. As-of footnote is pure tertiary — add a quiet Rest hairline above alarm block. **SHIP**
15. Compare card dual-scale copy is correct but visually flat under vessels. **IN-TREE**
16. WHOOP-app column blue vs NOOP gold needs clearer column separation (hairline). **IN-TREE**
17. Empty compare “—” cells lack the same inset depth as scored cells. **IN-TREE**
18. Charge overnight delta footnote can sit between alarm blocks — reorder for rhythm. **SHIP**
19. Live session entry teal is fine; needs matching overline tracking as alarm Rest. **IN-TREE**
20. Workout-in-progress rose card is styled; idle Today below it drops craft suddenly. **IN-TREE**
21. Carried-sleep note + calibrating note stack without section breathing room. **SHIPPED** (8.6.116)
22. Health strip tiles use mixed caption weights — unify to NoopType.caption / captionNumber. **IN-TREE**
23. Sparkline cards lack domain overlines (Charge / Effort / Rest) before charts. **IN-TREE** (Charge label + HR Effort overline)
24. Stress tip under hero uses body weight where overline would quiet the stack. **IN-TREE**

## Typography & numbers (25–34)

25. Bedtime / wake clocks must use `NoopType.number` + tnum (match Alarm). **SHIP**
26. Display tracking on bedtime ≥40sp on Today Quick. **SHIP**
27. “As of” vs alarm footnote share size — differentiate caption vs footnote roles. **SHIP**
28. Cycle calendar TextButton looks like a settings dump — Rest icon + quieter label. **SHIP**
29. Section titles below hero lack consistent title2 / overline pairing. **IN-TREE**
30. German/Spanish long labels on compare need maxLines + ellipsis already; verify Quick bed string. **IN-TREE**
31. Tabular figures missing on some delta footnotes (Charge ±N). **IN-TREE**
32. Overline letter-spacing on Rest blocks should match DESIGN (1.4sp). **SHIP**
33. Wake edit row: “Wake up” caption tertiary + TimeChip accent — not equal weight. **SHIP**
34. Status pill type should be overline/caption semibold, not body. **SHIP**

## Color & surfaces (35–42)

35. Quick card: Rest hairline border + surfaceInset wash (not purple, not glow). **SHIP**
36. Armed status uses `Palette.restColor`; may-drift stays Effort (honesty). **SHIP** (Today Off/Armed only)
37. Avoid gold wash on bedtime hero — Rest bright for bed, gold only for TimeChip/actions. **SHIP**
38. Hairline dividers between as-of → alarm → delta (0.5–1dp, hairline alpha). **SHIP**
39. Light scheme: verify Rest on raised still ≥3:1 for large bedtime. **IN-TREE** (`alarmBedChromeColor`)
40. Classic chart packs: Quick alarm must not pick Classic purple REM for chrome. **IN-TREE** (`alarmBedChromeColor`)
41. Sky / liquid packs: inactive secondary text, never ash tertiary for Arm CTA. **IN-TREE**
42. No radial glow under bedtime number (anti-pattern). **SHIP**

## Motion & a11y (43–50)

43. Arm settle on Today status pill (mirror Sleep; Reduce Motion = instant). **SHIP**
44. TimeChip open should haptic (already on Alarm; add on Today wake pick). **SHIP**
45. TalkBack: bedtime heading + “recommended bedtime HH:MM · aim … · wake …”. **SHIP**
46. Merge Quick card semantics so chips/links aren’t five stops. **SHIP**
47. 48dp min on Wake settings + Arm targets. **SHIP**
48. Prefers-reduced-motion: no infinite breath on Today Quick chrome. **IN-TREE**
49. Fold cover: Quick card padding 14→16 horizontal so bedtime doesn’t clip. **SHIP**
50. Document shipped subset in AppChangelog (≥20 items this MAIN). **SHIP**

---

## Why Today felt styleless (diagnosis)

| Cause | Effect |
|-------|--------|
| Quick = four preset chips | No composition; looks like a settings dump |
| Bedtime only on Sleep Alarm | Today never teaches “go to bed by …” |
| Flat 40% raised wash | Competes poorly with liquid vessel hero |
| Footnotes + Quick + Cycle equal weight | No Rest domain rhythm under as-of |
| Style energy stops at hero vessels | Below-fold reads unfinished |

No scoring / staging math changes — presentation only.
