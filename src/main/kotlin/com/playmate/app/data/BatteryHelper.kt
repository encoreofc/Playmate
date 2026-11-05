package com.playmate.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Many phones (Xiaomi, Oppo, Vivo, Samsung, Huawei...) stop apps in the background to save battery,
 * even music apps. Music apps that never stop are the ones the person has set to "Unrestricted".
 * The app cannot do that by itself, so it asks once and opens the right settings page.
 */
object BatteryHelper {

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("playmate_battery", Context.MODE_PRIVATE)

    fun isUnrestricted(ctx: Context): Boolean {
        val power = ctx.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return power.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /** True until the person has seen the question once (or the app is already unrestricted). */
    fun shouldAsk(ctx: Context): Boolean =
        !isUnrestricted(ctx) && !prefs(ctx).getBoolean("asked", false)

    fun markAsked(ctx: Context) {
        prefs(ctx).edit().putBoolean("asked", true).apply()
    }

    /** Opens the system dialog that allows the app to run without battery limits. */
    fun openSettings(ctx: Context) {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${ctx.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val general = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val details = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${ctx.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        for (intent in listOf(direct, general, details)) {
            if (runCatching { ctx.startActivity(intent) }.isSuccess) return
        }
    }
}
