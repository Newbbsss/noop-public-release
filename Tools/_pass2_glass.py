from pathlib import Path

# === Pass 2: SoftEdgeFade — deeper glass + earlier Fold edge falloff ===
fade = Path(r"C:\Users\Gilbert\Documents\Ai app store\noop-v8.4.0-src\android\app\src\main\java\com\noop\ui\SoftEdgeFade.kt")
fade.write_text(r'''package com.noop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft-dissolves this composable's own pixels at the top and/or bottom edge.
 *
 * Offscreen layer + [BlendMode.DstOut] so scrolling content actually fades
 * (composites) instead of hiding under an opaque black slab.
 */
fun Modifier.softFadeEdges(
    topFade: Dp = 0.dp,
    bottomFade: Dp = 0.dp,
): Modifier {
    if (topFade <= 0.dp && bottomFade <= 0.dp) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (topFade > 0.dp) {
                val h = topFade.toPx().coerceAtMost(size.height)
                if (h > 0.5f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black,
                                0.35f to Color.Black.copy(alpha = 0.62f),
                                0.70f to Color.Black.copy(alpha = 0.22f),
                                1.0f to Color.Transparent,
                            ),
                            startY = 0f,
                            endY = h,
                        ),
                        size = Size(size.width, h),
                        blendMode = BlendMode.DstOut,
                    )
                }
            }
            if (bottomFade > 0.dp) {
                val h = bottomFade.toPx().coerceAtMost(size.height)
                if (h > 0.5f) {
                    val top = size.height - h
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.28f to Color.Black.copy(alpha = 0.22f),
                                0.55f to Color.Black.copy(alpha = 0.55f),
                                0.82f to Color.Black.copy(alpha = 0.88f),
                                1.0f to Color.Black,
                            ),
                            startY = top,
                            endY = size.height,
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, h),
                        blendMode = BlendMode.DstOut,
                    )
                }
            }
        }
}

/**
 * Glass diffusion wash with horizontal edge falloff (Fold / wide ≥420dp).
 * Soft multi-stop alpha + mist tint — sky/content still reads through.
 */
@Composable
fun GlassDiffusionVeil(
    modifier: Modifier = Modifier,
    height: Dp,
    fromTop: Boolean,
    dayCycleOn: Boolean = true,
) {
    val lightBoost = if (Palette.isLight) 0.10f else 0f
    val aPeak = ((if (dayCycleOn) 0.48f else 0.60f) + lightBoost).coerceAtMost(0.72f)
    val aMid = ((if (dayCycleOn) 0.26f else 0.38f) + lightBoost * 0.7f)
    val aSoft = ((if (dayCycleOn) 0.10f else 0.16f) + lightBoost * 0.4f)
    val base = Palette.surfaceBase
    val mistA = if (Palette.isLight) 0.20f else 0.09f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val w = size.width
        fun mistStop(alpha: Float): Color {
            val m = mistA
            return Color(
                red = 1f * m + base.red * (1f - m),
                green = 1f * m + base.green * (1f - m),
                blue = 1f * m + base.blue * (1f - m),
                alpha = alpha,
            )
        }
        val stops = if (fromTop) {
            arrayOf(
                0.0f to base.copy(alpha = aPeak),
                0.18f to mistStop(aMid + 0.06f),
                0.42f to mistStop(aMid * 0.85f),
                0.68f to base.copy(alpha = aSoft),
                1.0f to Color.Transparent,
            )
        } else {
            arrayOf(
                0.0f to Color.Transparent,
                0.20f to base.copy(alpha = aSoft * 0.7f),
                0.42f to mistStop(aSoft + 0.08f),
                0.62f to base.copy(alpha = aMid),
                0.84f to mistStop(aPeak * 0.88f),
                1.0f to base.copy(alpha = aPeak),
            )
        }
        drawRect(brush = Brush.verticalGradient(colorStops = stops))
        // Fold / wide: dissolve veil at left/right so bar ends aren't a full-bleed slab.
        if (w >= 420f) {
            val edge = (w * 0.09f).coerceIn(40f, 110f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Black.copy(alpha = 0.90f),
                        (edge / w) to Color.Transparent,
                        (1f - edge / w) to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.90f),
                    ),
                ),
                blendMode = BlendMode.DstOut,
            )
        }
    }
}
''', encoding='utf-8')
print('pass2 SoftEdgeFade OK')

# === Pass 2: Today — full-bleed top veil; deeper softFade ===
today = Path(r"C:\Users\Gilbert\Documents\Ai app store\noop-v8.4.0-src\android\app\src\main\java\com\noop\ui\TodayScreen.kt")
t = today.read_text(encoding='utf-8')

old_inline_top = """                // Top glass diffusion under status/header (soft multi-stop — not a hard cut).
                GlassDiffusionVeil(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-10).dp),
                    height = 88.dp,
                    fromTop = true,
                    dayCycleOn = showDayCycleBackground,
                )
"""
if old_inline_top not in t:
    raise SystemExit('Today inline top veil missing')
t = t.replace(old_inline_top, "", 1)

old_fade = ".softFadeEdges(topFade = 18.dp, bottomFade = underBar + 64.dp)"
new_fade = ".softFadeEdges(topFade = 28.dp, bottomFade = underBar + 88.dp)"
if old_fade not in t:
    raise SystemExit('softFadeEdges call missing')
t = t.replace(old_fade, new_fade, 1)

old_bottom = """        // Bottom glass wash over the nav/+ bleed zone — pairs with LazyColumn softFadeEdges.
        GlassDiffusionVeil(
            modifier = Modifier.align(Alignment.BottomCenter),
            height = LocalUnderBarInset.current + 80.dp,
            fromTop = false,
            dayCycleOn = showDayCycleBackground,
        )
    } // sky Box"""
new_ends = """        // Full-bleed top + bottom glass (status cluster + nav/+ bleed). Pair with softFadeEdges.
        GlassDiffusionVeil(
            modifier = Modifier.align(Alignment.TopCenter),
            height = 104.dp,
            fromTop = true,
            dayCycleOn = showDayCycleBackground,
        )
        GlassDiffusionVeil(
            modifier = Modifier.align(Alignment.BottomCenter),
            height = LocalUnderBarInset.current + 96.dp,
            fromTop = false,
            dayCycleOn = showDayCycleBackground,
        )
    } // sky Box"""
if old_bottom not in t:
    raise SystemExit('Today bottom veil missing')
t = t.replace(old_bottom, new_ends, 1)
today.write_text(t, encoding='utf-8')
print('pass2 Today OK')
