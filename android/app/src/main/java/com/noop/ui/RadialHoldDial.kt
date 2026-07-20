package com.noop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hold-+ radial dial field — soft aura diffusion + dense motes (parity with + idle glow richness).
 *
 * Color theory: cool complementary mist at the rim so the lacquer-coin **gold** aura stays the hero;
 * champagne / soft rose-gold along spokes (analogous warmth) for selection bloom. Animation runs
 * only while [active] and Reduce Motion is off. Mote count is Fold-safe (~70) but denser than the
 * old sparse 42 so hold-bloom doesn't look thin next to the intense idle + glow (#393).
 */
@Composable
fun RadialHoldDialField(
    bloom: Float,
    selectedIndex: Int?,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 168.dp,
) {
    // Analogous warm core → complementary cool rim (splits from + gold, no clash).
    val champagne = Color(0xFFE8DCC8)
    val roseGold = Color(0xFFD4B896)
    val mist = Color(0xFFB8C4D4)
    val aurora = Color(0xFF9EB0C8)
    val seeds = remember { buildDialMotes(RADIAL_HOLD_DIAL_MOTE_COUNT) }
    var phase by remember { mutableFloatStateOf(0f) }
    val animate = !reducedMotion && bloom > 0.02f
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last).coerceAtMost(33_000_000L)) / 1_000_000_000f
                    phase = (phase + dt * 0.52f) % 100f
                }
                last = now
            }
        }
    }

    val alpha = (bloom * 0.96f).coerceIn(0f, 1f)
    Canvas(
        modifier
            .size(diameter)
            .graphicsLayer { this.alpha = alpha },
    ) {
        val r = size.minDimension / 2f
        val c = center
        val sel = selectedIndex
        val p = if (reducedMotion) 0f else phase

        // Layered aura hub — denser champagne core so hold bloom rivals idle + intensity.
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to champagne.copy(alpha = 0.34f),
                    0.18f to roseGold.copy(alpha = 0.22f),
                    0.42f to mist.copy(alpha = 0.14f),
                    0.68f to aurora.copy(alpha = 0.08f),
                    1.0f to Color.Transparent,
                ),
                center = c,
                radius = r * 1.10f,
            ),
            radius = r * 1.10f,
            center = c,
        )

        // Soft breathing corona (static under Reduce Motion).
        val corona = r * (0.88f + if (reducedMotion) 0f else 0.045f * sin(p * 1.6f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    champagne.copy(alpha = 0.12f),
                    mist.copy(alpha = 0.30f),
                    aurora.copy(alpha = 0.12f),
                    Color.Transparent,
                ),
                center = c,
                radius = corona,
            ),
            radius = corona,
            center = c,
            style = Stroke(width = 14.dp.toPx()),
        )
        drawCircle(
            color = champagne.copy(alpha = 0.38f),
            radius = r * 0.90f,
            center = c,
            style = Stroke(width = 1.25.dp.toPx()),
        )

        // Five tips match AppRoot hold-+ pentagon: top · UL · UR · LL · LR.
        val spokeTips = listOf(
            Offset(0f, -r * 0.72f),
            Offset(-r * 0.62f, -r * 0.18f),
            Offset(r * 0.62f, -r * 0.18f),
            Offset(-r * 0.50f, r * 0.55f),
            Offset(r * 0.50f, r * 0.55f),
        )

        // Soft sector wedges (selection bloom) — lighting, not pie-chart chrome.
        spokeTips.forEachIndexed { i, tip ->
            val lit = if (sel == i) 1f else 0.36f
            val mid = Offset(c.x + tip.x * 0.55f, c.y + tip.y * 0.55f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        champagne.copy(alpha = 0.26f * lit),
                        roseGold.copy(alpha = 0.12f * lit),
                        mist.copy(alpha = 0.07f * lit),
                        Color.Transparent,
                    ),
                    center = mid,
                    radius = r * 0.46f,
                ),
                radius = r * 0.46f,
                center = mid,
            )
        }

        // Diffuse light beams along spokes (gradient line, not a hard spoke).
        spokeTips.forEachIndexed { i, tip ->
            val lit = if (sel == i) 0.62f else 0.24f
            val end = Offset(c.x + tip.x, c.y + tip.y)
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        champagne.copy(alpha = lit * 0.14f),
                        champagne.copy(alpha = lit),
                        mist.copy(alpha = lit * 0.44f),
                        Color.Transparent,
                    ),
                    start = c,
                    end = end,
                ),
                start = c,
                end = end,
                strokeWidth = if (sel == i) 3.6.dp.toPx() else 2.1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Dense motes — orbit/pulse along spokes + rim sparks (#393 vs idle + glow richness).
        seeds.forEach { mote ->
            val tip = spokeTips[mote.spoke]
            val breathe = if (reducedMotion) 0.55f else (0.5f + 0.5f * sin((p + mote.phase) * 2.05f))
            val along = mote.along + if (reducedMotion) 0f else 0.04f * sin((p * 1.25f) + mote.phase)
            val px = c.x + tip.x * along + (-tip.y) * mote.lateral
            val py = c.y + tip.y * along + tip.x * mote.lateral
            val selectedBoost = if (sel == mote.spoke) 1.42f else 1f
            val col = when {
                mote.rim -> aurora
                mote.cool -> mist
                else -> champagne
            }
            val a = (0.22f + 0.58f * breathe) * selectedBoost * bloom * if (mote.rim) 0.82f else 1f
            drawCircle(
                color = col.copy(alpha = a.coerceIn(0f, 0.95f)),
                radius = mote.size.dp.toPx() * (0.88f + 0.30f * breathe) * selectedBoost *
                    if (mote.rim) 0.9f else 1f,
                center = Offset(px, py),
            )
        }

        // Soft node sockets at spoke tips (under the Compose icon nodes).
        spokeTips.forEachIndexed { i, tip ->
            val socket = Offset(c.x + tip.x * 1.02f, c.y + tip.y * 1.02f)
            val lit = if (sel == i) 1f else 0.46f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        champagne.copy(alpha = 0.48f * lit),
                        roseGold.copy(alpha = 0.18f * lit),
                        mist.copy(alpha = 0.09f * lit),
                        Color.Transparent,
                    ),
                    center = socket,
                    radius = 38.dp.toPx(),
                ),
                radius = 38.dp.toPx(),
                center = socket,
            )
        }

        // Aurora rim sparks — denser ring than the old 3-spark set.
        if (!reducedMotion) {
            for (i in 0 until 6) {
                val ang = -Math.PI.toFloat() / 2f + i * (2f * Math.PI.toFloat() / 6f) + p * 0.18f
                val spark = Offset(
                    c.x + cos(ang) * r * 0.94f,
                    c.y + sin(ang) * r * 0.94f,
                )
                val sparkA = 0.20f + 0.26f * (0.5f + 0.5f * sin(p * 2.5f + i))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            aurora.copy(alpha = sparkA),
                            champagne.copy(alpha = sparkA * 0.35f),
                            Color.Transparent,
                        ),
                        center = spark,
                        radius = 12.dp.toPx(),
                    ),
                    radius = 12.dp.toPx(),
                    center = spark,
                )
            }
        }

        // Tiny centre void so the + coin stays the gold hero underneath.
        val voidCore = if (Palette.isLight) {
            Palette.surfaceBase.copy(alpha = 0.38f)
        } else {
            Color.Black.copy(alpha = 0.38f)
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    voidCore,
                    Color.Transparent,
                ),
                center = c,
                radius = 28.dp.toPx(),
            ),
            radius = 28.dp.toPx(),
            center = c,
        )
    }
}

/** Fold-safe mote budget for [RadialHoldDialField] — denser spokes + softer rim sparks. */
internal const val RADIAL_HOLD_DIAL_MOTE_COUNT = 80

internal fun buildDialMotes(count: Int): List<DialMote> =
    List(count.coerceIn(24, 96)) { i ->
        val spoke = i % 5
        val t = (i * 0.113f) % 1f
        val along = 0.10f + t * 0.86f
        val jitter = ((i * 17) % 11) / 11f - 0.5f
        DialMote(
            spoke = spoke,
            along = along,
            lateral = jitter * 0.092f,
            size = 0.85f + ((i * 3) % 7) * 0.34f,
            phase = i * 0.37f,
            cool = i % 3 != 0,
            rim = i % 5 == 0,
        )
    }

internal data class DialMote(
    val spoke: Int,
    val along: Float,
    val lateral: Float,
    val size: Float,
    val phase: Float,
    val cool: Boolean,
    val rim: Boolean = false,
)

/** Quiet cue under the dial — tap vs hold grammar, not a second menu. */
fun radialHoldDialCaption(hasSelection: Boolean): String =
    if (hasSelection) "Release to open"
    else "Swipe to a shortcut · release centre to cancel"
