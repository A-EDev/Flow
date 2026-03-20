package io.github.aedev.flow.updater

import android.content.Context
import android.util.Log
import com.supersuman.apkupdater.ApkUpdater

/**
 * GitHub-flavor implementation of the update checker.
 */
class FlowUpdater(private val context: Context) {

    fun isUpdateCheckEnabled(): Boolean = true

    fun requestDownload() {
        try {
            val updater = ApkUpdater(context, "https://github.com/A-EDev/Flow/releases/latest")
            updater.requestDownload()
        } catch (e: Exception) {
            Log.e("FlowUpdater", "GitHub update download failed", e)
        }
    }
}
