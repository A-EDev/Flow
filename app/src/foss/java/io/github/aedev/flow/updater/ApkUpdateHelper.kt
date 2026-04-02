package io.github.aedev.flow.updater

import android.app.Activity

/**
 * Stub for FOSS builds — APK self-update is not available.
 * F-Droid / IzzyOnDroid handle updates through their own clients.
 */
object ApkUpdateHelper {
    fun requestDownload(activity: Activity, releaseUrl: String) {
        // No-op: self-update not available in FOSS builds
    }
}
