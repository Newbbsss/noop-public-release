package com.noop.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.alarm.WindDownStore
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Smart alarm (#207) — Android phone-based wake, with a guaranteed hard-deadline fallback.
 *
 * The user picks the EARLIEST acceptable wake time and a window length. NOOP watches the overnight
 * strap stream and, if it spots a lighter sleep phase inside the window, wakes you then — but a
 * GUARANTEED exact OS alarm is always scheduled at the window's END (via AlarmManager), independent
 * of Bluetooth, the strap, or the app being alive. The smart logic can only ever move the alarm
 * EARLIER; it can never cancel or skip the fallback. So you're woken by the window's end no matter
 * what. This screen is explicit about that safety guarantee.
 *
 * This is the ONE alarm surface (#766). It hosts the phone-based Wake Window above, the strap's own
 * standalone firmware wake-alarm (moved here from Automations), and the cross-platform WIND-DOWN nudge,
 * so every wake/alarm control lives together instead of being split across two screens.
 *
 * @param embedded when true (Sleep → Alarm pill), omit the outer LazyScreenScaffold chrome so the
 *   same settings-style body sits inside Sleep's pager without a second title stack.
 */
@Composable
fun SmartAlarmScreen(vm: AppViewModel, embedded: Boolean = false) {
    val context = LocalContext.current
    val enabled by vm.phoneAlarmEnabled.collectAsStateWithLifecycle()
    val targetMinutes by vm.phoneAlarmTargetMinutes.collectAsStateWithLifecycle()
    val windowMinutes by vm.phoneAlarmWindowMinutes.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val buzzWhoop4 by vm.buzzWhoop4Enabled.collectAsStateWithLifecycle()
    // #536: the hint adapts to bond state — the strap can only be armed when a WHOOP 4.0 is connected.
    val strapState by vm.live
        .map { state -> AlarmStrapState(state.bonded, state.whoop5Detected, state.connected) }
        .distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = AlarmStrapState(false, false, false))
    val bonded = strapState.bonded
    val strapLinked = strapState.connected
    // #821: the strap-buzz row was hardcoded to "WHOOP 4", which reads wrong on a connected 5/MG (issue
    // #730 follow-up). Name the actual strap generation instead: a detected 5/MG says "WHOOP 5/MG", anything
    // else (a 4.0, or nothing connected yet) keeps "WHOOP 4.0", so the label never claims the wrong device.
    val strapName = alarmStrapGenerationName(strapState.whoop5Detected)

    // True when exact alarms are permitted. Re-read on RESUME — user can grant/revoke in Settings.
    var canSchedule by remember { mutableStateOf(vm.canScheduleExactAlarms()) }
    var pendingArmAfterExact by remember { mutableStateOf(false) }
    var notifsOk by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val exactOk = vm.canScheduleExactAlarms()
                canSchedule = exactOk
                notifsOk = NotificationManagerCompat.from(context).areNotificationsEnabled()
                if (exactOk && pendingArmAfterExact) {
                    pendingArmAfterExact = false
                    val ok = vm.setPhoneAlarmEnabled(true)
                    if (!ok) {
                        android.widget.Toast.makeText(
                            context,
                            alarmExactAllowedReArmCaption(),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val liveSubtitle = alarmWakeLiveSubtitle(enabled, canSchedule)

    @Composable
    fun AlarmSections() {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Overline(LifeChapterLacquer.ALARM_GLANCE_OVERLINE, color = DomainTheme.Rest.color)
            Text(
                alarmTaxonomyGlossary(),
                style = NoopType.footnote,
                color = Palette.textTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = alarmTaxonomyGlossary() },
            )
            val zone = remember { java.util.TimeZone.getDefault() }
            Text(
                "Phone zone ${zone.getDisplayName(false, java.util.TimeZone.SHORT)} · wake times follow this phone clock after travel or DST.",
                style = NoopType.caption,
                color = Palette.textTertiary,
            )
            if (!strapLinked && buzzWhoop4) {
                Text(
                    "Strap buzz arms on next connect · Soft wake window on this phone needs the link for early cue.",
                    style = NoopType.caption,
                    color = Palette.metricAmber.copy(alpha = 0.95f),
                )
            }
            WindowCard(
                enabled = enabled,
                canExact = canSchedule,
                targetMinutes = targetMinutes,
                windowMinutes = windowMinutes,
                days = days,
                onEnabledChange = { want ->
                    if (want && !vm.canScheduleExactAlarms()) {
                        pendingArmAfterExact = true
                        requestExactAlarmAccess(context)
                        canSchedule = vm.canScheduleExactAlarms()
                    } else {
                        val ok = vm.setPhoneAlarmEnabled(want)
                        canSchedule = vm.canScheduleExactAlarms()
                        if (!ok) {
                            pendingArmAfterExact = true
                            requestExactAlarmAccess(context)
                        }
                    }
                },
                onRequestExactAccess = {
                    pendingArmAfterExact = true
                    requestExactAlarmAccess(context)
                    canSchedule = vm.canScheduleExactAlarms()
                },
                onTargetChange = { vm.setPhoneAlarmTargetMinutes(it) },
            )
            ExplanationCard()
            if (!enabled) {
                val whyShape = RoundedCornerShape(LifeChapterLacquer.CORNER_DP.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(whyShape)
                        .background(
                            Palette.surfaceInset.copy(
                                alpha = LifeChapterLacquer.SURFACE_ALPHA * LifeChapterLacquer.ALARM_WHY_SURFACE,
                            ),
                        )
                        .border(
                            1.dp,
                            Palette.restColor.copy(alpha = LifeChapterLacquer.BORDER_ALPHA),
                            whyShape,
                        )
                        .padding(horizontal = 12.dp, vertical = LifeChapterLacquer.PAD_V_DP.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.28f)
                            .height(1.dp)
                            .background(Palette.hairline.copy(alpha = 0.65f)),
                    )
                    Text(
                        alarmWhyHelpsCaption(),
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = alarmWhyHelpsCaption() },
                    )
                }
            }
            if (!canSchedule) {
                val warnShape = RoundedCornerShape(LifeChapterLacquer.CORNER_DP.dp)
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        alarmExactEarlyWarn(),
                        style = NoopType.footnote,
                        color = Palette.statusWarning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(warnShape)
                            .border(
                                1.dp,
                                Palette.statusWarning.copy(alpha = LifeChapterLacquer.ALARM_EXACT_WARN_BORDER),
                                warnShape,
                            )
                            .background(
                                Palette.statusWarning.copy(alpha = LifeChapterLacquer.ALARM_EXACT_WARN_WASH),
                            )
                            .clickable {
                                pendingArmAfterExact = true
                                requestExactAlarmAccess(context)
                                canSchedule = vm.canScheduleExactAlarms()
                            }
                            .semantics { contentDescription = alarmOpenExactSettingsA11y() }
                            .padding(horizontal = 12.dp, vertical = LifeChapterLacquer.PAD_V_DP.dp),
                    )
                    Text(
                        alarmWhyHelpsCaption(),
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = alarmWhyHelpsCaption() },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Overline(LifeChapterLacquer.ALARM_PLAN_OVERLINE, color = DomainTheme.Rest.color)
            PersonalSleepPlanCard(days = days, targetMinutes = targetMinutes)
            Spacer(Modifier.height(8.dp))
            Overline(LifeChapterLacquer.ALARM_ARM_OVERLINE, color = DomainTheme.Rest.color)
            AlarmSettingsBody(
                vm = vm,
                enabled = enabled,
                canSchedule = canSchedule,
                notifsOk = notifsOk,
                bonded = bonded,
                strapName = strapName,
                buzzWhoop4 = buzzWhoop4,
                targetMinutes = targetMinutes,
                windowMinutes = windowMinutes,
                onNotifsRefresh = {
                    notifsOk = NotificationManagerCompat.from(context).areNotificationsEnabled()
                },
                onRequestExact = {
                    pendingArmAfterExact = true
                    requestExactAlarmAccess(context)
                    canSchedule = vm.canScheduleExactAlarms()
                },
            )
            Spacer(Modifier.height(8.dp))
            Overline(LifeChapterLacquer.ALARM_CUSTOM_OVERLINE, color = DomainTheme.Rest.color)
            CustomAlarmsCard(vm)
            Spacer(Modifier.height(8.dp))
            Overline(LifeChapterLacquer.ALARM_EXTRAS_OVERLINE, color = DomainTheme.Rest.color)
            val dualShape = RoundedCornerShape(LifeChapterLacquer.CORNER_DP.dp)
            Text(
                alarmDualBuzzCaption(),
                style = NoopType.footnote,
                color = Palette.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(dualShape)
                    .border(1.dp, Palette.restColor.copy(alpha = LifeChapterLacquer.BORDER_ALPHA), dualShape)
                    .background(Palette.surfaceInset.copy(alpha = LifeChapterLacquer.SURFACE_ALPHA * LifeChapterLacquer.ALARM_DUAL_BUZZ_SURFACE))
                    .padding(horizontal = 12.dp, vertical = LifeChapterLacquer.PAD_V_DP.dp)
                    .semantics { contentDescription = alarmDualBuzzCaption() },
            )
            StrapAlarmCard(vm, strapState)
            if (enabled) {
                val tipShape = RoundedCornerShape(LifeChapterLacquer.CORNER_DP.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(tipShape)
                        .border(1.dp, Palette.restColor.copy(alpha = LifeChapterLacquer.BORDER_ALPHA), tipShape)
                        .background(Palette.surfaceInset.copy(alpha = LifeChapterLacquer.SURFACE_ALPHA))
                        .clickable {
                            pendingArmAfterExact = true
                            requestExactAlarmAccess(context)
                            canSchedule = vm.canScheduleExactAlarms()
                        }
                        .semantics { contentDescription = alarmOemTipA11y() }
                        .padding(horizontal = 12.dp, vertical = LifeChapterLacquer.PAD_V_DP.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(alarmOemTipTitle(), style = NoopType.caption, color = Palette.restColor)
                    Text(
                        alarmOemTipBody(),
                        style = NoopType.footnote,
                        color = Palette.textSecondary,
                    )
                }
            }
            Overline(LifeChapterLacquer.ALARM_EVENING_OVERLINE, color = DomainTheme.Rest.color)
            WindDownCard(vm)
        }
    }

    if (embedded) {
        AlarmSections()
    } else {
        LazyScreenScaffold(
            title = LifeChapterLacquer.ALARM_HOME_TITLE,
            subtitle = liveSubtitle,
        ) {
            item(key = "alarm_unified") { AlarmSections() }
        }
    }
}

/** Arm-chapter toggles (strap buzz, turn-back, wake rested, math dismiss) in Alarm settings style. */
@Composable
private fun AlarmSettingsBody(
    vm: AppViewModel,
    enabled: Boolean,
    canSchedule: Boolean,
    notifsOk: Boolean,
    bonded: Boolean,
    strapName: String,
    buzzWhoop4: Boolean,
    targetMinutes: Int,
    windowMinutes: Int,
    onNotifsRefresh: () -> Unit,
    onRequestExact: () -> Unit,
) {
    val context = LocalContext.current
    AlarmSettingsCard {
        if (enabled) {
            Text(
                alarmArmedPlanCaption(canSchedule),
                style = NoopType.caption,
                color = if (canSchedule) DomainTheme.Rest.color else Palette.effortColor,
            )
        }
        if (enabled && !notifsOk) {
            RowDividerLocal()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        requestNotificationSettingsAccess(context)
                        onNotifsRefresh()
                    }
                    .padding(vertical = 4.dp)
                    .semantics { contentDescription = alarmOpenNotifSettingsA11y() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    alarmNotifsOffCaption(),
                    style = NoopType.footnote,
                    color = Palette.statusWarning,
                    modifier = Modifier.weight(1f),
                )
                Text(LifeChapterLacquer.ALARM_OPEN_LABEL, style = NoopType.footnote, color = Palette.accent)
            }
        }
        if (enabled && !canSchedule) {
            RowDividerLocal()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRequestExact)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    alarmExactProtectCaption(),
                    style = NoopType.footnote,
                    color = Palette.statusWarning,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = alarmExactProtectCaption() },
                )
                Text(LifeChapterLacquer.ALARM_OPEN_LABEL, style = NoopType.footnote, color = Palette.accent)
            }
        }
        if (enabled) {
            RowDividerLocal()
            WakeWindowControl(
                targetMinutes = targetMinutes,
                windowMinutes = windowMinutes,
                honestlyArmed = canSchedule,
                onTargetPicked = { vm.setPhoneAlarmTargetMinutes(it) },
                onWindowChange = { vm.setPhoneAlarmWindowMinutes(it) },
            )
        }
        RowDividerLocal()
        ToggleRowLocal(
            label = LifeChapterLacquer.ALARM_BUZZ_STRAP_LABEL,
            help = alarmBuzzStrapHelp(bonded, strapName),
            checked = buzzWhoop4,
            onChange = { vm.setBuzzWhoop4Enabled(it) },
        )
        Text(
            "Phone alarm sound & vibe · system notification settings",
            style = NoopType.footnote,
            color = Palette.accent,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { requestNotificationSettingsAccess(context) }
                .padding(top = 4.dp)
                .semantics { contentDescription = "Open phone alarm sound and vibration settings" },
        )
        RowDividerLocal()
        val mathOn by vm.mathChallengeEnabled.collectAsStateWithLifecycle()
        val mathDrowsy by vm.mathOnDrowsyHr.collectAsStateWithLifecycle()
        val drowsyHr by vm.drowsyHrBpm.collectAsStateWithLifecycle()
        ToggleRowLocal(
            label = "Math to dismiss",
            help = "Solve a short sum before the phone alarm clears.",
            checked = mathOn,
            onChange = { vm.setMathChallengeEnabled(it) },
        )
        RowDividerLocal()
        ToggleRowLocal(
            label = "Math when drowsy",
            help = "At the hard deadline, require math + louder cue if live HR is below the drowsy threshold.",
            checked = mathDrowsy,
            onChange = { vm.setMathOnDrowsyHr(it) },
        )
        if (mathDrowsy) {
            RowDividerLocal()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Drowsy HR", style = NoopType.body, color = Palette.textPrimary)
                    Text(
                        "Below $drowsyHr bpm at final alarm → math + loud.",
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepperButton(
                        symbol = "−",
                        onClick = { vm.setDrowsyHrBpm(drowsyHr - 1) },
                        label = "Lower drowsy HR",
                    )
                    Text("$drowsyHr", style = NoopType.bodyNumber, color = Palette.textPrimary)
                    StepperButton(
                        symbol = "+",
                        onClick = { vm.setDrowsyHrBpm(drowsyHr + 1) },
                        label = "Higher drowsy HR",
                    )
                }
            }
        }
        RowDividerLocal()
        val turnBack by vm.turnBackEnabled.collectAsStateWithLifecycle()
        val turnBackWatch by vm.turnBackWatchMinutes.collectAsStateWithLifecycle()
        val turnBackDrop by vm.turnBackDropBpm.collectAsStateWithLifecycle()
        val turnBackPhone by vm.turnBackPhoneCue.collectAsStateWithLifecycle()
        ToggleRowLocal(
            label = LifeChapterLacquer.ALARM_TURN_BACK_LABEL,
            help = LifeChapterLacquer.ALARM_TURN_BACK_HELP,
            checked = turnBack,
            onChange = { vm.setTurnBackEnabled(it) },
        )
        if (turnBack) {
            RowDividerLocal()
            Text(
                alarmTurnBackTonightCaption(
                    turnBackWatch,
                    hhmm((targetMinutes + windowMinutes) % (24 * 60)),
                ),
                style = NoopType.footnote,
                color = Palette.textSecondary,
            )
            RowDividerLocal()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(LifeChapterLacquer.ALARM_WATCH_AFTER_WAKE_LABEL, style = NoopType.body, color = Palette.textPrimary)
                    Text(LifeChapterLacquer.ALARM_WATCH_AFTER_HELP, style = NoopType.footnote, color = Palette.textTertiary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepperButton(
                        symbol = "−",
                        onClick = { vm.setTurnBackWatchMinutes((turnBackWatch - 5).coerceAtLeast(15)) },
                        label = LifeChapterLacquer.ALARM_SHORTER_WATCH_A11Y,
                    )
                    Text("$turnBackWatch min", style = NoopType.bodyNumber, color = Palette.textPrimary)
                    StepperButton(
                        symbol = "+",
                        onClick = { vm.setTurnBackWatchMinutes((turnBackWatch + 5).coerceAtMost(90)) },
                        label = LifeChapterLacquer.ALARM_LONGER_WATCH_A11Y,
                    )
                }
            }
            RowDividerLocal()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(LifeChapterLacquer.ALARM_HR_DROP_LABEL, style = NoopType.body, color = Palette.textPrimary)
                    Text(alarmHrDropHelp(turnBackDrop), style = NoopType.footnote, color = Palette.textTertiary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepperButton(symbol = "−", onClick = { vm.setTurnBackDropBpm(turnBackDrop - 1) }, label = LifeChapterLacquer.ALARM_LESS_SENSITIVE_A11Y)
                    Text("$turnBackDrop", style = NoopType.bodyNumber, color = Palette.textPrimary)
                    StepperButton(symbol = "+", onClick = { vm.setTurnBackDropBpm(turnBackDrop + 1) }, label = LifeChapterLacquer.ALARM_MORE_SENSITIVE_A11Y)
                }
            }
            RowDividerLocal()
            ToggleRowLocal(
                label = LifeChapterLacquer.ALARM_PHONE_CUE_LABEL,
                help = LifeChapterLacquer.ALARM_PHONE_CUE_HELP,
                checked = turnBackPhone,
                onChange = { vm.setTurnBackPhoneCue(it) },
            )
        }
        RowDividerLocal()
        val wakeRested by vm.wakeWhenRested.collectAsStateWithLifecycle()
        val restedCharge by vm.restedChargeThreshold.collectAsStateWithLifecycle()
        val restedSleepPct by vm.restedSleepNeedPercent.collectAsStateWithLifecycle()
        ToggleRowLocal(
            label = LifeChapterLacquer.ALARM_WAKE_RESTED_LABEL,
            help = LifeChapterLacquer.ALARM_WAKE_RESTED_HELP,
            checked = wakeRested,
            onChange = { vm.setWakeWhenRested(it) },
        )
        if (wakeRested) {
            Text(
                alarmWakeRestedPlain(restedCharge, restedSleepPct),
                style = NoopType.footnote,
                color = Palette.textSecondary,
                modifier = Modifier.semantics {
                    contentDescription = alarmWakeRestedPlain(restedCharge, restedSleepPct)
                },
            )
            RowDividerLocal()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(LifeChapterLacquer.ALARM_CHARGE_THRESHOLD_LABEL, style = NoopType.body, color = Palette.textPrimary)
                    Text(
                        alarmChargeThresholdHelp(restedCharge),
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                    Text(
                        alarmChargeVesselCue(),
                        style = NoopType.caption,
                        color = Palette.chargeColor,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepperButton(symbol = "−", onClick = { vm.setRestedChargeThreshold(restedCharge - 1) }, label = LifeChapterLacquer.ALARM_LOWER_THRESHOLD_A11Y)
                    Text("$restedCharge", style = NoopType.bodyNumber, color = Palette.textPrimary)
                    StepperButton(symbol = "+", onClick = { vm.setRestedChargeThreshold(restedCharge + 1) }, label = LifeChapterLacquer.ALARM_HIGHER_THRESHOLD_A11Y)
                }
            }
            RowDividerLocal()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(LifeChapterLacquer.ALARM_SLEEP_NEED_MET_LABEL, style = NoopType.body, color = Palette.textPrimary)
                    Text(alarmSleepNeedMetHelp(restedSleepPct), style = NoopType.footnote, color = Palette.textTertiary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepperButton(symbol = "−", onClick = { vm.setRestedSleepNeedPercent(restedSleepPct - 5) }, label = LifeChapterLacquer.ALARM_LOWER_PERCENT_A11Y)
                    Text("$restedSleepPct%", style = NoopType.bodyNumber, color = Palette.textPrimary)
                    StepperButton(symbol = "+", onClick = { vm.setRestedSleepNeedPercent(restedSleepPct + 5) }, label = LifeChapterLacquer.ALARM_HIGHER_PERCENT_A11Y)
                }
            }
        }
    }
}

private data class AlarmStrapState(val bonded: Boolean, val whoop5Detected: Boolean, val connected: Boolean = false)

/**
 * Classic exact-time phone alarms — flat list, no nested chrome. Uses the same weekday picker
 * vocabulary as the strap alarm.
 */
@Composable
private fun CustomAlarmsCard(vm: AppViewModel) {
    val alarms by vm.customAlarms.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canSchedule by remember { mutableStateOf(vm.canScheduleExactAlarms()) }
    var renameTarget by remember { mutableStateOf<com.noop.alarm.CustomAlarm?>(null) }
    var removeTarget by remember { mutableStateOf<com.noop.alarm.CustomAlarm?>(null) }
    NoopCard(padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Overline(LifeChapterLacquer.ALARM_EXACT_TIME_OVERLINE)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = Palette.accent)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        alarmCustomAlarmsTitle(
                            enabledCount = alarms.count { it.enabled },
                            total = alarms.size,
                        ),
                        style = NoopType.title2,
                        color = Palette.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Text(
                    alarmCustomAlarmsHelp(),
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
                if (alarms.any { it.enabled } && !canSchedule) {
                    Text(
                        alarmCustomExactOffCaption(),
                        style = NoopType.footnote,
                        color = Palette.statusWarning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                requestExactAlarmAccess(context)
                                canSchedule = vm.canScheduleExactAlarms()
                            }
                            .padding(top = 4.dp),
                    )
                }
            }
            if (alarms.isEmpty()) {
                Text(
                    alarmCustomEmptyCaption(),
                    style = NoopType.footnote,
                    color = Palette.textSecondary,
                )
            }
            alarms.forEach { alarm ->
                RowDividerLocal()
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            alarm.label,
                            style = NoopType.body,
                            color = Palette.textPrimary,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable { renameTarget = alarm }
                                .padding(vertical = 8.dp)
                                .semantics {
                                    // SHIP #117 — announce as a custom preset, not a bare time label.
                                    contentDescription = "Custom alarm preset ${alarm.label}"
                                },
                        )
                        AlarmWeekdayPicker(
                            selected = alarm.weekdays,
                            onToggle = { dow ->
                                vm.upsertCustomAlarm(
                                    alarm.copy(weekdays = toggledSmartAlarmWeekday(dow, alarm.weekdays)),
                                )
                            },
                            onSetDays = { days ->
                                vm.upsertCustomAlarm(alarm.copy(weekdays = days))
                            },
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    TimeChip(
                        minutes = alarm.minutes,
                        accessibilityLabel = "${alarm.label} time",
                        onPicked = { vm.upsertCustomAlarm(alarm.copy(minutes = it)) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = alarm.enabled,
                        onCheckedChange = { want ->
                            val saved = vm.upsertCustomAlarm(alarm.copy(enabled = want))
                            canSchedule = vm.canScheduleExactAlarms()
                            if (want && !saved) requestExactAlarmAccess(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Palette.surfaceBase,
                            checkedTrackColor = Palette.accent,
                            uncheckedThumbColor = Palette.textSecondary,
                            uncheckedTrackColor = Palette.surfaceInset,
                            uncheckedBorderColor = Palette.hairline,
                        ),
                    )
                }
                Text(
                    LifeChapterLacquer.ALARM_REMOVE_LABEL,
                    style = NoopType.footnote,
                    color = Palette.statusCritical,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable { removeTarget = alarm }
                        .padding(top = 4.dp),
                )
            }
            if (alarms.size < com.noop.alarm.SmartAlarmStore.MAX_CUSTOM_ALARMS) {
                RowDividerLocal()
                Text(
                    alarmAddCustomAlarmLabel(alarms.isEmpty()),
                    style = NoopType.body,
                    color = Palette.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                            vm.upsertCustomAlarm(
                                com.noop.alarm.CustomAlarm(
                                    label = alarmDefaultCustomLabel(alarms.size + 1),
                                    minutes = 7 * 60,
                                ),
                            )
                        }
                        .padding(vertical = 4.dp),
                )
            } else {
                RowDividerLocal()
                Text(
                    alarmCustomLimitCaption(com.noop.alarm.SmartAlarmStore.MAX_CUSTOM_ALARMS),
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
        }
    }
    renameTarget?.let { alarm ->
        var draft by remember(alarm.id) { mutableStateOf(alarm.label) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(LifeChapterLacquer.ALARM_RENAME_TITLE, style = NoopType.title2, color = Palette.textPrimary) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(32) },
                    singleLine = true,
                    label = { Text(LifeChapterLacquer.ALARM_RENAME_FIELD_LABEL) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Palette.accent,
                        unfocusedBorderColor = Palette.hairline,
                        focusedTextColor = Palette.textPrimary,
                        unfocusedTextColor = Palette.textPrimary,
                        cursorColor = Palette.accent,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val next = draft.trim()
                        when {
                            next.isEmpty() -> {
                                android.widget.Toast.makeText(
                                    context,
                                    "Name can’t be blank — kept “${alarm.label}”",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                renameTarget = null
                            }
                            alarms.any { it.id != alarm.id && it.label.equals(next, ignoreCase = true) } -> {
                                android.widget.Toast.makeText(
                                    context,
                                    "Another custom alarm already uses that name",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                            else -> {
                                vm.upsertCustomAlarm(alarm.copy(label = next))
                                renameTarget = null
                            }
                        }
                    },
                ) { Text(LifeChapterLacquer.ALARM_SAVE_LABEL, color = Palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(LifeChapterLacquer.ALARM_CANCEL_LABEL, color = Palette.textSecondary)
                }
            },
        )
    }
    removeTarget?.let { alarm ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(alarmRemoveConfirmTitle(alarm.label), style = NoopType.title2, color = Palette.textPrimary) },
            text = {
                Text(
                    alarmRemoveConfirmBody(),
                    style = NoopType.footnote,
                    color = Palette.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteCustomAlarm(alarm.id)
                        removeTarget = null
                    },
                ) { Text(LifeChapterLacquer.ALARM_REMOVE_LABEL, color = Palette.statusCritical) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text(LifeChapterLacquer.ALARM_KEEP_LABEL, color = Palette.textSecondary)
                }
            },
        )
    }
}

/**
 * The strap's standalone silent wake-alarm (#766, moved from AutomationsScreen). Arms the strap's own
 * firmware alarm at the chosen time/weekdays over BLE, so it buzzes even if NOOP is closed. Reuses the
 * shared [AlarmWeekdayPicker] / [AlarmDayOverridePicker] from AutomationsScreen (same behaviour, just a
 * new home). Functions are untouched: it drives the same `viewModel.setSmartAlarm*` calls as before.
 */
@Composable
private fun StrapAlarmCard(vm: AppViewModel, strapState: AlarmStrapState) {
    val smartAlarm by vm.smartAlarmEnabled.collectAsStateWithLifecycle()
    val alarmMinutes by vm.smartAlarmMinutes.collectAsStateWithLifecycle()
    val alarmWeekdays by vm.smartAlarmWeekdays.collectAsStateWithLifecycle()
    val alarmDayOverrides by vm.smartAlarmDayOverrides.collectAsStateWithLifecycle()

    NoopCard(padding = 20.dp, tint = null) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Overline(LifeChapterLacquer.ALARM_MORNING_OVERLINE)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Watch, contentDescription = null, tint = Palette.accent)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        LifeChapterLacquer.ALARM_STRAP_WAKE_TITLE,
                        style = NoopType.title2,
                        color = Palette.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            // Truth-sync (#535): the WHOOP 4.0 alarm payload was captured from the official app and
            // confirmed buzzing on a real 4.0 by the capture author, so the copy no longer calls the
            // 4.0 path experimental. The 5/MG Experimental-gate branch below is deliberately untouched.
            ToggleRowLocal(
                label = LifeChapterLacquer.ALARM_STRAP_FIRMWARE_LABEL,
                help = alarmStrapFirmwareHelp(),
                checked = smartAlarm,
                onChange = { vm.setSmartAlarmEnabled(it) },
            )
            if (!smartAlarm) {
                // ALARM_PAGE #49 — day-override picker is hidden until on; tip discoverability.
                Text(
                    alarmStrapFirmwareOffTip(),
                    style = NoopType.caption,
                    color = Palette.textTertiary,
                )
            }
            if (smartAlarm) {
                RowDividerLocal()
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(LifeChapterLacquer.ALARM_WAKE_AT_LABEL, style = NoopType.body, color = Palette.textPrimary)
                    Spacer(Modifier.weight(1f))
                    TimeChip(
                        minutes = alarmMinutes,
                        accessibilityLabel = alarmStrapWakeTimeA11y(),
                        onPicked = { vm.setSmartAlarmMinutes(it) },
                    )
                }
                RowDividerLocal()
                AlarmWeekdayPicker(
                    selected = alarmWeekdays,
                    onToggle = { dow -> vm.setSmartAlarmWeekdays(toggledSmartAlarmWeekday(dow, alarmWeekdays)) },
                    onSetDays = { vm.setSmartAlarmWeekdays(it) },
                )
                RowDividerLocal()
                // Per-weekday wake-time OVERRIDES (#554): a different time for any day the alarm fires on.
                AlarmDayOverridePicker(
                    defaultMinutes = alarmMinutes,
                    enabledDays = alarmWeekdays,
                    overrides = alarmDayOverrides,
                    onSetOverride = { dow, minutes -> vm.setSmartAlarmDayOverride(dow, minutes) },
                )
                RowDividerLocal()
                // 5/MG: acknowledged command but wake never captured (#864). 4.0: confirmed (#535).
                Text(
                    alarmStrapArmedCaption(
                        whoop5 = strapState.whoop5Detected,
                        bonded = strapState.bonded,
                    ),
                    style = NoopType.footnote, color = Palette.textTertiary,
                )
            }
        }
    }
}

// MARK: - Cards

/**
 * The always-visible "you WILL be woken by" guarantee card — a small Rest-world frosted hero. The
 * wake window reads as a clean earliest→deadline time pairing in big rounded numerals over a scenic
 * Rest backdrop (it's about waking, so it lives in the indigo world, not the brand-green chrome).
 */
@Composable
private fun WindowCard(
    enabled: Boolean,
    canExact: Boolean,
    targetMinutes: Int,
    windowMinutes: Int,
    days: List<com.noop.data.DailyMetric>,
    onEnabledChange: (Boolean) -> Unit,
    onRequestExactAccess: () -> Unit,
    onTargetChange: (Int) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    var showBedPicker by remember { mutableStateOf(false) }
    var showWakePicker by remember { mutableStateOf(false) }
    val deadline = (targetMinutes + windowMinutes) % (24 * 60)
    // Fable #388 — do not promise "guaranteed" when exact-alarm access is off.
    val honestlyArmed = enabled && canExact
    val typical = remember(days) {
        days.mapNotNull { it.totalSleepMin?.takeIf { m -> m > 0.0 } }.takeLast(28)
            .takeIf { it.size >= 3 }?.average()
    }
    val needMin = sleepNeedMinutesForAlarm(typical)
    val suggestedBed = (targetMinutes - needMin + 24 * 60) % (24 * 60)
    val reduced = rememberReduceMotion()
    val statusLabel = alarmArmStatusLabel(enabled, canExact)
    val statusColor = alarmArmStatusColor(enabled, canExact)
    val statusShape = RoundedCornerShape(50)
    val armSettle by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.92f,
        animationSpec = if (reduced) {
            tween(0)
        } else {
            tween(LifeChapterLacquer.ARM_SETTLE_MS, easing = FastOutSlowInEasing)
        },
        label = "windowArmSettle",
    )
    // SHIP #118 — slow Armed-pill breathe so a static gold chip doesn't burn OLED.
    val armedBreathe = if (enabled && !reduced) {
        val infinite = rememberInfiniteTransition(label = "armedPillBreathe")
        infinite.animateFloat(
            initialValue = 0.72f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(4200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "armedPillAlpha",
        ).value
    } else {
        if (enabled) 0.88f else 0.85f
    }
    val shape = RoundedCornerShape(LifeChapterLacquer.CORNER_DP.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .frostedCardSurface(
                tint = Palette.restColor,
                cornerRadius = LifeChapterLacquer.CORNER_DP.dp,
                washStrength = LifeChapterLacquer.ALARM_WINDOW_WASH,
            )
            .border(1.dp, Palette.restColor.copy(alpha = LifeChapterLacquer.BORDER_ALPHA), shape),
    ) {
        ScenicHeroBackground(modifier = Modifier.matchParentSize(), domain = DomainTheme.Rest)
        AlarmBedMoonLifeMotes(
            reduced = reduced,
            accent = Palette.restColor,
            active = enabled,
            intensity = LifeChapterLacquer.ALARM_WINDOW_MOON_INTENSITY,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = alarmBedChromeColor())
                Spacer(Modifier.width(10.dp))
                Overline(
                    alarmBedOverline(enabled, canExact),
                    color = DomainTheme.Rest.color,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    statusLabel,
                    style = NoopType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = statusColor,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = 0.96f + 0.04f * armSettle
                            scaleY = 0.96f + 0.04f * armSettle
                            alpha = armedBreathe
                        }
                        .clip(statusShape)
                        .border(
                            1.dp,
                            statusColor.copy(alpha = LifeChapterLacquer.STATUS_PILL_BORDER),
                            statusShape,
                        )
                        .background(statusColor.copy(alpha = LifeChapterLacquer.STATUS_PILL_WASH))
                        .then(
                            if (enabled && !canExact) {
                                Modifier.clickable(onClick = onRequestExactAccess)
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .semantics {
                            contentDescription = if (enabled && !canExact) {
                                alarmOpenExactSettingsA11y()
                            } else {
                                statusLabel
                            }
                        },
                )
            }
            val aimCaption = alarmWindowAimCaption(
                aimLabel = formatAimSleepDuration(needMin),
                wakeStartLabel = hhmm(targetMinutes),
                wakeEndLabel = hhmm(deadline),
            )
            // Dual region: Bedtime | Wake — times under labels (matches Sleep Alarm glance).
            Row(
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        heading()
                        contentDescription = alarmBedtimeClockA11y(
                            statusLabel = statusLabel,
                            bedLabel = hhmm(suggestedBed),
                            aimCaption = aimCaption,
                        )
                    },
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        LifeChapterLacquer.ALARM_EDIT_BEDTIME_OVERLINE,
                        style = NoopType.overline,
                        color = alarmBedChromeColor().copy(alpha = 0.85f),
                    )
                    Text(
                        hhmm(suggestedBed),
                        style = NoopType.number(36f, weight = FontWeight.Bold),
                        color = alarmBedChromeColor(),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable {
                                showBedPicker = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            .semantics { contentDescription = "Bedtime" },
                    )
                    Text(
                        formatAimSleepDuration(needMin),
                        style = NoopType.caption,
                        color = Palette.textPrimary.copy(alpha = LifeChapterLacquer.ARMED_CAPTION_ALPHA),
                        maxLines = 1,
                    )
                }
                Box(
                    Modifier
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .width(1.dp)
                        .height(64.dp)
                        .background(
                            Palette.restColor.copy(alpha = LifeChapterLacquer.ALARM_DUAL_DIVIDER_ALPHA),
                        ),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        LifeChapterLacquer.ALARM_WAKE_OVERLINE,
                        style = NoopType.overline,
                        color = DomainTheme.Rest.color.copy(alpha = 0.85f),
                    )
                    Text(
                        hhmm(targetMinutes),
                        style = NoopType.number(36f, weight = FontWeight.Bold),
                        color = if (honestlyArmed) {
                            Palette.textPrimary
                        } else if (enabled) {
                            Palette.effortColor
                        } else {
                            Palette.textPrimary
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable {
                                showWakePicker = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            .semantics { contentDescription = alarmWakeUpTimeA11y() },
                    )
                    Text(
                        "by ${hhmm(deadline)} · ${windowMinutes}m",
                        style = NoopType.caption,
                        color = Palette.textPrimary.copy(alpha = LifeChapterLacquer.BRIDGE_CAPTION_ALPHA),
                        maxLines = 1,
                    )
                }
            }
            // #106 — Wake me up immediately under wake clock (exact continue via onEnabledChange).
            ToggleRowLocal(
                label = LifeChapterLacquer.ALARM_WAKE_ME_UP_LABEL,
                help = LifeChapterLacquer.ALARM_WAKE_ME_UP_HELP,
                checked = enabled,
                onChange = onEnabledChange,
            )
            if (enabled) {
                // Felt wake range — soft span (Arm control twin).
                WakeWindowSpanBar(
                    windowMinutes = windowMinutes,
                    maxWindow = com.noop.alarm.SmartAlarmStore.WINDOW_MAX,
                    accent = DomainTheme.Rest.color,
                    startLabel = hhmm(targetMinutes),
                    endLabel = hhmm(deadline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                )
                Text(
                    alarmDeadlineCue(enabled, canExact, hhmm(deadline)),
                    style = NoopType.footnote,
                    color = if (honestlyArmed) {
                        Palette.textPrimary.copy(alpha = LifeChapterLacquer.BRIDGE_CAPTION_ALPHA)
                    } else {
                        Palette.effortColor
                    },
                    modifier = if (!canExact) {
                        Modifier
                            .clickable(onClick = onRequestExactAccess)
                            .semantics { contentDescription = alarmOpenExactSettingsA11y() }
                    } else {
                        Modifier
                    },
                )
                if (!canExact) {
                    Text(
                        alarmWindowGuaranteeCaption(hhmm(deadline)),
                        style = NoopType.footnote,
                        color = Palette.statusWarning,
                        modifier = Modifier
                            .clickable(onClick = onRequestExactAccess)
                            .semantics { contentDescription = alarmOpenExactSettingsA11y() },
                    )
                } else {
                    Text(
                        alarmWindowBackupCaption(hhmm(deadline)),
                        style = NoopType.footnote,
                        color = Palette.textPrimary.copy(alpha = LifeChapterLacquer.BRIDGE_CAPTION_ALPHA),
                    )
                }
            } else {
                Text(
                    alarmMoonQuietCaption(),
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
        }
        if (showBedPicker) {
            NoopTimePickerDialog(
                title = "Bedtime",
                initialMinutes = suggestedBed,
                onDismiss = { showBedPicker = false },
                onConfirm = { picked ->
                    // Keep sleep-need span; move wake = bed + need.
                    onTargetChange((picked + needMin) % (24 * 60))
                    showBedPicker = false
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
            )
        }
        if (showWakePicker) {
            NoopTimePickerDialog(
                title = "Wake up time",
                initialMinutes = targetMinutes,
                onDismiss = { showWakePicker = false },
                onConfirm = { picked ->
                    onTargetChange(picked)
                    showWakePicker = false
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
            )
        }
    }
}
@Composable
private fun PersonalSleepPlanCard(days: List<com.noop.data.DailyMetric>, targetMinutes: Int) {
    val nights = days.mapNotNull { it.totalSleepMin?.takeIf { minutes -> minutes > 0.0 } }.takeLast(28)
    val typical = nights.takeIf { it.size >= 3 }?.average()
    // ALARM_PAGE #42 — same need floor as WindowCard / Alarm aim (not raw avg ≥450).
    val needMin = sleepNeedMinutesForAlarm(typical)
    val bedtime = if (typical != null) {
        ((targetMinutes - needMin) % (24 * 60) + (24 * 60)) % (24 * 60)
    } else {
        null
    }
    val shape = RoundedCornerShape(LifeChapterLacquer.CORNER_DP.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .frostedCardSurface(
                tint = Palette.restColor,
                cornerRadius = LifeChapterLacquer.CORNER_DP.dp,
                washStrength = LifeChapterLacquer.ALARM_WINDOW_WASH,
            )
            .border(1.dp, Palette.restColor.copy(alpha = LifeChapterLacquer.BORDER_ALPHA), shape)
            .padding(horizontal = 16.dp, vertical = LifeChapterLacquer.PAD_V_DP.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Overline(LifeChapterLacquer.ALARM_SLEEP_PLAN_OVERLINE, color = DomainTheme.Rest.color)
        if (bedtime != null) {
            Text(
                hhmm(bedtime),
                style = NoopType.number(32f, weight = FontWeight.Bold),
                color = alarmBedChromeColor(),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                alarmPlanFootCaption(
                    aimLabel = formatAimSleepDuration(needMin),
                    wakeLabel = hhmm(targetMinutes),
                    nightCount = nights.size,
                ),
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
            Text(
                alarmScheduleCueCaption(),
                style = NoopType.caption,
                color = Palette.textTertiary,
            )
        } else {
            Text(alarmPlanEmptyCaption(), style = NoopType.footnote, color = Palette.textSecondary)
        }
    }
}

@Composable
private fun AlarmSettingsCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(LifeChapterLacquer.CORNER_DP.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .frostedCardSurface(
                tint = Palette.restColor,
                cornerRadius = LifeChapterLacquer.CORNER_DP.dp,
                washStrength = LifeChapterLacquer.ALARM_ARM_WASH,
            )
            .border(1.dp, Palette.restColor.copy(alpha = LifeChapterLacquer.BORDER_ALPHA), shape)
            .padding(horizontal = 18.dp, vertical = LifeChapterLacquer.PAD_V_DP.dp + 3.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Alarm, contentDescription = null, tint = DomainTheme.Rest.color)
            Spacer(Modifier.width(10.dp))
            Text(alarmWakeAlarmHeadline(), style = NoopType.headline, color = Palette.textPrimary)
        }
        content()
    }
}

/** The cross-platform evening wind-down nudge — a gentle reminder, not an alarm. Rest-tinted when on. */
@Composable
private fun WindDownCard(vm: AppViewModel) {
    val enabled by vm.windDownEnabled.collectAsStateWithLifecycle()
    val targetMinutes by vm.phoneAlarmTargetMinutes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val store = remember { WindDownStore.from(context) }
    val fireMin = store.nudgeMinuteOfDay(targetMinutes)
    val is24 = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    val fireLabel = com.noop.alarm.NextAlarmDisplay.formatMinuteOfDay(fireMin, is24)
    val shape = RoundedCornerShape(LifeChapterLacquer.CORNER_DP.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .frostedCardSurface(
                tint = Palette.restColor,
                cornerRadius = LifeChapterLacquer.CORNER_DP.dp,
                washStrength = LifeChapterLacquer.ALARM_EXTRAS_WASH,
            )
            .border(1.dp, Palette.restColor.copy(alpha = LifeChapterLacquer.BORDER_ALPHA), shape)
            .padding(horizontal = 18.dp, vertical = LifeChapterLacquer.PAD_V_DP.dp + 3.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // SHIP #134 — evening wind-down overline distinct from Rest/Settings twins.
            Overline(LifeChapterLacquer.ALARM_EVENING_OVERLINE, color = Palette.metricCyan)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = DomainTheme.Rest.color)
                Spacer(Modifier.width(10.dp))
                Text(
                    LifeChapterLacquer.ALARM_WIND_DOWN_NUDGE_TITLE,
                    style = NoopType.title2,
                    color = Palette.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        ToggleRowLocal(
            label = LifeChapterLacquer.ALARM_WIND_DOWN_REMIND_LABEL,
            help = alarmWindDownRemindHelp(),
            checked = enabled,
            onChange = { vm.setWindDownEnabled(it) },
        )
        // ALARM_PAGE #50 — show derived fire time on the card.
        Text(
            alarmWindDownFireCaption(enabled, fireLabel, store.leadMinutes),
            style = NoopType.footnote,
            color = Palette.textTertiary,
            modifier = Modifier.semantics {
                contentDescription = alarmWindDownFireCaption(enabled, fireLabel, store.leadMinutes)
            },
        )
    }
}

@Composable
private fun ExplanationCard() {
    val shape = RoundedCornerShape(LifeChapterLacquer.CORNER_DP.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .frostedCardSurface(
                tint = Palette.restColor,
                cornerRadius = LifeChapterLacquer.CORNER_DP.dp,
                washStrength = LifeChapterLacquer.ALARM_EXPLAIN_SURFACE,
            )
            .border(1.dp, Palette.restColor.copy(alpha = LifeChapterLacquer.BORDER_ALPHA), shape)
            .padding(horizontal = 16.dp, vertical = LifeChapterLacquer.PAD_V_DP.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Overline(LifeChapterLacquer.ALARM_HOW_IT_WORKS_OVERLINE, color = DomainTheme.Rest.color)
        Text(LifeChapterLacquer.ALARM_HOW_SMART_WAKE_TITLE, style = NoopType.subhead, color = Palette.textPrimary)
        Text(
            alarmHowSmartWakeBody(),
            style = NoopType.footnote, color = Palette.textTertiary,
        )
    }
}

// MARK: - Window stepper (5–90 min in 5-min steps; matches SmartAlarmStore)

/**
 * Arm wake-window control — earliest TimeChip, soft span track, deadline clock, length stepper.
 * Replaces the old wordy dual-row help with one interactive range the user can feel.
 */
@Composable
private fun WakeWindowControl(
    targetMinutes: Int,
    windowMinutes: Int,
    honestlyArmed: Boolean,
    onTargetPicked: (Int) -> Unit,
    onWindowChange: (Int) -> Unit,
) {
    val deadline = (targetMinutes + windowMinutes) % (24 * 60)
    val startLabel = hhmm(targetMinutes)
    val endLabel = hhmm(deadline)
    val accent = if (honestlyArmed) DomainTheme.Rest.color else Palette.effortColor
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            alarmWakeSpanOverline(),
            style = NoopType.overline,
            color = accent.copy(alpha = 0.85f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    alarmEarliestOverline(),
                    style = NoopType.overline,
                    color = accent.copy(alpha = 0.75f),
                )
                TimeChip(
                    minutes = targetMinutes,
                    accessibilityLabel = LifeChapterLacquer.ALARM_EARLIEST_WAKE_A11Y,
                    onPicked = onTargetPicked,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    alarmDeadlineOverline(),
                    style = NoopType.overline,
                    color = accent.copy(alpha = 0.75f),
                )
                Text(
                    endLabel,
                    style = NoopType.number(28f, weight = FontWeight.Bold),
                    color = if (honestlyArmed) Palette.textPrimary else Palette.effortColor,
                    modifier = Modifier.semantics {
                        contentDescription = "Deadline $endLabel"
                    },
                )
            }
        }
        WakeWindowSpanBar(
            windowMinutes = windowMinutes,
            maxWindow = com.noop.alarm.SmartAlarmStore.WINDOW_MAX,
            accent = accent,
            startLabel = startLabel,
            endLabel = endLabel,
            modifier = Modifier.fillMaxWidth(),
        )
        WindowStepper(
            windowMinutes = windowMinutes,
            deadlineLabel = endLabel,
            onChange = onWindowChange,
        )
    }
}

@Composable
private fun WindowStepper(windowMinutes: Int, deadlineLabel: String, onChange: (Int) -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val minW = com.noop.alarm.SmartAlarmStore.WINDOW_MIN
    val maxW = com.noop.alarm.SmartAlarmStore.WINDOW_MAX
    // ALARM_STYLE #25 — match plan-card vertical rhythm (12dp gaps like Arm lacquer).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StepperButton(
            symbol = "−",
            onClick = {
                val next = (windowMinutes - 5).coerceAtLeast(minW)
                if (next != windowMinutes) {
                    onChange(next)
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                }
            },
            label = LifeChapterLacquer.ALARM_SHORTEN_WINDOW_A11Y,
            accelerateOnHold = true,
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                alarmWindowByDeadlineCaption(windowMinutes, deadlineLabel),
                style = NoopType.bodyNumber,
                color = Palette.textPrimary,
            )
            Text(
                alarmWindowLengthLabel(),
                style = NoopType.caption,
                color = Palette.textTertiary,
            )
        }
        StepperButton(
            symbol = "+",
            onClick = {
                val next = (windowMinutes + 5).coerceAtMost(maxW)
                if (next != windowMinutes) {
                    onChange(next)
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                }
            },
            label = LifeChapterLacquer.ALARM_LENGTHEN_WINDOW_A11Y,
            accelerateOnHold = true,
        )
    }
}

// MARK: - Local toggle / divider (mirror the AutomationsScreen idiom, kept local to this lane's file)

@Composable
private fun ToggleRowLocal(label: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = NoopType.body, color = Palette.textPrimary)
            Text(help, style = NoopType.footnote, color = Palette.textTertiary)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.surfaceBase,
                checkedTrackColor = Palette.accent,
                uncheckedThumbColor = Palette.textSecondary,
                uncheckedTrackColor = Palette.surfaceInset,
                uncheckedBorderColor = Palette.hairline,
            ),
        )
    }
}

@Composable
private fun RowDividerLocal() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Palette.hairline),
    )
}

// MARK: - Helpers

private fun hhmm(minutes: Int): String {
    val m = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
    return "%02d:%02d".format(m / 60, m % 60)
}

/** Open the system page where the user grants the exact-alarm special-access permission (API 31+).
 *  There's no runtime dialog for this; the user toggles it in Settings and returns. */
internal fun requestExactAlarmAccess(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        // Fall back to the app-details page if the OEM lacks the specific action.
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** Open app notification settings so Armed deadline can sound (#116). */
internal fun requestNotificationSettingsAccess(context: android.content.Context) {
    runCatching {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
