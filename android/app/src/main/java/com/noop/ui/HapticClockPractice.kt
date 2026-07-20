package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.protocol.HapticClock
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * TOP-A #354: practice sheet that highlights which HH:MM digit the strap is buzzing so the user
 * can learn the Morse-ish encoding on the phone while the wrist plays.
 */
@Composable
fun HapticClockPracticeDialog(
    onDismiss: () -> Unit,
    onBuzzStrap: (is24h: Boolean, speed: HapticClock.Speed, announce: Boolean) -> Unit,
    speed: HapticClock.Speed = HapticClock.Speed.NORMAL,
    announce: Boolean = false,
) {
    val context = LocalContext.current
    val is24h = remember { NoopPrefs.use24HourClock(context) }
    val cal = remember { Calendar.getInstance() }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val displayHour = if (is24h) hour else HapticClock.twelveHour(hour)
    val digits = remember(displayHour, minute) {
        intArrayOf(displayHour / 10, displayHour % 10, minute / 10, minute % 10)
    }
    val spans = remember(hour, minute, is24h, speed, announce) {
        HapticClock.practiceSpans(hour, minute, is24h, speed, announce)
    }
    var activeRole by remember { mutableStateOf<HapticClock.DigitRole?>(null) }

    LaunchedEffect(spans) {
        onBuzzStrap(is24h, speed, announce)
        val start = System.currentTimeMillis()
        for (span in spans) {
            val wait = (start + span.startMs) - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            activeRole = span.role
            val hold = (span.endMs - span.startMs).coerceAtLeast(1L)
            delay(hold)
        }
        activeRole = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Practice haptic time", style = NoopType.headline, color = Palette.textPrimary)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Watch which digit lights while the strap buzzes. Long = tens, short = ones.",
                    style = NoopType.footnote,
                    color = Palette.textSecondary,
                )
                if (activeRole == HapticClock.DigitRole.ANNOUNCE) {
                    Text(
                        "Announce",
                        style = NoopType.subhead,
                        color = Palette.accent,
                        modifier = Modifier.semantics { contentDescription = "Announce triple buzz" },
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = String.format(
                                "%02d:%02d practice",
                                displayHour,
                                minute,
                            )
                        },
                ) {
                    PracticeDigit(
                        value = digits[0],
                        active = activeRole == HapticClock.DigitRole.HOUR_TENS,
                        label = "hour tens",
                    )
                    PracticeDigit(
                        value = digits[1],
                        active = activeRole == HapticClock.DigitRole.HOUR_ONES,
                        label = "hour ones",
                    )
                    Text(
                        ":",
                        style = NoopType.number(36f, weight = FontWeight.Bold),
                        color = Palette.textTertiary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    PracticeDigit(
                        value = digits[2],
                        active = activeRole == HapticClock.DigitRole.MIN_TENS,
                        label = "minute tens",
                    )
                    PracticeDigit(
                        value = digits[3],
                        active = activeRole == HapticClock.DigitRole.MIN_ONES,
                        label = "minute ones",
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    HapticClock.readLegend(),
                    style = NoopType.caption,
                    color = Palette.textTertiary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Palette.accent)
            }
        },
    )
}

@Composable
private fun PracticeDigit(value: Int, active: Boolean, label: String) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .width(44.dp)
            .height(56.dp)
            .background(
                if (active) Palette.accent.copy(alpha = 0.22f) else Palette.surfaceInset,
                shape,
            )
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) Palette.accent else Palette.hairline,
                shape = shape,
            )
            .semantics {
                contentDescription = if (active) "$label $value buzzing" else "$label $value"
            },
    ) {
        Text(
            value.toString(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Palette.accent else Palette.textPrimary,
        )
    }
}
