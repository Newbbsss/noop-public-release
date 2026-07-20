#!/usr/bin/env python3
import sys
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8")
text = Path("android/app/src/main/java/com/noop/ui/HealthScreen.kt").read_text(encoding="utf-8")
lines = text.splitlines()
for n in (104, 749, 1085, 1198, 1199, 1200):
    print(f"L{n}: {lines[n-1]!r}")
print("pm_mojibake", "\u00c2\u00b1" in text)
print("emdash_mojibake", "\u00e2\u20ac\u201d" in text)
print("oe_left", "\u0153" in text)
print("proper_pm", "\u00b1" in text)
print("proper_em", "\u2014" in text)
print("proper_check", "\u2713" in text)
