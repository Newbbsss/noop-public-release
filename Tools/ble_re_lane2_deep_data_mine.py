#!/usr/bin/env python3
"""Lane 2: mine captures for deep-data path evidence (types 51-56, R22 FF ACKs, arming cmds).

Safe offline analysis only — no GATT writes, no DEBUG=MAIN, no Gradle.
"""
from __future__ import annotations

import json
import re
import zlib
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "pairing-logs" / "ble-re-lane2-20260717"

HEX_RE = re.compile(r'(?:hex=|raw=|"hex"\s*:\s*")([0-9a-fA-F]{24,})', re.I)
RAW_GATT_RE = re.compile(r"RAW_GATT.*?hex=([0-9a-fA-F]+)", re.I)

TYPE_NAMES = {
    51: "REALTIME_IMU_DATA_STREAM",
    52: "HISTORICAL_IMU_DATA_STREAM",
    53: "RELATIVE_PUFFIN_EVENTS",
    54: "PUFFIN_EVENTS_FROM_STRAP",
    55: "RELATIVE_BATTERY_PACK_CONSOLE_LOGS",
    56: "PUFFIN_METADATA",
}

CMD_NAMES = {
    3: "TOGGLE_REALTIME_HR",
    105: "TOGGLE_IMU_MODE_HISTORICAL",
    106: "TOGGLE_IMU_MODE",
    107: "ENABLE_OPTICAL_DATA",
    108: "TOGGLE_OPTICAL_MODE",
    115: "START_DEVICE_CONFIG_KEY_EXCHANGE",
    116: "SEND_NEXT_DEVICE_CONFIG",
    117: "START_FF_KEY_EXCHANGE",
    118: "SEND_NEXT_FF",
    119: "SET_DEVICE_CONFIG_VALUE",
    120: "SET_FF_VALUE",
    121: "GET_DEVICE_CONFIG_VALUE",
    128: "GET_FF_VALUE",
    131: "SET_RESEARCH_PACKET",
    132: "GET_RESEARCH_PACKET",
    153: "TOGGLE_PERSISTENT_R20",
    154: "TOGGLE_PERSISTENT_R21",
}

R22_FLAGS = [
    "enable_r22_packets",
    "enable_r22_v2_packets",
    "enable_r22_v3_packets",
    "enable_r22_v4_packets",
    "enable_r22_v5_packets",
    "enable_r22_v6_packets",
    "enable_r22_v8_packets",
    "make_hrfm_visible",
    "disable_pip_r26_packets",
    "wear_detect_bias",
    "hr_ch_switching",
    "ir_hw_switching",
    "enable_passive_strap_fit_gen5",
    "enable_sig11_during_sleep",
    "dorset_inhibit_wpt",
]


def parse_hex(s: str) -> bytes | None:
    try:
        return bytes.fromhex(s.strip())
    except ValueError:
        return None


def crc16_modbus(data: bytes) -> int:
    crc = 0xFFFF
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if (crc & 1) else (crc >> 1)
    return crc & 0xFFFF


def valid_puffin(frame: bytes) -> bool:
    if len(frame) < 12 or frame[:2] != b"\xaa\x01":
        return False
    declared = int.from_bytes(frame[2:4], "little")
    if declared + 8 != len(frame):
        return False
    if crc16_modbus(frame[:6]) != int.from_bytes(frame[6:8], "little"):
        return False
    return (zlib.crc32(frame[8:-4]) & 0xFFFFFFFF) == int.from_bytes(frame[-4:], "little")


def frame_type(frame: bytes) -> int | None:
    if len(frame) >= 9 and frame[:2] == b"\xaa\x01":
        return frame[8]
    return None


def iter_frames(text: str):
    for m in RAW_GATT_RE.finditer(text):
        raw = parse_hex(m.group(1))
        if raw:
            yield raw
    for m in HEX_RE.finditer(text):
        raw = parse_hex(m.group(1))
        if raw and len(raw) >= 12:
            yield raw
    for line in text.splitlines():
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            obj = json.loads(line)
        except json.JSONDecodeError:
            continue
        if not isinstance(obj, dict):
            continue
        for key in ("hex", "raw", "frame"):
            if key in obj:
                raw = parse_hex(str(obj[key]))
                if raw:
                    yield raw


def extract_ff_name(body: bytes) -> str | None:
    needles = (
        b"enable_",
        b"make_",
        b"wear_",
        b"hr_ch",
        b"ir_hw",
        b"dorset",
        b"disable_",
        b"whoop_",
    )
    for i in range(len(body)):
        for n in needles:
            if body[i : i + len(n)] == n:
                end = body.find(b"\x00", i)
                if end < 0:
                    end = min(i + 40, len(body))
                name = body[i:end].decode("ascii", "ignore").strip("\x00")
                if name:
                    return name
    return None


def mine_path(path: Path) -> dict:
    text = path.read_text(encoding="utf-8", errors="replace")
    types = Counter()
    deep = Counter()
    deep_lens: dict[int, Counter] = {t: Counter() for t in range(51, 57)}
    cmd_acks = Counter()
    ff_names = Counter()
    interest_acks = Counter()
    rare_heads = []
    set_ff_samples = []
    total = 0
    valid = 0

    for frame in iter_frames(text):
        total += 1
        t = frame_type(frame)
        if t is None:
            continue
        types[t] += 1
        ok = valid_puffin(frame)
        if ok:
            valid += 1
        if t in range(51, 57):
            deep[t] += 1
            deep_lens[t][len(frame)] += 1
            if t in (51, 52, 53, 55) and len(rare_heads) < 30:
                rare_heads.append(
                    {
                        "type": t,
                        "name": TYPE_NAMES[t],
                        "len": len(frame),
                        "crc_ok": ok,
                        "head": frame[: min(48, len(frame))].hex(),
                    }
                )
        if t in (36, 38) and len(frame) > 10:
            cmd = frame[10]
            cmd_acks[cmd] += 1
            if cmd in CMD_NAMES:
                interest_acks[cmd] += 1
            if cmd == 120 and len(frame) > 20:
                body = frame[11:-4] if len(frame) > 15 else frame[11:]
                name = extract_ff_name(body)
                if name:
                    ff_names[name] += 1
                    if len(set_ff_samples) < 40:
                        # result byte often at body[0]
                        set_ff_samples.append(
                            {
                                "result": body[0] if body else None,
                                "name": name,
                                "len": len(frame),
                                "head": frame[: min(64, len(frame))].hex(),
                            }
                        )

    return {
        "path": str(path),
        "bytes": path.stat().st_size,
        "frames_seen": total,
        "puffin_crc_ok": valid,
        "types": {str(k): v for k, v in sorted(types.items())},
        "deep_51_56": {str(k): deep[k] for k in range(51, 57)},
        "deep_lens": {
            str(t): dict(deep_lens[t].most_common(8)) for t in range(51, 57) if deep_lens[t]
        },
        "interest_cmd_acks": {
            str(c): {"name": CMD_NAMES[c], "count": interest_acks[c]}
            for c in sorted(interest_acks)
        },
        "set_ff_echo_names": dict(ff_names.most_common(40)),
        "set_ff_samples": set_ff_samples,
        "rare_deep_heads": rare_heads,
        "r22_flags_seen_in_acks": {
            name: ff_names.get(name, 0) for name in R22_FLAGS
        },
    }


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    paths = [
        ROOT / "pairing-logs/signal-hunt-20260717/debug-whoop5-backfill-capture.jsonl",
        ROOT / "pairing-logs/signal-hunt-20260717/noop-raw-capture-260716-1949.jsonl",
        ROOT / "pairing-logs/signal-hunt-20260717/noop-raw-capture-260716-1952.jsonl",
        ROOT / "pairing-logs/signal-hunt-20260717/debug-whoop5-events.jsonl",
        Path(r"C:\Users\Gilbert\Documents\Ai app store\pairing-logs\noop-pairing-log.txt"),
    ]
    files = []
    union_deep = Counter()
    union_interest = Counter()
    union_ff = Counter()
    for p in paths:
        if not p.exists():
            files.append({"path": str(p), "missing": True})
            continue
        print(f"mining {p.name} …", flush=True)
        m = mine_path(p)
        files.append(m)
        for k, v in m["deep_51_56"].items():
            union_deep[int(k)] += v
        for k, meta in m["interest_cmd_acks"].items():
            union_interest[int(k)] += meta["count"]
        for name, c in m["set_ff_echo_names"].items():
            union_ff[name] += c

    summary = {
        "generated_utc": datetime.now(timezone.utc).isoformat(),
        "lane": 2,
        "agent": "cursor-grok-4.5-high",
        "files": files,
        "union_deep_51_56": {
            str(t): {"name": TYPE_NAMES[t], "count": union_deep[t]} for t in range(51, 57)
        },
        "union_interest_cmd_acks": {
            str(c): {"name": CMD_NAMES[c], "count": union_interest[c]}
            for c in sorted(union_interest)
        },
        "union_set_ff_echo_names": dict(union_ff.most_common(50)),
        "r22_flags_ack_coverage": {
            name: union_ff.get(name, 0) for name in R22_FLAGS
        },
        "verdict": {
            "live_51_53_55_seen": any(union_deep[t] > 0 for t in (51, 52, 53, 55)),
            "type_54_seen": union_deep[54] > 0,
            "type_56_seen": union_deep[56] > 0,
            "set_ff_120_acks": union_interest.get(120, 0),
            "ff_key_exchange_117_118": {
                "117": union_interest.get(117, 0),
                "118": union_interest.get(118, 0),
            },
            "r20_r21_toggle_acks": {
                "153": union_interest.get(153, 0),
                "154": union_interest.get(154, 0),
            },
            "optical_imu_toggle_acks": {
                "105": union_interest.get(105, 0),
                "106": union_interest.get(106, 0),
                "107": union_interest.get(107, 0),
                "108": union_interest.get(108, 0),
            },
        },
    }
    out_json = OUT / "deep-data-mine.json"
    out_json.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary["union_deep_51_56"], indent=2))
    print(json.dumps(summary["union_interest_cmd_acks"], indent=2))
    print(json.dumps(summary["verdict"], indent=2))
    print(f"wrote {out_json}")


if __name__ == "__main__":
    main()
