package io.github.aedev.flow.notification

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object BackgroundWorkPolicy {
    private const val TAG = "BackgroundWorkPolicy"

    /** True when the OS will let background workers use the network on their own schedule. */
    fun isBackgroundWorkUnrestricted(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Shows the system exemption dialog, falling back to the battery-optimisation list on ROMs that
     * do not expose the direct request action. Returns false when neither screen can be opened, so
     * the caller can leave the setting row in place instead of silently doing nothing.
     */
    @SuppressLint("BatteryLife")
    fun requestUnrestrictedBackgroundWork(context: Context): Boolean {
        val request =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
        if (startSettingsActivity(context, request)) return true

        return startSettingsActivity(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun startSettingsActivity(
        context: Context,
        intent: Intent,
    ): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity for ${intent.action}: ${e.message}")
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Denied opening ${intent.action}: ${e.message}")
            false
        }
}
