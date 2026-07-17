package com.noop.notif

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.noop.R
import com.noop.ui.NoopPrefs
import com.noop.ui.appLaunchIntent

/**
 * Pure battery-alert decision logic so it's JVM-testable (IllnessAlertPolicy idiom). The two
 * `*Alerted` flags are PERSISTED state (NoopPrefs), so the decision survives process death — no
 * in-memory previous-pct crossing, which would re-fire on every 15↔14 jitter and reset on restart.
 *
 * A 25% re-arm band (hysteresis) means a battery hovering near 15% fires the low alert exactly once
 * per discharge cycle; the full alert re-arms only after the cell drops back below 100%.
 */
internal object BatteryAlertPolicy {
    const val LOW_THRESHOLD = 15
    const val LOW_REARM_ABOVE = 25
    const val FULL_THRESHOLD = 100

    data class Decision(
        val fireLow: Boolean,
        val fireFull: Boolean,
        val clearFull: Boolean,
        val newLowAlerted: Boolean,
        val newFullAlerted: Boolean,
    )

    /**
     * @param pct          current strap battery percentage (rounded to Int)
     * @param charging     charging state (null = unknown)
     * @param lowAlerted   persisted: has the low alert already fired this discharge cycle?
     * @param fullAlerted  persisted: has the full alert already fired since the last drop below 100?
     *
     * `clearFull` (#514): the strap was showing a "fully charged" notification and has now dropped
     * below 100% — the standing note is stale, so cancel it. It's exactly the full re-arm
     * transition (fullAlerted && pct < FULL_THRESHOLD), surfaced so the notifier can pull the
     * delivered full-charge notification by its id.
     */
    /** Charge-limit re-arm hysteresis: only a genuine discharge this far below the limit re-arms it,
     *  never the charging flag's true→null flicker (#80 lesson — the charge bit ticks ~8-minutely). */
    const val LIMIT_REARM_DROP = 5

    data class LimitDecision(val fire: Boolean, val newAlerted: Boolean)

    /**
     * "Charging started" session gate (TOP 2026-07-13). The strap's charge bit flickers true→null
     * ~every 8 min; clearing the alerted flag on `charging != true` re-fired "Charging" spam.
     * Only clear on an explicit `charging == false`, or a genuine discharge of [CHARGING_REARM_DROP]
     * points below the pct captured at fire (mirrors [evaluateLimit] hysteresis).
     */
    const val CHARGING_REARM_DROP = 5

    data class ChargingStartedDecision(
        val fire: Boolean,
        val newAlerted: Boolean,
        /** Persist the SoC at fire so a later discharge can re-arm; null = clear stored pct. */
        val newAnchorPct: Int?,
    )

    fun evaluateChargingStarted(
        charging: Boolean?,
        pct: Int?,
        alerted: Boolean,
        anchorPct: Int?,
        /** When true (app RESUMED), never fire — StrapChargingHost owns the foreground UI. */
        appForeground: Boolean = false,
    ): ChargingStartedDecision {
        var a = alerted
        var anchor = anchorPct
        // Explicit off-charger clears the session.
        if (charging == false) {
            return ChargingStartedDecision(fire = false, newAlerted = false, newAnchorPct = null)
        }
        // Genuine discharge while the bit is unknown/null also re-arms (unplugged mid-flicker).
        if (a && anchor != null && pct != null && pct <= anchor - CHARGING_REARM_DROP) {
            a = false
            anchor = null
        }
        val fire = !a && charging == true && !appForeground
        if (fire) {
            a = true
            anchor = pct
        }
        return ChargingStartedDecision(fire = fire, newAlerted = a, newAnchorPct = if (a) anchor else null)
    }

    /**
     * Charge-limit alert: fire once per charge session when a CHARGING strap crosses the user's limit
     * (Li-ion longevity — unplug at ~80% instead of soaking at 100%). [limitPct] outside 50..99 means
     * the feature is off. `charging == true` is required at the crossing (LiveState.charging is sticky
     * across the strap's flickery reporting, so a mid-charge crossing still sees true); re-arm only on
     * a genuine [LIMIT_REARM_DROP] discharge below the limit, mirroring the low alert's #80 fix.
     */
    fun evaluateLimit(pct: Int, charging: Boolean?, limitPct: Int, alerted: Boolean): LimitDecision {
        if (limitPct < 50 || limitPct > 99) return LimitDecision(fire = false, newAlerted = false)
        var a = alerted
        if (pct <= limitPct - LIMIT_REARM_DROP) a = false
        val fire = !a && charging == true && pct >= limitPct
        if (fire) a = true
        return LimitDecision(fire, a)
    }

    fun evaluate(pct: Int, charging: Boolean?, lowAlerted: Boolean, fullAlerted: Boolean): Decision {
        var low = lowAlerted
        var full = fullAlerted
        // The stale 100%-full note must be cleared the moment we re-arm below the full line.
        val clearFull = fullAlerted && pct < FULL_THRESHOLD
        // Re-arm (hysteresis) so jitter near a threshold can't re-fire. #80: re-arm ONLY on genuine recovery
        // (pct >= LOW_REARM_ABOVE), NOT on charging. The strap reports its charge bit only every ~8 min, so
        // it flickers true→null; re-arming on `true` then firing on the `null` gap re-fired the low alert
        // repeatedly WHILE charging. `fireLow`'s `charging != true` still suppresses an explicit charging
        // reading, and a null-charging strap (generic/FTMS) still alerts.
        if (pct >= LOW_REARM_ABOVE) low = false
        if (pct < FULL_THRESHOLD) full = false
        // Fire at most once per genuine crossing.
        val fireLow = !low && pct <= LOW_THRESHOLD && charging != true
        val fireFull = !full && pct >= FULL_THRESHOLD
        if (fireLow) low = true
        if (fireFull) full = true
        return Decision(fireLow, fireFull, clearFull, low, full)
    }
}

/**
 * Posts battery-state alerts — low battery (≤15%) and charge-complete (100%) — as real system
 * notifications. Mirrors [IllnessAlertNotifier]'s pattern: called from WhoopConnectionService on
 * every live-state update, gated behind a user setting and the OS notification permission. The
 * once-per-crossing dedupe lives in [BatteryAlertPolicy] over two persisted NoopPrefs flags.
 *
 * With thanks to @ujix (#368) for the original notification copy and channel.
 */
object BatteryAlertNotifier {
    private const val CHANNEL_ID = "noop_battery_alert"
    private const val NOTIF_ID_LOW = 4203
    private const val NOTIF_ID_FULL = 4204
    private const val NOTIF_ID_CHARGING = 4205
    private const val NOTIF_ID_LIMIT = 4206

    /**
     * Process-foreground flag for [BatteryAlertPolicy.evaluateChargingStarted]. Updated by
     * [com.noop.ui.StrapChargingHost] from the Activity lifecycle — not a second source of
     * charging truth, only suppresses the background notification while the full-screen UI owns it.
     */
    object AppProcessState {
        @Volatile
        var isResumed: Boolean = false
    }

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled() + runCatching
    fun onBatteryUpdate(context: Context, currPct: Int?, charging: Boolean?) {
        if (!NoopPrefs.batteryAlerts(context)) return
        // Defensive: never let a notify() throw (revoked POST_NOTIFICATIONS, OEM quirk) crash a collector.
        runCatching {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            ensureChannel(context)

            // Rising edge → "Charging" note (once per charge session). Foreground → full-screen only
            // (StrapChargingHost); background → this notification. Never re-arm on true→null flicker.
            val started = BatteryAlertPolicy.evaluateChargingStarted(
                charging = charging,
                pct = currPct,
                alerted = NoopPrefs.chargingStartedAlerted(context),
                anchorPct = NoopPrefs.chargingStartedAnchorPct(context),
                appForeground = AppProcessState.isResumed,
            )
            NoopPrefs.setChargingStartedAlerted(context, started.newAlerted)
            NoopPrefs.setChargingStartedAnchorPct(context, started.newAnchorPct)
            if (started.fire) {
                val pct = currPct
                val eta = pct?.let { estimateChargerEta(it) }
                val body = buildString {
                    if (pct != null) append("$pct%")
                    if (eta != null) {
                        if (isNotEmpty()) append(" · ")
                        append(eta)
                    }
                    if (isEmpty()) append("Strap on charger")
                    append(" · Limit not on open Bluetooth")
                }
                val n = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_heart)
                    .setContentTitle("Charging")
                    .setContentText(body)
                    .setContentIntent(openAppIntent(context))
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
                NotificationManagerCompat.from(context).notify(NOTIF_ID_CHARGING, n)
            } else if (charging == false) {
                NotificationManagerCompat.from(context).cancel(NOTIF_ID_CHARGING)
            }

            if (currPct == null) return
            val decision = BatteryAlertPolicy.evaluate(
                pct = currPct,
                charging = charging,
                lowAlerted = NoopPrefs.batteryLowAlerted(context),
                fullAlerted = NoopPrefs.batteryFullAlerted(context),
            )
            if (decision.fireLow) {
                val n = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_heart)
                    .setContentTitle("Low battery")
                    .setContentText("Recharge your WHOOP before tonight.")
                    .setContentIntent(openAppIntent(context))
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
                NotificationManagerCompat.from(context).notify(NOTIF_ID_LOW, n)
            }
            if (decision.fireFull) {
                val n = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_heart)
                    .setContentTitle("Strap fully charged")
                    .setContentText("Your WHOOP is at 100%.")
                    .setContentIntent(openAppIntent(context))
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                NotificationManagerCompat.from(context).notify(NOTIF_ID_FULL, n)
            }
            // #514: the strap has dropped below 100% — pull the stale "fully charged" note so it
            // can't linger after the cell discharges. cancel() covers a posted notification; a
            // not-yet-shown one simply no-ops.
            if (decision.clearFull) {
                NotificationManagerCompat.from(context).cancel(NOTIF_ID_FULL)
            }
            // Charge-limit alert: the user asked to be pinged at N% so they can unplug early (Li-ion
            // longevity). Honest scope in the copy — NOOP can't stop the charge, the open link has no
            // limit control. Off unless a limit is set (Settings → strap battery alerts).
            val limitPct = NoopPrefs.chargeLimitPct(context)
            val limitDecision = BatteryAlertPolicy.evaluateLimit(
                pct = currPct,
                charging = charging,
                limitPct = limitPct,
                alerted = NoopPrefs.chargeLimitAlerted(context),
            )
            if (limitDecision.fire) {
                val n = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_heart)
                    .setContentTitle("Charge limit reached")
                    .setContentText(
                        "Strap at $currPct% — your $limitPct% limit. Unplug now to preserve battery " +
                            "health. NOOP can't stop the charge itself (no limit control on open Bluetooth).",
                    )
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            "Strap at $currPct% — your $limitPct% limit. Unplug now to preserve battery " +
                                "health. NOOP can't stop the charge itself (no limit control on open Bluetooth).",
                        ),
                    )
                    .setContentIntent(openAppIntent(context))
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
                NotificationManagerCompat.from(context).notify(NOTIF_ID_LIMIT, n)
            }
            NoopPrefs.setChargeLimitAlerted(context, limitDecision.newAlerted)
            // ALWAYS persist the updated flags — re-arming must stick even when nothing fired.
            NoopPrefs.setBatteryLowAlerted(context, decision.newLowAlerted)
            NoopPrefs.setBatteryFullAlerted(context, decision.newFullAlerted)
        }
    }

    /** Rough charger ETA (~40%/h open-BLE estimate). */
    internal fun estimateChargerEta(pct: Int): String? {
        val remain = (100 - pct).coerceAtLeast(0)
        if (remain <= 0) return "Full"
        val hours = remain / 40.0
        return if (hours < 2.0) String.format("~%.0f min left on charger", hours * 60)
        else String.format("~%.1f h left on charger", hours)
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 3,
            appLaunchIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Battery alerts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Alerts when the strap battery is low, charging, or fully charged."
                },
            )
        }
    }
}
