package com.noop.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms wake alarms after reboot or when the device clock/zone jumps (#207, #389).
 *
 * AlarmManager schedules are cleared by a restart, so without this a phone that reboots overnight
 * would silently drop the alarm. On [Intent.ACTION_BOOT_COMPLETED] we re-schedule the SAME persisted
 * hard deadline via [SmartAlarmScheduler.rearmPersisted] (preserves an early smart-wake advance).
 *
 * On [Intent.ACTION_TIMEZONE_CHANGED] / [Intent.ACTION_TIME_CHANGED] absolute epoch edges are wrong
 * relative to the user's minute-of-day wake — recompute with [SmartAlarmScheduler.arm] and reschedule
 * custom alarms so Today and Alarm page stay on the same wall clock.
 */
class SmartAlarmBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                runCatching {
                    SmartAlarmScheduler.rearmPersisted(context, SmartAlarmStore.from(context))
                }
                rescheduleCustomsAndWindDown(context)
            }
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                runCatching {
                    val store = SmartAlarmStore.from(context)
                    if (store.enabled) {
                        SmartAlarmScheduler.arm(context, store)
                    }
                }
                rescheduleCustomsAndWindDown(context)
            }
        }
    }

    private fun rescheduleCustomsAndWindDown(context: Context) {
        runCatching {
            CustomAlarmScheduler.rescheduleAll(context, SmartAlarmStore.from(context))
        }
        // Re-schedule the (non-critical) wind-down nudge too — inexact repeating alarms are
        // cleared by a reboot on many OEMs, so re-arm from the user's earliest wake time.
        runCatching {
            val wind = WindDownStore.from(context)
            if (wind.enabled) {
                val wake = SmartAlarmStore.from(context).targetMinutes
                WindDownScheduler.schedule(context, wind, wake)
            }
        }
        runCatching {
            val creatine = CreatineReminderStore.from(context)
            if (creatine.enabled) {
                CreatineReminderScheduler.schedule(context, creatine)
            }
        }
    }
}
