# Public release sync (private â†’ public)

This document is the **canonical** how-to for publishing Gilbertâ€™s scrubbed public tree.

## Repos

| Role | GitHub | Visibility |
|------|--------|------------|
| Private daily driver | *(private Gilbert fork â€” not linked from public README)* | Private |
| Public release | `Newbbsss/noop-public-release` | **Public** |
| Upstream | `ryanbr/noop` | Public |

## Dual branding

- **Private** `versionName` may keep a `-fable` suffix (e.g. `8.6.153-fable`).
- **Public** `versionName` is plain (e.g. `8.6.243`) â€” see `android/app/build.gradle.kts` in this tree.
- Details: [DUAL_BRANDING.md](DUAL_BRANDING.md).

## What must never appear in public

1. Personal **Tailscale** IPs / `*.ts.net` hosts
2. Home **LAN** catalog URLs (e.g. `:8090/apps.json` on a desk IP)
3. **AI Build Store** / private catalog repo names as user-facing update paths
4. Private PATs, `deploy.env`, keystores, `local.properties`
5. Device serials / Fold adb notes from private handoff logs
6. The private `docs/agent/` grind notes (use `for-agents/` here instead)

Tailscale as a **generic** Friends-network product word is OK in Friends UI only â€” never with a personal IP.

## Sync steps (from private tree)

```powershell
# 1) Export scrubbed tree (this script)
Tools\sync_public_release.ps1 `
  -PublicVersionName 8.6.243 `
  -PublicVersionCode 513 `
  -PublicRepo Newbbsss/noop-public-release `
  -DestRoot <path-to-noop-public-release>

# 2) Review leak check output (script fails closed on hits)

# 3) Commit + push public repo
cd <path-to-noop-public-release>
git add -A
git commit -m "Public release 8.6.243"
git push origin main

# 4) Attach MAIN APK to a GitHub Release (tag v8.6.243)
gh release create "v8.6.243" .\NOOP-v8.6.243-main.apk `
  -R Newbbsss/noop-public-release `
  --title "NOOP 8.6.243 Public Release" `
  --notes "Public Release. Scrubbed source + MAIN APK. Updates via this repoâ€™s Releases only."
```

Private publish (`publish-main-release.ps1`) stays on the private fork and must **not** be the public update URL.

## APK notes

- Prefer building MAIN from **this** scrubbed tree so `versionName` matches `8.6.243`.
- If shipping a binary already built from the private tree, rename the asset without `-fable` and note dual branding in the release notes (internal APK metadata may still show a private `versionName` until rebuilt).

## Agent notes location

- Public: `for-agents/` only
- Private: `docs/agent/` (never mirrored wholesale)

Model lock for this channel setup: **cursor-grok-4.5-high**.
