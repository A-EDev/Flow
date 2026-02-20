package com.flow.youtube.player

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import java.util.concurrent.Executors

/**
 * Utility helpers for Google Cast / Chromecast integration.
 *
 * uses [MediaRouteChooserDialog] directly – it works in any Activity/Context.
 *
 * CastContext is initialised lazily on a background executor the first time
 * any helper is called, so the main thread is never blocked.
 */
object CastHelper {

    private const val TAG = "CastHelper"
    private const val RECEIVER_APP_ID = "CC1AD845" // Default Media Receiver

    /** Selector that matches Cast devices for the Flow receiver app. */
    val routeSelector: MediaRouteSelector by lazy {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(RECEIVER_APP_ID))
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()
    }

    /**
     * Initialise (or retrieve) the [CastContext] for [context].
     * Uses a dedicated single-thread executor so the main thread is never
     * blocked.  Safe to call multiple times – the SDK caches the instance.
     */
    private fun getContext(context: Context): CastContext? {
        return try {
            CastContext.getSharedInstance(context, Executors.newSingleThreadExecutor())
                .result  
        } catch (e: Exception) {
            Log.e(TAG, "CastContext unavailable: ${e.message}")
            null
        }
    }

    // ── State helpers ──────────────────────────────────────────────────────
    fun isCastAvailable(context: Context): Boolean {
        return try {
            getContext(context)?.castState != CastState.NO_DEVICES_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns `true` while an active Cast session is live (video playing on
     * a TV / speaker).
     */
    fun isCasting(context: Context): Boolean {
        return try {
            getContext(context)?.castState == CastState.CONNECTED
        } catch (e: Exception) {
            false
        }
    }

    // ── Actions ────────────────────────────────────────────────────────────

    /**
     * Opens a route-chooser dialog so the user can pick a Cast device.
     *
     * Uses [MediaRouteChooserDialog] (not the Fragment variant) so it works
     * inside Compose activities without needing a FragmentManager.  Adds a
     * network-scan callback so nearby devices populate as they are found.
     */
    fun showCastPicker(context: Context) {
        try {
            getContext(context)
            val themedCtx = ContextThemeWrapper(
                context,
                androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert
            )
            val dialog = MediaRouteChooserDialog(themedCtx)
            dialog.routeSelector = routeSelector
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "showCastPicker failed: ${e.message}", e)
            Toast.makeText(context, "Cast unavailable – check Wi-Fi connection", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Gracefully ends the current Cast session, returning playback to the
     * phone.
     */
    fun stopCasting(context: Context) {
        try {
            getContext(context)?.sessionManager?.endCurrentSession(true)
        } catch (e: Exception) {
            Log.e(TAG, "stopCasting failed: ${e.message}")
        }
    }
}
