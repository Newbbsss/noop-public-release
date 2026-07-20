#!/usr/bin/env python3
"""WHOOP 5/MG rapid-fire signal hunt — Lane 1: cmd ID / GET_FF / feature-flag sweeps.

Does NOT invent vitals. Does NOT write SpO2/%. Does NOT brick the bond.
Does NOT bump DEBUG=MAIN. Does NOT Gradle-ship.

Modes:
  catalog   Print dense probe + FF catalog JSON.
  inventory Analyze capture files for frame types.
  mine      Mine COMMAND_RESPONSE cmd echoes + unknown cmds from captures.
  dense     Generate dense FF GET frames + cmd-sweep plan + seeded random bursts (logged hex).
  session   Pull Fold (optional) + inventory into pairing-logs/signal-hunt-YYYYMMDD/.
  lane1     Lane-1 board session → pairing-logs/ble-re-lane1-YYYYMMDDHHMM/.

Hard denylist: DFU, firmware load, fuel-gauge reset, FORCE_TRIM, POWER_CYCLE, ship-adjacent.

Usage:
  python Tools/signal_hunt_rapid_fire.py catalog
  python Tools/signal_hunt_rapid_fire.py mine path/to/log.txt -o out/mine.json
  python Tools/signal_hunt_rapid_fire.py dense --seed 20260717 -o pairing-logs/ble-re-lane1-.../
  python Tools/signal_hunt_rapid_fire.py lane1 --serial RFCX70E8RCD --pull
"""

from __future__ import annotations

import argparse
import json
import random
import re
import subprocess
import sys
import zlib
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

REPO = Path(__file__).resolve().parents[1]
DEFAULT_PAIRING = REPO / "pairing-logs"
PARENT_PAIRING = REPO.parent / "pairing-logs"

# ---------------------------------------------------------------------------
# Framing
# ---------------------------------------------------------------------------


def crc16_modbus(data: bytes) -> int:
    crc = 0xFFFF
    for byte in data:
        crc ^= byte
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return crc & 0xFFFF


def build_puffin_command(cmd: int, seq: int = 1, payload: bytes = b"\x00") -> bytes:
    inner = bytes([0x23, seq & 0xFF, cmd & 0xFF]) + payload
    declared = len(inner) + 4
    head = bytes([0xAA, 0x01, declared & 0xFF, (declared >> 8) & 0xFF, 0x00, 0x01])
    c16 = crc16_modbus(head)
    frame = head + bytes([c16 & 0xFF, (c16 >> 8) & 0xFF]) + inner
    c32 = zlib.crc32(inner) & 0xFFFFFFFF
    return frame + c32.to_bytes(4, "little")


def ff_payload(name: str, value: int = 0) -> bytes:
    body = bytearray(40)
    raw = name.encode("ascii")[:32]
    body[: len(raw)] = raw
    body[32] = value & 0xFF
    return bytes(body)


def get_ff_frame(name: str, seq: int = 1) -> bytes:
    """GET_FF_VALUE (128) with b3=0x01 + 40-byte name body (value 0)."""
    return build_puffin_command(128, seq=seq, payload=bytes([0x01]) + ff_payload(name, 0))


def get_device_config_frame(name: str, seq: int = 1) -> bytes:
    """GET_DEVICE_CONFIG_VALUE (121) with b3=0x01 + 33-byte name body."""
    body = bytearray(33)
    raw = name.encode("ascii")[:32]
    body[: len(raw)] = raw
    return build_puffin_command(121, seq=seq, payload=bytes([0x01]) + bytes(body))


# ---------------------------------------------------------------------------
# Denylist + known command names (whoop_protocol.json)
# ---------------------------------------------------------------------------

DENYLIST_CMDS: dict[int, str] = {
    25: "FORCE_TRIM",
    32: "POWER_CYCLE_STRAP",
    36: "START_FIRMWARE_LOAD",
    37: "LOAD_FIRMWARE_DATA",
    38: "PROCESS_FIRMWARE_IMAGE",
    45: "ENTER_BLE_DFU",
    83: "VERIFY_FIRMWARE_IMAGE",
    99: "RESET_FUEL_GAUGE",
    142: "START_FIRMWARE_LOAD_NEW",
    143: "LOAD_FIRMWARE_DATA_NEW",
    144: "PROCESS_FIRMWARE_IMAGE_NEW",
}

# Named cmds from protocol (for labeling miners). Destructive ones stay in DENYLIST.
CMD_NAMES: dict[int, str] = {
    1: "LINK_VALID",
    2: "GET_MAX_PROTOCOL_VERSION",
    3: "TOGGLE_REALTIME_HR",
    7: "REPORT_VERSION_INFO",
    10: "SET_CLOCK",
    11: "GET_CLOCK",
    14: "TOGGLE_GENERIC_HR_PROFILE",
    16: "TOGGLE_R7_DATA_COLLECTION",
    19: "RUN_HAPTIC_PATTERN_MAVERICK",
    20: "ABORT_HISTORICAL_TRANSMITS",
    22: "SEND_HISTORICAL_DATA",
    23: "HISTORICAL_DATA_RESULT",
    26: "GET_BATTERY_LEVEL",
    29: "REBOOT_STRAP",
    33: "SET_READ_POINTER",
    34: "GET_DATA_RANGE",
    35: "GET_HELLO_HARVARD",
    39: "SET_LED_DRIVE",
    40: "GET_LED_DRIVE",
    41: "SET_TIA_GAIN",
    42: "GET_TIA_GAIN",
    43: "SET_BIAS_OFFSET",
    44: "GET_BIAS_OFFSET",
    48: "SEND_EVENT_PACKETS",
    52: "SET_DP_TYPE",
    53: "FORCE_DP_TYPE",
    61: "SET_AFE_PARAMETERS",
    62: "GET_AFE_PARAMETERS",
    63: "SEND_R10_R11_REALTIME",
    66: "SET_ALARM_TIME",
    67: "GET_ALARM_TIME",
    68: "RUN_ALARM",
    69: "DISABLE_ALARM",
    76: "GET_ADVERTISING_NAME_HARVARD",
    77: "SET_ADVERTISING_NAME_HARVARD",
    79: "RUN_HAPTICS_PATTERN",
    80: "GET_ALL_HAPTICS_PATTERN",
    81: "START_RAW_DATA",
    82: "STOP_RAW_DATA",
    84: "GET_BODY_LOCATION_AND_STATUS",
    96: "ENTER_HIGH_FREQ_SYNC",
    97: "EXIT_HIGH_FREQ_SYNC",
    98: "GET_EXTENDED_BATTERY_INFO",
    100: "CALIBRATE_CAPSENSE",
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
    122: "STOP_HAPTICS",
    123: "SELECT_WRIST",
    124: "TOGGLE_LABRADOR_DATA_GENERATION",
    125: "TOGGLE_LABRADOR_RAW_SAVE",
    128: "GET_FF_VALUE",
    131: "SET_RESEARCH_PACKET",
    132: "GET_RESEARCH_PACKET",
    139: "TOGGLE_LABRADOR_FILTERED",
    140: "SET_ADVERTISING_NAME",
    141: "GET_ADVERTISING_NAME",
    145: "GET_HELLO",
    151: "GET_BATTERY_PACK_INFO",
    153: "TOGGLE_PERSISTENT_R20",
    154: "TOGGLE_PERSISTENT_R21",
}

# Full R22 enable sequence names + extras worth GET_FF / GET_DEVICE_CONFIG reads.
# IPA 5.61.0 note: official FF *names* are NOT plaintext in the iOS binary (key-exchange /
# remote config). Names below come from HCI/APK RE + type-54 console + IPA Fake-FF id.
# Never map any SUCCESS value to SpO2 %. See docs/agent/research/WHOOP_IPA_MG_INTERCEPT_2026-07-18.md
FF_READ_NAMES: list[str] = [
    # Official R22 sequence (Whoop5Config.enableR22Sequence) — MG GET_FF SUCCESS catalog
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
    # IPA Fake FF key id + Lane3 console / Lane2 extras (GET only; expect mix SUCCESS/FAILURE)
    "sigproc_wear_detect",
    "enable_raw_data_w_ecg",
    "max_collection_backlog",
    "enable_sig12",
    "enable_maverick_model",
    # Device-config / broadcast / research hypotheses (GET only in Lane 1 plan)
    "whoop_live_hr_in_adv_ind_pkt",
    "enable_r22_v1_packets",
    "enable_r22_v7_packets",
    "enable_r20_packets",
    "enable_r21_packets",
    "enable_r26_packets",
    "enable_optical_data",
    "enable_imu_stream",
    "enable_labrador",
    "toggle_persistent_r20",
    "toggle_persistent_r21",
    "hrfm_visible",
    "sig11_during_sleep",
    "pip_r26_packets",
    "passive_strap_fit_gen5",
    "research_packet_enable",
    # Product/HealthKit names — IPA has bloodOxygen / HK OxygenSaturation only (no BLE %).
    # Keep as negative GET probes; do NOT invent SpO2 % on SUCCESS-or-fail.
    "spo2_enable",
    "blood_oxygen_enable",
    "continuous_hrv",
    "realtime_hr_rr",
]

DEVICE_CONFIG_NAMES: list[str] = [
    "whoop_live_hr_in_adv_ind_pkt",
    "advertising_name",
    "body_location",
    "wrist_preference",
    # Lane3 console / IPA DeviceConfiguration adjacency (GET_DEVICE_CONFIG 121)
    "max_collection_backlog",
    "sigproc_wear_detect",
]

KNOWN_TYPES = {
    35: "COMMAND",
    36: "COMMAND_RESPONSE",
    38: "PUFFIN_COMMAND_RESPONSE",
    40: "REALTIME_DATA",
    43: "REALTIME_RAW_DATA",
    47: "HISTORICAL_DATA",
    48: "EVENT",
    49: "METADATA",
    50: "CONSOLE_LOGS",
    51: "REALTIME_IMU_DATA_STREAM",
    52: "HISTORICAL_IMU_DATA_STREAM",
    53: "RELATIVE_PUFFIN_EVENTS",
    54: "PUFFIN_EVENTS_FROM_STRAP",
    55: "RELATIVE_BATTERY_PACK_CONSOLE_LOGS",
    56: "PUFFIN_METADATA",
}


@dataclass(frozen=True)
class Probe:
    cmd: int
    name: str
    tier: str
    payload_hex: str
    note: str
    expect: str


def _p(cmd: int, name: str, tier: str, payload_hex: str, note: str, expect: str = "ACK?") -> Probe:
    return Probe(cmd, name, tier, payload_hex, note, expect)


def build_safe_probes() -> list[Probe]:
    """Dense curated set — Lane 1 owns cmd/FF breadth; deep arming left to Lane 2."""
    probes: list[Probe] = [
        _p(3, "TOGGLE_REALTIME_HR", "known_stream", "01", "Arm type-40", "type-40"),
        _p(3, "TOGGLE_REALTIME_HR_OFF", "toggle_off", "00", "Disarm realtime", "ACK"),
        _p(63, "SEND_R10_R11_OFF", "toggle_off", "00", "Keep type-43 off", "ACK"),
        _p(63, "SEND_R10_R11_ON", "research", "01", "Bounded raw — Lane2 watches types", "type-43"),
        # Reads
        _p(1, "LINK_VALID", "read", "00", "Link check", "ACK"),
        _p(2, "GET_MAX_PROTOCOL_VERSION", "read", "00", "Protocol max", "version"),
        _p(7, "REPORT_VERSION_INFO", "read", "00", "FW block", "fw"),
        _p(11, "GET_CLOCK", "read", "", "Empty payload 5/MG", "RTC"),
        _p(26, "GET_BATTERY_LEVEL", "read", "00", "Bond-safe", "batt%"),
        _p(34, "GET_DATA_RANGE", "read", "", "History window", "range"),
        _p(35, "GET_HELLO_HARVARD", "read", "00", "4.0 hello (may UNSUPPORTED)", "hello?"),
        _p(40, "GET_LED_DRIVE", "read", "00", "AFE LED — Lane4 primary", "afe?"),
        _p(42, "GET_TIA_GAIN", "read", "00", "AFE TIA — Lane4", "afe?"),
        _p(44, "GET_BIAS_OFFSET", "read", "00", "AFE bias — Lane4", "afe?"),
        _p(48, "SEND_EVENT_PACKETS", "read", "01", "Request events?", "events?"),
        _p(62, "GET_AFE_PARAMETERS", "read", "00", "AFE blob — Lane4", "afe?"),
        _p(67, "GET_ALARM_TIME", "read", "00", "Alarm", "alarm"),
        _p(76, "GET_ADVERTISING_NAME_HARVARD", "read", "00", "4.0 name", "name?"),
        _p(80, "GET_ALL_HAPTICS_PATTERN", "read", "00", "Haptics table", "patterns"),
        _p(84, "GET_BODY_LOCATION_AND_STATUS", "read", "00", "Wear/location", "status"),
        _p(98, "GET_EXTENDED_BATTERY_INFO", "read", "00", "mV detail", "extbatt"),
        _p(121, "GET_DEVICE_CONFIG_VALUE", "read", "01", "b3=01 empty name probe", "CONFIG?"),
        _p(128, "GET_FF_VALUE", "read", "01", "b3=01 empty — use named FF frames", "FF?"),
        _p(132, "GET_RESEARCH_PACKET", "read", "00", "Research cfg — Lane4", "research?"),
        _p(141, "GET_ADVERTISING_NAME", "read", "00", "5/MG adv name", "name"),
        _p(145, "GET_HELLO", "read", "01", "Hello + fw", "hello"),
        _p(151, "GET_BATTERY_PACK_INFO", "read", "00", "MG pack", "pack"),
        # Already-seen answering cmds — re-probe shapes
        _p(14, "TOGGLE_GENERIC_HR_PROFILE_OFF", "toggle_off", "00", "Seen ACK in overnight", "ACK"),
        _p(14, "TOGGLE_GENERIC_HR_PROFILE_ON", "research", "01", "Generic HR profile", "ACK"),
        _p(20, "ABORT_HISTORICAL_TRANSMITS", "read", "00", "Seen ACK — abort offload", "ACK"),
        _p(115, "START_DEVICE_CONFIG_KEY_EXCHANGE", "read", "01", "Seen ACK overnight", "keys?"),
        _p(117, "START_FF_KEY_EXCHANGE", "read", "01", "Seen ACK overnight", "FF keys?"),
        # Research (Lane 2 owns activation proof; Lane 1 catalogs)
        _p(81, "START_RAW_DATA", "research", "00", "Raw window", "raw"),
        _p(82, "STOP_RAW_DATA", "toggle_off", "00", "End raw", "ACK"),
        # IPA 5.61: enterHighFreqHistoricalMode(duration: UInt16) — payload shape unpinned;
        # keep 00 as dry-run catalog only (Lane5 owns proving duration alters hist mix).
        _p(96, "ENTER_HIGH_FREQ_SYNC", "research", "00", "IPA EnterHighFreqHistoricalMode", "ACK"),
        _p(97, "EXIT_HIGH_FREQ_SYNC", "toggle_off", "00", "IPA ExitHighFreqHistoricalMode", "ACK"),
        # IPA Swift twins: ToggleHistoricalIMUData / ToggleHistoricalOpticalData /
        # ToggleRealtimeIMUStream / ToggleRealtimeOpticalData — MG live often FAILURE(0).
        _p(105, "TOGGLE_IMU_MODE_HISTORICAL", "research", "01", "IPA ToggleHistoricalIMUData — Lane2", "ACK≠stream"),
        _p(106, "TOGGLE_IMU_MODE", "research", "01", "IPA ToggleRealtimeIMUStream — Lane2", "watch 51-56"),
        _p(106, "TOGGLE_IMU_MODE_OFF", "toggle_off", "00", "IMU off", "ACK"),
        _p(107, "ENABLE_OPTICAL_DATA", "research", "01", "IPA ToggleHistoricalOpticalData — Lane2/4", "watch deep"),
        _p(107, "ENABLE_OPTICAL_DATA_OFF", "toggle_off", "00", "Optical off", "ACK"),
        _p(108, "TOGGLE_OPTICAL_MODE", "research", "01", "IPA ToggleRealtimeOpticalData — Lane2/4", "new types?"),
        _p(108, "TOGGLE_OPTICAL_MODE_OFF", "toggle_off", "00", "Optical restore", "ACK"),
        _p(124, "TOGGLE_LABRADOR_DATA_OFF", "toggle_off", "00", "Labrador off first", "ACK"),
        _p(124, "TOGGLE_LABRADOR_DATA_ON", "research", "01", "Labrador gen", "ACK"),
        _p(125, "TOGGLE_LABRADOR_RAW_OFF", "toggle_off", "00", "Labrador raw off", "ACK"),
        _p(125, "TOGGLE_LABRADOR_RAW_ON", "research", "01", "Labrador raw", "ACK"),
        _p(139, "TOGGLE_LABRADOR_FILTERED_OFF", "toggle_off", "00", "Labrador filt off", "ACK"),
        _p(139, "TOGGLE_LABRADOR_FILTERED_ON", "research", "01", "Labrador filt", "ACK"),
        _p(153, "TOGGLE_PERSISTENT_R20", "research", "01", "R20 — Lane2", "new frames?"),
        _p(153, "TOGGLE_PERSISTENT_R20_OFF", "toggle_off", "00", "R20 off", "ACK"),
        _p(154, "TOGGLE_PERSISTENT_R21", "research", "01", "R21 — Lane2", "new frames?"),
        _p(154, "TOGGLE_PERSISTENT_R21_OFF", "toggle_off", "00", "R21 off", "ACK"),
    ]
    return probes


SAFE_PROBES = build_safe_probes()

# ---------------------------------------------------------------------------
# Dense cmd sweep + random bursts (Lane 1)
# ---------------------------------------------------------------------------

# Safe GET-ish / toggle-read ranges to probe; skip denylist.
SWEEP_CMD_RANGES = [
    (1, 35),
    (39, 44),
    (48, 48),
    (61, 69),
    (76, 84),
    (96, 98),
    (105, 108),
    (115, 132),
    (139, 141),
    (145, 154),
]


def iter_sweep_cmds() -> list[int]:
    out: list[int] = []
    for lo, hi in SWEEP_CMD_RANGES:
        for c in range(lo, hi + 1):
            if c in DENYLIST_CMDS:
                continue
            out.append(c)
    return sorted(set(out))


def dense_ff_plan() -> list[dict]:
    plan = []
    seq = 1
    for name in FF_READ_NAMES:
        frame = get_ff_frame(name, seq=seq)
        plan.append(
            {
                "kind": "GET_FF_VALUE",
                "cmd": 128,
                "flag": name,
                "seq": seq,
                "hex": frame.hex(),
                "gap_ms": 80,
            }
        )
        seq = (seq + 1) & 0xFF or 1
    for name in DEVICE_CONFIG_NAMES:
        frame = get_device_config_frame(name, seq=seq)
        plan.append(
            {
                "kind": "GET_DEVICE_CONFIG_VALUE",
                "cmd": 121,
                "flag": name,
                "seq": seq,
                "hex": frame.hex(),
                "gap_ms": 80,
            }
        )
        seq = (seq + 1) & 0xFF or 1
    return plan


def dense_cmd_sweep_plan() -> list[dict]:
    """One read probe per sweep cmd with payloads [], [00], [01]."""
    plan = []
    seq = 1
    for cmd in iter_sweep_cmds():
        name = CMD_NAMES.get(cmd, f"CMD_{cmd}")
        for payload in (b"", b"\x00", b"\x01"):
            # Skip empty for cmds that protocol always uses a byte — still try both.
            frame = build_puffin_command(cmd, seq=seq, payload=payload if payload else b"\x00")
            if not payload:
                # also emit true-empty variant
                frame = build_puffin_command(cmd, seq=seq, payload=b"")
            plan.append(
                {
                    "kind": "CMD_SWEEP",
                    "cmd": cmd,
                    "name": name,
                    "payload_hex": payload.hex(),
                    "seq": seq,
                    "hex": frame.hex(),
                    "gap_ms": 90,
                    "tier": "read" if cmd in {2, 7, 11, 26, 34, 40, 42, 44, 62, 67, 80, 84, 98, 121, 128, 132, 141, 145, 151} else "probe",
                }
            )
            seq = (seq + 1) & 0xFF or 1
    return plan


def random_burst_plan(seed: int, n: int = 120, max_cmd: int = 160) -> list[dict]:
    """Seeded RNG over unknown cmd/payload patterns — logged only; hard rate limit metadata."""
    rng = random.Random(seed)
    plan = []
    seq = 1
    for i in range(n):
        cmd = rng.randint(1, max_cmd)
        while cmd in DENYLIST_CMDS:
            cmd = rng.randint(1, max_cmd)
        # Prefer short safe payloads
        choice = rng.choice(
            [
                b"",
                b"\x00",
                b"\x01",
                b"\x01\x00",
                b"\x00\x00",
                bytes([0x01]) + bytes(40),  # FF-shaped
                bytes([0x01]) + bytes(33),  # device-config-shaped
            ]
        )
        frame = build_puffin_command(cmd, seq=seq, payload=choice)
        plan.append(
            {
                "kind": "RANDOM",
                "i": i,
                "cmd": cmd,
                "name": CMD_NAMES.get(cmd, f"UNKNOWN_{cmd}"),
                "known": cmd in CMD_NAMES,
                "denied": False,
                "payload_hex": choice.hex(),
                "seq": seq,
                "hex": frame.hex(),
                "gap_ms": 100,
                "abort_on": ["bond_loss", "gatt_error", "link_down", "status_22"],
            }
        )
        seq = (seq + 1) & 0xFF or 1
    return plan


def cmd_catalog() -> dict:
    sweep = iter_sweep_cmds()
    return {
        "lane": 1,
        "probe_count": len(SAFE_PROBES),
        "ff_read_names": FF_READ_NAMES,
        "ff_name_count": len(FF_READ_NAMES),
        "device_config_names": DEVICE_CONFIG_NAMES,
        "sweep_cmd_ids": sweep,
        "sweep_cmd_count": len(sweep),
        "denylist": DENYLIST_CMDS,
        "burst_a_reads": [asdict(p) for p in SAFE_PROBES if p.tier == "read"],
        "burst_b_known": [asdict(p) for p in SAFE_PROBES if p.tier == "known_stream"],
        "burst_c_research_then_off": [
            asdict(p) for p in SAFE_PROBES if p.tier in ("research", "toggle_off")
        ],
        "rules": [
            "Lane 1: maximize answering cmds + FF GET coverage; log unknowns.",
            "Write WITH RESPONSE on fd4b0002; gap >= 80–100 ms.",
            "Never send denylist (DFU/firmware/FORCE_TRIM/POWER_CYCLE/fuel-gauge).",
            "Research ON windows <= 8 s then OFF — Lane 2 owns 51–56 activation claims.",
            "Never bank @82 as SpO2%; no invented vitals.",
            "Abort burst on bond/link/GATT errors.",
            "Type-54 decode → Lane 3; AFE/@82 → Lane 4; hist schema → Lane 5.",
        ],
        "dry_run_hex_samples": {
            "GET_CLOCK": build_puffin_command(11, 1, b"").hex(),
            "GET_FF_enable_r22": get_ff_frame("enable_r22_packets", 1).hex(),
            "GET_HELLO": build_puffin_command(145, 1, b"\x01").hex(),
        },
    }


# ---------------------------------------------------------------------------
# Capture inventory + mine answering cmds
# ---------------------------------------------------------------------------

HEX_RE = re.compile(r"(?:hex=|raw=|\"hex\"\s*:\s*\")([0-9a-fA-F]{24,})", re.I)
TYPE_RE = re.compile(r"type[=@]?\s*(\d+|0x[0-9a-fA-F]+)", re.I)
RAW_GATT_RE = re.compile(
    r"RAW_GATT.*?uuid=([^\s]+).*?len=(\d+).*?hex=([0-9a-fA-F]+)", re.I
)


def parse_hex(s: str) -> bytes | None:
    try:
        return bytes.fromhex(s.strip())
    except ValueError:
        return None


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
    if len(frame) >= 5 and frame[0] == 0xAA:
        return frame[4]
    return None


def iter_frames_from_text(text: str) -> Iterable[bytes]:
    for m in RAW_GATT_RE.finditer(text):
        raw = parse_hex(m.group(3))
        if raw:
            yield raw
    for m in HEX_RE.finditer(text):
        raw = parse_hex(m.group(1))
        if raw and len(raw) >= 12:
            yield raw
    try:
        data = json.loads(text)
        items = data if isinstance(data, list) else [data]
    except json.JSONDecodeError:
        items = []
        for line in text.splitlines():
            line = line.strip()
            if not line.startswith("{"):
                continue
            try:
                items.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    for item in items:
        if not isinstance(item, dict):
            continue
        for key in ("hex", "raw", "frame"):
            if key in item:
                raw = parse_hex(str(item[key]))
                if raw:
                    yield raw


def inventory_path(path: Path) -> dict:
    text = path.read_text(encoding="utf-8", errors="replace")
    types: Counter[int] = Counter()
    versions: Counter[int] = Counter()
    lens: Counter[int] = Counter()
    cmd_acks: Counter[int] = Counter()
    cmd_results: dict[int, Counter[int]] = defaultdict(Counter)
    valid = 0
    total = 0
    newish: list[dict] = []
    for frame in iter_frames_from_text(text):
        total += 1
        t = frame_type(frame)
        if t is None:
            continue
        types[t] += 1
        lens[len(frame)] += 1
        if valid_puffin(frame):
            valid += 1
            if t in (47, 0x2F) and len(frame) > 9:
                versions[frame[9]] += 1
            if t in (36, 38) and len(frame) > 12:
                cmd = frame[10]
                cmd_acks[cmd] += 1
                cmd_results[cmd][frame[12]] += 1
        name = KNOWN_TYPES.get(t, "UNKNOWN")
        if name == "UNKNOWN" or t in (51, 52, 53, 55):
            if len(newish) < 40:
                newish.append(
                    {
                        "type": t,
                        "len": len(frame),
                        "hex_head": frame[: min(32, len(frame))].hex(),
                        "label": name,
                    }
                )
    prose_types: Counter[int] = Counter()
    for m in TYPE_RE.finditer(text):
        prose_types[int(m.group(1), 0)] += 1
    return {
        "path": str(path),
        "bytes": path.stat().st_size,
        "frames_seen": total,
        "puffin_crc_ok": valid,
        "types": {str(k): v for k, v in sorted(types.items())},
        "type_names": {str(k): KNOWN_TYPES.get(k, "UNKNOWN") for k in sorted(types)},
        "hist_versions": {str(k): v for k, v in sorted(versions.items())},
        "cmd_acks": {str(k): v for k, v in sorted(cmd_acks.items())},
        "cmd_results": {
            str(cmd): {str(r): c for r, c in sorted(res.items())}
            for cmd, res in sorted(cmd_results.items())
        },
        "top_lens": dict(lens.most_common(12)),
        "interesting_heads": newish,
        "prose_type_mentions": {str(k): v for k, v in sorted(prose_types.items())},
    }


def mine_answering_cmds(paths: list[Path]) -> dict:
    """Aggregate COMMAND_RESPONSE echoes across captures — Lane 1 primary deliverable."""
    acks: Counter[int] = Counter()
    results: dict[int, Counter[int]] = defaultdict(Counter)
    files_hit: dict[int, set[str]] = defaultdict(set)
    unknown_cmds: Counter[int] = Counter()
    for path in paths:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for frame in iter_frames_from_text(text):
            if not valid_puffin(frame):
                continue
            t = frame_type(frame)
            if t not in (36, 38) or len(frame) < 13:
                continue
            cmd = frame[10]
            result = frame[12]
            acks[cmd] += 1
            results[cmd][result] += 1
            files_hit[cmd].add(path.name)
            if cmd not in CMD_NAMES:
                unknown_cmds[cmd] += 1

    answering = []
    for cmd, count in sorted(acks.items(), key=lambda x: (-x[1], x[0])):
        answering.append(
            {
                "cmd": cmd,
                "name": CMD_NAMES.get(cmd, f"UNKNOWN_{cmd}"),
                "known": cmd in CMD_NAMES,
                "denied": cmd in DENYLIST_CMDS,
                "ack_count": count,
                "results": {
                    # 0 FAILURE 1 SUCCESS 2 PENDING 3 UNSUPPORTED (protocol)
                    str(r): c
                    for r, c in sorted(results[cmd].items())
                },
                "files": sorted(files_hit[cmd])[:12],
            }
        )

    sweep_set = set(iter_sweep_cmds())
    still_dark = sorted(
        c
        for c in sweep_set
        if c not in acks and c not in DENYLIST_CMDS
    )
    return {
        "generated_utc": datetime.now(timezone.utc).isoformat(),
        "lane": 1,
        "files_mined": [str(p) for p in paths if p.exists()],
        "answering_cmd_count": len(answering),
        "answering_cmds": answering,
        "unknown_cmd_ids": {str(k): v for k, v in sorted(unknown_cmds.items())},
        "sweep_still_dark": still_dark,
        "sweep_still_dark_named": [
            {"cmd": c, "name": CMD_NAMES.get(c, f"CMD_{c}")} for c in still_dark
        ],
        "ff_get_planned": len(FF_READ_NAMES),
        "note": "ACK presence ≠ stream activation. result 3=UNSUPPORTED. Foreign 83 may appear — do not send.",
    }


# ---------------------------------------------------------------------------
# ADB
# ---------------------------------------------------------------------------


def adb(serial: str | None, *args: str, timeout: int = 120) -> subprocess.CompletedProcess[str]:
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += list(args)
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)


def pull_fold_session(out: Path, serial: str) -> list[str]:
    notes: list[str] = []
    out.mkdir(parents=True, exist_ok=True)
    (out / "adb-devices.txt").write_text(
        adb(serial, "devices", "-l").stdout
        + adb(serial, "shell", "getprop", "ro.product.model").stdout,
        encoding="utf-8",
    )
    for pkg, tag in (("com.noop.whoop", "main"), ("com.noop.whoop.debug", "debug")):
        ver = adb(serial, "shell", "dumpsys", "package", pkg)
        (out / f"package-{tag}.txt").write_text(ver.stdout[:20000], encoding="utf-8")
        notes.append(f"package {pkg}: exit={ver.returncode}")

    log = adb(serial, "logcat", "-d", "-t", "4000")
    lines = [
        ln
        for ln in log.stdout.splitlines()
        if re.search(r"Whoop|noop|RAW_GATT|fd4b|TOGGLE|R22|COMMAND|FF_|GET_", ln, re.I)
    ]
    (out / "logcat-filtered.txt").write_text("\n".join(lines[-3000:]), encoding="utf-8")
    notes.append(f"logcat filter lines={len(lines)}")

    for fname in (
        "whoop5-backfill-capture.jsonl",
        "whoop5-events.jsonl",
        "whoop5-deepbuffers.jsonl",
        "puffin-deepbuffers.jsonl",
    ):
        dest = out / f"debug-{fname}"
        pull = adb(
            serial,
            "shell",
            f"run-as com.noop.whoop.debug cat files/{fname} 2>/dev/null | head -c 8000000",
            timeout=180,
        )
        if pull.stdout and len(pull.stdout) > 50:
            dest.write_text(pull.stdout, encoding="utf-8", errors="replace")
            notes.append(f"pulled debug:{fname} bytes={len(pull.stdout)}")
        else:
            notes.append(f"missing debug:{fname}")

    for remote in (
        "/sdcard/Download/noop-raw-capture-260716-1949.jsonl",
        "/sdcard/Download/noop-raw-capture-260716-1952.jsonl",
    ):
        local = out / Path(remote).name
        if local.exists():
            notes.append(f"skip existing {local.name}")
            continue
        r = adb(serial, "pull", remote, str(local), timeout=180)
        notes.append(f"pull {remote}: rc={r.returncode}")
    return notes


def resolve_pairing_root() -> Path:
    if DEFAULT_PAIRING.exists():
        return DEFAULT_PAIRING
    if PARENT_PAIRING.exists():
        return PARENT_PAIRING
    DEFAULT_PAIRING.mkdir(parents=True, exist_ok=True)
    return DEFAULT_PAIRING


def collect_mine_paths(extra: list[Path], out: Path | None = None) -> list[Path]:
    root = resolve_pairing_root()
    candidates: list[Path] = list(extra)
    if out:
        candidates += list(out.glob("*.jsonl"))
        candidates += list(out.glob("logcat-filtered.txt"))
    # Prior signal-hunt + overnight
    for base in (root, PARENT_PAIRING):
        if not base.exists():
            continue
        p = base / "noop-pairing-log.txt"
        if p.exists():
            candidates.append(p)
        for p in sorted(base.glob("signal-hunt-*/**/*.jsonl"))[:20]:
            candidates.append(p)
        for p in sorted(base.glob("fold-pull-*/raw/*.jsonl"))[:6]:
            candidates.append(p)
        for p in sorted(base.glob("fold-pull-*/run-as-debug/*.jsonl"))[:6]:
            candidates.append(p)
    seen: set[str] = set()
    uniq: list[Path] = []
    for p in candidates:
        if not p.exists():
            continue
        key = str(p.resolve())
        if key in seen:
            continue
        seen.add(key)
        uniq.append(p)
    return uniq


# ---------------------------------------------------------------------------
# CLI commands
# ---------------------------------------------------------------------------


def cmd_catalog_print() -> int:
    data = cmd_catalog()
    print(json.dumps(data, indent=2))
    print(
        f"\n# probes={data['probe_count']} ff_names={data['ff_name_count']} "
        f"sweep_cmds={data['sweep_cmd_count']} deny={len(DENYLIST_CMDS)}"
    )
    return 0


def cmd_inventory(paths: list[Path], out: Path | None) -> int:
    results = [inventory_path(p) for p in paths if p.exists()]
    union: Counter[int] = Counter()
    for r in results:
        for k, v in r["types"].items():
            union[int(k)] += v
    summary = {
        "generated_utc": datetime.now(timezone.utc).isoformat(),
        "lane": 1,
        "files": results,
        "union_types": {
            str(k): {"count": v, "name": KNOWN_TYPES.get(k, "UNKNOWN")}
            for k, v in sorted(union.items())
        },
    }
    text = json.dumps(summary, indent=2)
    print(text)
    if out:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text, encoding="utf-8")
    return 0


def cmd_mine(paths: list[Path], out: Path | None) -> int:
    report = mine_answering_cmds(paths)
    text = json.dumps(report, indent=2)
    print(text)
    if out:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text, encoding="utf-8")
    return 0


def write_dense_plans(out: Path, seed: int, random_n: int) -> dict:
    out.mkdir(parents=True, exist_ok=True)
    catalog = cmd_catalog()
    ff_plan = dense_ff_plan()
    sweep_plan = dense_cmd_sweep_plan()
    rand_plan = random_burst_plan(seed, n=random_n)
    (out / "catalog.json").write_text(json.dumps(catalog, indent=2), encoding="utf-8")
    (out / "plan-ff-get.json").write_text(json.dumps(ff_plan, indent=2), encoding="utf-8")
    (out / "plan-cmd-sweep.json").write_text(json.dumps(sweep_plan, indent=2), encoding="utf-8")
    (out / "plan-random-burst.json").write_text(
        json.dumps({"seed": seed, "n": random_n, "bursts": rand_plan}, indent=2),
        encoding="utf-8",
    )
    # Flat executable log (one JSONL line per probe) for future DEBUG writer
    with (out / "plan-all.jsonl").open("w", encoding="utf-8") as fh:
        for row in ff_plan + sweep_plan + rand_plan:
            fh.write(json.dumps(row) + "\n")
    summary = {
        "ff_get_frames": len(ff_plan),
        "cmd_sweep_frames": len(sweep_plan),
        "random_frames": len(rand_plan),
        "total_logged_probes": len(ff_plan) + len(sweep_plan) + len(rand_plan),
        "unique_sweep_cmds": catalog["sweep_cmd_count"],
        "ff_names": catalog["ff_name_count"],
        "seed": seed,
        "rate_limit": "gap_ms 80–100; abort_on bond_loss/gatt_error/link_down",
        "live_fire": False,
        "note": "Plans are dry-run hex until DEBUG writer exists (no DEBUG=MAIN sync this lane).",
    }
    (out / "dense-summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    return summary


def cmd_dense(out: Path, seed: int, random_n: int) -> int:
    summary = write_dense_plans(out, seed, random_n)
    print(json.dumps(summary, indent=2))
    print(f"\nWrote dense plans → {out}")
    return 0


def cmd_lane1(serial: str | None, pull: bool, seed: int, random_n: int) -> int:
    root = resolve_pairing_root()
    stamp = datetime.now().strftime("%Y%m%d%H%M")
    out = root / f"ble-re-lane1-{stamp}"
    out.mkdir(parents=True, exist_ok=True)
    notes: list[str] = [f"session_dir={out}", "lane=1"]

    dense = write_dense_plans(out, seed, random_n)
    notes.append(f"dense_total_probes={dense['total_logged_probes']}")

    if pull:
        if not serial:
            print("ERROR: --pull requires --serial", file=sys.stderr)
            return 2
        notes.extend(pull_fold_session(out, serial))

    # Also reuse prior signal-hunt captures without re-pulling huge twins if present
    prior = root / "signal-hunt-20260717"
    if prior.exists():
        for name in (
            "debug-whoop5-backfill-capture.jsonl",
            "noop-raw-capture-260716-1949.jsonl",
        ):
            src = prior / name
            if src.exists() and not (out / name).exists():
                # symlink-style: just mine from prior path
                notes.append(f"mine_include_prior={src}")

    mine_paths = collect_mine_paths([], out)
    if prior.exists():
        mine_paths = collect_mine_paths(
            [
                prior / "debug-whoop5-backfill-capture.jsonl",
                prior / "noop-raw-capture-260716-1949.jsonl",
            ],
            out,
        )

    mine_report = mine_answering_cmds(mine_paths)
    (out / "answering-cmds.json").write_text(
        json.dumps(mine_report, indent=2), encoding="utf-8"
    )

    inv_paths = [p for p in mine_paths if p.suffix in {".jsonl", ".txt"}][:12]
    inv = {
        "generated_utc": datetime.now(timezone.utc).isoformat(),
        "files": [inventory_path(p) for p in inv_paths if p.exists()],
    }
    (out / "inventory-sample.json").write_text(json.dumps(inv, indent=2), encoding="utf-8")

    board = {
        "lane": 1,
        "generated_utc": datetime.now(timezone.utc).isoformat(),
        "serial": serial,
        "pulled": pull,
        "notes": notes,
        "dense": dense,
        "answering_cmd_count": mine_report["answering_cmd_count"],
        "answering_cmd_ids": [a["cmd"] for a in mine_report["answering_cmds"]],
        "unknown_cmd_ids": mine_report["unknown_cmd_ids"],
        "sweep_still_dark_count": len(mine_report["sweep_still_dark"]),
        "sweep_still_dark": mine_report["sweep_still_dark"][:40],
        "cross_links": {
            "lane2": "R20–R22 / 51–56 arming — do not claim from ACK alone",
            "lane3": "type-54 PUFFIN_EVENTS decode",
            "lane4": "AFE / research / @82",
            "lane5": "historical + whoop_protocol.json gaps",
        },
        "debug_refresh_later": (
            "DEBUG APK refresh (NOT now) would unlock in-app SignalHuntProbe writer "
            "to fire plan-all.jsonl live with RAW_GATT — Gilbert said wait on DEBUG=MAIN."
        ),
    }
    (out / "LANE1_SUMMARY.json").write_text(json.dumps(board, indent=2), encoding="utf-8")
    (out / "LANE1_SUMMARY.md").write_text(
        "# BLE RE Lane 1 summary\n\n```json\n" + json.dumps(board, indent=2) + "\n```\n",
        encoding="utf-8",
    )
    print(json.dumps(board, indent=2))
    print(f"\nWrote {out}")
    return 0


def cmd_session(serial: str | None, pull: bool, extra: list[Path]) -> int:
    """Legacy session folder (signal-hunt-YYYYMMDD). Prefer lane1 for board work."""
    root = resolve_pairing_root()
    day = datetime.now().strftime("%Y%m%d")
    out = root / f"signal-hunt-{day}"
    out.mkdir(parents=True, exist_ok=True)
    write_dense_plans(out, seed=20260717, random_n=80)
    notes = [f"session_dir={out}"]
    if pull:
        if not serial:
            return 2
        notes.extend(pull_fold_session(out, serial))
    paths = collect_mine_paths(extra, out)
    mine = mine_answering_cmds(paths)
    (out / "answering-cmds.json").write_text(json.dumps(mine, indent=2), encoding="utf-8")
    summary = {
        "notes": notes,
        "answering_cmd_count": mine["answering_cmd_count"],
        "out": str(out),
    }
    (out / "session-summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))
    return 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("catalog", help="Print dense catalog JSON")

    p_inv = sub.add_parser("inventory", help="Inventory frame types")
    p_inv.add_argument("paths", nargs="+", type=Path)
    p_inv.add_argument("-o", "--out", type=Path, default=None)

    p_mine = sub.add_parser("mine", help="Mine answering COMMAND_RESPONSE cmds")
    p_mine.add_argument("paths", nargs="+", type=Path)
    p_mine.add_argument("-o", "--out", type=Path, default=None)

    p_dense = sub.add_parser("dense", help="Write dense FF/cmd/random plans")
    p_dense.add_argument("-o", "--out", type=Path, required=True)
    p_dense.add_argument("--seed", type=int, default=20260717)
    p_dense.add_argument("--random-n", type=int, default=120)

    p_ses = sub.add_parser("session", help="Legacy signal-hunt-YYYYMMDD session")
    p_ses.add_argument("--serial", default=None)
    p_ses.add_argument("--pull", action="store_true")
    p_ses.add_argument("extra", nargs="*", type=Path)

    p_l1 = sub.add_parser("lane1", help="Lane-1 board session → ble-re-lane1-*")
    p_l1.add_argument("--serial", default=None)
    p_l1.add_argument("--pull", action="store_true")
    p_l1.add_argument("--seed", type=int, default=20260717)
    p_l1.add_argument("--random-n", type=int, default=160)

    args = ap.parse_args(argv)
    if args.cmd == "catalog":
        return cmd_catalog_print()
    if args.cmd == "inventory":
        return cmd_inventory(args.paths, args.out)
    if args.cmd == "mine":
        return cmd_mine(args.paths, args.out)
    if args.cmd == "dense":
        return cmd_dense(args.out, args.seed, args.random_n)
    if args.cmd == "session":
        return cmd_session(args.serial, args.pull, list(args.extra))
    if args.cmd == "lane1":
        return cmd_lane1(args.serial, args.pull, args.seed, args.random_n)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
