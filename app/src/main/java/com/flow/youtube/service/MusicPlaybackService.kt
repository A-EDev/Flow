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
import com.flow.youtube.ui.screens.music.MusicTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.net.URL

/**
 * Foreground service for music playback with media session and notification support
 */
class MusicPlaybackService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager
    
    private var currentTrack: MusicTrack? = null
    private var isPlaying = false
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "music_playback_channel"
        private const val CHANNEL_NAME = "Music Playback"
        
        const val ACTION_PLAY_PAUSE = "com.flow.youtube.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.flow.youtube.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.flow.youtube.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.flow.youtube.ACTION_STOP"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        
        // Initialize MediaSession
        mediaSession = MediaSessionCompat(this, "MusicPlaybackService").apply {
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
                    stopForeground(true)
                    stopSelf()
                }
            })
            
            isActive = true
        }
        
        // Observe player state
        serviceScope.launch {
            EnhancedMusicPlayerManager.currentTrack.collectLatest { track ->
                currentTrack = track
                updateNotification()
            }
        }
        
        serviceScope.launch {
            EnhancedMusicPlayerManager.playerState.collectLatest { state ->
                isPlaying = state.isPlaying
                updatePlaybackState(state.isPlaying)
                updateNotification()
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY_PAUSE -> EnhancedMusicPlayerManager.togglePlayPause()
                ACTION_NEXT -> EnhancedMusicPlayerManager.playNext()
                ACTION_PREVIOUS -> EnhancedMusicPlayerManager.playPrevious()
                ACTION_STOP -> {
                    EnhancedMusicPlayerManager.pause()
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
        
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        
        return START_STICKY
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
                description = "Controls for music playback"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun updatePlaybackState(isPlaying: Boolean) {
        val state = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build()
        
        mediaSession.setPlaybackState(playbackState)
    }
    
    private fun updateNotification() {
        val track = currentTrack ?: return
        
        // Update MediaSession metadata
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "YouTube Music")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.duration.toLong() * 1000)
            .build()
        
        mediaSession.setMetadata(metadata)
        
        // Load album art asynchronously
        serviceScope.launch(Dispatchers.IO) {
            val bitmap = try {
                if (track.thumbnailUrl.isNotEmpty()) {
                    val url = URL(track.thumbnailUrl)
                    BitmapFactory.decodeStream(url.openConnection().getInputStream())
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
            
            withContext(Dispatchers.Main) {
                showNotification(track, bitmap)
            }
        }
    }
    
    private fun showNotification(track: MusicTrack, albumArt: Bitmap?) {
        // Intent to open app when notification is clicked
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Action intents
        val playPauseIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MusicPlaybackService::class.java).apply {
                action = ACTION_PLAY_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val nextIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MusicPlaybackService::class.java).apply {
                action = ACTION_NEXT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val previousIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MusicPlaybackService::class.java).apply {
                action = ACTION_PREVIOUS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MusicPlaybackService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText("YouTube Music")
            .setSmallIcon(R.drawable.ic_music_note) // You'll need to add this icon
            .setLargeIcon(albumArt)
            .setContentIntent(contentIntent)
            .setDeleteIntent(stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(
                R.drawable.ic_previous, // You'll need to add this icon
                "Previous",
                previousIntent
            )
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play, // You'll need to add these icons
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(
                R.drawable.ic_next, // You'll need to add this icon
                "Next",
                nextIntent
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
}
