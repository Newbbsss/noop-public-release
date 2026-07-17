# WHOOP 5.0 / MG deep data — the "R22" unlock

**Status:** experimental, opt-in, with live type-47 v18 observations. Deep-history retrieval and R22 output remain hardware-specific and unverified as a complete data source.
**Tracking:** [#103](https://github.com/ryanbr/noop/issues/103) (raw HCI captures + new deep-record layouts).

## The problem

A WHOOP 5.0 / MG strap hands a freshly-connected third-party client **only live heart rate** (over the
standard `0x2A37` profile, which needs no bond). Recovery, strain, sleep, motion and history don't come
through. This is the single biggest gap in NOOP's 5/MG support, and it affects every independent WHOOP
app equally.

## Why — the feature-flag gate

The official app switches on the deeper streams by writing a short burst of **persistent feature-flag
config values** to the strap right after the hello handshake. The most load-bearing of these is
`enable_r22_packets`; "R22" is the strap's **optical/PPG data-product packet format** (versions v1–v8),
not a hardware revision. Until those flags are set, the strap keeps the deep streams to itself.

This was reached independently three ways, which is why we trust it:

| Source | Method | What it gives |
|---|---|---|
| [judes.club — "Cracking the WHOOP 5 Bluetooth Protocol"](https://judes.club/writing/cracking-the-whoop-5-bluetooth-protocol/) + [interactive spec](https://judes.club/experiments/whoop5/) | iOS HCI capture of the official app | The full frame format + the exact 15-flag enable sequence **with values**. Our `Whoop5Config` golden test is validated byte-for-byte against its frame-builder. |
| [Asherlc/dofek](https://github.com/Asherlc/dofek/blob/main/docs/whoop-ble-protocol.md) | Android APK decompilation | The config opcodes (`0x73 START_DEVICE_CONFIG_KEY_EXCHANGE`, `0x78 SET_FF_VALUE`) and the same key names/values. |
| A community BTSnoop capture ([#103](https://github.com/ryanbr/noop/issues/103)) | Bluetooth HCI log of the official app on a real strap | Independently surfaced the same `enable_r22_*` console report + the channel layout. |

## Channel layout (5.0 / MG)

| Channel (UUID suffix on `fd4b0001-…`) | Direction | Carries | NOOP |
|---|---|---|---|
| `0x2A37` standard HR | strap → app | live heart rate | subscribed ✅ |
| `fd4b0002` | app → strap | `0xAA`-framed commands | writes here ✅ |
| `fd4b0003/4/5/7` | strap → app | `0xAA`-framed responses + data + console | subscribes to all four ✅ |

NOOP already writes commands **and** subscribes to every data channel. So the blocker is not that NOOP
isn't listening — the strap simply doesn't *start* the deep streams for a session that hasn't set the
flags.

## The frame format

Commands use the maverick/puffin envelope NOOP already implements
(`Framing.puffinCommandFrame` / `crc16Modbus` + `crc32`):

```
[0xAA][0x01][declLen u16 LE][field=0x0100][CRC16-MODBUS of the 6 header bytes]
  [inner: 0x23 type][seq][cmd][b3][payload…]
[CRC32 of inner, u32 LE]
```

- **`b3` (4th inner byte)** matters: GET_HELLO / SET_CONFIG want `0x01`; GET_DATA_RANGE /
  SEND_HISTORICAL want `0x00`. NOOP carries `b3` as the first payload byte (so `sendHistoricalData`
  with `[0x00]` is correct).
- **Write WITH RESPONSE** — write-no-response is silently dropped by the strap.

## The enable sequence (`Whoop5Config`)

One `SET_CONFIG` (cmd `0x78`) per flag; the 40-byte body is the flag name as ASCII NUL-padded to 32
bytes, the value byte (an ASCII `'1'`/`'2'`) at offset 32, then 7 zeros. The exact ordered set, with
values, is in [`Whoop5Config.swift`](../Packages/WhoopProtocol/Sources/WhoopProtocol/Whoop5Config.swift)
and [`Whoop5Config.kt`](../android/app/src/main/java/com/noop/protocol/Whoop5Config.kt), golden-tested on
both platforms. `enable_r22_packets` is the one that opens the type-`0x2F` biometric stream; the rest
tune channel selection, wear detection and sleep behaviour.

## How NOOP uses it (opt-in, reversible)

- A **default-off** Settings → Experimental toggle, separate from the read-only probes because this one
  *writes* to the strap.
- A manual **"Send enable sequence to strap"** button (not auto-run on connect), enabled only when a
  5/MG is **bonded and worn** (the R22 stream is on-wrist gated).
- The 15 flags are written with-response, ~80 ms apart.
- It's **reversible** — it only changes which data the strap chooses to emit — and is the same thing the
  official app does on every connect.
- **iOS / Android only on real hardware:** macOS CoreBluetooth can't complete the authenticated SMP bond
  the command characteristic requires, so the write path is unavailable on Mac.

## Honest limits

- **Acceptance ≠ activation (hardware-confirmed, dual-strap overnight).** The `enable_r22_*`
  SET_CONFIG sequence is **accepted 15/15** with COMMAND_RESPONSE echoing each flag name + value on
  real MG hardware. That does **not** by itself start a live deep stream: after 5 sessions and 4+ hours
  post full ACK, captures still showed **zero type 51–56 deep frames** — only plain type-40 HR (+ R-R).
  Hypothesis: deep optical product streams may be further **subscription / server-gated** on straps
  without an active WHOOP membership. Treat R22 flag ACKs as "strap wrote the flags", not "deep data is
  flowing". Keep watching the live deep-packet counter; share strap logs if types 51–56 ever appear.
  **Same honesty for live IMU:** `TOGGLE_IMU_MODE` / cmd **106** can ACK without streaming — ACK ≠
  activation. Settings → **6-axis motion tester** uses phone sensors for the live moving dot until a
  real type-51 stream appears; historical 1244-B offload is labeled **not live**.
- **Live IMU refused; historical IMU works (validated).** `TOGGLE_IMU_MODE` / cmd **106** ACKs on
  5/MG but **never streams** live types 51–52. The connect-time / historical offload **does** ship
  1244-B type-`0x2F` buffers with full accel **and** gyro @ 100 Hz (columnar i16; scale 1/4096 g and
  ±2000 dps). Hardware-validated on 1423 buffers (fw 50.40.1.0): ~1.01 g gravity shell, gyro r≈0.79
  with accel motion. NOOP decodes these via `Whoop5RawImu` + inline `"imu":{…}` features in the
  opt-in deep-buffer JSONL (`whoop5-deepbuffers.jsonl` / `puffin-deepbuffers.jsonl`, #455/#476/#481).
  The 2140-B optical buffer is still captured raw (layout open). Do **not** expect live IMU without
  membership proving types 51–56. The Settings / Test Centre live-dot tester prefers strap type-51
  when present, else **phone sensors** (clearly labeled); offload samples never claim to be live.
- **What works without a subscribed MG (client-side, tonight).** Type-40 `REALTIME_DATA` on
  `fd4b0005` is useful without membership or R22 unlock:
  - `[10:13]` u32 LE unix timestamp · `[16]` u8 HR · `[17]` u8 R-R count · `[18+]` u16 LE R-R (ms)
  - Worn captures often carry ≥1 R-R (~70% once optical lock settles) → live HRV source
  - NOOP decodes this at +4 offsets (`decodeRealtimeWhoop5`), banks HR/R-R into Room (including during
    history sync), and arms continuous/spot HRV from those intervals when present
  - Standard `0x2A37` remains a parallel HR/R-R path when the profile reports intervals
- **No cloud scores.** Recovery/strain/sleep *scores* are computed in WHOOP's cloud and no public
  project has reproduced them. What any deep unlock buys is the **raw inputs** — which is exactly what
  NOOP needs, since NOOP computes its own scores on-device.
- **It may not even be necessary for history.** [goose #24](https://github.com/b-nnett/goose/issues/24)
  shows a Gen5 band streaming type-47 history to a third-party app *without* any config write. So the
  first thing to confirm is whether a clocked 5/MG already returns deep history through the plain
  `get_data_range`/`send_historical_data` loop NOOP already runs.
- **The decode of deep records is still open.** If types 51–56 / type-`0x2F` ever start arriving after
  membership or a future unlock path, map layouts and feed motion into NOOP's existing stagers — do not
  invent vitals meanwhile. High-rate IMU from the **historical** 1244-B path is already decoded (above);
  live 51–56 remain gated.

## How to help (5.0 / MG owners)

1. Update to the latest NOOP, **Settings → Experimental → "Unlock WHOOP 5/MG deep data (R22)"**.
2. With the strap **on and bonded**, tap **Send enable sequence to strap**.
3. Keep wearing it, let it sync, then **share your strap log** on [#103](https://github.com/ryanbr/noop/issues/103) — we're looking for new deep
   records (type `0x2F`) to start arriving.
4. Even better: a Bluetooth HCI capture of the **official app syncing a full night's history** shows the deep
   packets actually flowing and their layout. Method: iOS **PacketLogger** (Bluetooth diagnostic profile → `.pklg`)
   or Android **Developer Options → Bluetooth HCI snoop log** → `btsnoop_hci.log`, opened in Wireshark — the
   same iOS-HCI approach the [judes.club write-up](https://judes.club/writing/cracking-the-whoop-5-bluetooth-protocol/)
used. Filter to just the WHOOP peripheral and attach it to [#103](https://github.com/ryanbr/noop/issues/103).

## Practical retrieval order

1. Connect and bond the strap, then use **Sync now**. NOOP always tries the guarded normal history path first.
2. For a 5/MG, refresh the standard Battery Level characteristic separately; do not use a WHOOP 4 battery command.
3. If history remains absent, enable raw capture only when intentionally debugging. Raw capture stays local and is exported only by user action.
4. Use the R22 opt-in only while the strap is worn and bonded. It writes strap configuration, so keep the official app available and capture before/after logs.
5. Treat missing deep data as missing. NOOP may still show live HR, a 5/MG step counter, and activity class from observed type-47 v18 frames, but it must not infer cloud scores, blood pressure, SpO2, ECG, or complete history from them.

Credit to **judes.club**, **Asherlc/dofek**, and **b-nnett/goose** for the public protocol work this
builds on.
