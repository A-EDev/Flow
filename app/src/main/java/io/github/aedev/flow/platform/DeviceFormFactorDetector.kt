package io.github.aedev.flow.platform

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** Runtime device categories relevant to Flow's interface selection. */
enum class DeviceFormFactor {
    MOBILE,
    TV,
}

/** Detects television devices without making touchscreen availability mandatory. */
object DeviceFormFactorDetector {
    fun detect(context: Context): DeviceFormFactor {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTelevisionMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return if (isTelevisionMode || hasLeanback) DeviceFormFactor.TV else DeviceFormFactor.MOBILE
    }
}
