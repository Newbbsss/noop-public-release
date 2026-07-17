from pathlib import Path
import re

path = Path(r"C:\Users\Gilbert\Documents\Ai app store\noop-v8.4.0-src\android\app\src\main\java\com\noop\ui\AppRoot.kt")
text = path.read_text(encoding="utf-8")

# Pass 3: remove dead QuickAction list (sheet gone)
text2 = re.sub(
    r"\n/\*\* A centre-FAB quick action:.*?private val quickActions: List<QuickAction> = listOf\(\n(?:.*\n)*?\)\n",
    "\n",
    text,
    count=1,
    flags=re.S,
)
if text2 == text:
    # try simpler
    start = text.find("/** A centre-FAB quick action:")
    end = text.find("// MARK: - Navigation motion", start)
    if start > 0 and end > start:
        text = text[:start] + text[end:]
        print("ok removed QuickAction dead code")
    else:
        print("WARN QuickAction not removed")
else:
    text = text2
    print("ok removed QuickAction via regex")

text = text.replace(
    "val islandShadow = if (frosted) 6.dp else 8.dp",
    "val islandShadow = if (frosted) 8.dp else 10.dp",
    1,
)

# Gold-tinted coin lift so glow reads against sky
old_coin_shadow = ".shadow(elev.dp, CircleShape, clip = false)"
new_coin_shadow = """.shadow(
                    elevation = (elev + 6f).dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = gold.copy(alpha = 0.55f),
                    spotColor = gold.copy(alpha = 0.35f),
                )"""
if old_coin_shadow not in text:
    raise SystemExit("coin shadow missing")
text = text.replace(old_coin_shadow, new_coin_shadow, 1)
print("ok gold coin shadow")

text = text.replace(
    """                        // The quick-action "+" lives in the Today header's top-right now (off the
                        // bottom bar) — it opens the same quick-action sheet the bar used to.
                        onQuickActions = { showUpdatesInbox = true },""",
    """                        // Header no longer hosts a duplicate +; keep callback for ABI → Updates.
                        onQuickActions = { showUpdatesInbox = true },""",
    1,
)

path.write_text(text, encoding="utf-8")
print("pass3 AppRoot OK")

# Changelog bump entries
chg = Path(r"C:\Users\Gilbert\Documents\Ai app store\noop-v8.4.0-src\android\app\src\main\java\com\noop\ui\AppChangelog.kt")
c = chg.read_text(encoding="utf-8")
if 'CURRENT_VERSION = "8.6.73"' not in c:
    c = c.replace('CURRENT_VERSION = "8.6.70"', 'CURRENT_VERSION = "8.6.73"', 1)
    if 'CURRENT_VERSION = "8.6.73"' not in c:
        c = c.replace('CURRENT_VERSION = "8.6.72"', 'CURRENT_VERSION = "8.6.73"', 1)
    entry = '''        Release(
            version = "8.6.73",
            title = "Glass ends + glowing +",
            date = "July 2026",
            items = listOf(
                "**Today soft-fades at both ends.** Content dissolves under the status cluster and under the nav/+ zone — real composite fade, not a hard black cut.",
                "**Nav islands cast soft shadows on both ends** with Fold edge falloff on the glass veil, so the bar is no longer a black slab.",
                "**The centre + is fatter with a real gold glow** — wide halo, hot core, champagne spark. Tap opens the menu; hold opens the dial.",
                "**Less redundancy near +.** One tap menu (Updates · Workouts · Breathe); Themes stay in Settings.",
            ),
        ),
'''
    marker = "    val releases: List<Release> = listOf(\n"
    if marker not in c:
        raise SystemExit("changelog releases marker missing")
    c = c.replace(marker, marker + entry, 1)
    chg.write_text(c, encoding="utf-8")
    print("pass3 changelog OK")
else:
    print("changelog already 8.6.73")
