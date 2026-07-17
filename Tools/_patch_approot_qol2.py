from pathlib import Path

path = Path(r"C:\Users\Gilbert\Documents\Ai app store\noop-v8.4.0-src\android\app\src\main\java\com\noop\ui\AppRoot.kt")
text = path.read_text(encoding="utf-8")
n = 0

def rep(old, new, label):
    global text, n
    if old not in text:
        raise SystemExit(f"MISSING: {label}")
    text = text.replace(old, new, 1)
    n += 1
    print(f"ok {label}")

rep(
"""    val islandColor = if (frosted) {
        if (Palette.isLight) Color.White.copy(alpha = 0.52f) else Color.White.copy(alpha = 0.14f)
    } else {
        Palette.surfaceRaised.copy(alpha = if (dayCycleOn) 0.68f else 0.82f)
    }
    val islandBorder = if (frosted) {
        Color.White.copy(alpha = if (Palette.isLight) 0.50f else 0.28f)
    } else {
        Palette.hairline.copy(alpha = 0.45f)
    }""",
"""    val islandColor = if (frosted) {
        if (Palette.isLight) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.12f)
    } else {
        // Translucent lacquer — not an opaque black slab; sky still reads through.
        Palette.surfaceRaised.copy(alpha = if (dayCycleOn) 0.55f else 0.68f)
    }
    val islandBorder = if (frosted) {
        Color.White.copy(alpha = if (Palette.isLight) 0.55f else 0.32f)
    } else {
        Palette.hairline.copy(alpha = 0.55f)
    }""",
"island frost")

rep(
"""    onLogWorkout: () -> Unit = {},
    onStrengthTrainer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onQuickRoute: (String) -> Unit = {},
) {""",
"""    onLogWorkout: () -> Unit = {},
    onStrengthTrainer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenUpdates: () -> Unit = {},
    onQuickRoute: (String) -> Unit = {},
) {""",
"bar sig")

rep(
"""                Text("Quick actions", style = NoopType.headline, color = Palette.textPrimary)
                // Fable 200 #101 — shorter subtitle.
                Text(
                    "Log or jump. Themes live in Settings.",
                    style = NoopType.footnote,
                    color = Palette.textSecondary,
                )
                WetBounceButton(
                    label = "Log workout",
                    modifier = Modifier.fillMaxWidth(),
                    tint = Palette.effortColor,
                    onClick = {
                        showPlusSheet = false
                        onLogWorkout()
                    },
                )
                WetBounceButton(
                    label = "Workouts · Strength",
                    modifier = Modifier.fillMaxWidth(),
                    tint = Palette.accent,
                    onClick = {
                        showPlusSheet = false
                        onStrengthTrainer()
                    },
                )
                WetBounceButton(
                    label = "Themes & appearance",
                    modifier = Modifier.fillMaxWidth(),
                    tint = Palette.metricRose,
                    onClick = {
                        showPlusSheet = false
                        onOpenSettings()
                    },
                )
                Spacer(Modifier.height(24.dp))""",
"""                Text("Quick actions", style = NoopType.headline, color = Palette.textPrimary)
                Text(
                    "Tap picks here. Hold + and swipe the dial for Workout · Live · Journal.",
                    style = NoopType.footnote,
                    color = Palette.textSecondary,
                )
                WetBounceButton(
                    label = "Updates",
                    modifier = Modifier.fillMaxWidth(),
                    tint = Palette.accent,
                    onClick = {
                        showPlusSheet = false
                        onOpenUpdates()
                    },
                )
                WetBounceButton(
                    label = "Workouts & strength",
                    modifier = Modifier.fillMaxWidth(),
                    tint = Palette.effortColor,
                    onClick = {
                        showPlusSheet = false
                        onLogWorkout()
                    },
                )
                WetBounceButton(
                    label = "Breathe",
                    modifier = Modifier.fillMaxWidth(),
                    tint = Palette.restColor,
                    onClick = {
                        showPlusSheet = false
                        onQuickRoute(Destination.Breathe.route)
                    },
                )
                Spacer(Modifier.height(24.dp))""",
"plus sheet")

# Veil: find by unique markers
veil_start = text.find("            // Soft veil: sky dissolves into the bar")
if veil_start < 0:
    raise SystemExit("MISSING: veil start")
veil_end = text.find("        Column(", veil_start)
if veil_end < 0:
    raise SystemExit("MISSING: veil end")
new_veil = """            // Bottom glass diffusion under nav/+ — soft multi-stop with Fold edge falloff
            // (GlassDiffusionVeil), not a hard black slab. Pairs with Today softFadeEdges.
            GlassDiffusionVeil(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                height = 108.dp,
                fromTop = false,
                dayCycleOn = dayCycleOn,
            )
"""
text = text[:veil_start] + new_veil + text[veil_end:]
n += 1
print("ok veil")

# Gutter + shadow — line based
lines = text.splitlines(keepends=True)
out = []
i = 0
replaced_gutter = False
while i < len(lines):
    line = lines[i]
    if (not replaced_gutter) and "val plusGutter = 22.dp" in line:
        # drop prior skinny comments
        while out and ("Skinny +" in out[-1] or "TOP-B #347" in out[-1] or "plusGutter" in out[-1]):
            if "val plusGutter" in out[-1]:
                break
            out.pop()
        out.append("            // Fat + needs room — widen gutter so the coin + gold aura aren't crushed.\n")
        out.append("            val plusGutter = 56.dp\n")
        i += 1
        # skip old islandShadow comment + assignment
        while i < len(lines) and (
            "islandShadow" in lines[i]
            or "Fable 200 #10: frosted" in lines[i]
            or "frosted" in lines[i] and "shadow" in lines[i]
        ):
            i += 1
        out.append("            // Shadows on BOTH islands always (frosted used to zero them → slab on Fold ends).\n")
        out.append("            val islandShadow = if (frosted) 6.dp else 8.dp\n")
        replaced_gutter = True
        n += 1
        print("ok gutter+shadow")
        continue
    out.append(line)
    i += 1
if not replaced_gutter:
    raise SystemExit("MISSING: plusGutter")
text = "".join(out)

rep(
"""                onLogWorkout = { nav.navigatePush(Destination.Workouts.route) },
                // Fable 200 #56 — Strength lives under Workouts; + sheet links there (not a side route).
                onStrengthTrainer = { nav.navigateTopLevel(Destination.Workouts.route) },
                onOpenSettings = { nav.navigatePush(Destination.Settings.route) },
                onQuickRoute = { route -> nav.navigatePush(route) },
            )""",
"""                onLogWorkout = { nav.navigatePush(Destination.Workouts.route) },
                // Fable 200 #56 — Strength lives under Workouts; + sheet links there (not a side route).
                onStrengthTrainer = { nav.navigateTopLevel(Destination.Workouts.route) },
                onOpenSettings = { nav.navigatePush(Destination.Settings.route) },
                onOpenUpdates = { showUpdatesInbox = true },
                onQuickRoute = { route -> nav.navigatePush(route) },
            )""",
"bar call")

rep(
"""    val hitRadius = 48.dp
    val buttonVisual = 52.dp
    val hitBox = 60.dp
    val holdMs = 160L""",
"""    val hitRadius = 48.dp
    val buttonVisual = 64.dp
    val hitBox = 76.dp
    val holdMs = 160L""",
"plus sizes")

# Aura only — leave radial Popup alone
aura_marker = "        val auraAlpha = when {"
aura_idx = text.find(aura_marker)
if aura_idx < 0:
    raise SystemExit("MISSING: auraAlpha")
# find end of first Canvas after auraAlpha (the old single aura)
canvas_start = text.find("        Canvas(", aura_idx)
if canvas_start < 0:
    raise SystemExit("MISSING: aura Canvas")
# end of that Canvas block: find "        }\n\n        if (holding"
end_marker = "\n        if (holding || holdBloom > 0.02f)"
end_idx = text.find(end_marker, canvas_start)
if end_idx < 0:
    raise SystemExit("MISSING: holding marker after aura")
# also replace from auraAlpha through end of Canvas
new_aura = """        // Unmistakable gold glow (Gilbert QoL): bright core + wide soft halo. + is the one glow exception.
        // Radial dial visuals are owned by the particle-lighting sibling — do not rewrite that Popup here.
        val auraAlpha = when {
            reduced -> 0.72f + 0.18f * holdBloom
            holding || pressed -> 0.95f
            else -> 0.82f + 0.18f * idleBreath
        }
        Canvas(
            Modifier
                .size(176.dp)
                .graphicsLayer { alpha = auraAlpha * 0.85f },
        ) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to gold.copy(alpha = 0.55f),
                        0.35f to gold.copy(alpha = 0.28f),
                        0.65f to gold.copy(alpha = 0.12f),
                        1.0f to Color.Transparent,
                    ),
                    center = center,
                    radius = r,
                ),
                radius = r,
            )
        }
        Canvas(
            Modifier
                .size(96.dp)
                .graphicsLayer { alpha = auraAlpha },
        ) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to gold.copy(alpha = 0.95f),
                        0.40f to gold.copy(alpha = 0.55f),
                        0.75f to gold.copy(alpha = 0.18f),
                        1.0f to Color.Transparent,
                    ),
                    center = center,
                    radius = r,
                ),
                radius = r,
            )
        }
"""
text = text[:aura_idx] + new_aura + text[end_idx:]
n += 1
print("ok aura glow")

rep(
"""                rotate(degrees = 45f * holdBloom) {
                    val stroke = 1.75.dp.toPx()
                    val arm = 20.dp.toPx() * 0.42f""",
"""                rotate(degrees = 45f * holdBloom) {
                    val stroke = 3.15.dp.toPx()
                    val arm = 28.dp.toPx() * 0.46f""",
"plus glyph")

rep(
"""                .border(0.5.dp, gold.copy(alpha = 0.55f + 0.40f * holdBloom), CircleShape)""",
"""                .border(1.25.dp, gold.copy(alpha = 0.72f + 0.28f * holdBloom), CircleShape)""",
"gold rim")

rep(
"""        Text(
            "Tap for menu",
            style = NoopType.footnote,
            color = Palette.textSecondary,
        )
        Text(
            "Hold & swipe · Workout · Live · Journal",
            style = NoopType.subhead,
            color = Palette.accent,
        )""",
"""        Text(
            "Tap + · menu",
            style = NoopType.footnote,
            color = Palette.textSecondary,
        )
        Text(
            "Hold + · dial · Workout · Live · Journal",
            style = NoopType.subhead,
            color = Palette.accent,
        )""",
"coach tip")

path.write_text(text, encoding="utf-8")
print(f"DONE {n} patches")
