package com.noop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sparse petal / leaf drift for nature theme packs — calm, not party confetti.
 */
@Composable
fun NatureMotifOverlay(modifier: Modifier = Modifier) {
    if (!ThemePackPrefs.current.natureMotif) return
    val reduced = rememberReduceMotion()
    val phase = if (reduced) {
        0.35f
    } else {
        val inf = rememberInfiniteTransition(label = "natureMotif")
        val p by inf.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(14_000, easing = FastOutSlowInEasing), RepeatMode.Restart),
            label = "naturePhase",
        )
        p
    }
    val petal = Palette.accent.copy(alpha = 0.14f)
    val leaf = Palette.chargeColor.copy(alpha = 0.10f)
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        fun petalAt(nx: Float, ny: Float, rot: Float, scale: Float, color: Color) {
            val cx = nx * w
            val cy = (ny + phase * 0.08f) % 1.1f * h
            rotate(rot + phase * 40f, Offset(cx, cy)) {
                val path = Path().apply {
                    moveTo(cx, cy - 10f * scale)
                    cubicTo(cx + 8f * scale, cy - 4f * scale, cx + 8f * scale, cy + 6f * scale, cx, cy + 10f * scale)
                    cubicTo(cx - 8f * scale, cy + 6f * scale, cx - 8f * scale, cy - 4f * scale, cx, cy - 10f * scale)
                    close()
                }
                drawPath(path, color)
            }
        }
        fun leafAt(nx: Float, ny: Float, rot: Float, scale: Float) {
            val cx = nx * w
            val cy = ((ny + 1f - phase * 0.06f) % 1.1f) * h
            rotate(rot - phase * 25f, Offset(cx, cy)) {
                val path = Path().apply {
                    moveTo(cx, cy - 12f * scale)
                    quadraticBezierTo(cx + 10f * scale, cy, cx, cy + 12f * scale)
                    quadraticBezierTo(cx - 10f * scale, cy, cx, cy - 12f * scale)
                    close()
                }
                drawPath(path, leaf)
            }
        }
        petalAt(0.12f, 0.18f, 12f, 1.1f, petal)
        petalAt(0.78f, 0.22f, -18f, 0.9f, petal)
        petalAt(0.55f, 0.08f, 40f, 0.7f, petal.copy(alpha = 0.10f))
        leafAt(0.22f, 0.55f, 25f, 1.0f)
        leafAt(0.88f, 0.48f, -30f, 0.85f)
        leafAt(0.40f, 0.72f, 8f, 0.75f)
        // Soft tree hint — distant canopy arcs
        val canopy = Palette.accent.copy(alpha = 0.05f)
        drawCircle(canopy, radius = w * 0.28f, center = Offset(w * 0.08f, -h * 0.05f))
        drawCircle(canopy, radius = w * 0.22f, center = Offset(w * 0.92f, -h * 0.02f))
        // Ignore unused trig imports keep for future wind sway
        @Suppress("UNUSED_EXPRESSION")
        sin(phase * 6.28f) + cos(phase * 6.28f)
    }
}
