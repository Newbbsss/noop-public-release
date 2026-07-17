from pathlib import Path

path = Path(r"C:\Users\Gilbert\Documents\Ai app store\noop-v8.4.0-src\android\app\src\main\java\com\noop\ui\AppRoot.kt")
text = path.read_text(encoding="utf-8")

# 1) Full-bleed veil: restructure outer Box so veil escapes horizontal padding
old_box = """    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp)
            .padding(top = 0.dp, bottom = Metrics.space8),
        contentAlignment = Alignment.Center,
    ) {
            // Bottom glass diffusion under nav/+ — soft multi-stop with Fold edge falloff
            // (GlassDiffusionVeil), not a hard black slab. Pairs with Today softFadeEdges.
            GlassDiffusionVeil(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                height = 108.dp,
                fromTop = false,
                dayCycleOn = dayCycleOn,
            )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Tabletop / fold-unfold: allow a wider island row before capping (Fable Today #32).
                .widthIn(max = 640.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {"""

new_box = """    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 0.dp, bottom = Metrics.space8),
        contentAlignment = Alignment.Center,
    ) {
        // Full-bleed bottom glass (outside island pad) so Fold ends soften correctly.
        GlassDiffusionVeil(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            height = 120.dp,
            fromTop = false,
            dayCycleOn = dayCycleOn,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                // Tabletop / fold-unfold: allow a wider island row before capping (Fable Today #32).
                .widthIn(max = 640.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {"""

if old_box not in text:
    raise SystemExit("bar box structure missing")
text = text.replace(old_box, new_box, 1)
print("ok full-bleed veil")

# 2) Island soft ambient shadows via Modifier.shadow (both ends) — keep elevation + add soft black
old_left = """                Surface(
                    shape = leftCrescent,
                    color = islandColor,
                    tonalElevation = 0.dp,
                    shadowElevation = islandShadow,
                    modifier = Modifier
                        .weight(leftWeight)
                        // Fable 200 #10: frosted/non-frosted islands always 0.5dp hairline.
                        .border(0.5.dp, islandBorder, leftCrescent),
                )"""
new_left = """                Surface(
                    shape = leftCrescent,
                    color = islandColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .weight(leftWeight)
                        // Soft lift on BOTH ends (Material elevation alone was invisible when frosted).
                        .shadow(
                            elevation = islandShadow,
                            shape = leftCrescent,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.55f),
                            spotColor = Color.Black.copy(alpha = 0.40f),
                        )
                        .border(0.5.dp, islandBorder, leftCrescent),
                )"""
if old_left not in text:
    raise SystemExit("left Surface missing")
text = text.replace(old_left, new_left, 1)
print("ok left shadow")

old_right = """                Surface(
                    shape = rightCrescent,
                    color = islandColor,
                    tonalElevation = 0.dp,
                    shadowElevation = islandShadow,
                    modifier = Modifier
                        .weight(rightWeight)
                        .border(0.5.dp, islandBorder, rightCrescent),
                )"""
new_right = """                Surface(
                    shape = rightCrescent,
                    color = islandColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .weight(rightWeight)
                        .shadow(
                            elevation = islandShadow,
                            shape = rightCrescent,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.55f),
                            spotColor = Color.Black.copy(alpha = 0.40f),
                        )
                        .border(0.5.dp, islandBorder, rightCrescent),
                )"""
if old_right not in text:
    raise SystemExit("right Surface missing")
text = text.replace(old_right, new_right, 1)
print("ok right shadow")

# 3) Hotter + glow — widen outer bloom; add spark core (still no radial rewrite)
old_aura = """        // Unmistakable gold glow (Gilbert QoL): bright core + wide soft halo. + is the one glow exception.
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
        }"""

new_aura = """        // Unmistakable gold glow (Gilbert QoL): wide halo + hot core + spark. + is the one glow exception.
        // Radial dial visuals are owned by the particle-lighting sibling — do not rewrite that Popup here.
        val auraAlpha = when {
            reduced -> 0.78f + 0.16f * holdBloom
            holding || pressed -> 1f
            else -> 0.88f + 0.12f * idleBreath
        }
        Canvas(
            Modifier
                .size(200.dp)
                .graphicsLayer { alpha = auraAlpha * 0.90f },
        ) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to gold.copy(alpha = 0.62f),
                        0.30f to gold.copy(alpha = 0.34f),
                        0.55f to gold.copy(alpha = 0.16f),
                        0.78f to gold.copy(alpha = 0.06f),
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
                .size(108.dp)
                .graphicsLayer { alpha = auraAlpha },
        ) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to gold.copy(alpha = 0.98f),
                        0.35f to gold.copy(alpha = 0.62f),
                        0.70f to gold.copy(alpha = 0.22f),
                        1.0f to Color.Transparent,
                    ),
                    center = center,
                    radius = r,
                ),
                radius = r,
            )
        }
        // Tiny champagne spark so the glow reads even against bright day-cycle sky.
        Canvas(
            Modifier
                .size(42.dp)
                .graphicsLayer { alpha = auraAlpha * 0.95f },
        ) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFF2C8).copy(alpha = 0.95f),
                        gold.copy(alpha = 0.35f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = r,
                ),
                radius = r,
            )
        }"""

if old_aura not in text:
    raise SystemExit("aura block missing")
text = text.replace(old_aura, new_aura, 1)
print("ok hotter glow")

# 4) Fat coin: bump visual slightly; stronger elev
old_sizes = """    val buttonVisual = 64.dp
    val hitBox = 76.dp"""
new_sizes = """    val buttonVisual = 66.dp
    val hitBox = 80.dp"""
if old_sizes not in text:
    raise SystemExit("sizes missing")
text = text.replace(old_sizes, new_sizes, 1)

# plus gutter: 56 -> 60 so 200dp glow has room optically between crescents
text = text.replace("val plusGutter = 56.dp", "val plusGutter = 60.dp", 1)
print("ok sizes/gutter")

# 5) Kill redundant showQuickActions sheet (Updates now on + tap sheet)
# Soft approach: leave var but make onQuickActions open the same Updates via plus path —
# better: remove sheet and wire onQuickActions to showPlusSheet if still passed.
# Today still passes onQuickActions but QuickActionDisc unused — null the dead sheet.

import re
# Remove showQuickActions ModalBottomSheet block
m = re.search(
    r"\n        // Quick-actions sheet, opened by the raised gold centre FAB\..*?\n        \}\n\n        // The Updates inbox",
    text,
    flags=re.S,
)
if not m:
    # try alternate
    m = re.search(
        r"\n        if \(showQuickActions\) \{.*?\n        \}\n\n        // The Updates inbox",
        text,
        flags=re.S,
    )
if not m:
    print("WARN: showQuickActions sheet not removed")
else:
    text = text[: m.start()] + "\n\n        // The Updates inbox" + text[m.end() :]
    # fix double "The Updates inbox" if any
    text = text.replace("// The Updates inbox\n        // The Updates inbox", "// The Updates inbox", 1)
    print("ok removed dead quickActions sheet")

# Point Today onQuickActions at Updates (or no-op) — use showUpdatesInbox
text = text.replace(
    "onQuickActions = { showQuickActions = true },",
    "onQuickActions = { showUpdatesInbox = true },",
    1,
)
# Remove unused var if present
if "var showQuickActions by remember" in text and "showQuickActions" not in text.replace("var showQuickActions by remember { mutableStateOf(false) }", ""):
    text = text.replace("    var showQuickActions by remember { mutableStateOf(false) }\n", "", 1)
    print("ok removed showQuickActions var")
else:
    # check remaining refs
    refs = [i for i,l in enumerate(text.splitlines()) if "showQuickActions" in l]
    print(f"showQuickActions refs lines: {refs}")
    if len(refs) == 1 and "var showQuickActions" in text.splitlines()[refs[0]]:
        text = text.replace("    var showQuickActions by remember { mutableStateOf(false) }\n", "", 1)
        print("ok removed orphan var")

path.write_text(text, encoding="utf-8")
print("pass2 AppRoot OK")
