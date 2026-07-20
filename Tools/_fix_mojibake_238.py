#!/usr/bin/env python3
"""Fix UTF-8 mojibake in android sources.

Handles both latin-1 mojibake (bytes→latin-1) and the CP1252 remapped form
where 0x80/0x91–0x94 became €/‘/’/“/” before a second UTF-8 save.
"""
from __future__ import annotations

import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1] / "android" / "app" / "src" / "main"

# CP1252 byte → Unicode char used when UTF-8 was mis-decoded as CP1252 then re-saved.
CP1252_SPECIAL = {
    0x80: "\u20ac",  # €
    0x82: "\u201a",
    0x83: "\u0192",
    0x84: "\u201e",
    0x85: "\u2026",  # …
    0x86: "\u2020",
    0x87: "\u2021",
    0x88: "\u02c6",
    0x89: "\u2030",
    0x8a: "\u0160",
    0x8b: "\u2039",
    0x8c: "\u0152",  # Œ
    0x8d: "\u008d",
    0x8e: "\u017d",
    0x91: "\u2018",  # ‘
    0x92: "\u2019",  # ’
    0x93: "\u201c",  # “
    0x94: "\u201d",  # ”
    0x95: "\u2022",
    0x96: "\u2013",  # –
    0x97: "\u2014",  # —
    0x98: "\u02dc",
    0x99: "\u2122",
    0x9a: "\u0161",
    0x9b: "\u203a",
    0x9c: "\u0153",  # œ  ← Gilbert's "oe"
    0x9d: "\u009d",
    0x9e: "\u017e",
    0x9f: "\u0178",
}


def utf8_as_cp1252(ch: str) -> str:
    """UTF-8 bytes of ch, each byte mapped through CP1252 (as editors often do)."""
    out = []
    for b in ch.encode("utf-8"):
        if b in CP1252_SPECIAL:
            out.append(CP1252_SPECIAL[b])
        elif 0x80 <= b <= 0x9F:
            out.append(chr(b))  # C1 control fallback
        else:
            out.append(chr(b))
    return "".join(out)


def utf8_as_latin1(ch: str) -> str:
    return ch.encode("utf-8").decode("latin-1")


CHARS = list("±·°—–“”‘’…₂✓⚠○≈×→↔↑↓Δ‰™")


def main() -> int:
    dry = "--dry" in sys.argv
    mapping: dict[str, str] = {}
    for ch in CHARS:
        for bad in (utf8_as_latin1(ch), utf8_as_cp1252(ch)):
            if bad != ch:
                mapping[bad] = ch

    # Explicit known troublemakers (em dash / subscript / checkmarks)
    for ch in ("—", "–", "₂", "✓", "⚠", "○", "≈", "×", "→", "↔", "Δ", "±", "·", "°", "œ", "Œ"):
        # Note: œ itself is NOT always wrong — only when it's mojibake of something else.
        # We do NOT map lone œ → something; we map the 3-char mojibake of intended chars.
        if ch in ("œ", "Œ"):
            continue
        mapping[utf8_as_cp1252(ch)] = ch
        mapping[utf8_as_latin1(ch)] = ch

    counts: dict[str, int] = {}
    files_hit: list[str] = []
    keys = sorted(mapping.keys(), key=len, reverse=True)
    for p in list(ROOT.rglob("*.kt")) + list(ROOT.rglob("*.xml")):
        try:
            text = p.read_text(encoding="utf-8")
        except Exception:
            continue
        orig = text
        for a in keys:
            b = mapping[a]
            n = text.count(a)
            if n:
                counts[repr(a)] = counts.get(repr(a), 0) + n
                text = text.replace(a, b)
        if text != orig:
            files_hit.append(str(p.relative_to(ROOT)))
            if not dry:
                p.write_text(text, encoding="utf-8", newline="\n")
    print(f"{'DRY ' if dry else ''}fixed_files={len(files_hit)}")
    for f in files_hit:
        print(f"  {f}")
    print("counts:")
    for k, v in sorted(counts.items(), key=lambda kv: -kv[1])[:40]:
        print(f"  {k}: {v}")
    # Show what em-dash maps from
    print("emdash bad sample:", repr(utf8_as_cp1252("—")), "-> —")
    print("subscript2 bad:", repr(utf8_as_cp1252("₂")), "-> ₂")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
