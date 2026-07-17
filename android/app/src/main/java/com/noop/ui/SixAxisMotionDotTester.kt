package com.noop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.ble.WhoopBleClient
import com.noop.motion.SixAxisMotionLabels
import com.noop.motion.SixAxisSample
import com.noop.motion.SixAxisSourceKind
import kotlin.math.roundToInt

/**
 * Settings / Test Centre 6-axis motion tester: a moving dot driven by **band IMU only**.
 *
 * Preference: strap live type-51 when present → else strap offload 1244-B (labeled **not live**).
 * While live strap IMU is gated (cmd-106 ACK ≠ activation; types 51–56), show an empty / waiting
 * band state — never fall back to phone accel/gyro.
 */
@Composable
fun SixAxisMotionDotTester(
    ble: WhoopBleClient,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val strap by ble.latestStrapImu.collectAsStateWithLifecycle()
    val driver = remember(strap) { SixAxisMotionLabels.preferStrapDriver(strap) }
    val live = SixAxisMotionLabels.isLiveDriver(driver)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!compact) {
            Text(
                "Band IMU only — move the strap. Live type-51 drives the dot when it streams; " +
                    "historical offload is shown as not-live. Phone sensors are not used.",
                style = NoopType.caption,
                color = Palette.textTertiary,
            )
        }

        SourceBadge(driver = driver)

        MotionPad(sample = driver, live = live)

        AxisReadout(sample = driver)

        Text(
            "Honesty: TOGGLE_IMU_MODE (cmd 106) can ACK without streaming. " +
                "R22 flag ACK ≠ live types 51–56. Historical 1244-B offload is real 6-axis, not live. " +
                "No phone fallback.",
            style = NoopType.caption,
            color = Palette.textTertiary,
        )
    }
}

@Composable
private fun SourceBadge(driver: SixAxisSample?) {
    val title = driver?.let { SixAxisMotionLabels.sourceTitle(it.source) }
        ?: SixAxisMotionLabels.WAITING_TITLE
    val detail = driver?.let { SixAxisMotionLabels.sourceDetail(it.source) }
        ?: SixAxisMotionLabels.WAITING_DETAIL
    val tint = when (driver?.source) {
        SixAxisSourceKind.STRAP_LIVE -> Palette.statusPositive
        SixAxisSourceKind.STRAP_OFFLOAD -> Palette.statusWarning
        null -> Palette.textTertiary
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(tint, CircleShape),
            )
            Text(title, style = NoopType.subhead, color = Palette.textPrimary)
        }
        Text(detail, style = NoopType.caption, color = Palette.textTertiary)
    }
}

@Composable
private fun MotionPad(sample: SixAxisSample?, live: Boolean) {
    val density = LocalDensity.current
    val reduced = rememberReduceMotion()
    val waiting = sample == null
    // Map tilt (accel in g) into a unit square. Flat rest → center; tilt moves the dot.
    val targetX = sample?.let { (it.ax / 1.2f).coerceIn(-1f, 1f) } ?: 0f
    val targetY = sample?.let { (-it.ay / 1.2f).coerceIn(-1f, 1f) } ?: 0f
    val animMs = when {
        reduced || waiting || !live -> 0
        else -> 90
    }
    val smoothX by animateFloatAsState(
        targetValue = targetX,
        animationSpec = tween(durationMillis = animMs, easing = NoopMotion.EaseOutQuint),
        label = "imuDotX",
    )
    val smoothY by animateFloatAsState(
        targetValue = targetY,
        animationSpec = tween(durationMillis = animMs, easing = NoopMotion.EaseOutQuint),
        label = "imuDotY",
    )

    val padShape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .clip(padShape)
            .background(Palette.surfaceInset)
            .border(1.dp, Palette.hairline, padShape)
            .semantics { contentDescription = "Six-axis motion pad" },
        contentAlignment = Alignment.Center,
    ) {
        // this.size — layout.size import shadows DrawScope.size otherwise.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = this.size
            val cx = canvasSize.width / 2f
            val cy = canvasSize.height / 2f
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = Offset(0f, cy),
                end = Offset(canvasSize.width, cy),
                strokeWidth = 1f,
                pathEffect = dash,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = Offset(cx, 0f),
                end = Offset(cx, canvasSize.height),
                strokeWidth = 1f,
                pathEffect = dash,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = 4.dp.toPx(),
                center = Offset(cx, cy),
            )
        }

        if (waiting) {
            Text(
                "No band IMU yet\nWaiting for type-51 live or 1244-B offload",
                style = NoopType.caption,
                color = Palette.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        } else {
            val travelX = with(density) { ((smoothX * 0.42f) * 160.dp.toPx()).roundToInt() }
            val travelY = with(density) { ((smoothY * 0.42f) * 160.dp.toPx()).roundToInt() }
            val dotColor = when {
                live -> Palette.statusPositive
                else -> Palette.statusWarning
            }
            Box(
                Modifier
                    .offset { IntOffset(travelX, travelY) }
                    .size(18.dp)
                    .background(dotColor, CircleShape)
                    .border(2.dp, Palette.textPrimary.copy(alpha = 0.35f), CircleShape),
            )
        }
    }
}

@Composable
private fun AxisReadout(sample: SixAxisSample?) {
    if (sample == null) {
        Text(
            "a  —  —  —  g\nω  —  —  —  °/s",
            style = NoopType.footnote,
            color = Palette.textTertiary,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "a  ${fmt(sample.ax)}  ${fmt(sample.ay)}  ${fmt(sample.az)}  g",
            style = NoopType.footnote,
            color = Palette.textSecondary,
        )
        Text(
            "ω  ${fmt(sample.gx)}  ${fmt(sample.gy)}  ${fmt(sample.gz)}  °/s",
            style = NoopType.footnote,
            color = Palette.textSecondary,
        )
        Text(
            "|a|=${fmt(sample.accelMagG)} g · |ω|=${fmt(sample.gyroMagDps)} °/s",
            style = NoopType.caption,
            color = Palette.textTertiary,
        )
    }
}

private fun fmt(v: Float): String = "%+.2f".format(v)

/** Settings section chrome wrapping [SixAxisMotionDotTester]. */
@Composable
fun SixAxisMotionTesterSection(ble: WhoopBleClient) {
    NoopCard(padding = 16.dp, tint = null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = Palette.accent,
                    modifier = Modifier.size(18.dp),
                )
                Text("6-axis motion tester", style = NoopType.title2, color = Palette.textPrimary)
            }
            Text(
                "Band IMU only. Live type-51 when it streams; offload labeled not-live. " +
                    "Waiting state while live strap IMU is gated — no phone sensors.",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
            SixAxisMotionDotTester(ble = ble)
        }
    }
}
