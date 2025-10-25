package com.flow.youtube.service

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.flow.youtube.MainActivity
import com.flow.youtube.R
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.data.model.Video
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
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
    
    private var currentVideo: Video? = null
    private var isPlaying = false
    
    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "video_playback_channel"
        private const val CHANNEL_NAME = "Video Playback"
        
        const val ACTION_PLAY_PAUSE = "com.flow.youtube.video.ACTION_PLAY_PAUSE"
        const val ACTION_STOP = "com.flow.youtube.video.ACTION_STOP"
        const val ACTION_CLOSE = "com.flow.youtube.video.ACTION_CLOSE"
        
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_VIDEO_CHANNEL = "video_channel"
        const val EXTRA_VIDEO_THUMBNAIL = "video_thumbnail"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        
        // Initialize MediaSession for lock-screen controls
        mediaSession = MediaSessionCompat(this, "VideoPlayerService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            
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
            })
            
            isActive = true
        }
        
        // Observe player state and update notification
        serviceScope.launch {
            EnhancedPlayerManager.getInstance().playerState.collectLatest { state ->
                isPlaying = state.isPlaying
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
        intent?.let { handleIntent(it) }
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        
        return START_NOT_STICKY // Don't restart if killed (NewPipe behavior)
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
                    updateNotification()
                }
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        mediaSession.isActive = false
        mediaSession.release()
        serviceScope.cancel()
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
                PlaybackStateCompat.ACTION_SEEK_TO
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
        
        // Load thumbnail asynchronously
        serviceScope.launch(Dispatchers.IO) {
            val bitmap = try {
                if (video.thumbnailUrl.isNotEmpty()) {
                    val url = URL(video.thumbnailUrl)
                    BitmapFactory.decodeStream(url.openConnection().getInputStream())
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
            
            withContext(Dispatchers.Main) {
                showNotification(video, bitmap)
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
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
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
