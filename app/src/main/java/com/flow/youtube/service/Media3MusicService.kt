package com.flow.youtube.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.flow.youtube.MainActivity
import com.flow.youtube.R
import dagger.hilt.android.AndroidEntryPoint
import androidx.media3.session.DefaultMediaNotificationProvider
import com.google.common.collect.ImmutableList
import androidx.media3.session.SessionCommand
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionResult
import androidx.media3.common.Player
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import android.os.Bundle
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.flow.youtube.data.download.DownloadUtil
import com.flow.youtube.extensions.setOffloadEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class Media3MusicService : MediaLibraryService() {

    companion object {
        private const val TAG = "Media3MusicService"
        private const val ACTION_TOGGLE_SHUFFLE = "ACTION_TOGGLE_SHUFFLE"
        private const val ACTION_TOGGLE_REPEAT = "ACTION_TOGGLE_REPEAT"
        private const val MAX_RETRY_PER_SONG = 3
        private const val RETRY_DELAY_MS = 1000L
        
        private val CommandToggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
        private val CommandToggleRepeat = SessionCommand(ACTION_TOGGLE_REPEAT, Bundle.EMPTY)
    }

    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var player: ExoPlayer
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val retryCountMap = mutableMapOf<String, Int>()

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        initializeSession()
    }

    private fun initializePlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        val mediaSourceFactory = DefaultMediaSourceFactory(downloadUtil.getPlayerDataSourceFactory())
        
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()
        
        player.setOffloadEnabled(true)
            
        player.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateNotification()
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                updateNotification()
            }
            
            override fun onPlayerError(error: PlaybackException) {
                val mediaId = player.currentMediaItem?.mediaId ?: return
                Log.e(TAG, "Playback error for $mediaId: ${error.message}", error)
                
                val currentRetry = retryCountMap.getOrDefault(mediaId, 0)
                if (currentRetry < MAX_RETRY_PER_SONG) {
                    retryCountMap[mediaId] = currentRetry + 1
                    Log.d(TAG, "Attempting retry ${currentRetry + 1}/$MAX_RETRY_PER_SONG for $mediaId")
                    
                    downloadUtil.invalidateUrlCache(mediaId)
                    
                    serviceScope.launch {
                        delay(RETRY_DELAY_MS)
                        try {
                            val position = player.currentPosition
                            player.prepare()
                            player.seekTo(position)
                            player.play()
                        } catch (e: Exception) {
                            Log.e(TAG, "Retry failed for $mediaId", e)
                        }
                    }
                } else {
                    Log.w(TAG, "Max retries reached for $mediaId, skipping to next")
                    retryCountMap.remove(mediaId)
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                        player.prepare()
                        player.play()
                    }
                }
            }
            
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                mediaItem?.mediaId?.let { 
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                        retryCountMap.clear()
                    }
                }
            }
        })
    }

    @OptIn(UnstableApi::class)
    private fun initializeSession() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .build()
            
        setMediaNotificationProvider(CustomNotificationProvider())
            
        updateNotification()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        if (::mediaLibrarySession.isInitialized) {
            mediaLibrarySession.release()
        }
        if (::player.isInitialized) {
            player.release()
        }
        super.onDestroy()
    }

    private fun updateNotification() {
        if (!::mediaLibrarySession.isInitialized) return
        
        val shuffleIcon = if (player.shuffleModeEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle
        val repeatIcon = when (player.repeatMode) {
             Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
             Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_on
             else -> R.drawable.ic_repeat
        }

        val shuffleButton = CommandButton.Builder()
            .setDisplayName("Shuffle")
            .setIconResId(shuffleIcon)
            .setSessionCommand(CommandToggleShuffle)
            .build()

        val repeatButton = CommandButton.Builder()
            .setDisplayName("Repeat")
            .setIconResId(repeatIcon)
            .setSessionCommand(CommandToggleRepeat)
            .build()
            
        mediaLibrarySession.setCustomLayout(listOf(shuffleButton, repeatButton))
    }

    @OptIn(UnstableApi::class)
    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val validCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(CommandToggleShuffle)
                .add(CommandToggleRepeat)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(validCommands)
                .build()
        }
        
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
             if (customCommand.customAction == ACTION_TOGGLE_SHUFFLE) {
                 player.shuffleModeEnabled = !player.shuffleModeEnabled
                 return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
             }
             if (customCommand.customAction == ACTION_TOGGLE_REPEAT) {
                 val newMode = when (player.repeatMode) {
                     Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                     Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                     else -> Player.REPEAT_MODE_OFF
                 }
                 player.repeatMode = newMode
                 return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
             }
             return super.onCustomCommand(session, controller, customCommand, args)
        }
    }
    
    @OptIn(UnstableApi::class)
    private inner class CustomNotificationProvider : DefaultMediaNotificationProvider(this@Media3MusicService) {
        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            customLayout: ImmutableList<CommandButton>,
            showPauseButton: Boolean
        ): ImmutableList<CommandButton> {
            val playPauseButton = CommandButton.Builder()
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setIconResId(if (showPauseButton) R.drawable.ic_pause else R.drawable.ic_play)
                .setDisplayName(if (showPauseButton) "Pause" else "Play")
                .setEnabled(playerCommands.contains(Player.COMMAND_PLAY_PAUSE))
                .build()
            
            val prevButton = CommandButton.Builder()
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setIconResId(R.drawable.ic_previous)
                .setDisplayName("Previous")
                .setEnabled(playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
                .build()
                
            val nextButton = CommandButton.Builder()
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setIconResId(R.drawable.ic_next)
                .setDisplayName("Next")
                .setEnabled(playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT))
                .build()

            var shuffleButton: CommandButton? = null
            var repeatButton: CommandButton? = null
            
            for (button in customLayout) {
                if (button.sessionCommand?.customAction == ACTION_TOGGLE_SHUFFLE) {
                    shuffleButton = button
                } else if (button.sessionCommand?.customAction == ACTION_TOGGLE_REPEAT) {
                    repeatButton = button
                }
            }
            
            val builder = ImmutableList.builder<CommandButton>()
            
            shuffleButton?.let { builder.add(it) }
            builder.add(prevButton)
            builder.add(playPauseButton)
            builder.add(nextButton)
            repeatButton?.let { builder.add(it) }
            
            return builder.build()
        }
    }
}
