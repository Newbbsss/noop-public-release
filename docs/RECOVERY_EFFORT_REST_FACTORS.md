# Recovery / Charge / Effort / Rest / Workout factors and literature

**Updated 2026-07-20 (8.6.233 Gilbert trust).** Charge/Rest knobs below match current Kotlin.
Canonical product-owner map: [`docs/agent/research/SCORE_ALGORITHM_REPORT.md`](agent/research/SCORE_ALGORITHM_REPORT.md).

---

## How each number is actually calculated (user-facing explainer, verified against code)

### Charge (Gilbert recovery score), `RecoveryScorer.kt`

A logistic squash of a weighted z-score composite, NOT a lookup table:

```
z = Σ(termZ × weight) / Σ(weight)      — missing terms drop out and renormalize
Charge = 100 / (1 + e^(−1.6 × (z − (−0.05))))     — anchored so z=0 → ~52% (Gilbert honesty floor)
```

Terms and weights: HRV vs personal baseline **0.55** (dominant), resting HR vs baseline **0.20**
(lower is better), sleep performance/Rest **0.15**, respiration vs baseline **0.05**, skin-temp deviation
**0.05** (symmetric). Requires an HRV baseline or returns **null**.

### Effort (cardiovascular load)

`StrainScorer` Edwards/%HRR TRIMP on 0–100 (dual 0–21 display is UI rescale only).

### Rest (sleep performance), `RestScorer`

```
Rest_raw = 0.45·duration + 0.20·efficiency + 0.25·restorative + 0.10·consistency
Rest     = Rest_raw × 0.84     // Gilbert honesty scale (restWhoopAlignScale)
```

- **duration** — asleep vs personal need (default 8h, floor 7.5h); thin restorative (&lt;16%) discounts duration.
- **efficiency** — asleep / in-bed.
- **restorative** — (deep+REM)/asleep vs 50% target × deepFactor (deep share target 13%, floor **0.14**).
- **consistency** — regularity, or neutral 50.

Perfect night → **~84** Rest. Rest is sleep-tracking-driven: inflated Deep/REM lifts Rest → Charge.

---

## Shipped / superseded by 8.6.233

- Rest weights **0.45 / 0.20 / 0.25 / 0.10** (docs that still say 0.50/0.20/0.20 are stale).
- `deepFloorFactor = 0.14`; `restWhoopAlignScale = 0.84`; `logisticZ0 = -0.05`; `populationMean = 52`.
- Sleep V2 defaults **off** for all families; WHOOP 4 hard-gated.

## Still open

1. Surface full Rest term breakdown more prominently in UI.
2. Per-night Rest confidence when staging is degenerate.
3. Nightly HRV plausibility guards (mirror resting-HR bin guards).
4. Align Stress skin-temp handling with Charge’s symmetric penalty where it matters.

## Related paths

- `RecoveryScorer.kt`, `AnalyticsEngine.kt` (`RestScorer`), `RestDrivers.kt`
- `docs/agent/research/SCORE_ALGORITHM_REPORT.md`
