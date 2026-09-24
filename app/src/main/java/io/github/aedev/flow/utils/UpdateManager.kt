package io.github.aedev.flow.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object UpdateManager {
    // Helper to open browser
    fun triggerDownload(
        context: Context,
        url: String,
    ) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Could not open browser", e)
        }
    }
}
