#!/usr/bin/env python3
"""Decode My Calendar .pc like PcCalendarImport.kt + PeriodCalendar-style forecast."""
from __future__ import annotations
import math
import struct
import zlib
from collections import Counter
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

PC_PATH = Path(r"C:\Users\Gilbert\Downloads\My Calendar2026-07-10_iphone (2).pc")
TODAY = date(2026, 7, 12)
MIN_HITS = 3
PK = b"PK\x03\x04"


def u16(b: bytes, off: int) -> int:
    return b[off] | (b[off + 1] << 8)


def u32(b: bytes, off: int) -> int:
    return b[off] | (b[off + 1] << 8) | (b[off + 2] << 16) | (b[off + 3] << 24)


def index_of(hay: bytes, needle: bytes, start: int = 0) -> int:
    return hay.find(needle, start)


def inflate_raw(data: bytes, dstart: int, length: int) -> bytes | None:
    try:
        chunk = data[dstart : dstart + length]
        # nowrap raw deflate (Inflater(true) in Java)
        out = zlib.decompress(chunk, -zlib.MAX_WBITS)
        return out if out else None
    except Exception:
        return None


def inflate_scan(data: bytes, dstart: int) -> bytes | None:
    best: bytes | None = None
    max_len = min(len(data) - dstart, 8000)
    length = 32
    while length <= max_len:
        out = inflate_raw(data, dstart, length)
        if out is not None and (best is None or len(out) > len(best)):
            best = out
        length += 8 if length < 512 else 32
    return best


def extract_zip_member(data: bytes, name: str) -> bytes | None:
    pk = index_of(data, PK)
    if pk < 0:
        return None
    # Manual local-header walk (matches Kotlin fallback; ZipInputStream may fail on truncated EOCD)
    pos = pk
    while pos >= 0 and pos + 30 < len(data):
        if data[pos : pos + 4] != PK:
            break
        nlen = u16(data, pos + 26)
        elen = u16(data, pos + 28)
        method = u16(data, pos + 8)
        if pos + 30 + nlen > len(data):
            break
        entry_name = data[pos + 30 : pos + 30 + nlen].decode("utf-8", errors="replace")
        dstart = pos + 30 + nlen + elen
        if name.lower() in entry_name.lower():
            if method == 0:
                csize = u32(data, pos + 18)
                if csize < 0:
                    csize = 0
                end = min(dstart + csize, len(data))
                if dstart < end:
                    return data[dstart:end]
            if method == 8:
                inflated = inflate_scan(data, dstart)
                if inflated is not None:
                    return inflated
        nxt = index_of(data, PK, dstart + 1)
        if nxt < 0:
            break
        pos = nxt
    return None


def mine_period_starts(blob: bytes, today: date = TODAY, min_hits: int = MIN_HITS) -> list[date]:
    min_d = date(2018, 1, 1)
    max_d = today + timedelta(days=45)
    counts: Counter[str] = Counter()

    def add(day: date) -> None:
        if day < min_d or day > max_d:
            return
        counts[day.isoformat()] += 1

    # LE int32 scan
    for i in range(0, len(blob) - 3):
        (v,) = struct.unpack_from("<i", blob, i)
        if 20180101 <= v <= 20351231:
            s = f"{v:08d}"
            try:
                add(date(int(s[0:4]), int(s[4:6]), int(s[6:8])))
            except ValueError:
                pass
        if 1_500_000_000 <= v <= 1_900_000_000:
            try:
                day = datetime.fromtimestamp(v, tz=timezone.utc).date()
                add(day)
            except (OverflowError, OSError, ValueError):
                pass

    # LE millis u64
    for i in range(0, len(blob) - 7):
        (millis,) = struct.unpack_from("<Q", blob, i)
        if 1_500_000_000_000 <= millis <= 1_900_000_000_000:
            try:
                day = datetime.fromtimestamp(millis / 1000.0, tz=timezone.utc).date()
                add(day)
            except (OverflowError, OSError, ValueError):
                pass

    raw_days = sorted(
        date.fromisoformat(k) for k, c in counts.items() if c >= min_hits
    )
    # Collapse consecutive bleed days -> run starts only
    starts: list[date] = []
    prev = None
    for d in raw_days:
        if prev is None or (d - prev).days > 1:
            starts.append(d)
        prev = d

    if len(starts) <= 24:
        return starts

    filtered = []
    for day in starts:
        has_cycle_gap = any(
            other != day and 18 <= abs((day - other).days) <= 45 for other in starts
        )
        if has_cycle_gap or counts[day.isoformat()] >= min_hits + 6:
            filtered.append(day)
    return filtered if len(filtered) >= 3 else starts


def median_int(vals: list[int]) -> int:
    if not vals:
        return 28
    s = sorted(vals)
    n = len(s)
    if n % 2 == 1:
        return s[n // 2]
    # average of two middle, round half up toward nearest int
    a, b = s[n // 2 - 1], s[n // 2]
    return int(round((a + b) / 2.0))


def planning_starts(starts: list[date], min_gap: int = 14) -> list[date]:
    if len(starts) <= 1:
        return list(starts)
    out: list[date] = []
    prev: date | None = None
    for d in starts:
        if prev is None or (d - prev).days >= min_gap:
            out.append(d)
            prev = d
    return out


def cycle_length_model(starts: list[date], fallback: int = 28) -> tuple[int, float, int]:
    """Match PeriodCalendar.cycleLengthModel: phys 21–40, skip-repair 42–80, recency weights."""
    if len(starts) < 2:
        return max(21, min(40, fallback)), 4.0, 0
    gaps = [(starts[i] - starts[i - 1]).days for i in range(1, len(starts))]
    phys: list[int] = []
    running = float(fallback)
    for g in gaps:
        if 21 <= g <= 40:
            phys.append(g)
            running = g if len(phys) == 1 else running * 0.7 + g * 0.3
        elif 42 <= g <= 80:
            half = g / 2.0
            if abs(half - running) <= 8.0 or 21.0 <= half <= 40.0:
                repaired = max(21, min(40, int(round(half))))
                phys.append(repaired)
                running = running * 0.7 + repaired * 0.3
    if not phys:
        return max(21, min(40, fallback)), 5.0, 0
    n = len(phys)
    w_sum = 0.0
    x_sum = 0.0
    for i, x in enumerate(phys):
        age = (n - 1 - i)
        w = math.exp(-age / 3.0)
        w_sum += w
        x_sum += w * x
    mean = max(21, min(40, int(round(x_sum / w_sum))))
    recent = phys[-min(6, len(phys)) :]
    if len(recent) >= 2:
        m = sum(recent) / len(recent)
        sd = math.sqrt(sum((v - m) ** 2 for v in recent) / (len(recent) - 1))
    else:
        sd = 4.0
    return mean, sd, len(phys)


def forecast(starts: list[date], today: date = TODAY) -> None:
    past = [d for d in starts if d <= today]
    planned = planning_starts(past)
    mean, sd, n_phys = cycle_length_model(planned, 28)
    avg_period = 5
    last = planned[-1] if planned else None
    print("\n=== PeriodCalendar-style predictions (Kotlin-aligned) ===")
    print(f"today={today.isoformat()}")
    print(f"planningStarts={len(planned)} (from {len(past)} historical; minGap=14)")
    print(f"avgCycle={mean} (cycleLengthModel; sd~{sd:.1f}; validGaps={n_phys})")
    print(f"avgPeriodLength={avg_period} (default)")
    print(f"lastStart={last.isoformat() if last else None}")

    if last is None:
        print("No last start <= today; cannot forecast.")
        return

    nxt = last + timedelta(days=mean)
    while nxt < today:
        nxt = nxt + timedelta(days=mean)
    earliest = max(nxt - timedelta(days=2), today)
    latest = nxt + timedelta(days=2)
    print(f"nextLikely={nxt.isoformat()}  (>= today invariant)")
    print(f"earliest={earliest.isoformat()} latest={latest.isoformat()}")

    print("\nNext 3 forecast windows:")
    for i in range(3):
        likely = nxt + timedelta(days=mean * i)
        if likely < today:
            continue
        half = min(4, max(2 if n_phys >= 3 else 3, int(math.ceil(1.96 * sd * (i + 1) ** 0.5))))
        e = max(likely - timedelta(days=half), today)
        l = likely + timedelta(days=half)
        print(f"  window {i+1}: earliest={e.isoformat()} likely={likely.isoformat()} latest={l.isoformat()}")

    print("\nPredicted period days (next window, avgPeriodLength days from likely):")
    for i in range(avg_period):
        d = nxt + timedelta(days=i)
        print(f"  {d.isoformat()}")


def main() -> None:
    print(f"Reading: {PC_PATH}")
    print(f"exists={PC_PATH.exists()} size={PC_PATH.stat().st_size if PC_PATH.exists() else 'N/A'}")
    data = PC_PATH.read_bytes()
    print(f"file bytes={len(data)} magic={data[:4].hex()}")

    blob = extract_zip_member(data, "1.period")
    if blob is None:
        blob = extract_zip_member(data, "period")
    if blob is None:
        print("ERROR: could not extract 1.period; mining whole file")
        blob = data
    else:
        print(f"extracted 1.period size={len(blob)}")

    starts = mine_period_starts(blob)
    print(f"\n=== ALL period starts ({len(starts)}) ===")
    for d in starts:
        print(d.isoformat())

    forecast(starts, TODAY)


if __name__ == "__main__":
    main()
