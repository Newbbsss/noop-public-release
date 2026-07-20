#!/usr/bin/env python3
"""Lane 2: dry-run puffin frames for deep-data arming sequences (no GATT)."""
from __future__ import annotations

import json
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "pairing-logs" / "ble-re-lane2-20260717" / "proposed-sequences.json"


def crc16_modbus(data: bytes) -> int:
    crc = 0xFFFF
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if (crc & 1) else (crc >> 1)
    return crc & 0xFFFF


def puffin(cmd: int, seq: int, payload: bytes) -> bytes:
    inner = bytes([0x23, seq & 0xFF, cmd & 0xFF]) + payload
    body = inner + (zlib.crc32(inner) & 0xFFFFFFFF).to_bytes(4, "little")
    hdr = bytes([0xAA, 0x01]) + len(body).to_bytes(2, "little") + bytes([0x01, 0x00])
    return hdr + crc16_modbus(hdr).to_bytes(2, "little") + body


def ff_body(name: str, value: int) -> bytes:
    b = bytearray(40)
    raw = name.encode("ascii")[:32]
    b[: len(raw)] = raw
    b[32] = value & 0xFF
    return bytes(b)


R22 = [
    ("enable_r22_packets", 0x32),
    ("enable_r22_v2_packets", 0x32),
    ("enable_r22_v3_packets", 0x32),
    ("enable_r22_v4_packets", 0x31),
    ("enable_r22_v5_packets", 0x32),
    ("enable_r22_v6_packets", 0x32),
    ("enable_r22_v8_packets", 0x32),
    ("make_hrfm_visible", 0x32),
    ("disable_pip_r26_packets", 0x32),
    ("wear_detect_bias", 0x32),
    ("hr_ch_switching", 0x32),
    ("ir_hw_switching", 0x32),
    ("enable_passive_strap_fit_gen5", 0x31),
    ("enable_sig11_during_sleep", 0x32),
    ("dorset_inhibit_wpt", 0x32),
]

# Seen in SET_FF ACKs from captures but NOT in Whoop5Config.enableR22Sequence.
EXTRA_FROM_CAPTURES = [
    ("enable_sig12", 0x32),
    ("enable_maverick_model", 0x32),
]


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    seq = 1
    sequences: dict = {}

    a = []
    for name, val in R22:
        frame = puffin(0x78, seq, bytes([0x01]) + ff_body(name, val))
        a.append(
            {
                "seq": seq,
                "cmd": 120,
                "name": name,
                "value_ascii": chr(val),
                "hex": frame.hex(),
            }
        )
        seq += 1
    sequences["A_noop_r22_15"] = a

    b = []
    for name, val in R22 + EXTRA_FROM_CAPTURES:
        frame = puffin(0x78, seq, bytes([0x01]) + ff_body(name, val))
        b.append(
            {
                "seq": seq,
                "cmd": 120,
                "name": name,
                "value_ascii": chr(val),
                "hex": frame.hex(),
                "extra": name in {n for n, _ in EXTRA_FROM_CAPTURES},
            }
        )
        seq += 1
    sequences["B_r22_plus_capture_extra_ff"] = b

    c = []
    for name, _ in R22 + EXTRA_FROM_CAPTURES:
        frame = puffin(128, seq, bytes([0x01]) + ff_body(name, 0x00))
        c.append({"seq": seq, "cmd": 128, "name": name, "hex": frame.hex()})
        seq += 1
    sequences["C_get_ff_readback"] = c

    d = []
    for label, cmd, pay in [
        ("ENABLE_OPTICAL_DATA_ON", 107, bytes([0x01])),
        ("TOGGLE_OPTICAL_MODE_ON", 108, bytes([0x01])),
        ("LISTEN_8S", 0, b""),
        ("TOGGLE_OPTICAL_MODE_OFF", 108, bytes([0x00])),
        ("ENABLE_OPTICAL_DATA_OFF", 107, bytes([0x00])),
    ]:
        if cmd == 0:
            d.append({"name": label, "listen_s": 8})
            continue
        frame = puffin(cmd, seq, pay)
        d.append({"seq": seq, "cmd": cmd, "name": label, "hex": frame.hex()})
        seq += 1
    sequences["D_arm_optical_then_off"] = d

    e = []
    for label, cmd, pay in [
        ("TOGGLE_IMU_MODE_ON", 106, bytes([0x01])),
        ("LISTEN_8S", 0, b""),
        ("TOGGLE_IMU_MODE_OFF", 106, bytes([0x00])),
        ("TOGGLE_IMU_MODE_HISTORICAL_ON", 105, bytes([0x01])),
        ("LISTEN_8S", 0, b""),
        ("TOGGLE_IMU_MODE_HISTORICAL_OFF", 105, bytes([0x00])),
    ]:
        if cmd == 0:
            e.append({"name": label, "listen_s": 8})
            continue
        frame = puffin(cmd, seq, pay)
        e.append({"seq": seq, "cmd": cmd, "name": label, "hex": frame.hex()})
        seq += 1
    sequences["E_arm_imu_then_off"] = e

    f = []
    for label, cmd, pay in [
        ("TOGGLE_PERSISTENT_R20_ON", 153, bytes([0x01])),
        ("LISTEN_8S", 0, b""),
        ("TOGGLE_PERSISTENT_R20_OFF", 153, bytes([0x00])),
        ("TOGGLE_PERSISTENT_R21_ON", 154, bytes([0x01])),
        ("LISTEN_8S", 0, b""),
        ("TOGGLE_PERSISTENT_R21_OFF", 154, bytes([0x00])),
    ]:
        if cmd == 0:
            f.append({"name": label, "listen_s": 8})
            continue
        frame = puffin(cmd, seq, pay)
        f.append({"seq": seq, "cmd": cmd, "name": label, "hex": frame.hex()})
        seq += 1
    sequences["F_arm_r20_r21_then_off"] = f

    sequences["G_post_r22_combo_bounded"] = {
        "prereq": [
            "WHOOP5/MG encryptedBond (not live-HR-only)",
            "worn == true (on-wrist gate)",
            "PuffinExperiment.isDeepDataEnabled",
            "RAW_GATT / puffin capture ON",
            "run A (or B) first; wait 15/15 SET_FF COMMAND_RESPONSE echoes",
        ],
        "steps": [
            {"cmd": 3, "name": "TOGGLE_REALTIME_HR_ON", "payload_hex": "01", "listen_s": 2},
            {"cmd": 107, "name": "ENABLE_OPTICAL_DATA_ON", "payload_hex": "01", "listen_s": 8},
            {"cmd": 106, "name": "TOGGLE_IMU_MODE_ON", "payload_hex": "01", "listen_s": 8},
            {"cmd": 153, "name": "TOGGLE_PERSISTENT_R20_ON", "payload_hex": "01", "listen_s": 8},
            {"cmd": 154, "name": "TOGGLE_PERSISTENT_R21_ON", "payload_hex": "01", "listen_s": 8},
        ],
        "restore_off": [
            {"cmd": 154, "payload_hex": "00"},
            {"cmd": 153, "payload_hex": "00"},
            {"cmd": 106, "payload_hex": "00"},
            {"cmd": 107, "payload_hex": "00"},
        ],
        "watch_type_at_8": [51, 52, 53, 55, 43],
        "note_type_54_56": "54=events (already seen); 56=PUFFIN_METADATA alias — not a live IMU/optical product stream",
        "success_criterion": (
            "NEW type@8 in {51,52,53,55} OR sustained live non-offload product buffers. "
            "COMMAND_RESPONSE ACK alone is NOT success."
        ),
        "rules": [
            "Write WITH RESPONSE on fd4b0002",
            "Inter-command gap >= 80 ms",
            "Research ON windows <= 8 s then OFF",
            "Never send denylist 45/83/99/142-144",
            "Never invent SpO2/BP/vitals from ACK or @82",
        ],
        "blocker_today": (
            "WhoopBleClient.send() 5/MG allowlist blocks 105-108/128/153/154 "
            "(only SET_CONFIG when deepData opt-in). Sequences D–G need a debug writer / allowlist expand."
        ),
    }

    # Sanity: first optical ON payload must be single 0x01
    d0 = next(x for x in d if x.get("cmd") == 107)
    assert d0["hex"].endswith("01") or bytes.fromhex(d0["hex"])[11] == 0x01
    inner = bytes.fromhex(d0["hex"])
    assert inner[8] == 0x23 and inner[10] == 107 and inner[11] == 0x01

    OUT.write_text(
        json.dumps(
            {
                "lane": 2,
                "agent": "cursor-grok-4.5-high",
                "purpose": "Exact dry-run command sequences to try for deep-data arming",
                "sequences": sequences,
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"wrote {OUT}")
    print("D ENABLE_OPTICAL payload byte:", inner[11])


if __name__ == "__main__":
    main()
