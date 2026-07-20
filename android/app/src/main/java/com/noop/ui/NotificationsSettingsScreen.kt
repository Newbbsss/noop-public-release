package com.noop.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.notif.CallAlertController
import com.noop.notif.CallAlertSource
import java.util.Calendar

// MARK: - NotificationsSettingsScreen
//
// Android port of NotificationSettingsView.swift. Choose which apps tap your wrist and
// how (per-app buzz pattern), with a master switch and overnight quiet hours.
//
// macOS resolves real installed apps via LaunchServices/NSWorkspace. Android restricts
// package visibility (API 30+) and there is no equivalent "notification-capable app"
// query, so we ship a curated catalog of common notification apps grouped exactly like
// the Mac screen. Preferences persist in SharedPreferences (the Android counterpart to
// UserDefaults); when the background bridge ships it reads the same prefs.
//
// Delivery requires a NotificationListenerService with Notification Access granted — the
// behaviour card deep-links to Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS for that.

// MARK: - Domain model (mirrors NotificationSettingsStore.swift)

/** Haptic pattern fired on the strap; only the repeat count varies. `label` stays EN for prefs/JVM. */
internal enum class BuzzPattern(val label: String, val loops: Int, @StringRes val labelRes: Int) {
    Single("Single", 1, R.string.notif_pattern_single),
    Double("Double", 2, R.string.notif_pattern_double),
    Triple("Triple", 3, R.string.notif_pattern_triple),
    Long("Long", 5, R.string.notif_pattern_long),
}

/** Grouping for the settings screen, with its header icon + default pattern. `title` stays EN for JVM. */
internal enum class NotifCategory(
    val title: String,
    val icon: ImageVector,
    val defaultPattern: BuzzPattern,
    @StringRes val titleRes: Int,
) {
    Email("Email", Icons.Filled.Email, BuzzPattern.Double, R.string.notif_cat_email),
    Messaging("Messaging", Icons.AutoMirrored.Filled.Chat, BuzzPattern.Single, R.string.notif_cat_messaging),
    Meetings("Meetings", Icons.Filled.Videocam, BuzzPattern.Triple, R.string.notif_cat_meetings),
    Calendar("Calendar & Reminders", Icons.Filled.CalendarMonth, BuzzPattern.Double, R.string.notif_cat_calendar),
}

/** A notification-capable app NOOP can mirror to the wrist. `id` is the persistence key. */
internal data class NotifApp(
    val id: String,
    val name: String,
    val category: NotifCategory,
    val glyph: ImageVector,
)

/**
 * Curated catalog of common Android notification apps, grouped to match the Mac screen.
 * Unlike macOS we cannot enumerate which are actually installed (restricted package
 * visibility), so we present the full set as configurable examples.
 */
private val notifCatalog: List<NotifApp> = listOf(
    NotifApp("com.google.android.gm", "Gmail", NotifCategory.Email, Icons.Filled.Email),
    NotifApp("com.microsoft.office.outlook", "Outlook", NotifCategory.Email, Icons.Filled.Email),
    NotifApp("com.whatsapp", "WhatsApp", NotifCategory.Messaging, Icons.AutoMirrored.Filled.Chat),
    NotifApp("com.google.android.apps.messaging", "Messages", NotifCategory.Messaging, Icons.AutoMirrored.Filled.Chat),
    NotifApp("com.Slack", "Slack", NotifCategory.Messaging, Icons.AutoMirrored.Filled.Chat),
    NotifApp("org.telegram.messenger", "Telegram", NotifCategory.Messaging, Icons.AutoMirrored.Filled.Chat),
    // Teams' ringing-call notifications are handled by the Calls card below (VoIP path). This
    // per-app row covers everything else Teams sends to the shade (chats, @-mentions, channel
    // posts), which read as messages, so it lives under Messaging with the chat glyph.
    NotifApp("com.microsoft.teams", "Microsoft Teams", NotifCategory.Messaging, Icons.AutoMirrored.Filled.Chat),
    NotifApp("us.zoom.videomeetings", "Zoom", NotifCategory.Meetings, Icons.Filled.Videocam),
    NotifApp("com.google.android.calendar", "Calendar", NotifCategory.Calendar, Icons.Filled.CalendarMonth),
)

private fun appsIn(category: NotifCategory): List<NotifApp> =
    notifCatalog.filter { it.category == category }

private val activeCategories: List<NotifCategory> =
    NotifCategory.entries.filter { appsIn(it).isNotEmpty() }

// MARK: - SharedPreferences store (mirrors the UserDefaults-backed Swift store)

/**
 * Plain-prefs store for wrist-alert settings (the AI key uses encrypted prefs; these are
 * non-secret toggles). Per-app prefs are flattened to `app.<id>.enabled` / `app.<id>.pattern`
 * keys so no JSON dependency is needed.
 */
internal object NotifPrefs {
    private const val FILE = "noop_notif_prefs"
    const val MASTER = "notif.masterEnabled"
    /** Catch-all: buzz for any app NOT in the curated catalog (Android can't enumerate installed
     *  apps, so this is how a user covers BeReal/etc. that aren't listed). Opt-in, default OFF. (#168) */
    const val ALL_OTHER = "notif.allOtherApps"
    const val WORN = "notif.onlyWhenWorn"
    const val QUIET = "notif.quietHoursEnabled"
    const val QUIET_START = "notif.quietStartMinutes"
    const val QUIET_END = "notif.quietEndMinutes"
    const val CALLS_MASTER = "notif.calls.masterEnabled"
    const val CALLS_PHONE = "notif.calls.phoneEnabled"
    const val CALLS_VOIP = "notif.calls.voipEnabled"
    const val CALLS_PATTERN = "notif.calls.pattern"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getBool(ctx: Context, key: String, default: Boolean) =
        prefs(ctx).getBoolean(key, default)

    fun setBool(ctx: Context, key: String, value: Boolean) =
        prefs(ctx).edit().putBoolean(key, value).apply()

    fun getInt(ctx: Context, key: String, default: Int) =
        prefs(ctx).getInt(key, default)

    fun setInt(ctx: Context, key: String, value: Int) =
        prefs(ctx).edit().putInt(key, value).apply()

    fun appEnabled(ctx: Context, id: String): Boolean =
        prefs(ctx).getBoolean("app.$id.enabled", false) // opt-in, default OFF

    fun setAppEnabled(ctx: Context, id: String, value: Boolean) =
        prefs(ctx).edit().putBoolean("app.$id.enabled", value).apply()

    fun appPattern(ctx: Context, app: NotifApp): BuzzPattern {
        val name = prefs(ctx).getString("app.${app.id}.pattern", null)
        return BuzzPattern.entries.firstOrNull { it.name == name } ?: app.category.defaultPattern
    }

    fun setAppPattern(ctx: Context, id: String, pattern: BuzzPattern) =
        prefs(ctx).edit().putString("app.$id.pattern", pattern.name).apply()

    /** Buzz loop-count for [pkg] (for the notification listener; no NotifApp needed). Defaults to
     *  Double if no per-app pattern was chosen. */
    fun appLoops(ctx: Context, pkg: String): Int {
        val name = prefs(ctx).getString("app.$pkg.pattern", null)
        return BuzzPattern.entries.firstOrNull { it.name == name }?.loops ?: BuzzPattern.Double.loops
    }

    fun callPattern(ctx: Context): BuzzPattern {
        val name = prefs(ctx).getString(CALLS_PATTERN, null)
        return BuzzPattern.entries.firstOrNull { it.name == name } ?: BuzzPattern.Triple
    }

    fun setCallPattern(ctx: Context, pattern: BuzzPattern) =
        prefs(ctx).edit().putString(CALLS_PATTERN, pattern.name).apply()

    fun callLoops(ctx: Context): Int = callPattern(ctx).loops

    fun inQuietHours(ctx: Context): Boolean {
        if (!getBool(ctx, QUIET, false)) return false
        val start = getInt(ctx, QUIET_START, 22 * 60)
        val end = getInt(ctx, QUIET_END, 7 * 60)
        val cal = Calendar.getInstance()
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        // Quiet window may wrap midnight (e.g. 22:00 -> 07:00).
        return if (start <= end) now in start until end else (now >= start || now < end)
    }
}

// MARK: - Screen

@Composable
fun NotificationsSettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val live by vm.live.collectAsStateWithLifecycle()

    // Header settings, seeded from prefs once and written through on change.
    var masterEnabled by remember { mutableStateOf(NotifPrefs.getBool(context, NotifPrefs.MASTER, false)) }
    var onlyWhenWorn by remember { mutableStateOf(NotifPrefs.getBool(context, NotifPrefs.WORN, true)) }
    var allOtherApps by remember { mutableStateOf(NotifPrefs.getBool(context, NotifPrefs.ALL_OTHER, false)) }
    var quietHoursEnabled by remember { mutableStateOf(NotifPrefs.getBool(context, NotifPrefs.QUIET, false)) }
    var quietStartMinutes by remember { mutableStateOf(NotifPrefs.getInt(context, NotifPrefs.QUIET_START, 22 * 60)) }
    var quietEndMinutes by remember { mutableStateOf(NotifPrefs.getInt(context, NotifPrefs.QUIET_END, 7 * 60)) }
    var callsEnabled by remember { mutableStateOf(NotifPrefs.getBool(context, NotifPrefs.CALLS_MASTER, false)) }
    var phoneCallsEnabled by remember { mutableStateOf(NotifPrefs.getBool(context, NotifPrefs.CALLS_PHONE, false)) }
    var voipCallsEnabled by remember { mutableStateOf(NotifPrefs.getBool(context, NotifPrefs.CALLS_VOIP, false)) }
    var callsPattern by remember { mutableStateOf(NotifPrefs.callPattern(context)) }
    // Scheduled report notifications (#517) — opt-in, default OFF. SharedPreferences isn't reactive, so
    // each Switch mirrors into local state and writes straight through to NoopPrefs.
    var morningReport by remember { mutableStateOf(NoopPrefs.morningReportEnabled(context)) }
    var postWorkoutReport by remember { mutableStateOf(NoopPrefs.postWorkoutReportEnabled(context)) }
    var phonePermissionDenied by remember { mutableStateOf(false) }
    val phonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        phoneCallsEnabled = granted
        phonePermissionDenied = !granted
        NotifPrefs.setBool(context, NotifPrefs.CALLS_PHONE, granted)
    }

    // Per-app enabled state, seeded from prefs so the UI is reactive within the session.
    val enabledState: SnapshotStateMap<String, Boolean> = remember {
        mutableStateMapOf<String, Boolean>().apply {
            notifCatalog.forEach { put(it.id, NotifPrefs.appEnabled(context, it.id)) }
        }
    }
    val patternState: SnapshotStateMap<String, BuzzPattern> = remember {
        mutableStateMapOf<String, BuzzPattern>().apply {
            notifCatalog.forEach { put(it.id, NotifPrefs.appPattern(context, it)) }
        }
    }
    val enabledCount = enabledState.values.count { it }

    ScreenScaffold(
        title = stringResource(R.string.nav_notifications),
        subtitle = stringResource(R.string.notif_subtitle),
    ) {
        // #108 — Alarms vs Notifications vs Cycle reminders.
        Text(
            alarmTaxonomyGlossaryLocalized(context),
            style = NoopType.footnote,
            color = Palette.textTertiary,
            modifier = Modifier.fillMaxWidth(),
        )
        // MARK: Master card
        AlertSection(
            icon = Icons.Filled.NotificationsActive,
            title = stringResource(R.string.notif_wrist_alerts_title),
            blurb = stringResource(R.string.notif_wrist_alerts_blurb),
        ) {
            val enableLabel = stringResource(R.string.notif_enable_wrist_alerts)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(enableLabel, style = NoopType.body, color = Palette.textPrimary)
                Spacer(Modifier.weight(1f))
                NoopSwitch(
                    checked = masterEnabled,
                    onChange = {
                        masterEnabled = it
                        NotifPrefs.setBool(context, NotifPrefs.MASTER, it)
                    },
                    label = enableLabel,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatePill(strapPillTitle(context, live), tone = strapPillTone(live), pulsing = live.connected)
                StatePill(
                    stringResource(
                        if (enabledCount == 1) R.string.notif_apps_on_one else R.string.notif_apps_on_many,
                        enabledCount,
                    ),
                    tone = if (enabledCount > 0) StrandTone.Positive else StrandTone.Neutral,
                    showsDot = false,
                )
                Spacer(Modifier.weight(1f))
                PillButton(
                    label = stringResource(R.string.notif_test_buzz),
                    icon = Icons.Filled.GraphicEq,
                    enabled = live.bonded,
                    onClick = { vm.buzz(loops = 2) },
                )
            }

            DeliveryNote()
        }

        CallsCard(
            masterEnabled = masterEnabled,
            callsEnabled = callsEnabled,
            phoneCallsEnabled = phoneCallsEnabled,
            voipCallsEnabled = voipCallsEnabled,
            pattern = callsPattern,
            bonded = live.bonded,
            permissionDenied = phonePermissionDenied,
            onCallsEnabled = {
                callsEnabled = it
                NotifPrefs.setBool(context, NotifPrefs.CALLS_MASTER, it)
                if (!it) CallAlertController.stopAll()
            },
            onPhoneCallsEnabled = { value ->
                if (!value) {
                    phoneCallsEnabled = false
                    phonePermissionDenied = false
                    NotifPrefs.setBool(context, NotifPrefs.CALLS_PHONE, false)
                    CallAlertController.stopSource(CallAlertSource.PHONE)
                    return@CallsCard
                }
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    phoneCallsEnabled = true
                    phonePermissionDenied = false
                    NotifPrefs.setBool(context, NotifPrefs.CALLS_PHONE, true)
                } else {
                    phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                }
            },
            onVoipCallsEnabled = {
                voipCallsEnabled = it
                NotifPrefs.setBool(context, NotifPrefs.CALLS_VOIP, it)
                if (!it) CallAlertController.stopSource(CallAlertSource.VOIP)
            },
            onPattern = {
                callsPattern = it
                NotifPrefs.setCallPattern(context, it)
            },
            onTest = { vm.buzz(loops = callsPattern.loops) },
        )

        // MARK: Category cards
        activeCategories.forEach { cat ->
            CategoryCard(
                category = cat,
                apps = appsIn(cat),
                masterEnabled = masterEnabled,
                bonded = live.bonded,
                enabledState = enabledState,
                patternState = patternState,
                onToggle = { app, value ->
                    enabledState[app.id] = value
                    NotifPrefs.setAppEnabled(context, app.id, value)
                },
                onPattern = { app, pattern ->
                    patternState[app.id] = pattern
                    NotifPrefs.setAppPattern(context, app.id, pattern)
                },
                onTest = { app -> vm.buzz(loops = (patternState[app.id] ?: app.category.defaultPattern).loops) },
            )
        }

        // MARK: Behaviour card
        AlertSection(
            icon = Icons.Filled.Tune,
            title = stringResource(R.string.notif_behaviour_title),
            blurb = stringResource(R.string.notif_behaviour_blurb),
        ) {
            FormToggleRow(
                label = stringResource(R.string.notif_only_when_worn),
                help = stringResource(R.string.notif_only_when_worn_help),
                checked = onlyWhenWorn,
                onChange = {
                    onlyWhenWorn = it
                    NotifPrefs.setBool(context, NotifPrefs.WORN, it)
                },
            )
            RowDivider()
            FormToggleRow(
                label = stringResource(R.string.notif_all_other_apps),
                help = stringResource(R.string.notif_all_other_apps_help),
                checked = allOtherApps,
                onChange = {
                    allOtherApps = it
                    NotifPrefs.setBool(context, NotifPrefs.ALL_OTHER, it)
                },
            )
            RowDivider()
            FormToggleRow(
                label = stringResource(R.string.notif_quiet_hours),
                help = stringResource(R.string.notif_quiet_hours_help),
                checked = quietHoursEnabled,
                onChange = {
                    quietHoursEnabled = it
                    NotifPrefs.setBool(context, NotifPrefs.QUIET, it)
                },
            )
            if (quietHoursEnabled) {
                RowDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.notif_quiet_from), style = NoopType.body, color = Palette.textPrimary)
                    TimeChip(
                        minutes = quietStartMinutes,
                        accessibilityLabel = stringResource(R.string.notif_quiet_start_a11y),
                        onPicked = {
                            quietStartMinutes = it
                            NotifPrefs.setInt(context, NotifPrefs.QUIET_START, it)
                        },
                    )
                    Text(stringResource(R.string.notif_quiet_to), style = NoopType.body, color = Palette.textSecondary)
                    TimeChip(
                        minutes = quietEndMinutes,
                        accessibilityLabel = stringResource(R.string.notif_quiet_end_a11y),
                        onPicked = {
                            quietEndMinutes = it
                            NotifPrefs.setInt(context, NotifPrefs.QUIET_END, it)
                        },
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // MARK: Daily reports (#517) — phone notifications, not wrist buzzes. Opt-in, default OFF, no AI.
        AlertSection(
            icon = Icons.Filled.NotificationsActive,
            title = stringResource(R.string.notif_daily_reports_title),
            blurb = stringResource(R.string.notif_daily_reports_blurb),
        ) {
            FormToggleRow(
                label = stringResource(R.string.notif_morning_recap),
                help = stringResource(R.string.notif_morning_recap_help),
                checked = morningReport,
                onChange = {
                    morningReport = it
                    NoopPrefs.setMorningReportEnabled(context, it)
                },
            )
            RowDivider()
            FormToggleRow(
                label = stringResource(R.string.notif_post_workout),
                help = stringResource(R.string.notif_post_workout_help),
                checked = postWorkoutReport,
                onChange = {
                    postWorkoutReport = it
                    NoopPrefs.setPostWorkoutReportEnabled(context, it)
                    // Seed the frontier to the newest existing workout when turning ON, so enabling it
                    // doesn't immediately fire a summary for a session already in history.
                    if (it) vm.seedWorkoutReportFrontier()
                },
            )
        }
    }
}

// MARK: - Strap status (mirrors the three-state mapping from the Mac screen)

private fun strapPillTitle(context: Context, live: com.noop.ble.LiveState): String = when {
    live.connected -> context.getString(R.string.notif_strap_connected)
    live.bonded -> context.getString(R.string.notif_strap_idle)
    else -> context.getString(R.string.notif_strap_not_connected)
}

private fun strapPillTone(live: com.noop.ble.LiveState): StrandTone = when {
    live.connected -> StrandTone.Positive
    live.bonded -> StrandTone.Warning
    else -> StrandTone.Critical
}

@Composable
private fun CallsCard(
    masterEnabled: Boolean,
    callsEnabled: Boolean,
    phoneCallsEnabled: Boolean,
    voipCallsEnabled: Boolean,
    pattern: BuzzPattern,
    bonded: Boolean,
    permissionDenied: Boolean,
    onCallsEnabled: (Boolean) -> Unit,
    onPhoneCallsEnabled: (Boolean) -> Unit,
    onVoipCallsEnabled: (Boolean) -> Unit,
    onPattern: (BuzzPattern) -> Unit,
    onTest: () -> Unit,
) {
    val contentAlpha = if (masterEnabled) 1f else Palette.disabledOpacity
    val buzzCallsLabel = stringResource(R.string.notif_buzz_incoming_calls)
    AlertSection(
        icon = Icons.Filled.Call,
        title = stringResource(R.string.notif_calls_title),
        blurb = stringResource(R.string.notif_calls_blurb),
    ) {
        Column(modifier = Modifier.alphaIf(contentAlpha)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(buzzCallsLabel, style = NoopType.body, color = Palette.textPrimary)
                    Text(
                        stringResource(R.string.notif_buzz_incoming_calls_help),
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                }
                if (callsEnabled) {
                    PatternMenu(pattern = pattern, enabled = masterEnabled, appName = "calls", onSelect = onPattern)
                    TestIconButton(enabled = masterEnabled && bonded, appName = "calls", onClick = onTest)
                }
                NoopSwitch(
                    checked = callsEnabled,
                    onChange = onCallsEnabled,
                    enabled = masterEnabled,
                    label = buzzCallsLabel,
                )
            }
            if (callsEnabled) {
                RowDivider()
                FormToggleRow(
                    label = stringResource(R.string.notif_phone_calls),
                    help = stringResource(R.string.notif_phone_calls_help),
                    checked = phoneCallsEnabled,
                    enabled = masterEnabled,
                    onChange = onPhoneCallsEnabled,
                )
                if (permissionDenied) {
                    Text(
                        stringResource(R.string.notif_phone_permission_denied),
                        style = NoopType.footnote,
                        color = Palette.statusCritical,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    )
                }
                RowDivider()
                FormToggleRow(
                    label = stringResource(R.string.notif_voip_calls),
                    help = stringResource(R.string.notif_voip_calls_help),
                    checked = voipCallsEnabled,
                    enabled = masterEnabled,
                    onChange = onVoipCallsEnabled,
                )
            }
        }
    }
}

// MARK: - Delivery note (Notification Access requirement + deep link)

@Composable
private fun DeliveryNote() {
    val context = LocalContext.current
    val shape = RoundedCornerShape(10.dp)
    val openAccessA11y = stringResource(R.string.notif_open_notification_access_a11y)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surfaceInset)
            .border(1.dp, Palette.accent.copy(alpha = 0.22f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = Palette.accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                stringResource(R.string.notif_delivery_note),
                style = NoopType.footnote,
                color = Palette.textSecondary,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .semantics { contentDescription = openAccessA11y },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = Palette.accent,
                modifier = Modifier.size(14.dp),
            )
            Text(stringResource(R.string.notif_open_notification_access), style = NoopType.caption, color = Palette.accent)
        }
    }
}

// MARK: - Category card (rows of apps, dimmed/disabled when master is off)

@Composable
private fun CategoryCard(
    category: NotifCategory,
    apps: List<NotifApp>,
    masterEnabled: Boolean,
    bonded: Boolean,
    enabledState: SnapshotStateMap<String, Boolean>,
    patternState: SnapshotStateMap<String, BuzzPattern>,
    onToggle: (NotifApp, Boolean) -> Unit,
    onPattern: (NotifApp, BuzzPattern) -> Unit,
    onTest: (NotifApp) -> Unit,
) {
    val contentAlpha = if (masterEnabled) 1f else Palette.disabledOpacity
    AlertSection(icon = category.icon, title = stringResource(category.titleRes)) {
        Column(modifier = Modifier.alphaIf(contentAlpha)) {
            apps.forEachIndexed { idx, app ->
                AppRow(
                    app = app,
                    enabled = enabledState[app.id] ?: false,
                    pattern = patternState[app.id] ?: app.category.defaultPattern,
                    interactive = masterEnabled,
                    bonded = bonded,
                    onToggle = { onToggle(app, it) },
                    onPattern = { onPattern(app, it) },
                    onTest = { onTest(app) },
                )
                if (idx < apps.size - 1) RowDivider()
            }
        }
    }
}

@Composable
private fun AppRow(
    app: NotifApp,
    enabled: Boolean,
    pattern: BuzzPattern,
    interactive: Boolean,
    bonded: Boolean,
    onToggle: (Boolean) -> Unit,
    onPattern: (BuzzPattern) -> Unit,
    onTest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            // An enabled app reads as a selected row: a soft accentMuted wash behind it.
            .clip(RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.background(Palette.accentMuted) else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // App glyph in a rounded inset tile (stand-in for the real macOS app icon).
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Palette.surfaceInset),
            contentAlignment = Alignment.Center,
        ) {
            Icon(app.glyph, contentDescription = null, tint = Palette.textSecondary, modifier = Modifier.size(18.dp))
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(app.name, style = NoopType.body, color = Palette.textPrimary)
            Text(
                if (enabled) stringResource(R.string.notif_buzzes_wrist) else stringResource(R.string.notif_off),
                style = NoopType.footnote,
                color = if (enabled) Palette.accent else Palette.textTertiary,
            )
        }

        if (enabled) {
            PatternMenu(
                pattern = pattern,
                enabled = interactive,
                appName = app.name,
                onSelect = onPattern,
            )
            TestIconButton(enabled = interactive && bonded, appName = app.name, onClick = onTest)
        }

        NoopSwitch(
            checked = enabled,
            onChange = onToggle,
            enabled = interactive,
            label = stringResource(R.string.notif_app_switch_a11y, app.name),
        )
    }
}

// MARK: - Pattern menu (DropdownMenu replacing the macOS Menu)

@Composable
private fun PatternMenu(
    pattern: BuzzPattern,
    enabled: Boolean,
    appName: String,
    onSelect: (BuzzPattern) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val patternA11y = stringResource(R.string.notif_pattern_a11y, appName)
    Box {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(Palette.surfaceInset)
                .border(1.dp, Palette.hairline, shape)
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .semantics { contentDescription = patternA11y },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = Palette.textSecondary,
                modifier = Modifier.size(12.dp),
            )
            Text(stringResource(pattern.labelRes), style = NoopType.caption, color = Palette.textSecondary)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Palette.surfaceOverlay),
        ) {
            BuzzPattern.entries.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(p.labelRes),
                            style = NoopType.body,
                            color = if (p == pattern) Palette.accent else Palette.textPrimary,
                        )
                    },
                    onClick = {
                        onSelect(p)
                        expanded = false
                    },
                )
            }
        }
    }
}

// MARK: - Test buttons

@Composable
private fun TestIconButton(enabled: Boolean, appName: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val tint = if (enabled) Palette.accent else Palette.textTertiary
    val testA11y = stringResource(R.string.notif_test_buzz_a11y, appName)
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(shape)
            .background(Palette.accent.copy(alpha = if (enabled) 0.12f else 0.04f))
            .border(1.dp, tint.copy(alpha = 0.30f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = testA11y },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun PillButton(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val tint = if (enabled) Palette.accent else Palette.textTertiary
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Palette.accent.copy(alpha = if (enabled) 0.12f else 0.04f))
            .border(1.dp, tint.copy(alpha = 0.30f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(label, style = NoopType.caption, color = tint)
    }
}

// MARK: - Time chip (NOOP wheel picker). Shared by Alarm / Automations / quiet hours.
// No Android radial dial — vertical hour/minute steppers on lacquer (Impeccable product density).

/**
 * Dual Bedtime|Wake clock face: hour:minute at [digitSp], AM/PM as a smaller caption so the
 * period never shares the digit size or overflows the half-width column (12h only).
 */
@Composable
fun AlarmWallClockText(
    minutes: Int,
    is24Hour: Boolean,
    digitSp: Float,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Bold,
    contentAlignment: Alignment = Alignment.CenterStart,
) {
    val parts = remember(minutes, is24Hour) {
        com.noop.alarm.NextAlarmDisplay.clockParts(minutes, is24Hour)
    }
    Box(modifier = modifier, contentAlignment = contentAlignment) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                parts.digits,
                style = NoopType.number(digitSp, weight = weight),
                color = color,
                maxLines = 1,
                softWrap = false,
            )
            if (parts.meridiem != null) {
                Text(
                    parts.meridiem,
                    style = NoopType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = color.copy(alpha = 0.78f),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
internal fun TimeChip(
    minutes: Int,
    accessibilityLabel: String,
    onPicked: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    // 12-/24-hour follows Settings → Units → Time format, like the picker dialog it opens.
    val is24 = NoopPrefs.use24HourClock(LocalContext.current)
    Text(
        text = com.noop.alarm.NextAlarmDisplay.formatMinuteOfDay(minutes, is24),
        style = NoopType.number(17f, weight = FontWeight.SemiBold),
        color = Palette.accent,
        modifier = Modifier
            .clip(shape)
            .background(Palette.surfaceInset)
            .border(1.dp, Palette.hairline, shape)
            .clickable { showPicker = true }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { contentDescription = accessibilityLabel },
    )

    if (showPicker) {
        NoopTimePickerDialog(
            title = accessibilityLabel,
            initialMinutes = minutes,
            onDismiss = { showPicker = false },
            onConfirm = { picked ->
                onPicked(picked)
                showPicker = false
            },
        )
    }
}

/**
 * Styled minute-of-day picker — hour + minute columns with ± steppers (no radial clock dial).
 * Rest lacquer, Material 3 dialog chrome, NOOP tokens. Used by [TimeChip] and Sleep bed/wake edits.
 */
@Composable
fun NoopTimePickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit,
    confirmLabel: String = stringResource(R.string.notif_time_set),
) {
    val context = LocalContext.current
    // Fresh read (not remember) so Units → Time format applies if Settings changed this session.
    val is24 = NoopPrefs.use24HourClock(context)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val seed = ((initialMinutes % (24 * 60)) + 24 * 60) % (24 * 60)
    var hour by remember(seed) { mutableStateOf(seed / 60) }
    var minute by remember(seed) { mutableStateOf(seed % 60) }
    val shape = RoundedCornerShape(LifeChapterLacquer.CORNER_DP.dp)
    val accent = DomainTheme.Rest.color
    val hourLabel = stringResource(R.string.notif_time_hour)
    val minLabel = stringResource(R.string.notif_time_min)
    val pickerA11y = stringResource(R.string.notif_time_picker_a11y, title)
    val steppersA11y = stringResource(R.string.notif_time_steppers_a11y)
    val cancelA11y = stringResource(R.string.notif_time_cancel_a11y)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(shape)
                .background(Palette.surfaceOverlay)
                .border(1.dp, accent.copy(alpha = LifeChapterLacquer.BORDER_ALPHA), shape)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = NoopType.headline, color = Palette.textPrimary,
                modifier = Modifier.semantics { contentDescription = pickerA11y })
            val reduced = rememberReduceMotion()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = steppersA11y
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoopTimeWheelColumn(
                    value = if (is24) hour else ((hour + 11) % 12) + 1,
                    label = hourLabel,
                    accent = accent,
                    // 12-hour face reads "7" not "07"; minutes stay zero-padded.
                    padTwo = is24,
                    reducedMotion = reduced,
                    onBump = { delta ->
                        hour = (hour + delta + 24) % 24
                        haptic.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
                        )
                    },
                )
                Text(
                    ":",
                    style = NoopType.number(36f, weight = FontWeight.Bold),
                    color = Palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                NoopTimeWheelColumn(
                    value = minute,
                    label = minLabel,
                    accent = accent,
                    padTwo = true,
                    reducedMotion = reduced,
                    onBump = { delta ->
                        // 1-min steps; wrap within the hour.
                        minute = (minute + delta + 60) % 60
                        haptic.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
                        )
                    },
                )
                if (!is24) {
                    Spacer(Modifier.width(10.dp))
                    NoopAmPmToggle(
                        isPm = hour >= 12,
                        accent = accent,
                        onToggle = {
                            hour = (hour + 12) % 24
                            haptic.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
                            )
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Text(
                    stringResource(R.string.notif_time_cancel),
                    style = NoopType.body,
                    color = Palette.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .semantics { contentDescription = cancelA11y },
                )
                Text(
                    confirmLabel,
                    style = NoopType.body.copy(fontWeight = FontWeight.SemiBold),
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.14f))
                        .clickable { onConfirm(hour * 60 + minute) }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .semantics { contentDescription = confirmLabel },
                )
            }
        }
    }
}

@Composable
private fun NoopTimeWheelColumn(
    value: Int,
    label: String,
    accent: Color,
    onBump: (delta: Int) -> Unit,
    padTwo: Boolean = true,
    reducedMotion: Boolean = false,
) {
    val display = if (padTwo) "%02d".format(value) else value.toString()
    val cell = RoundedCornerShape(14.dp)
    val decreaseA11y = stringResource(R.string.notif_time_decrease_a11y, label)
    val increaseA11y = stringResource(R.string.notif_time_increase_a11y, label)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics { contentDescription = "$label $display" },
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(50))
                .clickable { onBump(-1) }
                .semantics { contentDescription = decreaseA11y },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "−",
                style = NoopType.number(22f, weight = FontWeight.Medium),
                color = accent,
                textAlign = TextAlign.Center,
            )
        }
        Box(
            modifier = Modifier
                .width(72.dp)
                .clip(cell)
                .background(Palette.surfaceInset)
                .border(1.dp, accent.copy(alpha = 0.35f), cell)
                .padding(vertical = 12.dp)
                .semantics { contentDescription = "$label value $display" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                display,
                style = NoopType.number(32f, weight = FontWeight.Bold),
                color = Palette.textPrimary,
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(50))
                .clickable { onBump(1) }
                .semantics { contentDescription = increaseA11y },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "+",
                style = NoopType.number(22f, weight = FontWeight.Medium),
                color = accent,
                textAlign = TextAlign.Center,
            )
        }
        Text(label, style = NoopType.caption, color = Palette.textTertiary)
    }
    // reducedMotion reserved for future hold-to-accelerate; steppers stay tap-only (#102/#133).
    @Suppress("UNUSED_PARAMETER")
    val _rm = reducedMotion
}

@Composable
private fun NoopAmPmToggle(
    isPm: Boolean,
    accent: Color,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    // Footnote + tight width — subordinate to 32sp hour/minute wheels; never spill the picker row.
    Column(
        modifier = Modifier
            .widthIn(max = 36.dp)
            .heightIn(min = 56.dp)
            .clip(shape)
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(
            "AM",
            style = NoopType.footnote.copy(
                fontWeight = if (!isPm) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (!isPm) accent else Palette.textTertiary,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
        Text(
            "PM",
            style = NoopType.footnote.copy(
                fontWeight = if (isPm) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (isPm) accent else Palette.textTertiary,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}

// MARK: - Section card (icon + title header, optional blurb, content)

@Composable
private fun AlertSection(
    icon: ImageVector,
    title: String,
    blurb: String? = null,
    overline: String = stringResource(R.string.notif_alerts_overline),
    content: @Composable () -> Unit,
) {
    NoopCard(padding = 20.dp, tint = Palette.accent) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Overline(overline)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(icon, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(18.dp))
                    Text(title, style = NoopType.title2, color = Palette.textPrimary)
                }
            }
            if (blurb != null) {
                Text(blurb, style = NoopType.subhead, color = Palette.textSecondary)
            }
            content()
        }
    }
}

// MARK: - Label + help + switch row (mirrors FormToggleRow)

@Composable
private fun FormToggleRow(
    label: String,
    help: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = NoopType.body, color = Palette.textPrimary)
            Text(help, style = NoopType.footnote, color = Palette.textTertiary)
        }
        Spacer(Modifier.width(16.dp))
        NoopSwitch(checked = checked, onChange = onChange, enabled = enabled, label = label)
    }
}

// MARK: - Shared bits

@Composable
private fun NoopSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    label: String,
) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Palette.surfaceBase,
            checkedTrackColor = Palette.accent,
            uncheckedThumbColor = Palette.textSecondary,
            uncheckedTrackColor = Palette.surfaceInset,
            uncheckedBorderColor = Palette.hairline,
        ),
        modifier = Modifier.semantics { contentDescription = label },
    )
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(vertical = 4.dp)
            .background(Palette.hairline),
    )
}

/** Apply a uniform alpha to a subtree (dims disabled category content). */
private fun Modifier.alphaIf(value: Float): Modifier = this.alpha(value)
