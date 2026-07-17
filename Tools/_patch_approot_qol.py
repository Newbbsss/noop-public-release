from pathlib import Path

path = Path(r"C:\Users\Gilbert\Documents\Ai app store\noop-v8.4.0-src\android\app\src\main\java\com\noop\ui\AppRoot.kt")
text = path.read_text(encoding="utf-8")
orig = text

# 1) Deeper crescent bite for fat +
old_bite = """        // Shallower cup + nearer centre: rim kisses a skinny +, no canyon gap.
        val biteR = size.height * 0.42f
        val cx = if (biteFromRight) size.width + biteR * 0.02f else -biteR * 0.02f"""
new_bite = """        // Deeper cup for the fat lacquer coin so the rim cups it without a canyon.
        val biteR = size.height * 0.58f
        val cx = if (biteFromRight) size.width + biteR * 0.08f else -biteR * 0.08f"""
if old_bite not in text:
    raise SystemExit("crescent bite not found")
text = text.replace(old_bite, new_bite, 1)

# 2) Island frost: more translucent; always soft shadow both ends
old_island = """    val islandColor = if (frosted) {
        if (Palette.isLight) Color.White.copy(alpha = 0.52f) else Color.White.copy(alpha = 0.14f)
    } else {
        Palette.surfaceRaised.copy(alpha = if (dayCycleOn) 0.68f else 0.82f)
    }
    val islandBorder = if (frosted) {
        Color.White.copy(alpha = if (Palette.isLight) 0.50f else 0.28f)
    } else {
        Palette.hairline.copy(alpha = 0.45f)
    }"""
new_island = """    val islandColor = if (frosted) {
        if (Palette.isLight) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.12f)
    } else {
        // Translucent lacquer — not an opaque black slab; sky still reads through.
        Palette.surfaceRaised.copy(alpha = if (dayCycleOn) 0.55f else 0.68f)
    }
    val islandBorder = if (frosted) {
        Color.White.copy(alpha = if (Palette.isLight) 0.55f else 0.32f)
    } else {
        Palette.hairline.copy(alpha = 0.55f)
    }"""
if old_island not in text:
    raise SystemExit("island color not found")
text = text.replace(old_island, new_island, 1)

# 3) Replace hard veil with GlassDiffusionVeil + widen + gutter + shadows both ends
old_veil = """            // Soft veil: sky dissolves into the bar — light weighted toward the BOTTOM of the bar
            // (TOP-B #345: diffuse wash reads from below, not a top slab). Quieter when day-cycle is
            // on; slightly stronger when sky is off so content doesn't collide with the islands.
            // Light theme needs a stronger veil (Fable Today #22) — white islands wash out without it.
            val lightBoost = if (Palette.isLight) 0.12f else 0f
            val veilTop = (if (dayCycleOn) 0.10f else 0.18f) + lightBoost
            val veilMid = (if (dayCycleOn) 0.28f else 0.40f) + lightBoost
            val veilBot = (if (dayCycleOn) 0.52f else 0.68f) + lightBoost
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.35f to Palette.surfaceBase.copy(alpha = veilTop),
                                0.70f to Palette.surfaceBase.copy(alpha = veilMid),
                                1.0f to Palette.surfaceBase.copy(alpha = veilBot),
                            ),
                        ),
                    ),
            )"""
new_veil = """            // Bottom glass diffusion under nav/+ — soft multi-stop with Fold edge falloff
            // (GlassDiffusionVeil), not a hard black slab. Content soft-fades here + Today softFadeEdges.
            GlassDiffusionVeil(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Bleed past horizontal padding so the veil spans the full screen width.
                    .offset(x = (-10).dp)
                    .fillMaxWidth(1.05f),
                height = 108.dp,
                fromTop = false,
                dayCycleOn = dayCycleOn,
            )"""
# fillMaxWidth(1.05f) might not exist in older compose - use a simpler approach
new_veil = """            // Bottom glass diffusion under nav/+ — soft multi-stop with Fold edge falloff
            // (GlassDiffusionVeil), not a hard black slab. Content soft-fades here + Today softFadeEdges.
            GlassDiffusionVeil(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                height = 108.dp,
                fromTop = false,
                dayCycleOn = dayCycleOn,
            )"""
if old_veil not in text:
    raise SystemExit("veil block not found")
text = text.replace(old_veil, new_veil, 1)

old_gutter = """            // Skinny + (~44 visual) sits in a tight gutter so crescents kiss, not canyon.
            // TOP-B #347: shrink plus↔side gaps a bit further (was 30dp).
            val plusGutter = 22.dp
            // Fable 200 #10: frosted → no shadow; #9 height token stays 56dp below.
            val islandShadow = if (frosted) 0.dp else 2.dp"""
new_gutter = """            // Fat + needs room — widen gutter so the coin + gold aura aren't crushed by crescents.
            val plusGutter = 56.dp
            // Shadows on BOTH islands always (frosted used to zero them → slab look on Fold ends).
            val islandShadow = if (frosted) 6.dp else 8.dp"""
if old_gutter not in text:
    # try without special arrow char
    old_gutter2 = """            // Skinny + (~44 visual) sits in a tight gutter so crescents kiss, not canyon.
            // TOP-B #347: shrink plus"""
    idx = text.find("val plusGutter = 22.dp")
    if idx < 0:
        raise SystemExit("plusGutter not found")
    # replace surrounding block by line surgery
    lines = text.splitlines(keepends=True)
    out = []
    i = 0
    while i < len(lines):
        if "val plusGutter = 22.dp" in lines[i]:
            # replace previous comment lines if skinny
            while out and ("Skinny +" in out[-1] or "TOP-B #347" in out[-1] or "plusGutter" in out[-1]):
                # don't pop plusGutter line we haven't added
                if "val plusGutter" in out[-1]:
                    break
                out.pop()
            out.append("            // Fat + needs room — widen gutter so the coin + gold aura aren't crushed by crescents.\n")
            out.append("            val plusGutter = 56.dp\n")
            i += 1
            # skip old islandShadow lines
            while i < len(lines) and ("islandShadow" in lines[i] or "frosted → no shadow" in lines[i] or "frosted -> no shadow" in lines[i] or "Fable 200 #10: frosted" in lines[i]):
                i += 1
            out.append("            // Shadows on BOTH islands always (frosted used to zero them → slab look on Fold ends).\n")
            out.append("            val islandShadow = if (frosted) 6.dp else 8.dp\n")
            continue
        out.append(lines[i])
        i += 1
    text = "".join(out)
else:
    text = text.replace(old_gutter, new_gutter, 1)

# Ensure island Surfaces use clip=false style shadow via drawBehind if needed —
# Material shadowElevation should be enough with non-zero values.

# 4) Plus sheet: drop Themes redundancy; clarify tap vs hold; keep Updates reachable
old_sheet = """                Text("Quick actions", style = NoopType.headline, color = Palette.textPrimary)
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
                Spacer(modifier.height(24.dp))"""

new_sheet = """                Text("Quick actions", style = NoopType.headline, color = Palette.textPrimary)
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
                Spacer(Modifier.height(24.dp))"""

if old_sheet not in text:
    raise SystemExit("plus sheet block not found")
text = text.replace(old_sheet, new_sheet, 1)

# Add onOpenUpdates param to GlassBottomBar signature
old_sig = """    onLogWorkout: () -> Unit = {},
    onStrengthTrainer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onQuickRoute: (String) -> Unit = {},
) {"""
new_sig = """    onLogWorkout: () -> Unit = {},
    onStrengthTrainer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenUpdates: () -> Unit = {},
    onQuickRoute: (String) -> Unit = {},
) {"""
if old_sig not in text:
    raise SystemExit("GlassBottomBar sig not found")
text = text.replace(old_sig, new_sig, 1)

# Wire call site
old_call = """                onLogWorkout = { nav.navigatePush(Destination.Workouts.route) },
                // Fable 200 #56 — Strength lives under Workouts; + sheet links there (not a side route).
                onStrengthTrainer = { nav.navigateTopLevel(Destination.Workouts.route) },
                onOpenSettings = { nav.navigatePush(Destination.Settings.route) },
                onQuickRoute = { route -> nav.navigatePush(route) },
            )"""
new_call = """                onLogWorkout = { nav.navigatePush(Destination.Workouts.route) },
                // Fable 200 #56 — Strength lives under Workouts; + sheet links there (not a side route).
                onStrengthTrainer = { nav.navigateTopLevel(Destination.Workouts.route) },
                onOpenSettings = { nav.navigatePush(Destination.Settings.route) },
                onOpenUpdates = { showUpdatesInbox = true },
                onQuickRoute = { route -> nav.navigatePush(route) },
            )"""
if old_call not in text:
    raise SystemExit("GlassBottomBar call not found")
text = text.replace(old_call, new_call, 1)

# 5) Fat + + REAL glow — sizes and aura only (do NOT touch radial Popup / nodes)
old_sizes = """    val hitRadius = 48.dp
    val buttonVisual = 52.dp
    val hitBox = 60.dp
    val holdMs = 160L"""
new_sizes = """    val hitRadius = 48.dp
    val buttonVisual = 64.dp
    val hitBox = 76.dp
    val holdMs = 160L"""
if old_sizes not in text:
    raise SystemExit("plus sizes not found")
text = text.replace(old_sizes, new_sizes, 1)

old_aura = """        val auraAlpha = when {
            reduced -> 0.52f + 0.14f * holdBloom
            holding || pressed -> 0.78f + 0.20f * holdBloom
            else -> 0.68f + 0.22f * idleBreath
        }
        Canvas(
            Modifier
                .size(128.dp)
                .graphicsLayer { alpha = auraAlpha },
        ) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to gold.copy(alpha = 0.88f),
                        0.22f to gold.copy(alpha = 0.52f),
                        0.48f to gold.copy(alpha = 0.26f),
                        0.72f to gold.copy(alpha = 0.10f),
                        1.0f to Color.Transparent,
                    ),
                    center = center,
                    radius = r,
                ),
                radius = r,
            )
        }"""

new_aura = """        // Unmistakable gold glow (Gilbert QoL): bright core + wide soft halo. + is the one glow exception.
        val auraAlpha = when {
            reduced -> 0.72f + 0.18f * holdBloom
            holding || pressed -> 0.95f
            else -> 0.82f + 0.18f * idleBreath
        }
        // Outer soft bloom
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
        // Hot core so the aura reads even on bright sky / Fold glare
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
        }"""
if old_aura not in text:
    raise SystemExit("aura block not found")
text = text.replace(old_aura, new_aura, 1)

# Thicken + glyph (still inside CenterPlusButton Canvas — not radial)
old_glyph = """                rotate(degrees = 45f * holdBloom) {
                    val stroke = 1.75.dp.toPx()
                    val arm = 20.dp.toPx() * 0.42f"""
new_glyph = """                rotate(degrees = 45f * holdBloom) {
                    val stroke = 3.15.dp.toPx()
                    val arm = 28.dp.toPx() * 0.46f"""
if old_glyph not in text:
    raise SystemExit("plus glyph not found")
text = text.replace(old_glyph, new_glyph, 1)

# Stronger gold rim on coin
old_rim = """                .border(0.5.dp, gold.copy(alpha = 0.55f + 0.40f * holdBloom), CircleShape)"""
new_rim = """                .border(1.25.dp, gold.copy(alpha = 0.72f + 0.28f * holdBloom), CircleShape)"""
if old_rim not in text:
    raise SystemExit("rim not found")
text = text.replace(old_rim, new_rim, 1)

# Coach tip: clearer tap vs hold (no dial rewrite)
old_coach = """        Text(
            "Tap for menu",
            style = NoopType.footnote,
            color = Palette.textSecondary,
        )
        Text(
            "Hold & swipe · Workout · Live · Journal",
            style = NoopType.subhead,
            color = Palette.accent,
        )"""
new_coach = """        Text(
            "Tap + · menu",
            style = NoopType.footnote,
            color = Palette.textSecondary,
        )
        Text(
            "Hold + · dial · Workout · Live · Journal",
            style = NoopType.subhead,
            color = Palette.accent,
        )"""
if old_coach not in text:
    raise SystemExit("coach tip not found")
text = text.replace(old_coach, new_coach, 1)

if text == orig:
    raise SystemExit("no changes made")
path.write_text(text, encoding="utf-8")
print("AppRoot nav/+ glow patches OK")
