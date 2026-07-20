package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noop.R
import com.noop.analytics.CyclePhysiology
import com.noop.analytics.PeriodCalendar
import com.noop.data.PeriodCalendarStore
import java.time.LocalDate

/**
 * Concise Cycle card on Today when cycle tracking is enabled.
 * Life anim: shared [CycleStarLifeMotes] — four-point stars spawn → hover → break.
 * Learning phase uses dimmed stars. Reduce Motion = static poses.
 */
@Composable
fun TodayCycleLifeCard(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val reduced = rememberReduceMotion()
    var snapshot by remember { mutableStateOf<PeriodCalendar.Snapshot?>(null) }
    LaunchedEffect(Unit) {
        val store = PeriodCalendarStore.from(context)
        val events = store.loadEvents()
        val prefs = store.loadPrefs()
        snapshot = PeriodCalendar.evaluate(
            today = LocalDate.now(),
            events = events,
            prefs = prefs.copy(enabled = true),
        )
    }
    val phase = snapshot?.phase ?: PeriodCalendar.CalendarPhase.LEARNING
    val cycleDay = snapshot?.cycleDay
    val effect = CyclePhysiology.softEffectFromSnapshot(snapshot)
    val phaseLabel = cyclePhaseLabel(context, phase)
    val dayBit = cycleDayBit(context, cycleDay)
    val softBit = cycleSoftBitCaption(
        context = context,
        needsMoreFuel = effect?.needsMoreFuel == true,
        takeItEasy = effect?.takeItEasy == true,
        recoveryCapacityFactor = effect?.recoveryCapacityFactor,
    )
    val learning = phase == PeriodCalendar.CalendarPhase.LEARNING ||
        phase == PeriodCalendar.CalendarPhase.UNKNOWN
    val shape = RoundedCornerShape(LifeChapterLacquer.CORNER_DP.dp)
    val accent = Palette.restColor
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LifeChapterLacquer.CHAPTER_MIN_HEIGHT_DP.dp)
            .clip(shape)
            .background(Palette.surfaceInset.copy(alpha = LifeChapterLacquer.SURFACE_ALPHA))
            .border(1.dp, accent.copy(alpha = LifeChapterLacquer.BORDER_ALPHA), shape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onOpen()
            }
            .semantics {
                contentDescription = cycleCardA11y(context, phaseLabel, dayBit, softBit)
            },
    ) {
        CycleStarLifeMotes(
            reduced = reduced,
            accent = accent,
            dimmed = learning,
            intensity = LifeChapterLacquer.CYCLE_INTENSITY,
            modifier = Modifier.matchParentSize(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = LifeChapterLacquer.PAD_V_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = accent.copy(alpha = 0.90f),
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.cycle_overline),
                    style = NoopType.overline,
                    color = accent,
                )
                Text(
                    "$phaseLabel$dayBit$softBit",
                    style = NoopType.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = Palette.textPrimary,
                    maxLines = 1,
                )
                val cite = cycleScienceCite(context, phase)
                if (cite.isNotBlank()) {
                    Text(
                        cite,
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                        maxLines = 2,
                    )
                }
            }
            Text(
                stringResource(R.string.cycle_open),
                style = NoopType.footnote,
                color = accent,
            )
        }
    }
}
