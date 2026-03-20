package io.github.aedev.flow.updater

import android.app.Activity
import android.util.Log
import com.supersuman.apkupdater.ApkUpdater

/**
 * GitHub-flavor implementation of the update checker.
 */
class FlowUpdater(private val activity: Activity) {

    fun isUpdateCheckEnabled(): Boolean = true

    fun requestDownload() {
        try {
            val updater = ApkUpdater(activity, "https://github.com/A-EDev/Flow/releases/latest")
            updater.requestDownload()
        } catch (e: Exception) {
            Log.e("FlowUpdater", "GitHub update download failed", e)
        }
    }
}
