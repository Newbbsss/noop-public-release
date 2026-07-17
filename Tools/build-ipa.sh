#!/usr/bin/env bash
# build-ipa.sh — Gilbert fork unsigned iOS IPA for AltStore / SideStore.
#
# Requires macOS + Xcode + xcodegen. Produces:
#   dist/NOOP-v<VER>-ios.ipa
#
# Usage (from repo root):
#   Tools/build-ipa.sh                 # VER from project.yml MARKETING_VERSION
#   Tools/build-ipa.sh 8.6.140-fable   # explicit version stamp in filename
#
# Matches fork-release.yml ios job + Tools/build-v7-artifacts.sh iOS path:
#   NOOPiOS Release, generic/platform=iOS, CODE_SIGNING_ALLOWED=NO,
#   anonymize, strip Watch for free-Apple-ID sideload.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "✗ IPA build requires macOS + Xcode. This host is $(uname -s)." >&2
  echo "  On Mac: brew install xcodegen && Tools/build-ipa.sh" >&2
  echo "  Or dispatch .github/workflows/fork-release.yml (ios job → NOOP-ios-unsigned-v*.ipa)." >&2
  exit 2
fi

command -v xcodegen >/dev/null || { echo "✗ install xcodegen (brew install xcodegen)" >&2; exit 1; }
command -v xcodebuild >/dev/null || { echo "✗ xcodebuild missing — install Xcode" >&2; exit 1; }

VER="${1:-}"
if [[ -z "$VER" ]]; then
  VER="$(grep -m1 'MARKETING_VERSION:' project.yml | sed -E 's/.*"([^"]+)".*/\1/')"
fi
[[ -n "$VER" ]] || { echo "✗ could not resolve version" >&2; exit 1; }

DIST="$ROOT/dist"
mkdir -p "$DIST"
DD="$ROOT/build/ios-dd"
STAGE="$ROOT/build/ios-stage"
LOG="$ROOT/build/ios-ipa.log"
rm -rf "$DD" "$STAGE"
mkdir -p "$ROOT/build"

echo "═══ xcodegen ═══"
xcodegen generate

echo "═══ NOOPiOS Release (unsigned device) · v$VER ═══"
# Destination-driven (NOT -sdk iphoneos): embeds watchOS correctly during build;
# sideload IPA strips Watch below (free Apple ID companion limit).
set +e
xcodebuild -scheme NOOPiOS -configuration Release \
  -destination 'generic/platform=iOS' \
  -derivedDataPath "$DD" \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" \
  build >"$LOG" 2>&1
XC=$?
set -e
if [[ $XC -ne 0 ]]; then
  echo "✗ xcodebuild failed (exit $XC). Tail of $LOG:" >&2
  grep -E 'error:' "$LOG" | head -40 >&2 || tail -40 "$LOG" >&2
  exit $XC
fi

IOSAPP="$DD/Build/Products/Release-iphoneos/NOOP.app"
if [[ ! -d "$IOSAPP" ]]; then
  IOSAPP="$(find "$DD/Build/Products/Release-iphoneos" -maxdepth 1 -name '*.app' -type d | head -1 || true)"
fi
[[ -d "$IOSAPP" ]] || { echo "✗ NOOP.app not found under Release-iphoneos" >&2; exit 1; }
echo "  built: $IOSAPP"

if [[ -x "$ROOT/Tools/anonymize-ios-app.sh" ]]; then
  "$ROOT/Tools/anonymize-ios-app.sh" "$IOSAPP"
else
  echo "  (skip anonymize — Tools/anonymize-ios-app.sh missing)"
fi

mkdir -p "$STAGE/Payload"
cp -R "$IOSAPP" "$STAGE/Payload/"
if [[ -d "$STAGE/Payload/NOOP.app/Watch" ]]; then
  rm -rf "$STAGE/Payload/NOOP.app/Watch"
  echo "  ✓ stripped embedded Watch from sideload IPA"
fi

OUT="$DIST/NOOP-v${VER}-ios.ipa"
rm -f "$OUT"
( cd "$STAGE" && zip -qry "$OUT" Payload )
echo "✓ $OUT ($(du -h "$OUT" | awk '{print $1}'))"
echo "  Artifact path: $OUT"
echo "  Next: Tools/update-altstore-source.sh $VER $OUT   # optional AltStore manifest"
echo "  Or upload: gh release upload v${VER} \"$OUT\" --clobber"
