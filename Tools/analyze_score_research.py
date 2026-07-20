#!/usr/bin/env python3
"""Analyze NOOP Room DB for score anomalies (Effort / Charge / Rest / HRV). Honesty only — no invented SpO2."""
from __future__ import annotations

import json
import sqlite3
import sys
from datetime import datetime, timezone
from pathlib import Path


def main() -> int:
    if len(sys.argv) < 4:
        print("usage: analyze_score_research.py <db> <report.md> <summary.json>", file=sys.stderr)
        return 2
    db = Path(sys.argv[1])
    report = Path(sys.argv[2])
    summary_path = Path(sys.argv[3])
    lines: list[str] = []
    anomalies: list[str] = []

    def add(s: str = "") -> None:
        lines.append(s)

    add("# Score research report")
    add(f"Generated: {datetime.now(timezone.utc).isoformat()}")
    add(f"DB: `{db.name}` ({db.stat().st_size if db.exists() else 0} bytes)")
    add("")

    if not db.exists() or db.stat().st_size < 1024:
        add("**No DB** - DEBUG pull failed (MAIN release cannot run-as).")
        report.write_text("\n".join(lines), encoding="utf-8")
        summary_path.write_text(json.dumps({"anomaly_count": -1, "reason": "no_db"}), encoding="utf-8")
        return 0

    c = sqlite3.connect(str(db))
    cur = c.cursor()

    add("## dailyMetric (last 14 days)")
    add("| day | deviceId | strain | recovery | steps | rhr | hrv |")
    add("|---|---|---:|---:|---:|---:|---:|")
    rows = list(
        cur.execute(
            "SELECT day, deviceId, strain, recovery, steps, restingHr, avgHrv "
            "FROM dailyMetric WHERE day >= date('now','-14 days') ORDER BY day DESC, deviceId"
        )
    )
    for r in rows:
        add(
            "| {0} | `{1}` | {2} | {3} | {4} | {5} | {6} |".format(
                *[( "" if x is None else x) for x in r]
            )
        )

    add("")
    add("## HR sample density (last 7 local days)")
    add("| day | n | avg bpm | max bpm |")
    add("|---|---:|---:|---:|")
    hr = list(
        cur.execute(
            "SELECT date(ts,'unixepoch','localtime') AS d, COUNT(*), ROUND(AVG(bpm),1), MAX(bpm) "
            "FROM hrSample WHERE ts > strftime('%s','now','-7 days') GROUP BY d ORDER BY d"
        )
    )
    for r in hr:
        add("| {0} | {1} | {2} | {3} |".format(*r))

    by_day: dict[str, list[dict]] = {}
    for day, device_id, strain, recovery, steps, rhr, hrv in rows:
        by_day.setdefault(day, []).append(
            {
                "deviceId": device_id,
                "strain": strain,
                "recovery": recovery,
                "steps": steps,
                "rhr": rhr,
                "hrv": hrv,
            }
        )
    hr_by = {d: (n, avg, mx) for d, n, avg, mx in hr}

    add("")
    add("## Anomalies")
    for day, entries in sorted(by_day.items(), reverse=True):
        strains = [e["strain"] for e in entries if e["strain"] is not None]
        steps_vals = [e["steps"] for e in entries if e["steps"] is not None]
        max_steps = max(steps_vals) if steps_vals else 0
        max_strain = max(strains) if strains else None
        hr_n = hr_by.get(day, (0, None, None))[0]

        if any(e["strain"] == 0.0 for e in entries):
            msg = (
                f"**{day}**: banked strain=0.0 (lock risk) · steps max={max_steps} · hr_n={hr_n}"
            )
            anomalies.append(msg)
            add(f"- {msg}")
        if max_steps >= 5000 and max_strain is None and hr_n >= 600:
            msg = (
                f"**{day}**: steps>=5k + hr>=600 but Effort null across devices "
                "— movement floor / merge miss?"
            )
            anomalies.append(msg)
            add(f"- {msg}")
        if any(e["hrv"] is not None and e["recovery"] is None for e in entries):
            msg = f"**{day}**: HRV banked with recovery=null (overnight vitals carry expected)"
            add(f"- _{msg}_")
        pos = [e for e in entries if e["strain"] is not None and e["strain"] > 0]
        if len({round(e["strain"], 1) for e in pos}) > 1:
            msg = f"**{day}**: conflicting Effort across deviceIds: " + ", ".join(
                f"{e['deviceId']}={e['strain']}" for e in pos
            )
            anomalies.append(msg)
            add(f"- {msg}")

    if not anomalies:
        add("- (none hard) — check table for null Effort on active days")

    add("")
    add("## Checks performed")
    add("1. Banked Effort (strain) vs steps / HR density")
    add("2. strain=0.0 lock artifact")
    add("3. Multi-deviceId Effort drift")
    add("4. Charge/recovery null with HRV present (note)")
    add("")
    add("Honesty: no invented SpO2 %; MAIN release DB may be absent (use DEBUG / Export backup).")

    report.write_text("\n".join(lines), encoding="utf-8")
    summary_path.write_text(
        json.dumps({"anomaly_count": len(anomalies), "anomalies": anomalies[:20]}, indent=2),
        encoding="utf-8",
    )
    print(f"anomalies={len(anomalies)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
