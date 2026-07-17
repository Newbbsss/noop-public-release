package com.noop.ui

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
                    // Soft underlap curve: never slam to full Black at the physical bottom —
                    // nav crescents + GlassDiffusionVeil own the last ~20% so the dissolve
                    // meets the bar instead of dying as a hard edge above it.
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.18f to Color.Black.copy(alpha = 0.18f),
                                0.40f to Color.Black.copy(alpha = 0.42f),
                                0.62f to Color.Black.copy(alpha = 0.68f),
                                0.82f to Color.Black.copy(alpha = 0.84f),
                                1.0f to Color.Black.copy(alpha = 0.90f),
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
 *
 * [sinkProgress] (0–1) deepens the veil when Today vessels collapse into the header so
 * scroll content and chrome share one blur story (not a static slab).
 */
@Composable
fun GlassDiffusionVeil(
    modifier: Modifier = Modifier,
    height: Dp,
    fromTop: Boolean,
    dayCycleOn: Boolean = true,
    sinkProgress: Float = 0f,
    /** When true (bottom nav), skip hard full-bleed peak + side punch so Samsung gesture nav
     *  doesn't read as a shadow cut at the screen edge. */
    softenBottomEdge: Boolean = false,
) {
    val sink = sinkProgress.coerceIn(0f, 1f)
    val light = Palette.isLight
    // Light: no white mist boost — prior +0.10 / mist 0.20 washed paper under day-cycle.
    val lightBoost = 0f
    // #398 — day-cycle OFF must stay quieter than ON (raising alphas washed the + glow).
    // Light top header: denser paper peak so Settings/scaffold titles aren't sky-blown.
    val peakBase = when {
        light && fromTop && dayCycleOn -> 0.72f
        light && fromTop -> 0.62f
        dayCycleOn -> 0.58f
        else -> 0.48f
    }
    val aPeak = ((peakBase + lightBoost) * (1f + 0.22f * sink)).coerceAtMost(if (light) 0.92f else 0.86f)
    val aMid = ((if (dayCycleOn) 0.34f else 0.28f) + lightBoost * 0.7f) * (1f + 0.16f * sink)
    val aSoft = ((if (dayCycleOn) 0.14f else 0.10f) + lightBoost * 0.4f) * (1f + 0.12f * sink)
    // Light top veil uses raised paper (not mid canvas) so the header reads as chrome, not mud.
    val base = if (light && fromTop) Palette.surfaceRaised else Palette.surfaceBase
    val mistA = if (light) 0.04f else 0.09f

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
                0.0f to base.copy(alpha = aPeak * 0.85f),
                0.22f to mistStop(aMid * 0.7f),
                0.55f to base.copy(alpha = aSoft * 0.6f),
                1.0f to Color.Transparent,
            )
        } else if (softenBottomEdge) {
            // Soft handoff into system nav — Fold gesture bar must not read as a shadow cut.
            // Stronger dissolve so content melts into the bar (Gilbert: more pronounced diffusion).
            arrayOf(
                0.0f to Color.Transparent,
                0.32f to base.copy(alpha = aSoft * 0.35f),
                0.58f to mistStop(aSoft * 0.55f),
                0.78f to base.copy(alpha = aMid * 0.32f),
                0.92f to mistStop(aPeak * 0.28f),
                1.0f to base.copy(alpha = aPeak * 0.18f),
            )
        } else {
            arrayOf(
                0.0f to Color.Transparent,
                0.14f to base.copy(alpha = aSoft * 0.55f),
                0.32f to mistStop(aSoft * 0.95f),
                0.52f to base.copy(alpha = aMid * 0.85f),
                0.72f to mistStop(aPeak * 0.65f),
                0.88f to base.copy(alpha = aPeak * 0.52f),
                1.0f to base.copy(alpha = aPeak * 0.38f),
            )
        }
        drawRect(brush = Brush.verticalGradient(colorStops = stops))
        // Fold / wide side falloff — soft DstOut only (SHIP #370: 0.90 Black punched holes in + glow).
        // Skip on light paper (no OLED edge bloom to tame) and when softening bottom nav.
        if (w >= 420f && !softenBottomEdge && !light) {
            val edge = (w * 0.09f).coerceIn(40f, 110f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Black.copy(alpha = 0.28f),
                        (edge / w) to Color.Transparent,
                        (1f - edge / w) to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.28f),
                    ),
                ),
                blendMode = BlendMode.DstOut,
            )
        }
    }
}
