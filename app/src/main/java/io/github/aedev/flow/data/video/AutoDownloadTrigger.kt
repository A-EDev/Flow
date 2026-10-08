package io.github.aedev.flow.data.video

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.localmedia.LocalMediaIds
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.downloader.work.DownloadController
import io.github.aedev.flow.utils.NetworkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves a video the viewer opens for offline when "Download videos you open" allows it, at the
 * default download quality and codec. It waits for the player's own load to resolve, so live and
 * upcoming streams, failures and files already on the device never queue, and it adds no fetch:
 * the download resolves its streams when its turn in the queue comes, as every download does.
 */
@Singleton
class AutoDownloadTrigger
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: PlayerPreferences,
        private val queuer: BackgroundDownloadQueuer,
        private val controller: DownloadController,
        private val downloadDao: DownloadDao,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val arm = AutoDownloadArm()

        // Undone this session; the download row is gone after a cancel, so nothing else remembers it.
        private val declined = ConcurrentHashMap.newKeySet<String>()

        private val _queued = MutableSharedFlow<String>(extraBufferCapacity = 4)

        /** Ids of videos just queued, for the app snackbar and its Undo. */
        val queued: SharedFlow<String> = _queued.asSharedFlow()

        fun onOpened(
            video: Video,
            userOpened: Boolean,
        ) = arm.arm(video, userOpened)

        fun onLoadStarted(
            videoId: String,
            loadToken: Long,
        ) = arm.onLoadStarted(videoId, loadToken)

        /** [resolved] is what the player now knows about the video, richer than the opened card. */
        fun onResolved(
            videoId: String,
            loadToken: Long,
            resolution: AutoDownloadResolution,
            resolved: Video?,
        ) {
            val opened = arm.consume(videoId, loadToken, resolution) ?: return
            val video = resolved?.takeIf { it.id == videoId } ?: opened
            scope.launch {
                val candidate =
                    AutoDownloadCandidate(
                        mode = preferences.autoDownloadOpenedVideos.first(),
                        onWifi = NetworkState.isOnWifi(context),
                        userOpened = true,
                        isVod = true,
                        isShort = video.isShort || opened.isShort,
                        isMusic = video.isMusic || opened.isMusic,
                        isLocal = LocalMediaIds.isLocal(videoId),
                        existing = downloadDao.getDownloadWithItems(videoId)?.overallStatus,
                    )
                if (videoId in declined || !candidate.shouldAutoDownload()) return@launch
                if (queuer.queue(video) == QueueOutcome.QUEUED) _queued.emit(videoId)
            }
        }

        /** Cancels a download this queued, and keeps it from queueing again this session. */
        fun undo(videoId: String) {
            declined += videoId
            controller.cancel(videoId)
        }
    }
