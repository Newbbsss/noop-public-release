package com.noop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Snappy press button — scale settle only (no overshoot bounce). Fully opaque flat fill
 * (no translucent wash / radial orb). Honours Reduce Motion (instant dip, no spring).
 */
@Composable
fun WetBounceButton(
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = Palette.accent,
    rounded: Dp = 18.dp,
    onClick: () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val reduced = rememberReduceMotion()
    val shape = RoundedCornerShape(rounded)
    val fill = Palette.surfaceRaised
    Box(
        modifier = modifier
            .scale(scale.value)
            .clip(shape)
            .background(fill)
            .border(1.5.dp, tint.copy(alpha = 0.85f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                scope.launch {
                    if (reduced) {
                        scale.snapTo(0.97f)
                        scale.snapTo(1f)
                    } else {
                        scale.snapTo(0.96f)
                        scale.animateTo(1f, NoopMotion.press())
                    }
                }
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = NoopType.headline, color = Palette.textPrimary)
    }
}

@Composable
fun WetBounceIconCircle(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Palette.accent,
    onClick: () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val reduced = rememberReduceMotion()
    Box(
        modifier = modifier
            .scale(scale.value)
            .clip(CircleShape)
            .background(Palette.surfaceRaised)
            .border(1.5.dp, tint.copy(alpha = 0.75f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                scope.launch {
                    if (reduced) {
                        scale.snapTo(0.96f)
                        scale.snapTo(1f)
                    } else {
                        scale.snapTo(0.94f)
                        scale.animateTo(1f, NoopMotion.press())
                    }
                }
                onClick()
            }
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
