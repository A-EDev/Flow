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
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.player.RepeatMode
import com.flow.youtube.ui.screens.music.MusicTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.net.URL


class MusicPlaybackService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager
    
    private var currentTrack: MusicTrack? = null
    private var isPlaying = false
    private var isShuffleEnabled = false
    private var repeatMode = RepeatMode.OFF
    private var isLiked = false
    private var currentPosition = 0L
    private var currentDuration = 0L
    private var currentAlbumArt: Bitmap? = null
    
    // Position update job for smooth progress tracking
    private var positionUpdateJob: Job? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "music_playback_channel"
        private const val CHANNEL_NAME = "Music Playback"
        
        const val ACTION_PLAY_PAUSE = "com.flow.youtube.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.flow.youtube.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.flow.youtube.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.flow.youtube.ACTION_STOP"
        const val ACTION_SHUFFLE = "com.flow.youtube.ACTION_SHUFFLE"
        const val ACTION_REPEAT = "com.flow.youtube.ACTION_REPEAT"
        const val ACTION_LIKE = "com.flow.youtube.ACTION_LIKE"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        
        // Initialize MediaSession with full transport controls
        mediaSession = MediaSessionCompat(this, "FlowMusicService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    EnhancedMusicPlayerManager.play()
                }
                
                override fun onPause() {
                    EnhancedMusicPlayerManager.pause()
                }
                
                override fun onSkipToNext() {
                    EnhancedMusicPlayerManager.playNext()
                }
                
                override fun onSkipToPrevious() {
                    EnhancedMusicPlayerManager.playPrevious()
                }
                
                override fun onStop() {
                    EnhancedMusicPlayerManager.pause()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                
                override fun onSeekTo(pos: Long) {
                    EnhancedMusicPlayerManager.seekTo(pos)
                    currentPosition = pos
                    updatePlaybackState()
                }
                
                override fun onSetRepeatMode(repeatMode: Int) {
                    EnhancedMusicPlayerManager.toggleRepeat()
                }
                
                override fun onSetShuffleMode(shuffleMode: Int) {
                    EnhancedMusicPlayerManager.toggleShuffle()
                }
            })
            
            isActive = true
        }
        
        // Observe current track changes
        serviceScope.launch {
            EnhancedMusicPlayerManager.currentTrack.collectLatest { track ->
                if (track != currentTrack) {
                    currentTrack = track
                    currentAlbumArt = null 
                    currentPosition = 0
                    loadAlbumArtAndUpdateNotification()
                }
            }
        }
        
        // Observe player state (isPlaying, buffering, duration)
        serviceScope.launch {
            EnhancedMusicPlayerManager.playerState.collectLatest { state ->
                val wasPlaying = isPlaying
                isPlaying = state.isPlaying
                
                if (state.duration > 0) {
                    currentDuration = state.duration
                }
                
                if (state.position > 0) {
                    currentPosition = state.position
                }
                
                updatePlaybackState()
                
                if (wasPlaying != isPlaying) {
                    updateNotificationUI()
                }
            }
        }
        
        serviceScope.launch {
            EnhancedMusicPlayerManager.currentPosition.collectLatest { position ->
                if (position > 0) {
                    currentPosition = position
                    updatePlaybackState()
                }
            }
        }
        
        serviceScope.launch {
            EnhancedMusicPlayerManager.shuffleEnabled.collectLatest { enabled ->
                if (isShuffleEnabled != enabled) {
                    isShuffleEnabled = enabled
                    updateNotificationUI()
                }
            }
        }
        
        serviceScope.launch {
            EnhancedMusicPlayerManager.repeatMode.collectLatest { mode ->
                if (repeatMode != mode) {
                    repeatMode = mode
                    updateNotificationUI()
                }
            }
        }

        serviceScope.launch {
            EnhancedMusicPlayerManager.isLiked.collectLatest { liked ->
                if (isLiked != liked) {
                    isLiked = liked
                    updateNotificationUI()
                }
            }
        }
        
        startPositionPolling()
    }
    
    /**
     * Polls player position regularly to keep notification progress bar in sync
     */
    private fun startPositionPolling() {
        positionUpdateJob?.cancel()
        positionUpdateJob = serviceScope.launch {
            while (isActive) {
                if (isPlaying) {
                    val position = EnhancedMusicPlayerManager.getCurrentPosition()
                    val duration = EnhancedMusicPlayerManager.getDuration()
                    
                    if (position >= 0 && (position != currentPosition || (duration > 0 && duration != currentDuration))) {
                        currentPosition = position
                        if (duration > 0) currentDuration = duration
                        updatePlaybackState()
                    }
                }
                delay(1000) 
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY_PAUSE -> EnhancedMusicPlayerManager.togglePlayPause()
                ACTION_NEXT -> EnhancedMusicPlayerManager.playNext()
                ACTION_PREVIOUS -> EnhancedMusicPlayerManager.playPrevious()
                ACTION_SHUFFLE -> EnhancedMusicPlayerManager.toggleShuffle()
                ACTION_REPEAT -> EnhancedMusicPlayerManager.toggleRepeat()
                ACTION_LIKE -> EnhancedMusicPlayerManager.toggleLike()
                ACTION_STOP -> {
                    EnhancedMusicPlayerManager.pause()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        
        currentTrack?.let { track ->
            showNotification(track, currentAlbumArt)
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        positionUpdateJob?.cancel()
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
                description = "Music playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Updates the MediaSession playback state with current position and duration
     */
    private fun updatePlaybackState() {
        val state = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        
        // Use 1.0f playback speed when playing, 0f when paused
        val playbackSpeed = if (isPlaying) 1f else 0f
        
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
            )
            .setState(state, currentPosition.coerceAtLeast(0), playbackSpeed)
            .build()
        
        mediaSession.setPlaybackState(playbackState)
    }
    
    /**
     * Loads album art and updates the notification
     */
    private fun loadAlbumArtAndUpdateNotification() {
        val track = currentTrack ?: return
        
        updateMediaSessionMetadata(track, null)
        showNotification(track, null)
        
        // Load album art asynchronously
        serviceScope.launch(Dispatchers.IO) {
            val bitmap = try {
                if (track.thumbnailUrl.isNotEmpty()) {
                    val url = URL(track.thumbnailUrl)
                    val connection = url.openConnection()
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    BitmapFactory.decodeStream(connection.getInputStream())
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
            
            withContext(Dispatchers.Main) {
                if (currentTrack?.videoId == track.videoId) {
                    currentAlbumArt = bitmap
                    updateMediaSessionMetadata(track, bitmap)
                    showNotification(track, bitmap)
                }
            }
        }
    }
    
    /**
     * Updates notification UI without reloading album art
     */
    private fun updateNotificationUI() {
        currentTrack?.let { track ->
            showNotification(track, currentAlbumArt)
        }
    }
    
    /**
     * Updates MediaSession metadata (title, artist, duration, album art)
     */
    private fun updateMediaSessionMetadata(track: MusicTrack, albumArt: Bitmap?) {
        val duration = if (currentDuration > 0) currentDuration else track.duration.toLong() * 1000
        
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Flow Music")
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, track.videoId)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        
        albumArt?.let {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
        }
        
        mediaSession.setMetadata(metadataBuilder.build())
    }
    
    /**
     * Shows the notification with all controls
     */
    private fun showNotification(track: MusicTrack, albumArt: Bitmap?) {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_music_player", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val shuffleIntent = createActionIntent(ACTION_SHUFFLE, 0)
        val previousIntent = createActionIntent(ACTION_PREVIOUS, 1)
        val playPauseIntent = createActionIntent(ACTION_PLAY_PAUSE, 2)
        val nextIntent = createActionIntent(ACTION_NEXT, 3)
        val repeatIntent = createActionIntent(ACTION_REPEAT, 4)
        val likeIntent = createActionIntent(ACTION_LIKE, 5)
        val stopIntent = createActionIntent(ACTION_STOP, 6)
        
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText(getStatusText())
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(albumArt)
            .setContentIntent(contentIntent)
            .setDeleteIntent(stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(isPlaying)
        
        notificationBuilder.addAction(
            NotificationCompat.Action.Builder(
                if (isShuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle,
                "Shuffle",
                shuffleIntent
            ).build()
        )
        
        notificationBuilder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_previous,
                "Previous",
                previousIntent
            ).build()
        )
        
        notificationBuilder.addAction(
            NotificationCompat.Action.Builder(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            ).build()
        )
        
        notificationBuilder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_next,
                "Next",
                nextIntent
            ).build()
        )
        
        notificationBuilder.addAction(
            NotificationCompat.Action.Builder(
                getRepeatIcon(),
                "Repeat",
                repeatIntent
            ).build()
        )

        notificationBuilder.addAction(
            NotificationCompat.Action.Builder(
                if (isLiked) R.drawable.ic_like_filled else R.drawable.ic_like,
                "Like",
                likeIntent
            ).build()
        )
        
        notificationBuilder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(1, 2, 3) // Previous, Play/Pause, Next
                .setShowCancelButton(true)
                .setCancelButtonIntent(stopIntent)
        )
        
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
    }
    
    private fun createActionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun getRepeatIcon(): Int {
        return when (repeatMode) {
            RepeatMode.OFF -> R.drawable.ic_repeat
            RepeatMode.ALL -> R.drawable.ic_repeat_on
            RepeatMode.ONE -> R.drawable.ic_repeat_one
        }
    }
    
    private fun getStatusText(): String {
        val shuffleText = if (isShuffleEnabled) "Shuffle" else ""
        val repeatText = when (repeatMode) {
            RepeatMode.OFF -> ""
            RepeatMode.ALL -> "Repeat All"
            RepeatMode.ONE -> "Repeat One"
        }
        
        return when {
            shuffleText.isNotEmpty() && repeatText.isNotEmpty() -> "$shuffleText • $repeatText"
            shuffleText.isNotEmpty() -> shuffleText
            repeatText.isNotEmpty() -> repeatText
            else -> "Flow Music"
        }
    }
}
