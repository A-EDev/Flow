package com.flow.youtube.player

import android.content.Context
import com.flow.youtube.player.dlna.DlnaCastManager
import com.flow.youtube.player.dlna.DlnaDevice

/**
 * Thin compatibility shim that redirects cast calls to the DLNA engine.
 *
 * Chromecast/Google Play Services have been removed in favour of the open
 * DLNA/UPnP protocol ([DlnaCastManager]).  Any smart TV, Kodi, VLC, or
 * media renderer on the same Wi-Fi network is automatically discovered
 * without requiring Google services.
 *
 * Interactive device-picker UI lives in GlobalPlayerOverlay (Compose dialog).
 */
object CastHelper {

    /** True while a DLNA session is active. */
    fun isCasting(@Suppress("UNUSED_PARAMETER") context: Context): Boolean =
        DlnaCastManager.isCasting

    /** Not used with DLNA – kept for source-compatibility. Cast availability
     *  is provided by [DlnaCastManager.devices] state flow instead. */
    fun isCastAvailable(@Suppress("UNUSED_PARAMETER") context: Context): Boolean =
        DlnaCastManager.devices.value.isNotEmpty()

    /**
     * Legacy entry-point; discovery and the device-picker are now handled
     * by the Compose dialog in GlobalPlayerOverlay.  This is a no-op so
     * existing call-sites don't need to change.
     */
    fun showCastPicker(@Suppress("UNUSED_PARAMETER") context: Context) {
        // The caller (GlobalPlayerOverlay) switches to the Compose dialog directly.
    }

    /** Send video to the selected DLNA device. */
    fun castTo(device: DlnaDevice, videoUrl: String, title: String) {
        DlnaCastManager.castTo(device, videoUrl, title)
    }

    /** Stop casting and return playback to the phone. */
    fun stopCasting() {
        DlnaCastManager.stop()
    }
}
