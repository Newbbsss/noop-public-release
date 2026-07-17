from pathlib import Path

path = Path(r"C:\Users\Gilbert\Documents\Ai app store\noop-v8.4.0-src\android\app\src\main\java\com\noop\ui\TodayScreen.kt")
text = path.read_text(encoding="utf-8")

old_top = """                // Soft top fade under status/header so Today title cluster doesn't clip hard into chrome.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .offset(y = (-8).dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Palette.surfaceBase.copy(alpha = 0.72f),
                                    Palette.surfaceBase.copy(alpha = 0f),
                                ),
                            ),
                        ),
                )"""

new_top = """                // Top glass diffusion under status/header (soft multi-stop — not a hard cut).
                GlassDiffusionVeil(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-10).dp),
                    height = 88.dp,
                    fromTop = true,
                    dayCycleOn = showDayCycleBackground,
                )"""

if old_top not in text:
    raise SystemExit("top fade block not found")
text = text.replace(old_top, new_top, 1)

old_lazy = """            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Fable #242: after vessels (traversalIndex 0).
                    .semantics(mergeDescendants = false) {
                        isTraversalGroup = true
                        traversalIndex = 1f
                    },"""

new_lazy = """            val underBar = LocalUnderBarInset.current
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Real composite fade: content pixels dissolve under header + nav (not a slab).
                    .softFadeEdges(topFade = 18.dp, bottomFade = underBar + 64.dp)
                    // Fable #242: after vessels (traversalIndex 0).
                    .semantics(mergeDescendants = false) {
                        isTraversalGroup = true
                        traversalIndex = 1f
                    },"""

if old_lazy not in text:
    raise SystemExit("lazy column block not found")
text = text.replace(old_lazy, new_lazy, 1)

old_close = """            } // LazyColumn
        } // Column (sticky + scroll)
    } // sky Box"""

new_close = """            } // LazyColumn
        } // Column (sticky + scroll)
        // Bottom glass wash over the nav/+ bleed zone — pairs with LazyColumn softFadeEdges.
        GlassDiffusionVeil(
            modifier = Modifier.align(Alignment.BottomCenter),
            height = LocalUnderBarInset.current + 80.dp,
            fromTop = false,
            dayCycleOn = showDayCycleBackground,
        )
    } // sky Box"""

if old_close not in text:
    raise SystemExit("close block not found")
text = text.replace(old_close, new_close, 1)

path.write_text(text, encoding="utf-8")
print("TodayScreen dual-end glass OK")
