package com.noop.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.noop.R
import com.noop.analytics.NutritionStore
import com.noop.ui.appLaunchIntent
import java.util.Calendar

/**
 * Daily local creatine reminder (opt-in). Inexact repeating — not safety-critical.
 * Skips the notification when Creatine is already logged for today.
 */
object CreatineReminderScheduler {

    private const val REQUEST_CODE = 7322
    const val ACTION_NUDGE = "com.noop.alarm.action.CREATINE_REMIND"
    const val CHANNEL_ID = "noop_creatine_remind"
    private const val NOTIF_ID = 4322

    fun schedule(context: Context, store: CreatineReminderStore) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = nudgePendingIntent(context)
        am.cancel(pi)
        val first = nextOccurrence(store.minuteOfDay())
        am.setInexactRepeating(
            AlarmManager.RTC,
            first.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pi,
        )
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(nudgePendingIntent(context))
    }

    fun fireNotification(context: Context) {
        val already = NutritionStore.supplementsForDay(context)
            .any { it.name.equals("Creatine", ignoreCase = true) }
        if (already) return
        ensureChannel(context)
        runCatching {
            val open = PendingIntent.getActivity(
                context, 0, appLaunchIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_heart)
                .setContentTitle("Creatine reminder")
                .setContentText("Optional nudge — log in Nutrition when you’ve taken it. Not medical advice.")
                .setContentIntent(open)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIF_ID, n)
        }
    }

    private fun nudgePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CreatineReminderReceiver::class.java).setAction(ACTION_NUDGE)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Creatine reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Optional daily creatine log nudge. Local only."
                    setShowBadge(false)
                },
            )
        }
    }

    private fun nextOccurrence(minuteOfDay: Int): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
}

class CreatineReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != CreatineReminderScheduler.ACTION_NUDGE) return
        CreatineReminderScheduler.fireNotification(context)
    }
}
