# iOS â†” Android parity â€” ship bar **8.6.240-fable / 510**

**Scope:** Gilbert fork (`noop-v8.4.0-src`), Android private store **8.6.240-fable / 510**.  
**Date:** 2026-07-20  
**Policy:** document debt; claim Swift parity only under Done. Pragmatic stamp: iOS version matches Android; Android-only gaps remain honest.

**Public:** `noop-public-release` ships MAIN APK + unsigned IPA + AltStore source.

## Stamp (240)

| Platform | Version | Build |
|----------|---------|-------|
| Android | `8.6.240-fable` | 510 |
| iOS (`project.yml`) | `8.6.240-fable` | 510 |

## Inventory notes (post-164)

| Feature | Android | iOS / Swift | Parity |
|---------|---------|-------------|--------|
| Skin-temp swipe Today/Tonight (â‰¥18:00) | `HealthScreen.kt` | Suite cards exist; mini-swipe Today/Tonight is Android-first this pass | **Partial** |
| Charge cold-open / Effort floor / FA unlock | 238 bank | Shared analytics where ported; UI honesty Android-first | **Partial** |
| Drive SAF backup CTA | `BackupCloudHints` | `BackupSyncView` folder â†’ Drive/iCloud | **Partial** |
| Cycle Period + Replay | Settings | Awareness-only | **Android-only** |
| Strength dense dial | Dense | Slim port | **Partial** |
| AlarmRing UI | Full | Logic only | **Partial** |

Prior 151â€“164 matrix rows (sleep stages, Effort â‰¤0, HapticClock, liquid clouds, 6-axis tester, bug report) stay as previously **Aligned** / **Partial** â€” see git history for the full table.

## IPA pipeline

| Path | Status |
|------|--------|
| `Tools/build-ipa.sh` | Mac unsigned `dist/NOOP-v<VER>-ios.ipa` |
| `Tools/build-ipa.ps1` | Windows honest exit 2 |
| `.github/workflows/ios-ipa.yml` | Defaults **8.6.240-fable** / `v8.6.240-fable` |
| AltStore | `altstore-source.json` â†’ public `noop-public-release` raw URL |
| **This agent host** | Windows â€” IPA via CI |

**CI dispatch:**  
`gh workflow run ios-ipa.yml -f version=8.6.240-fable -f release_tag=v8.6.240-fable --repo Newbbsss/noop-public-release`

**AltStore source (public):**  
`https://raw.githubusercontent.com/Newbbsss/noop-public-release/main/altstore-source.json`

## Remaining / Android-only

1. Cycle Period calendar + Replay Cycle setup  
2. Strength dense muscle-heat dial  
3. AlarmRing math dismiss UI + critical wake  
4. Multi-bond last-saved / MG-named preference order  
5. Turn-back / Wake-rested alarm cues  
6. Skin-temp swipe mini-card Today/Tonight twin (optional)

## Related

- `ANY_MODEL_CONTINUE.md` â€” store bar + leftovers  
- `docs/IOS.md` â€” sideload / AltStore  
- `docs/agent/PUBLIC_RELEASE_CHANNEL.md` â€” public channel  
