package io.github.aedev.flow.updater

import android.app.Activity

/**
 * FOSS-flavor stub — intentional no-op.
 */
class FlowUpdater(private val activity: Activity) {

    fun isUpdateCheckEnabled(): Boolean = false

    fun requestDownload() {
        // No-op: update checking is the store's responsibility in the FOSS variant.
    }
}
