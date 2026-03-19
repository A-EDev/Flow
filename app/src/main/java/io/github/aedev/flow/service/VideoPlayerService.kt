package io.github.aedev.flow.service

import android.app.*
import android.content.Intent
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import io.github.aedev.flow.MainActivity
import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import java.net.URL

/**
 * Foreground service for video playback with media session and notification support.
 * Allows playback to continue in background, survive app kills, and show lock-screen controls.
 * Modeled after NewPipe's PlayerService architecture.
 */
class VideoPlayerService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    /**
     * Coroutine job that releases WakeLock/WifiLock after a 30-second grace period.
     * Cancelled and re-scheduled each time playback state changes so brief pauses
     * (buffering, audio focus loss) don’t let the CPU deep-sleep and kill the stream.
     */
    private var lockReleaseJob: Job? = null
    
    private var currentVideo: Video? = null
    private var isPlaying = false
    private var cachedThumbnailUrl: String? = null
    private var cachedThumbnailBitmap: Bitmap? = null
    private var thumbnailLoadJob: Job? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "video_playback_channel"
        private const val CHANNEL_NAME = "Video Playback"
        
        const val ACTION_PLAY_PAUSE = "io.github.aedev.flow.video.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "io.github.aedev.flow.video.ACTION_NEXT"
        const val ACTION_PREVIOUS = "io.github.aedev.flow.video.ACTION_PREVIOUS"
        const val ACTION_STOP = "io.github.aedev.flow.video.ACTION_STOP"
        const val ACTION_CLOSE = "io.github.aedev.flow.video.ACTION_CLOSE"
        
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_VIDEO_CHANNEL = "video_channel"
        const val EXTRA_VIDEO_THUMBNAIL = "video_thumbnail"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        
        // Initialize WakeLock and WifiLock
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Flow:VideoPlayerWakeLock")
            wakeLock?.setReferenceCounted(false)
            
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "Flow:VideoPlayerWifiLock")
            wifiLock?.setReferenceCounted(false)
        } catch (e: Exception) {
            Log.e("VideoPlayerService", "Failed to acquire locks", e)
        }
        
        // Initialize MediaSession for lock-screen controls
        mediaSession = MediaSessionCompat(this, "VideoPlayerService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    EnhancedPlayerManager.getInstance().play()
                }
                
                override fun onPause() {
                    EnhancedPlayerManager.getInstance().pause()
                }
                
                override fun onStop() {
                    stopPlayback()
                }
                
                override fun onSeekTo(pos: Long) {
                    EnhancedPlayerManager.getInstance().seekTo(pos)
                }
                
                override fun onSkipToNext() {
                    EnhancedPlayerManager.getInstance().playNext()
                }
                
                override fun onSkipToPrevious() {
                    EnhancedPlayerManager.getInstance().playPrevious()
                }
            })
            
            isActive = true
        }
        
        // Observe player state and update notification
        serviceScope.launch {
            EnhancedPlayerManager.getInstance().playerState.collectLatest { state ->
                isPlaying = state.isPlaying
                val isPlaybackActive = state.isPlaying || state.isBuffering
                
                if (isPlaybackActive) {
                    lockReleaseJob?.cancel()
                    lockReleaseJob = null
                    acquireLocks()
                } else {
                    lockReleaseJob?.cancel()
                    lockReleaseJob = serviceScope.launch {
                        delay(30_000L)
                        releaseLocks()
                        stopPlayback()
                    }
                }
                
                updatePlaybackState(state.isPlaying, EnhancedPlayerManager.getInstance().getCurrentPosition())
                
                // Stop service if playback ended
                if (state.hasEnded) {
                    stopPlayback()
                }
                
                updateNotification()
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && intent != null) {
            try {
                startForeground(NOTIFICATION_ID, createPlaceholderNotification())
            } catch (e: Exception) {
                Log.w("VideoPlayerService", "Immediate foreground start failed", e)
            }
        }
        
        intent?.let { handleIntent(it) }
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        
        return START_NOT_STICKY
    }
    
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                if (isPlaying) {
                    EnhancedPlayerManager.getInstance().pause()
                } else {
                    EnhancedPlayerManager.getInstance().play()
                }
            }
            ACTION_NEXT -> EnhancedPlayerManager.getInstance().playNext()
            ACTION_PREVIOUS -> EnhancedPlayerManager.getInstance().playPrevious()
            ACTION_STOP, ACTION_CLOSE -> {
                stopPlayback()
            }
            else -> {
                // Starting playback with video info
                val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
                val title = intent.getStringExtra(EXTRA_VIDEO_TITLE)
                val channel = intent.getStringExtra(EXTRA_VIDEO_CHANNEL)
                val thumbnail = intent.getStringExtra(EXTRA_VIDEO_THUMBNAIL)
                
                if (videoId != null && title != null) {
                    currentVideo = Video(
                        id = videoId,
                        title = title,
                        channelName = channel ?: "",
                        channelId = "",
                        thumbnailUrl = thumbnail ?: "",
                        duration = 0,
                        viewCount = 0L,
                        uploadDate = ""
                    )
                    if (cachedThumbnailUrl != thumbnail) {
                        cachedThumbnailUrl = thumbnail
                        cachedThumbnailBitmap = null
                        thumbnailLoadJob?.cancel()
                        thumbnailLoadJob = null
                    }
                    updateNotification()
                }
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Prevent aggressive OEM ROMs (Xiaomi MIUI, Samsung OneUI, Huawei EMUI, CRDroid)
     * from killing the service when the app is swiped from the recents screen.
     *
     * By default, Android calls stopSelf() via onTaskRemoved() when a task is removed
     * and the service was not started in a sticky fashion.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (isPlaying) {
            updateNotification()
        } else {
            stopPlayback()
        }
    }
    
    override fun onDestroy() {
        Log.d("VideoPlayerService", "onDestroy() called")
        lockReleaseJob?.cancel()
        lockReleaseJob = null
        thumbnailLoadJob?.cancel()
        thumbnailLoadJob = null
        stopPlayback()
        releaseLocks()
        mediaSession.isActive = false
        mediaSession.release()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for video playback"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun updatePlaybackState(isPlaying: Boolean, position: Long) {
        val state = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, position, 1f)
            .build()
        
        mediaSession.setPlaybackState(playbackState)
    }
    
    private fun updateNotification() {
        val video = currentVideo ?: return
        
        // Update MediaSession metadata
        val duration = EnhancedPlayerManager.getInstance().getDuration()
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, video.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, video.channelName)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, video.title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, video.channelName)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .build()
        
        mediaSession.setMetadata(metadata)

        showNotification(video, cachedThumbnailBitmap)

        val thumbnailUrl = video.thumbnailUrl
        if (thumbnailUrl.isBlank()) return
        if (cachedThumbnailBitmap != null && cachedThumbnailUrl == thumbnailUrl) return
        if (thumbnailLoadJob?.isActive == true) return

        cachedThumbnailUrl = thumbnailUrl
        thumbnailLoadJob = serviceScope.launch(Dispatchers.IO) {
            val bitmap = try {
                val url = URL(thumbnailUrl)
                BitmapFactory.decodeStream(url.openConnection().getInputStream())
            } catch (_: Exception) {
                null
            }

            withContext(Dispatchers.Main) {
                cachedThumbnailBitmap = bitmap
                if (currentVideo?.thumbnailUrl == thumbnailUrl) {
                    showNotification(video, cachedThumbnailBitmap)
                }
            }
        }
    }
    
    private fun showNotification(video: Video, thumbnail: Bitmap?) {
        // Intent to open app when notification is clicked
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("video_id", video.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Action intents
        val playPauseIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, VideoPlayerService::class.java).apply {
                action = ACTION_PLAY_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val closeIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, VideoPlayerService::class.java).apply {
                action = ACTION_CLOSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val nextIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, VideoPlayerService::class.java).apply {
                action = ACTION_NEXT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val prevIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, VideoPlayerService::class.java).apply {
                action = ACTION_PREVIOUS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(video.title)
            .setContentText(video.channelName)
            .setSubText("Flow Player")
            .setSmallIcon(R.drawable.ic_play)
            .setLargeIcon(thumbnail)
            .setContentIntent(contentIntent)
            .setDeleteIntent(closeIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
             // Add Previous Action
            .addAction(
                R.drawable.ic_previous,
                "Previous",
                prevIntent
            )
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            // Add Next Action
            .addAction(
                R.drawable.ic_next,
                "Next",
                nextIntent
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2) // Show Prev, Play, Next
            )
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createPlaceholderNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Flow Player")
            .setContentText("Loading...")
            .setSmallIcon(R.drawable.ic_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire()
        }
        if (wifiLock?.isHeld != true) {
            wifiLock?.acquire()
        }
    }
    
    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
    }
    
    private fun stopPlayback() {
        EnhancedPlayerManager.getInstance().pause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }
}
