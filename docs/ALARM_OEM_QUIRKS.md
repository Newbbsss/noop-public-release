# Alarm OEM / exact-alarm quirks (Android)

Short rider-facing notes for phone wake deadlines. NOOP cannot override OEM battery policies.

## Exact alarms (API 31+)

- **Settings → Apps → Special app access → Alarms & reminders** (or “Exact alarms”) must allow NOOP.
- Without it, Arm may look on while fire times **drift** (Alarm shows **Armed · may drift**).
- Some OEMs bury the toggle under **Battery** or **App info → Permissions**.

## Notifications

- Master **Notifications** off for NOOP silences the phone deadline even when exact access is on.
- Also check the **NOOP alarm** notification channel isn’t muted / set to silent.

## Battery / autostart (common OEM traps)

| OEM family | Where to look |
|------------|----------------|
| Samsung | Device care → Battery → Background usage limits; allow NOOP unrestricted |
| Xiaomi / HyperOS | App battery saver → No restrictions; Autostart on |
| Oppo / OnePlus / Realme | Battery optimization → Don’t optimize; Startup manager |
| Huawei / Honor | Launch / App launch → Manage manually (auto-launch + secondary) |
| Pixel / AOSP | Usually fine after Exact alarms + Notifications |

## Boot

- Phone deadline is re-armed after reboot when Armed.
- Strap buzz needs the strap connected again after reboot.

## Verify

1. Arm wake with exact + notifications on.
2. Optional: set a near-term custom alarm and confirm it rings with screen off.
3. After OEM battery change, re-check Exact alarms + Notifications.
