package io.github.aedev.flow.service

import android.app.*
import android.content.Intent
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
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
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import android.app.ActivityManager
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Process
import android.os.PowerManager

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
    private var hasStartedForeground = false

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
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (EnhancedPlayerManager.getInstance().playerState.value.hasEnded) {
                        EnhancedPlayerManager.getInstance().replay()
                    } else {
                        EnhancedPlayerManager.getInstance().play()
                    }
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

                override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                    if (action == ACTION_CLOSE) {
                        GlobalPlayerState.requestDismiss()
                        stopPlayback()
                    }
                }
            })
            
            isActive = true
        }

        // Set an initial playback state immediately so the session is always defined.
        // Without this, MediaSessionManager may not route media buttons here on first activation.
        val initialState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 1f)
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    ACTION_CLOSE, "Close", R.drawable.ic_close
                ).build()
            )
            .build()
        mediaSession.setPlaybackState(initialState)

        // Register as the explicit media-button handler so this session wins over
        // Media3MusicService's internal session on Android 5–12.
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON).also {
            it.setClass(this, VideoPlayerService::class.java)
        }
        val mediaButtonPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(this, 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE)
        }
        mediaSession.setMediaButtonReceiver(mediaButtonPendingIntent)

        // Observe player state and update notification
        serviceScope.launch {
            EnhancedPlayerManager.getInstance().playerState.collectLatest { state ->
                isPlaying = state.isPlaying
                val isPlaybackActive = state.isPlaying || state.isBuffering

                val globalVideo = GlobalPlayerState.currentVideo.value
                if (globalVideo != null && globalVideo.id != currentVideo?.id) {
                    currentVideo = Video(
                        id = globalVideo.id,
                        title = globalVideo.title,
                        channelName = globalVideo.channelName,
                        channelId = globalVideo.channelId,
                        thumbnailUrl = globalVideo.thumbnailUrl,
                        duration = globalVideo.duration,
                        viewCount = globalVideo.viewCount,
                        uploadDate = globalVideo.uploadDate
                    )
                    if (cachedThumbnailUrl != globalVideo.thumbnailUrl) {
                        thumbnailLoadJob?.cancel()
                        thumbnailLoadJob = null
                        cachedThumbnailUrl = null
                        cachedThumbnailBitmap = null
                    }
                }

                updateLocks(isPlaybackActive)
                
                updatePlaybackState(state.isPlaying, EnhancedPlayerManager.getInstance().getCurrentPosition())

                updateNotification()
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestedVideoId = intent?.getStringExtra(EXTRA_VIDEO_ID)
        val requestedTitle = intent?.getStringExtra(EXTRA_VIDEO_TITLE)
        val requestedChannel = intent?.getStringExtra(EXTRA_VIDEO_CHANNEL)

        intent?.let { handleIntent(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !hasStartedForeground) {
            try {
                val video = currentVideo
                if (video != null) {
                    showNotification(video, cachedThumbnailBitmap)
                } else {
                    startForeground(
                        NOTIFICATION_ID,
                        createPlaceholderNotification(requestedTitle, requestedChannel)
                    )
                    hasStartedForeground = true
                }
            } catch (e: Exception) {
                Log.w("VideoPlayerService", "Immediate foreground start failed", e)
            }
        }

        // Re-assert this session as the most recently active one whenever a new video
        // is started. Toggling isActive forces the system to re-register the session
        // timestamp so it beats Media3MusicService in media-button routing priority.
        if (requestedVideoId != null) {
            mediaSession.isActive = false
            mediaSession.isActive = true
        }

        MediaButtonReceiver.handleIntent(mediaSession, intent)

        return START_STICKY
    }
    
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                if (EnhancedPlayerManager.getInstance().playerState.value.hasEnded) {
                    EnhancedPlayerManager.getInstance().replay()
                } else if (isPlaying) {
                    EnhancedPlayerManager.getInstance().pause()
                } else {
                    EnhancedPlayerManager.getInstance().play()
                }
            }
            ACTION_NEXT -> EnhancedPlayerManager.getInstance().playNext()
            ACTION_PREVIOUS -> EnhancedPlayerManager.getInstance().playPrevious()
            ACTION_STOP -> stopPlayback()
            ACTION_CLOSE -> {
                GlobalPlayerState.requestDismiss()
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
        if (GlobalPlayerState.isInPipMode.value) {
            GlobalPlayerState.requestDismiss()
            stopPlayback()
            return
        }

        if (isPlaying) {
            updateNotification()
        }
    }
    private var isStoppingPlayback = false
    
    override fun onDestroy() {
        Log.d("VideoPlayerService", "onDestroy() called")
        lockReleaseJob?.cancel()
        lockReleaseJob = null
        thumbnailLoadJob?.cancel()
        thumbnailLoadJob = null
        teardownPlaybackUi()
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

        val closeAction = PlaybackStateCompat.CustomAction.Builder(
            ACTION_CLOSE,
            "Close",
            R.drawable.ic_close
        ).build()

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, position, 1f)
            .addCustomAction(closeAction)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }
    
    private fun buildMediaMetadata(video: Video, bitmap: Bitmap?): MediaMetadataCompat {
        val duration = EnhancedPlayerManager.getInstance().getDuration()
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, video.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, video.channelName)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, video.title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, video.channelName)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        if (bitmap != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
        }
        return builder.build()
    }

    /**
     * Build candidate thumbnail URLs for notification artwork.
     * Extracts the video ID so each URL is a clean, predictable path.
     */
    private fun buildThumbnailCandidates(originalUrl: String): List<String> {
        val candidates = mutableListOf<String>()
        if (originalUrl.contains("i.ytimg.com") || originalUrl.contains("i.youtube.com")) {
            val videoId = Regex("(?:vi|vi_webp)/([a-zA-Z0-9_-]+)/").find(originalUrl)
                ?.groupValues?.getOrNull(1)
            if (videoId != null) {
                candidates.add("https://i.ytimg.com/vi/$videoId/hq720.jpg")
                candidates.add("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")     // 480×360
                candidates.add("https://i.ytimg.com/vi/$videoId/mqdefault.jpg")     // 320×180
                candidates.add("https://i.ytimg.com/vi/$videoId/default.jpg")       // 120×90
            }
        }
        if (originalUrl !in candidates) candidates.add(originalUrl)
        return candidates
    }

    /**
     * Load notification artwork via Coil (proper HTTP headers, disk cache).
     * Falls back down the resolution ladder on any failure and normalizes
     * dimensions so OEM SystemUI code paths don't silently drop oversized bitmaps.
     */
    private suspend fun loadBestThumbnail(originalUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        for (url in buildThumbnailCandidates(originalUrl)) {
            val request = ImageRequest.Builder(applicationContext)
                .data(url)
                .allowHardware(false)
                .build()
            val bitmap = when (val result = applicationContext.imageLoader.execute(request)) {
                is SuccessResult -> (result.drawable as? BitmapDrawable)?.bitmap
                else -> null
            }
            if (bitmap != null) return@withContext resizeForNotification(bitmap)
        }
        null
    }

    /**
     * Keep notification artwork within a safe size for binder transport and OEM SystemUI.
     */
    private fun resizeForNotification(bitmap: Bitmap, maxPx: Int = 512): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longestSide = maxOf(w, h)
        if (longestSide <= maxPx) return bitmap
        val scale = maxPx.toFloat() / longestSide.toFloat()
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun updateNotification() {
        val video = currentVideo ?: return

        mediaSession.setMetadata(buildMediaMetadata(video, cachedThumbnailBitmap))
        showNotification(video, cachedThumbnailBitmap)

        val thumbnailUrl = video.thumbnailUrl
        if (thumbnailUrl.isBlank()) return
        if (cachedThumbnailBitmap != null && cachedThumbnailUrl == thumbnailUrl) return
        if (thumbnailLoadJob?.isActive == true) return

        cachedThumbnailUrl = thumbnailUrl
        thumbnailLoadJob = serviceScope.launch {
            val bitmap = loadBestThumbnail(thumbnailUrl)
            withContext(Dispatchers.Main) {
                cachedThumbnailBitmap = bitmap
                if (currentVideo?.thumbnailUrl == thumbnailUrl) {
                    mediaSession.setMetadata(buildMediaMetadata(video, bitmap))
                    showNotification(video, bitmap)
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
            1,
            Intent(this, VideoPlayerService::class.java).apply {
                action = ACTION_PLAY_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val closeIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, VideoPlayerService::class.java).apply {
                action = ACTION_CLOSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, VideoPlayerService::class.java).apply {
                action = ACTION_NEXT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val prevIntent = PendingIntent.getService(
            this,
            4,
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
            .setSmallIcon(R.drawable.ic_notification_logo)
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
            .addAction(
                R.drawable.ic_close,
                "Close",
                closeIntent
            )
            .addAction(
                R.drawable.ic_next,
                "Next",
                nextIntent
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        if (!hasStartedForeground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, notification)
            hasStartedForeground = true
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createPlaceholderNotification(title: String? = null, channel: String? = null): Notification {
        val closeIntent = PendingIntent.getService(
            this,
            5,
            Intent(this, VideoPlayerService::class.java).apply { action = ACTION_CLOSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title?.takeIf { it.isNotEmpty() } ?: "Flow Player")
            .setContentText(channel?.takeIf { it.isNotEmpty() } ?: "Preparing playback...")
            .setSubText("Flow Player")
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .setDeleteIntent(closeIntent)
            .addAction(R.drawable.ic_close, "Close", closeIntent)
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

    private fun updateLocks(isPlaybackActive: Boolean) {
        lockReleaseJob?.cancel()
        lockReleaseJob = null

        if (isPlaybackActive) {
            if (isAppInForeground()) {
                releaseLocks()
            } else {
                acquireLocks()
            }
            return
        }

        lockReleaseJob = serviceScope.launch {
            delay(30_000L)
            releaseLocks()
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun releaseWifiLock() {
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
    }

    private fun releaseLocks() {
        releaseWakeLock()
        releaseWifiLock()
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val runningProcess = activityManager.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }
        return when (runningProcess?.importance) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> true
            else -> false
        }
    }

    private fun teardownPlaybackUi() {
        currentVideo = null
        cachedThumbnailUrl = null
        cachedThumbnailBitmap = null
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 0f)
                .build()
        )
        mediaSession.setMetadata(null)
        mediaSession.isActive = false
        notificationManager.cancel(NOTIFICATION_ID)
    }
    
    private fun stopPlayback() {
        if (isStoppingPlayback) return
        isStoppingPlayback = true
        teardownPlaybackUi()
        EnhancedPlayerManager.getInstance().stop()
        hasStartedForeground = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }
}
