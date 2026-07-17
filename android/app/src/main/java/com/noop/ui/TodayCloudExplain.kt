package com.noop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Today floating-cloud explain language (SHIP_IMPROVE_400 finale epic).
 *
 * Workshop → pick:
 * - **A — Ambient-only wash + sheets:** soft clouds behind the list; explains stay ModalBottomSheet /
 *   Dialog. Rejected — Gilbert wants the explanation *inside* the bubble motion.
 * - **B — In-grid tile morph:** Key Metrics tile expands in place. Rejected as primary — 3-col grid
 *   can't hold a full Charge breakdown without fighting LazyColumn layout.
 * - **C — Forward-grow cloud portal (winner):** tap opens a centered lacquer cloud that scales
 *   forward (0.78→1 + slight lift), mist-blurs the Today surface behind, and hosts the full explain
 *   body. Idle Key Metrics tiles stay still (float retired — Gilbert: cards shook). Reduce Motion →
 *   crossfade / static pose.
 *
 * No decorative glow, no nested cards, no invented vitals — Material 3 structure + NOOP tokens.
 */

object TodayCloudLacquer {
    /** Forward-grow settle (ms). Signature moment — still EaseOutQuint, not bounce. */
    const val GROW_MS = 420
    /** Backdrop mist peak alpha (dark theme). */
    const val MIST_PEAK = 0.62f
    /** Backdrop blur radius when expanded (API-friendly Modifier.blur). */
    val BACKDROP_BLUR_DP = 18.dp
    /** Idle Key Metrics float amplitude — kept at 0 (Gilbert: cards shook; float retired). */
    val IDLE_FLOAT_DP = 0.dp
    /** Idle float period base (ms); unused while float is identity. */
    const val IDLE_FLOAT_MS = 5_200
    /** Cloud bubble corner — soft cloud, not 32dp+ AI pill. */
    val BUBBLE_CORNER_DP = 22.dp
    /** Expanded bubble max height fraction of screen. */
    const val BUBBLE_HEIGHT_FRAC = 0.78f
    /** Ambient list-wash intensity (token alpha ≤0.22). */
    const val LIST_WASH_INTENSITY = 0.88f
    /** Top band height as a fraction of the wash canvas — clouds stay under the header only. */
    const val LIST_WASH_TOP_FRAC = 0.34f
}

/**
 * Idle motion for Today Key Metrics tiles.
 *
 * Gilbert: cards were "shaking" — the old XY sine float read as jitter, not atmosphere.
 * Identity forever (seed retained for call-site compatibility). Open-cloud portal motion
 * is unchanged in [TodayCloudExplainOverlay].
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.todayCloudFloat(seed: Int): Modifier = this

/**
 * Soft drifting cloud wash — **top band only**. Fades with [scrollFade] (0 = at top, 1 = gone).
 * Richer multi-lobe puffs; token-alpha only. Reduce Motion = static poses.
 */
@Composable
fun TodayListCloudWash(
    modifier: Modifier = Modifier,
    accent: Color = Palette.effortColor,
    intensity: Float = TodayCloudLacquer.LIST_WASH_INTENSITY,
    scrollFade: Float = 0f,
) {
    val reduced = rememberReduceMotion()
    val inten = intensity.coerceIn(0.4f, 1f)
    val fade = (1f - scrollFade.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    if (fade < 0.02f) return
    val transition = rememberInfiniteTransition(label = "todayListCloudWash")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_400, easing = NoopMotion.EaseOutQuint),
            repeatMode = RepeatMode.Restart,
        ),
        label = "todayListCloudWashT",
    )
    // Top-only cluster — denser multi-lobe clouds (never mid-list).
    val clouds = remember {
        listOf(
            WashCloud(0.12f, 0.18f, 0.00f, 1.28f, detail = 1),
            WashCloud(0.38f, 0.12f, 0.14f, 1.05f, detail = 2),
            WashCloud(0.62f, 0.22f, 0.33f, 1.18f, detail = 1),
            WashCloud(0.86f, 0.14f, 0.48f, 0.92f, detail = 0),
            WashCloud(0.28f, 0.32f, 0.58f, 0.88f, detail = 2),
            WashCloud(0.74f, 0.36f, 0.72f, 0.78f, detail = 0),
            WashCloud(0.50f, 0.08f, 0.88f, 0.70f, detail = 1),
        )
    }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade }
            .semantics { contentDescription = "Floating clouds" },
    ) {
        val w = size.width
        val bandH = size.height * TodayCloudLacquer.LIST_WASH_TOP_FRAC
        clouds.forEach { c ->
            val local = if (reduced) 0.28f else ((t + c.phase) % 1f)
            val drift = if (reduced) 0f else sin(local * Math.PI.toFloat() * 2f) * 12f * density * inten
            val lift = if (reduced) 0f else cos(local * Math.PI.toFloat() * 1.5f) * 6f * density * inten
            val alpha = if (reduced) {
                0.08f * inten * fade
            } else {
                ((0.06f + 0.10f * (0.5f + 0.5f * sin(local * Math.PI.toFloat() * 2f))) * inten * fade)
                    .coerceIn(0.04f, 0.18f)
            }
            drawTodaySoftCloud(
                cx = w * c.nx + drift,
                cy = bandH * c.ny + lift,
                scale = c.scale,
                color = accent.copy(alpha = alpha),
                detail = c.detail,
            )
        }
    }
}

private data class WashCloud(
    val nx: Float,
    val ny: Float,
    val phase: Float,
    val scale: Float,
    /** 0 = simple, 1 = mid, 2 = rich multi-lobe. */
    val detail: Int = 0,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTodaySoftCloud(
    cx: Float,
    cy: Float,
    scale: Float,
    color: Color,
    detail: Int = 0,
) {
    val s = 11f * density * scale
    drawCircle(color = color, radius = s * 1.2f, center = Offset(cx - s * 0.9f, cy))
    drawCircle(color = color, radius = s * 1.55f, center = Offset(cx, cy - s * 0.28f))
    drawCircle(color = color, radius = s * 1.1f, center = Offset(cx + s * 1.0f, cy + s * 0.06f))
    drawCircle(color = color.copy(alpha = color.alpha * 0.5f), radius = s * 1.95f, center = Offset(cx, cy))
    if (detail >= 1) {
        drawCircle(color = color.copy(alpha = color.alpha * 0.72f), radius = s * 0.85f, center = Offset(cx - s * 1.55f, cy + s * 0.22f))
        drawCircle(color = color.copy(alpha = color.alpha * 0.68f), radius = s * 0.95f, center = Offset(cx + s * 1.55f, cy - s * 0.12f))
        drawCircle(color = color.copy(alpha = color.alpha * 0.35f), radius = s * 2.35f, center = Offset(cx + s * 0.2f, cy + s * 0.15f))
    }
    if (detail >= 2) {
        drawCircle(color = color.copy(alpha = color.alpha * 0.55f), radius = s * 0.62f, center = Offset(cx - s * 0.35f, cy - s * 0.85f))
        drawCircle(color = color.copy(alpha = color.alpha * 0.48f), radius = s * 0.72f, center = Offset(cx + s * 0.55f, cy + s * 0.72f))
        drawCircle(color = color.copy(alpha = color.alpha * 0.28f), radius = s * 2.7f, center = Offset(cx - s * 0.4f, cy))
    }
}

/**
 * Progress (0–1) for the open cloud portal. Parent applies [Modifier.todayCloudBackdropBlur]
 * to the Today surface so the list literally softens behind the bubble.
 */
@Composable
fun rememberTodayCloudProgress(visible: Boolean): Float {
    val reduced = rememberReduceMotion()
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduced) {
            tween(0)
        } else {
            tween(TodayCloudLacquer.GROW_MS, easing = NoopMotion.EaseOutQuint)
        },
        label = "todayCloudGrow",
    )
    return progress
}

/** Softens the Today surface behind an open cloud (Reduce Motion → mist alpha only via parent). */
fun Modifier.todayCloudBackdropBlur(progress: Float): Modifier {
    val p = progress.coerceIn(0f, 1f)
    if (p < 0.02f) return this
    return this
        .graphicsLayer(
            scaleX = 1f - 0.018f * p,
            scaleY = 1f - 0.018f * p,
            alpha = 1f - 0.08f * p,
        )
        .blur(TodayCloudLacquer.BACKDROP_BLUR_DP * p)
}

/**
 * Forward-grow cloud portal over Today. Hosts a full explanation inside the bubble.
 * Parent should blur the Today surface with [Modifier.todayCloudBackdropBlur] using the same
 * [progress] from [rememberTodayCloudProgress].
 */
@Composable
fun TodayCloudExplainOverlay(
    visible: Boolean,
    title: String,
    tint: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = -1f,
    valueLine: String? = null,
    valueColor: Color = tint,
    paragraphs: List<String> = emptyList(),
    calloutOverline: String? = null,
    calloutBody: String? = null,
    calloutFoot: String? = null,
    whoopNote: String? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    /** Rich body (e.g. Charge drivers) — flat sections, no nested cards. */
    richBody: (@Composable () -> Unit)? = null,
) {
    // Always call remember* (Compose rules) — ignore when parent supplies [progress].
    val rememberedProgress = rememberTodayCloudProgress(visible)
    val internalProgress = if (progress < 0f) rememberedProgress else progress
    if (internalProgress < 0.001f && !visible) return

    BackHandler(enabled = visible) { onDismiss() }

    val density = LocalDensity.current
    val liftPx = with(density) { 28.dp.toPx() }
    val scale = 0.78f + 0.22f * internalProgress
    val mist = TodayCloudLacquer.MIST_PEAK * internalProgress * (if (Palette.isLight) 0.72f else 1f)
    val bubbleAlpha = internalProgress

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "$title explanation cloud" },
    ) {
        // Mist scrim — the real backdrop blur lives on the Today surface (parent).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.surfaceBase.copy(alpha = mist * 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tint.copy(alpha = mist * 0.10f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            val maxH = maxHeight * TodayCloudLacquer.BUBBLE_HEIGHT_FRAC
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxH)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationY = (1f - internalProgress) * liftPx,
                        alpha = bubbleAlpha,
                    )
                    .clip(RoundedCornerShape(TodayCloudLacquer.BUBBLE_CORNER_DP))
                    .frostedCardSurface(
                        tint = tint,
                        cornerRadius = TodayCloudLacquer.BUBBLE_CORNER_DP,
                        washStrength = 1.15f,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* consume — don't dismiss when tapping body */ },
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = NoopType.headline,
                        color = Palette.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(Metrics.iconButton),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Palette.textSecondary,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxH - 56.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (valueLine != null) {
                        Text(
                            valueLine,
                            style = NoopType.number(28f),
                            color = valueColor,
                        )
                    }
                    paragraphs.forEach { line ->
                        Text(line, style = NoopType.body, color = Palette.textSecondary)
                    }
                    if (calloutOverline != null || calloutBody != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Palette.surfaceInset.copy(alpha = 0.72f))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (calloutOverline != null) {
                                Text(
                                    calloutOverline,
                                    style = NoopType.overline,
                                    color = Palette.textTertiary,
                                )
                            }
                            if (calloutBody != null) {
                                Text(
                                    calloutBody,
                                    style = NoopType.subhead,
                                    color = Palette.textPrimary,
                                )
                            }
                            if (calloutFoot != null) {
                                Text(
                                    calloutFoot,
                                    style = NoopType.footnote,
                                    color = tint,
                                )
                            }
                        }
                    }
                    if (whoopNote != null) {
                        Text(
                            whoopNote,
                            style = NoopType.footnote,
                            color = Color(0xFF5B9DFF),
                        )
                    }
                    richBody?.invoke()
                    if (secondaryLabel != null && onSecondary != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    onClickLabel = secondaryLabel,
                                    onClick = onSecondary,
                                )
                                .background(Palette.surfaceInset.copy(alpha = 0.85f))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                secondaryLabel,
                                style = NoopType.subhead,
                                color = Palette.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Palette.textTertiary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
