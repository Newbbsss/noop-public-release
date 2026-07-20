package com.noop.ble

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Android background-survival helpers (ryanbr #473 / #539). NOOP already runs a foreground service
 * + exact alarms, but aggressive OEM battery managers kill even those — the reliable lever is a
 * USER action: whitelist NOOP from battery optimisation (and on the worst vendors, enable
 * auto-start). Settings "Keep NOOP alive overnight" and Test Centre diagnostics share this source.
 *
 * POPUP DISCIPLINE: nothing here fires a system dialog on its own. Callers start an Intent only
 * on a user tap; the toggle reflects live [isBatteryExempt] so an already-exempt user is never
 * prompted again.
 */
object BackgroundHealth {

    /**
     * Manufacturers whose proprietary battery managers kill background work regardless of the AOSP
     * foreground-service contract (dontkillmyapp.com set). Pure.
     */
    val AGGRESSIVE_VENDORS: List<String> =
        listOf("xiaomi", "oppo", "vivo", "huawei", "oneplus", "realme", "meizu")

    fun isAggressiveVendor(manufacturer: String = Build.MANUFACTURER): Boolean {
        val m = manufacturer.lowercase()
        return AGGRESSIVE_VENDORS.any { m.contains(it) }
    }

    fun isBatteryExempt(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    /**
     * One-tap system dialog to whitelist NOOP from battery optimisation. Sideload-only intent
     * (Play restricts it; NOOP doesn't ship there). Build-only — caller starts on tap.
     */
    fun batteryExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))

    fun appBatterySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))

    /**
     * Best-effort deep-link to the OEM auto-start / protected-app screen. Separate from the
     * exemption dialog so one tap never spawns two popups. Never throws.
     */
    fun oemAutostartIntent(context: Context): Intent? {
        val m = Build.MANUFACTURER.lowercase()
        val candidates: List<Pair<String, String>> = when {
            m.contains("xiaomi") -> listOf(
                "com.miui.securitycenter" to
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
            m.contains("oppo") || m.contains("realme") -> listOf(
                "com.coloros.safecenter" to
                    "com.coloros.safecenter.startupapp.StartupAppListActivity",
                "com.oppo.safe" to
                    "com.oppo.safe.permission.startup.StartupAppListActivity",
            )
            m.contains("vivo") -> listOf(
                "com.vivo.permissionmanager" to
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            )
            m.contains("huawei") -> listOf(
                "com.huawei.systemmanager" to
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            )
            m.contains("meizu") -> listOf(
                "com.meizu.safe" to "com.meizu.safe.permission.SmartBGActivity",
            )
            else -> emptyList()
        }
        for ((pkg, cls) in candidates) {
            val intent = Intent().setClassName(pkg, cls)
            if (context.packageManager.resolveActivity(intent, 0) != null) return intent
        }
        return null
    }
}
