package io.github.aedev.flow.service

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.error.PlayerDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@UnstableApi
class VideoPlayerService : MediaSessionService() {

    companion object {
        private const val TAG = "VideoPlayerService"
        private const val LOCK_RELEASE_DELAY_MS = 30_000L

        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_VIDEO_CHANNEL = "video_channel"
        const val EXTRA_VIDEO_THUMBNAIL = "video_thumbnail"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var lockReleaseJob: Job? = null

    private fun serviceSnapshot(): String {
        val player = EnhancedPlayerManager.getInstance().getPlayer()
        val state = when (player?.playbackState) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            null -> "NO_PLAYER"
            else -> "UNKNOWN(${player.playbackState})"
        }
        return "exo=$state pwr=${player?.playWhenReady} playing=${player?.isPlaying} " +
            "pos=${player?.currentPosition}/${player?.duration} " +
            "idx=${player?.currentMediaItemIndex} count=${player?.mediaItemCount} " +
            "wakeHeld=${wakeLock?.isHeld} wifiHeld=${wifiLock?.isHeld} " +
            "ongoing=${runCatching { isPlaybackOngoing() }.getOrDefault(false)} " +
            "activeForLocks=${isPlaybackActiveForLocks()}"
    }

    private fun serviceLog(message: String) {
        val full = "$message | ${serviceSnapshot()}"
        Log.w(TAG, full)
        PlayerDiagnostics.logWarning(TAG, full)
    }

    override fun onCreate() {
        super.onCreate()
        serviceLog("onCreate")

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Flow:VideoPlayerWakeLock")
            wakeLock?.setReferenceCounted(false)

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "Flow:VideoPlayerWifiLock")
            wifiLock?.setReferenceCounted(false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create locks", e)
        }

        EnhancedPlayerManager.getInstance().initialize(applicationContext)

        serviceScope.launch {
            EnhancedPlayerManager.getInstance().playerState.collectLatest {
                serviceLog("playerState collect update")
                updateLocks(isPlaybackActiveForLocks())
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        EnhancedPlayerManager.getInstance().initialize(applicationContext)
        serviceLog("onGetSession controller=${controllerInfo.packageName}")
        return EnhancedPlayerManager.getInstance().getVideoMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        serviceLog("onStartCommand action=${intent?.action}")
        updateLocks(isPlaybackActiveForLocks())
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        serviceLog("onTaskRemoved pip=${GlobalPlayerState.isInPipMode.value}")
        if (GlobalPlayerState.isInPipMode.value) {
            GlobalPlayerState.requestDismiss()
            EnhancedPlayerManager.getInstance().stop()
            releaseLocks()
            stopSelf()
            return
        }

        if (isPlaybackOngoing()) return

        EnhancedPlayerManager.getInstance().stop()
        releaseLocks()
        stopSelf()
    }
    override fun onDestroy() {
        serviceLog("onDestroy")
        lockReleaseJob?.cancel()
        lockReleaseJob = null
        releaseLocks()
        serviceScope.cancel()
        super.onDestroy()
    }
    private fun acquireLocks() {
        serviceLog("acquireLocks")
        if (wakeLock?.isHeld != true) wakeLock?.acquire()
        if (wifiLock?.isHeld != true) wifiLock?.acquire()
    }

    private fun updateLocks(isPlaybackActive: Boolean) {
        serviceLog("updateLocks active=$isPlaybackActive")
        lockReleaseJob?.cancel()
        lockReleaseJob = null

        if (isPlaybackActive) {
            acquireLocks()
            return
        }

        lockReleaseJob = serviceScope.launch {
            delay(LOCK_RELEASE_DELAY_MS)
            releaseLocks()
        }
    }

    private fun isPlaybackActiveForLocks(): Boolean {
        val player = EnhancedPlayerManager.getInstance().getPlayer() ?: return false
        return player.isPlaying ||
            (player.playWhenReady &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED)
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wake lock", e)
        }
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wifi lock", e)
        }
    }

}
