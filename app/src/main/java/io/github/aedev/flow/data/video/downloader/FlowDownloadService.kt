package io.github.aedev.flow.data.video.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.aedev.flow.MainActivity
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.entity.DownloadFileType
import io.github.aedev.flow.data.local.entity.DownloadItemEntity
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.SponsorBlockRepository
import io.github.aedev.flow.data.video.DownloadProgressUpdate
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.player.sabr.integration.SabrDownloadEngine
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class FlowDownloadService : Service() {

    @Inject
    lateinit var parallelDownloader: ParallelDownloader

    @Inject
    lateinit var preferences: PlayerPreferences

    @Inject
    lateinit var downloadManager: VideoDownloadManager

    @Inject
    lateinit var sponsorBlockRepository: SponsorBlockRepository

    private val gson = Gson()

    private val activeMissions = ConcurrentHashMap<String, FlowDownloadMission>()
    private val activeSabrEngines = ConcurrentHashMap<String, SabrDownloadEngine>()
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Room item IDs for each video's download items (videoId -> list of itemIds)
    private val itemIds = ConcurrentHashMap<String, MutableList<Int>>()

    // WiFi connectivity callback
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        private const val TAG = "FlowDownloadService"
        const val CHANNEL_ID = "flow_downloads"
        const val NOTIFICATION_GROUP = "flow_download_group"
        
        const val ACTION_START_DOWNLOAD = "io.github.aedev.flow.START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "io.github.aedev.flow.PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "io.github.aedev.flow.RESUME_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "io.github.aedev.flow.CANCEL_DOWNLOAD"

        /** Audio-only download mode flag — if set, only download audio stream */
        const val EXTRA_AUDIO_ONLY = "audio_only"
        const val EXTRA_AUDIO_EXTENSION = "audio_extension"
        const val EXTRA_AUDIO_MIME_TYPE = "audio_mime_type"
        const val EXTRA_IS_MUSIC = "is_music"

        /**
         * Optional video codec hint (e.g. "vp9", "vp8", "h264").
         * When set to "vp9" or "vp8" the service will use a .webm output container
         * instead of .mp4, since Android's MediaMuxer cannot mux VP9 into MPEG-4.
         */
        const val EXTRA_VIDEO_CODEC = "video_codec"
        const val EXTRA_THREADS = "download_threads"

        private const val EXTRA_SABR_STREAMING_URL = "sabr_streaming_url"
        private const val EXTRA_SABR_AUDIO_ITAG = "sabr_audio_itag"
        private const val EXTRA_SABR_AUDIO_LMT = "sabr_audio_lmt"
        private const val EXTRA_SABR_VIDEO_ITAG = "sabr_video_itag"
        private const val EXTRA_SABR_VIDEO_LMT = "sabr_video_lmt"
        private const val EXTRA_SABR_PO_TOKEN = "sabr_po_token"
        private const val EXTRA_SABR_VISITOR_ID = "sabr_visitor_id"
        private const val EXTRA_SABR_DURATION_MS = "sabr_duration_ms"

        fun startSabrDownload(
            context: Context,
            video: Video,
            quality: String,
            sabrStreamingUrl: String,
            audioItag: Int,
            audioLmt: Long,
            videoItag: Int,
            videoLmt: Long,
            poToken: String = "",
            visitorId: String = "",
            durationMs: Long = 0,
            audioOnly: Boolean = false,
            videoCodec: String? = null,
            audioExtension: String? = null,
            audioMimeType: String? = null,
            isMusic: Boolean = false
        ) {
            val intent = Intent(context, FlowDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra("video_id", video.id)
                putExtra("video_title", video.title)
                putExtra("video_url", "sabr://${video.id}")
                putExtra("video_quality", quality)
                putExtra("video_thumbnail", video.thumbnailUrl)
                putExtra("video_channel", video.channelName)
                putExtra("video_duration", video.duration)
                putExtra(EXTRA_AUDIO_ONLY, audioOnly)
                putExtra(EXTRA_IS_MUSIC, isMusic)
                putExtra(EXTRA_SABR_STREAMING_URL, sabrStreamingUrl)
                putExtra(EXTRA_SABR_AUDIO_ITAG, audioItag)
                putExtra(EXTRA_SABR_AUDIO_LMT, audioLmt)
                putExtra(EXTRA_SABR_VIDEO_ITAG, videoItag)
                putExtra(EXTRA_SABR_VIDEO_LMT, videoLmt)
                putExtra(EXTRA_SABR_PO_TOKEN, poToken)
                putExtra(EXTRA_SABR_VISITOR_ID, visitorId)
                putExtra(EXTRA_SABR_DURATION_MS, durationMs)
                if (videoCodec != null) putExtra(EXTRA_VIDEO_CODEC, videoCodec)
                if (audioExtension != null) putExtra(EXTRA_AUDIO_EXTENSION, audioExtension)
                if (audioMimeType != null) putExtra(EXTRA_AUDIO_MIME_TYPE, audioMimeType)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startDownload(
            context: Context,
            video: Video,
            url: String,
            quality: String,
            audioUrl: String? = null,
            audioOnly: Boolean = false,
            userAgent: String? = null,
            videoCodec: String? = null,
            audioExtension: String? = null,
            audioMimeType: String? = null,
            isMusic: Boolean = false,
            threads: Int? = null
        ) {
            val intent = Intent(context, FlowDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra("video_id", video.id)
                putExtra("video_title", video.title)
                putExtra("video_url", url)
                putExtra("video_audio_url", audioUrl)
                putExtra("video_quality", quality)
                putExtra("video_thumbnail", video.thumbnailUrl)
                putExtra("video_channel", video.channelName)
                putExtra("video_duration", video.duration)
                putExtra("video_user_agent", userAgent)
                putExtra(EXTRA_AUDIO_ONLY, audioOnly)
                putExtra(EXTRA_IS_MUSIC, isMusic)
                if (videoCodec != null) putExtra(EXTRA_VIDEO_CODEC, videoCodec)
                if (audioExtension != null) putExtra(EXTRA_AUDIO_EXTENSION, audioExtension)
                if (audioMimeType != null) putExtra(EXTRA_AUDIO_MIME_TYPE, audioMimeType)
                if (threads != null) putExtra(EXTRA_THREADS, threads)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseDownload(context: Context, videoId: String) {
            val intent = Intent(context, FlowDownloadService::class.java).apply {
                action = ACTION_PAUSE_DOWNLOAD
                putExtra("video_id", videoId)
            }
            context.startService(intent)
        }

        fun resumeDownload(context: Context, videoId: String) {
            val intent = Intent(context, FlowDownloadService::class.java).apply {
                action = ACTION_RESUME_DOWNLOAD
                putExtra("video_id", videoId)
            }
            context.startService(intent)
        }

        fun cancelDownload(context: Context, videoId: String) {
            val intent = Intent(context, FlowDownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra("video_id", videoId)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            val customPath = preferences.downloadLocation.firstOrNull()
            downloadManager.customDownloadPath = customPath
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoId = intent?.getStringExtra("video_id")
        Log.d(TAG, "onStartCommand: action=${intent?.action}, videoId=$videoId")

        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                if (videoId == null) {
                    Log.e(TAG, "onStartCommand: videoId is null for START_DOWNLOAD")
                    return START_NOT_STICKY
                }
                val title = intent.getStringExtra("video_title") ?: "Unknown Video"
                val url = intent.getStringExtra("video_url")
                if (url == null) {
                    Log.e(TAG, "onStartCommand: url is null for START_DOWNLOAD")
                    return START_NOT_STICKY
                }
                val audioUrl = intent.getStringExtra("video_audio_url")
                val quality = intent.getStringExtra("video_quality") ?: "720p"
                val thumbnail = intent.getStringExtra("video_thumbnail") ?: ""
                val channel = intent.getStringExtra("video_channel") ?: "Unknown"
                val duration = intent.getIntExtra("video_duration", 0)
                val userAgent = intent.getStringExtra("video_user_agent")
                val audioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)
                val videoCodec = intent.getStringExtra(EXTRA_VIDEO_CODEC)
                val audioExtension = intent.getStringExtra(EXTRA_AUDIO_EXTENSION)
                val audioMimeType = intent.getStringExtra(EXTRA_AUDIO_MIME_TYPE)
                val isMusic = intent.getBooleanExtra(EXTRA_IS_MUSIC, false)
                val threadsOverride = intent.getIntExtra(EXTRA_THREADS, 0).takeIf { it > 0 }

                val sabrStreamingUrl = intent.getStringExtra(EXTRA_SABR_STREAMING_URL)
                val sabrAudioItag = intent.getIntExtra(EXTRA_SABR_AUDIO_ITAG, 0)
                val sabrAudioLmt = intent.getLongExtra(EXTRA_SABR_AUDIO_LMT, 0)
                val sabrVideoItag = intent.getIntExtra(EXTRA_SABR_VIDEO_ITAG, 0)
                val sabrVideoLmt = intent.getLongExtra(EXTRA_SABR_VIDEO_LMT, 0)
                val sabrPoToken = intent.getStringExtra(EXTRA_SABR_PO_TOKEN) ?: ""
                val sabrVisitorId = intent.getStringExtra(EXTRA_SABR_VISITOR_ID) ?: ""
                val sabrDurationMs = intent.getLongExtra(EXTRA_SABR_DURATION_MS, 0)

                Log.d(TAG, "onStartCommand: handleStartDownload for '$title', audioOnly=$audioOnly, codec=$videoCodec, sabr=${!sabrStreamingUrl.isNullOrEmpty()}")
                handleStartDownload(
                    videoId, title, url, audioUrl, quality,
                    thumbnail, channel, duration, audioOnly, userAgent, videoCodec,
                    audioExtension, audioMimeType, isMusic,
                    threadsOverride = threadsOverride,
                    sabrStreamingUrl = sabrStreamingUrl,
                    sabrAudioItag = sabrAudioItag,
                    sabrAudioLmt = sabrAudioLmt,
                    sabrVideoItag = sabrVideoItag,
                    sabrVideoLmt = sabrVideoLmt,
                    sabrPoToken = sabrPoToken,
                    sabrVisitorId = sabrVisitorId,
                    sabrDurationMs = sabrDurationMs
                )
            }
            ACTION_PAUSE_DOWNLOAD -> {
                Log.d(TAG, "onStartCommand: Handling PAUSE_DOWNLOAD for $videoId")
                videoId?.let { handlePause(it) }
            }
            ACTION_RESUME_DOWNLOAD -> {
                Log.d(TAG, "onStartCommand: Handling RESUME_DOWNLOAD for $videoId")
                videoId?.let { handleResume(it) }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                Log.d(TAG, "onStartCommand: Handling CANCEL_DOWNLOAD for $videoId")
                videoId?.let { handleCancel(it) }
            }
        }
        return START_NOT_STICKY
    }

    // ===== Download Lifecycle =====

    private fun handleStartDownload(
        videoId: String, title: String, url: String, audioUrl: String?,
        quality: String, thumbnail: String, channel: String,
        duration: Int, audioOnly: Boolean, userAgent: String?,
        videoCodec: String? = null,
        audioExtension: String? = null,
        audioMimeType: String? = null,
        isMusic: Boolean = false,
        threadsOverride: Int? = null,
        sabrStreamingUrl: String? = null,
        sabrAudioItag: Int = 0,
        sabrAudioLmt: Long = 0,
        sabrVideoItag: Int = 0,
        sabrVideoLmt: Long = 0,
        sabrPoToken: String = "",
        sabrVisitorId: String = "",
        sabrDurationMs: Long = 0
    ) {
        try {
            Log.d(TAG, "handleStartDownload: Checking directories...")
            val fileType = if (audioOnly) DownloadFileType.AUDIO else DownloadFileType.VIDEO
            val codecHint = videoCodec?.trim()?.lowercase()
            val isWebMCodec = codecHint?.let {
                it == "vp9" || it == "vp8" || it.startsWith("vp09") || it.startsWith("vp08")
            } ?: false
            val isAv1Codec = codecHint?.let { it == "av1" || it.startsWith("av01") || it.startsWith("av1") } ?: false
            val av1NeedsMkv = isAv1Codec
            val normalizedAudioExtension = audioExtension
                ?.trim()
                ?.lowercase()
                ?.trimStart('.')
                ?.takeIf { it.isNotBlank() }
                ?: "m4a"
            val normalizedAudioMimeType = audioMimeType
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "audio/mp4"
            val extension = when {
                audioOnly  -> normalizedAudioExtension
                isWebMCodec -> "webm"
                av1NeedsMkv -> "mkv"
                else -> "mp4"
            }
            downloadManager.customDownloadPath = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                if (isMusic) {
                    preferences.musicDownloadLocation.firstOrNull()
                        ?: preferences.downloadLocation.firstOrNull()
                } else {
                    preferences.downloadLocation.firstOrNull()
                }
            }
            val downloadDir = downloadManager.getDownloadDir(fileType)
            Log.d(TAG, "handleStartDownload: downloadDir=${downloadDir.absolutePath}, exists=${downloadDir.exists()}, canWrite=${downloadDir.canWrite()}")

            val fileName = downloadManager.generateFileName(title, quality, extension)
            val savePath = File(downloadDir, fileName).absolutePath
            Log.d(TAG, "handleStartDownload: savePath=$savePath")

            val video = Video(
                id = videoId, title = title, channelName = channel, channelId = "local",
                thumbnailUrl = thumbnail, duration = duration, viewCount = 0,
                uploadDate = System.currentTimeMillis().toString(),
                description = "Downloaded locally",
                isMusic = isMusic
            )

            val effectiveUrl = if (audioOnly && audioUrl != null) audioUrl else url
            val effectiveAudioUrl = if (audioOnly) null else audioUrl

            val threadCount = threadsOverride
                ?: kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    preferences.downloadThreads.firstOrNull() ?: 3
                }
            Log.d(TAG, "handleStartDownload: Using $threadCount download threads${if (threadsOverride != null) " (per-download override)" else ""}")

            val mission = if (userAgent != null) {
                FlowDownloadMission(
                    video = video,
                    url = effectiveUrl,
                    audioUrl = effectiveAudioUrl,
                    quality = quality,
                    savePath = savePath,
                    fileName = fileName,
                    threads = threadCount,
                    userAgent = userAgent
                )
            } else {
                FlowDownloadMission(
                    video = video,
                    url = effectiveUrl,
                    audioUrl = effectiveAudioUrl,
                    quality = quality,
                    savePath = savePath,
                    fileName = fileName,
                    threads = threadCount
                )
            }

            activeMissions[videoId] = mission

            val notificationId = getNotificationId(videoId)
            Log.d(TAG, "handleStartDownload: Starting foreground service (id=$notificationId)...")
            val notification = createNotification(mission, videoId)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    startForeground(
                        notificationId,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "handleStartDownload: startForeground failed (SDK 34+)", e)
                    startForeground(notificationId, notification)
                }
            } else {
                startForeground(notificationId, notification)
            }
            Log.d(TAG, "handleStartDownload: Foreground service started.")

            val job = serviceScope.launch {
                Log.d(TAG, "Starting download job for $videoId...")
                try {
                    val items = mutableListOf<DownloadItemEntity>()

                    if (audioOnly) {
                        items.add(DownloadItemEntity(
                            videoId = videoId, fileType = DownloadFileType.AUDIO,
                            fileName = fileName, filePath = savePath,
                            format = normalizedAudioExtension, quality = quality,
                            mimeType = normalizedAudioMimeType,
                            status = DownloadItemStatus.PENDING
                        ))
                    } else {
                        items.add(DownloadItemEntity(
                            videoId = videoId, fileType = DownloadFileType.VIDEO,
                            fileName = fileName, filePath = savePath,
                            format = when { isWebMCodec -> "webm"; isAv1Codec -> "mkv"; else -> "mp4" }, quality = quality,
                            status = DownloadItemStatus.PENDING
                        ))
                    }

                    Log.d(TAG, "Saving to database...")
                    downloadManager.saveDownload(video, items)
                    Log.d(TAG, "Saved to database.")

                    val download = downloadManager.getDownloadWithItems(videoId)
                    val ids = download?.items?.map { it.id }?.toMutableList() ?: mutableListOf()
                    itemIds[videoId] = ids

                    Log.d(TAG, "Saved download for $videoId with ${ids.size} item(s): $ids")

                    val wifiOnly = preferences.downloadOverWifiOnly.firstOrNull() ?: false
                    if (wifiOnly && !isOnWifi()) {
                        Log.i(TAG, "WiFi only enabled but not on WiFi. Pausing.")
                        mission.status = MissionStatus.PAUSED
                        mission.error = "Waiting for WiFi"
                        updateAllItemStatuses(videoId, DownloadItemStatus.PAUSED)
                        updateNotification(mission, videoId)
                        registerWifiCallback(videoId)
                        return@launch
                    }

                    val isSabrDownload = !sabrStreamingUrl.isNullOrEmpty() && sabrAudioItag > 0
                    if (isSabrDownload) {
                        Log.d(TAG, "Executing SABR download...")
                        executeSabrDownload(
                            mission, videoId, audioOnly, normalizedAudioMimeType,
                            sabrStreamingUrl = sabrStreamingUrl!!,
                            sabrAudioItag = sabrAudioItag,
                            sabrAudioLmt = sabrAudioLmt,
                            sabrVideoItag = sabrVideoItag,
                            sabrVideoLmt = sabrVideoLmt,
                            sabrPoToken = sabrPoToken,
                            sabrVisitorId = sabrVisitorId,
                            sabrDurationMs = sabrDurationMs
                        )
                    } else {
                        Log.d(TAG, "Executing download...")
                        executeDownload(mission, videoId, audioOnly, normalizedAudioMimeType)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e(TAG, "Error in download job for $videoId", e)
                    mission.status = MissionStatus.FAILED
                    mission.error = "Download failed. Please try again."
                    updateAllItemStatuses(videoId, DownloadItemStatus.FAILED)
                    updateNotification(mission, videoId)
                    stopForeground(false)
                }
            }
            downloadJobs[videoId] = job
        } catch (e: Exception) {
            Log.e(TAG, "Exception in handleStartDownload", e)
        }
    }

    private suspend fun executeDownload(
        mission: FlowDownloadMission,
        videoId: String,
        audioOnly: Boolean,
        audioMimeType: String = "audio/mp4"
    ) {
        Log.d(TAG, "executeDownload: Starting execution for $videoId. AudioOnly=$audioOnly")
        
        try {
            updateAllItemStatuses(videoId, DownloadItemStatus.DOWNLOADING)
            mission.status = MissionStatus.RUNNING
            Log.d(TAG, "executeDownload: Status updated to DOWNLOADING/RUNNING")

            val progressJob = serviceScope.launch {
                while (mission.status == MissionStatus.RUNNING) {
                    val ids = itemIds[videoId]
                    if (!ids.isNullOrEmpty()) {
                        downloadManager.emitProgress(
                            DownloadProgressUpdate(
                                videoId = videoId,
                                itemId = ids.first(),
                                downloadedBytes = (mission.downloadedBytes + mission.audioDownloadedBytes),
                                totalBytes = (mission.totalBytes + mission.audioTotalBytes),
                                status = DownloadItemStatus.DOWNLOADING
                            )
                        )
                    }
                    updateNotification(mission, videoId)
                    delay(250L)
                }
            }

            val downloadSuccess = parallelDownloader.start(mission) { /* no-op: progress polled above */ }
            progressJob.cancel()

            Log.d(TAG, "executeDownload: parallelDownloader.start finished. Result=$downloadSuccess")

            if (downloadSuccess) {
                var finalSuccess = true

                // Mux if DASH (separate video + audio streams)
                if (!audioOnly && mission.audioUrl != null) {
                    Log.d(TAG, "executeDownload: Starting Muxing...")

                    // Notify in-app UI that we are now merging (no byte-progress changes during mux)
                    val ids = itemIds[videoId]
                    if (!ids.isNullOrEmpty()) {
                        downloadManager.emitProgress(
                            DownloadProgressUpdate(
                                videoId = videoId,
                                itemId = ids.first(),
                                downloadedBytes = mission.downloadedBytes + mission.audioDownloadedBytes,
                                totalBytes = mission.totalBytes + mission.audioTotalBytes,
                                status = DownloadItemStatus.DOWNLOADING,
                                isMerging = true
                            )
                        )
                    }

                    // Update notification to show muxing phase
                    mission.error = "Merging audio & video..."
                    updateNotification(mission, videoId, isMuxing = true)
                    
                    val videoTmp = "${mission.savePath}.video.tmp"
                    val audioTmp = "${mission.savePath}.audio.tmp"
                    
                    val vFile = File(videoTmp)
                    val aFile = File(audioTmp)
                    
                    Log.d(TAG, "executeDownload: Muxing inputs - Video: ${vFile.length()} bytes, Audio: ${aFile.length()} bytes")

                    // Elevate the IO thread priority for the duration of the mux so the
                    // file-copy loop gets more CPU time and completes faster.
                    val prevPriority = android.os.Process.getThreadPriority(android.os.Process.myTid())
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND)

                    val muxSuccess = try {
                        FlowStreamMuxer.mux(videoTmp, audioTmp, mission.savePath)
                    } catch (e: Exception) {
                        Log.e(TAG, "executeDownload: Muxing threw exception", e)
                        false
                    } finally {
                        android.os.Process.setThreadPriority(prevPriority)
                    }
                    
                    Log.d(TAG, "executeDownload: Muxing result=$muxSuccess")

                    if (muxSuccess) {
                        File(videoTmp).delete()
                        File(audioTmp).delete()
                    } else {
                        Log.e(TAG, "executeDownload: Muxing failed. Audio/video format mismatch likely.")
                        finalSuccess = false
                        mission.status = MissionStatus.FAILED
                        mission.error = "Muxing failed — audio format incompatible with video container. Try re-downloading."
                        updateAllItemStatuses(videoId, DownloadItemStatus.FAILED)
                        
                        // Clean up temp files
                        try {
                            File(videoTmp).takeIf { it.exists() }?.delete()
                            File(audioTmp).takeIf { it.exists() }?.delete()
                            File(mission.savePath).takeIf { it.exists() }?.delete()
                        } catch (cleanupErr: Exception) {
                            Log.w(TAG, "executeDownload: Cleanup after mux failure", cleanupErr)
                        }
                    }
                } else {
                    Log.d(TAG, "executeDownload: No muxing required (AudioOnly or SingleStream)")
                }

                if (finalSuccess) {
                    Log.d(TAG, "executeDownload: Download SUCCESS")
                    mission.status = MissionStatus.FINISHED
                    mission.finishTime = System.currentTimeMillis()

                    // Update Room with final info
                    val fileSize = File(mission.savePath).length()
                    Log.d(TAG, "executeDownload: Final file size=$fileSize")
                    
                    val ids = itemIds[videoId]
                    if (!ids.isNullOrEmpty()) {
                        downloadManager.updateItemFull(ids.first(), fileSize, fileSize, DownloadItemStatus.COMPLETED)
                    }
                    
                    try {
                        val mimeType = when {
                            audioOnly -> audioMimeTypeForPath(mission.savePath, audioMimeType)
                            mission.savePath.endsWith(".webm") -> "video/webm"
                            mission.savePath.endsWith(".mkv")  -> "video/x-matroska"
                            else -> "video/mp4"
                        }
                        downloadManager.scanFile(mission.savePath, mimeType)
                    } catch (e: Exception) {
                        Log.w(TAG, "executeDownload: MediaScanner indexing failed (non-fatal)", e)
                    }

                    // Emit final progress
                    val ids2 = itemIds[videoId]
                    if (!ids2.isNullOrEmpty()) {
                        downloadManager.emitProgress(
                            DownloadProgressUpdate(
                                videoId = videoId,
                                itemId = ids2.first(),
                                downloadedBytes = fileSize,
                                totalBytes = fileSize,
                                status = DownloadItemStatus.COMPLETED
                            )
                        )
                    }

                    // Remove the foreground notification first (stopForeground(false) leaves the
                    // old "Merging…" notification stuck on MIUI; true removes it cleanly).
                    stopForeground(true)
                    // Post a fresh completion notification after the foreground one is gone.
                    val nmComplete = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nmComplete.notify(getNotificationId(videoId), createNotification(mission, videoId, isComplete = true))

                    // Fetch and persist SponsorBlock segments inline (awaited) so it completes
                    // before the service's finally-block calls stopSelf() → onDestroy() → serviceScope.cancel().
                    try {
                        val segments = sponsorBlockRepository.getSegments(videoId)
                        if (segments.isNotEmpty()) {
                            val json = gson.toJson(segments)
                            downloadManager.saveSponsorBlockData(videoId, json)
                            Log.d(TAG, "Saved ${segments.size} SponsorBlock segments for $videoId")
                        } else {
                            Log.d(TAG, "No SponsorBlock segments found for $videoId")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "SponsorBlock fetch failed for $videoId (non-fatal)", e)
                    }
                } else {
                    Log.e(TAG, "executeDownload: Final success check failed after download/mux")
                    stopForeground(true)
                    updateNotification(mission, videoId)
                }
            } else if (mission.status == MissionStatus.PAUSED) {
                Log.d(TAG, "executeDownload: Download paused for $videoId (workers stopped naturally)")
                val ids = itemIds[videoId]
                if (!ids.isNullOrEmpty()) {
                    val downloaded = mission.downloadedBytes + mission.audioDownloadedBytes
                    val total = mission.totalBytes + mission.audioTotalBytes
                    downloadManager.updateItemFull(ids.first(), downloaded, total, DownloadItemStatus.PAUSED)
                }
            } else {
                Log.e(TAG, "executeDownload: parallelDownloader.start returned false")
                mission.status = MissionStatus.FAILED
                mission.error = "Download failed"
                updateAllItemStatuses(videoId, DownloadItemStatus.FAILED)
                stopForeground(false)
                updateNotification(mission, videoId)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "executeDownload: Critical error", e)
            mission.status = MissionStatus.FAILED
            mission.error = "Download failed. Please try again."
            updateAllItemStatuses(videoId, DownloadItemStatus.FAILED)
            stopForeground(false)
            updateNotification(mission, videoId)
        } finally {
            val currentStatus = activeMissions[videoId]?.status
            Log.d(TAG, "executeDownload: Cleanup for $videoId (status=$currentStatus)")
            if (currentStatus != MissionStatus.PAUSED) {
                activeMissions.remove(videoId)
                itemIds.remove(videoId)
            }
            downloadJobs.remove(videoId)
        }

        // Stop service if no more active downloads
        if (activeMissions.isEmpty()) {
            stopSelf()
        }
    }

    private suspend fun executeSabrDownload(
        mission: FlowDownloadMission,
        videoId: String,
        audioOnly: Boolean,
        audioMimeType: String = "audio/mp4",
        sabrStreamingUrl: String,
        sabrAudioItag: Int,
        sabrAudioLmt: Long,
        sabrVideoItag: Int,
        sabrVideoLmt: Long,
        sabrPoToken: String,
        sabrVisitorId: String,
        sabrDurationMs: Long
    ) {
        Log.d(TAG, "executeSabrDownload: Starting for $videoId, audioOnly=$audioOnly")

        try {
            updateAllItemStatuses(videoId, DownloadItemStatus.DOWNLOADING)
            mission.status = MissionStatus.RUNNING

            val engine = SabrDownloadEngine()
            activeSabrEngines[videoId] = engine

            val videoTmp = "${mission.savePath}.video.tmp"
            val audioTmp = "${mission.savePath}.audio.tmp"

            val progressJob = serviceScope.launch {
                while (mission.status == MissionStatus.RUNNING) {
                    val ids = itemIds[videoId]
                    if (!ids.isNullOrEmpty()) {
                        downloadManager.emitProgress(
                            DownloadProgressUpdate(
                                videoId = videoId,
                                itemId = ids.first(),
                                downloadedBytes = engine.downloadedVideoBytes.get() + engine.downloadedAudioBytes.get(),
                                totalBytes = mission.totalBytes.coerceAtLeast(1),
                                status = DownloadItemStatus.DOWNLOADING
                            )
                        )
                    }
                    updateNotification(mission, videoId)
                    delay(500L)
                }
            }

            val downloadSuccess = engine.download(
                streamingUrl = sabrStreamingUrl,
                videoId = videoId,
                audioItag = sabrAudioItag,
                audioLmt = sabrAudioLmt,
                videoItag = sabrVideoItag,
                videoLmt = sabrVideoLmt,
                poToken = sabrPoToken,
                visitorId = sabrVisitorId,
                ustreamerConfig = ByteArray(0),
                durationMs = sabrDurationMs,
                videoOutputPath = videoTmp,
                audioOutputPath = audioTmp,
                audioOnly = audioOnly
            ) { downloaded, estimated ->
                mission.downloadedBytesAtomic.set(if (audioOnly) 0 else engine.downloadedVideoBytes.get())
                mission.audioDownloadedBytesAtomic.set(engine.downloadedAudioBytes.get())
                if (estimated > 0) mission.totalBytes = estimated
            }

            progressJob.cancel()
            activeSabrEngines.remove(videoId)

            if (downloadSuccess) {
                var finalSuccess = true

                if (!audioOnly) {
                    Log.d(TAG, "executeSabrDownload: Muxing SABR output...")
                    val ids = itemIds[videoId]
                    if (!ids.isNullOrEmpty()) {
                        downloadManager.emitProgress(
                            DownloadProgressUpdate(
                                videoId = videoId,
                                itemId = ids.first(),
                                downloadedBytes = engine.downloadedVideoBytes.get() + engine.downloadedAudioBytes.get(),
                                totalBytes = engine.downloadedVideoBytes.get() + engine.downloadedAudioBytes.get(),
                                status = DownloadItemStatus.DOWNLOADING,
                                isMerging = true
                            )
                        )
                    }
                    mission.error = "Merging audio & video..."
                    updateNotification(mission, videoId, isMuxing = true)

                    val prevPriority = android.os.Process.getThreadPriority(android.os.Process.myTid())
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND)

                    val muxSuccess = try {
                        FlowStreamMuxer.mux(videoTmp, audioTmp, mission.savePath)
                    } catch (e: Exception) {
                        Log.e(TAG, "executeSabrDownload: Muxing failed", e)
                        false
                    } finally {
                        android.os.Process.setThreadPriority(prevPriority)
                    }

                    if (muxSuccess) {
                        File(videoTmp).delete()
                        File(audioTmp).delete()
                    } else {
                        Log.e(TAG, "executeSabrDownload: Mux failed")
                        finalSuccess = false
                        mission.status = MissionStatus.FAILED
                        mission.error = "Muxing failed — try re-downloading."
                        updateAllItemStatuses(videoId, DownloadItemStatus.FAILED)
                        listOf(videoTmp, audioTmp, mission.savePath).forEach { path ->
                            try { File(path).takeIf { it.exists() }?.delete() } catch (_: Exception) {}
                        }
                    }
                } else {
                    File(audioTmp).renameTo(File(mission.savePath))
                }

                if (finalSuccess) {
                    mission.status = MissionStatus.FINISHED
                    mission.finishTime = System.currentTimeMillis()
                    val fileSize = File(mission.savePath).length()

                    val ids = itemIds[videoId]
                    if (!ids.isNullOrEmpty()) {
                        downloadManager.updateItemFull(ids.first(), fileSize, fileSize, DownloadItemStatus.COMPLETED)
                    }

                    try {
                        val mimeType = when {
                            audioOnly -> audioMimeTypeForPath(mission.savePath, audioMimeType)
                            mission.savePath.endsWith(".webm") -> "video/webm"
                            mission.savePath.endsWith(".mkv") -> "video/x-matroska"
                            else -> "video/mp4"
                        }
                        downloadManager.scanFile(mission.savePath, mimeType)
                    } catch (e: Exception) {
                        Log.w(TAG, "executeSabrDownload: MediaScanner failed (non-fatal)", e)
                    }

                    val ids2 = itemIds[videoId]
                    if (!ids2.isNullOrEmpty()) {
                        downloadManager.emitProgress(
                            DownloadProgressUpdate(
                                videoId = videoId,
                                itemId = ids2.first(),
                                downloadedBytes = fileSize,
                                totalBytes = fileSize,
                                status = DownloadItemStatus.COMPLETED
                            )
                        )
                    }

                    stopForeground(true)
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(getNotificationId(videoId), createNotification(mission, videoId, isComplete = true))

                    try {
                        val segments = sponsorBlockRepository.getSegments(videoId)
                        if (segments.isNotEmpty()) {
                            downloadManager.saveSponsorBlockData(videoId, gson.toJson(segments))
                            Log.d(TAG, "Saved ${segments.size} SponsorBlock segments for $videoId")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "SponsorBlock fetch failed (non-fatal)", e)
                    }
                } else {
                    stopForeground(true)
                    updateNotification(mission, videoId)
                }
            } else {
                mission.status = MissionStatus.FAILED
                mission.error = "SABR download failed"
                updateAllItemStatuses(videoId, DownloadItemStatus.FAILED)
                stopForeground(false)
                updateNotification(mission, videoId)
                listOf("${mission.savePath}.video.tmp", "${mission.savePath}.audio.tmp").forEach { path ->
                    try { File(path).takeIf { it.exists() }?.delete() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "executeSabrDownload: Critical error", e)
            mission.status = MissionStatus.FAILED
            mission.error = "SABR download failed. Please try again."
            updateAllItemStatuses(videoId, DownloadItemStatus.FAILED)
            stopForeground(false)
            updateNotification(mission, videoId)
        } finally {
            activeSabrEngines.remove(videoId)
            val currentStatus = activeMissions[videoId]?.status
            if (currentStatus != MissionStatus.PAUSED) {
                activeMissions.remove(videoId)
                itemIds.remove(videoId)
            }
            downloadJobs.remove(videoId)
        }

        if (activeMissions.isEmpty()) {
            stopSelf()
        }
    }

    private fun handlePause(videoId: String) {
        val mission = activeMissions[videoId] ?: run {
            Log.w(TAG, "handlePause: No active mission for $videoId")
            return
        }
        if (mission.status != MissionStatus.RUNNING) {
            Log.d(TAG, "handlePause: Mission $videoId is not running (${mission.status}), ignoring")
            return
        }
        Log.d(TAG, "handlePause: Setting status to PAUSED for $videoId")
        mission.status = MissionStatus.PAUSED

        activeSabrEngines.remove(videoId)?.cancel()

        val calls = mission.activeCalls.toList()
        mission.activeCalls.clear()
        calls.forEach { it.cancel() }
        Log.d(TAG, "handlePause: Cancelled ${calls.size} in-flight OkHttp call(s) for $videoId")

        serviceScope.launch {
            updateAllItemStatuses(videoId, DownloadItemStatus.PAUSED)
            val ids = itemIds[videoId]
            if (!ids.isNullOrEmpty()) {
                downloadManager.emitProgress(
                    DownloadProgressUpdate(
                        videoId = videoId, itemId = ids.first(),
                        downloadedBytes = mission.downloadedBytes + mission.audioDownloadedBytes,
                        totalBytes = mission.totalBytes + mission.audioTotalBytes,
                        status = DownloadItemStatus.PAUSED
                    )
                )
            }
        }

        updateNotification(mission, videoId)

        if (activeMissions.values.none { it.status == MissionStatus.RUNNING || it.status == MissionStatus.PAUSED }) {
            stopForeground(false)
        }
    }

    private fun handleResume(videoId: String) {
        val mission = activeMissions[videoId] ?: run {
            Log.w(TAG, "handleResume: No mission found for $videoId — cannot resume")
            return
        }
        if (mission.status != MissionStatus.PAUSED) {
            Log.d(TAG, "handleResume: Mission $videoId is not paused (${mission.status}), ignoring")
            return
        }
        Log.d(TAG, "handleResume: Resuming $videoId")

        val notificationId = getNotificationId(videoId)
        val notification = createNotification(mission, videoId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    notificationId, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } catch (e: Exception) {
                startForeground(notificationId, notification)
            }
        } else {
            startForeground(notificationId, notification)
        }

        val audioOnly = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            downloadManager.getDownloadWithItems(videoId)?.isAudioOnly
        } ?: (mission.audioUrl == null && mission.savePath.endsWith(".m4a", ignoreCase = true))
        val previousJob = downloadJobs[videoId]
        val job = serviceScope.launch {
            previousJob?.join()
            executeDownload(mission, videoId, audioOnly)
        }
        downloadJobs[videoId] = job
    }

    private fun handleCancel(videoId: String) {
        val mission = activeMissions[videoId]
        mission?.status = MissionStatus.FAILED

        activeSabrEngines.remove(videoId)?.cancel()

        mission?.let { m ->
            val calls = m.activeCalls.toList()
            m.activeCalls.clear()
            calls.forEach { it.cancel() }
            Log.d(TAG, "handleCancel: Cancelled ${calls.size} in-flight OkHttp call(s) for $videoId")
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(getNotificationId(videoId))

        val downloadJob = downloadJobs[videoId]
        downloadJob?.cancel()

        activeMissions.remove(videoId)
        downloadJobs.remove(videoId)
        itemIds.remove(videoId)

        serviceScope.launch {
            updateAllItemStatuses(videoId, DownloadItemStatus.CANCELLED)
            try { downloadJob?.join() } catch (_: Exception) {}
            mission?.let { m ->
                listOf(
                    m.savePath,
                    "${m.savePath}.video.tmp",
                    "${m.savePath}.audio.tmp"
                ).forEach { path ->
                    try { File(path).takeIf { it.exists() }?.delete() } catch (_: Exception) {}
                }
                Log.d(TAG, "handleCancel: Cleaned up tmp files for $videoId")
            }
            downloadManager.deleteDownload(videoId)
        }

        if (activeMissions.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun audioMimeTypeForPath(path: String, fallback: String): String {
        return when {
            path.endsWith(".webm", ignoreCase = true) -> "audio/webm"
            path.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
            path.endsWith(".opus", ignoreCase = true) -> "audio/ogg"
            path.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            else -> fallback
        }
    }

    // ===== WiFi Management =====

    private fun isOnWifi(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun registerWifiCallback(videoId: String) {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    // WiFi available — resume paused downloads
                    activeMissions.forEach { (id, mission) ->
                        if (mission.status == MissionStatus.PAUSED && mission.error == "Waiting for WiFi") {
                            handleResume(id)
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                serviceScope.launch {
                    val wifiOnly = preferences.downloadOverWifiOnly.firstOrNull() ?: false
                    if (wifiOnly && !isOnWifi()) {
                        // Pause all running downloads
                        activeMissions.forEach { (id, mission) ->
                            if (mission.status == MissionStatus.RUNNING) {
                                mission.error = "Waiting for WiFi"
                                handlePause(id)
                            }
                        }
                    }
                }
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        cm.registerNetworkCallback(request, connectivityCallback!!)
    }

    // ===== Notifications =====

    private fun createNotification(
        mission: FlowDownloadMission,
        videoId: String,
        isComplete: Boolean = false,
        isMuxing: Boolean = false
    ): android.app.Notification {
        val progress = (mission.progress * 100).toInt()
        val contentText = when {
            isComplete -> "Download complete"
            isMuxing -> "Merging audio & video..."
            mission.isFailed() -> mission.error ?: "Download failed"
            mission.status == MissionStatus.PAUSED -> "Paused — ${mission.error ?: "tap to resume"}"
            else -> "$progress% • ${formatBytes(mission.downloadedBytes + mission.audioDownloadedBytes)} / ${formatBytes(mission.totalBytes + mission.audioTotalBytes)}"
        }

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this, videoId.hashCode(), tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(mission.video.title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapPendingIntent)
            .setGroup(NOTIFICATION_GROUP)

        if (!isComplete && !mission.isFailed()) {
            if (isMuxing) {
                builder.setProgress(100, 100, true)
            } else {
                builder.setProgress(100, progress, false)

                if (mission.status == MissionStatus.PAUSED) {
                    // Show Resume button
                    val resumeIntent = Intent(this, FlowDownloadService::class.java).apply {
                        action = ACTION_RESUME_DOWNLOAD
                        putExtra("video_id", videoId)
                    }
                    val resumePending = PendingIntent.getService(
                        this, "resume_$videoId".hashCode(), resumeIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePending)
                } else {
                    // Show Pause button
                    val pauseIntent = Intent(this, FlowDownloadService::class.java).apply {
                        action = ACTION_PAUSE_DOWNLOAD
                        putExtra("video_id", videoId)
                    }
                    val pausePending = PendingIntent.getService(
                        this, "pause_$videoId".hashCode(), pauseIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePending)
                }

                // Cancel button
                val cancelIntent = Intent(this, FlowDownloadService::class.java).apply {
                    action = ACTION_CANCEL_DOWNLOAD
                    putExtra("video_id", videoId)
                }
                val cancelPending = PendingIntent.getService(
                    this, "cancel_$videoId".hashCode(), cancelIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPending)
            }
        } else {
            builder.setProgress(0, 0, false)
            if (isComplete) {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                builder.setAutoCancel(true)
            }
        }

        return builder.build()
    }

    private fun updateNotification(
        mission: FlowDownloadMission,
        videoId: String,
        isComplete: Boolean = false,
        isMuxing: Boolean = false
    ) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(getNotificationId(videoId), createNotification(mission, videoId, isComplete, isMuxing))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Flow Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Video and audio download progress"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun getNotificationId(videoId: String): Int {
        val hash = videoId.hashCode()
        return if (hash == 0) 1 else hash
    }

    // ===== Helpers =====

    private suspend fun updateAllItemStatuses(videoId: String, status: DownloadItemStatus) {
        try {
            downloadManager.updateAllItemsStatus(videoId, status)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update item statuses for $videoId", e)
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.1f GB", bytes / (1024 * 1024 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024 * 1024.0))
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        connectivityCallback?.let {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        serviceScope.cancel()
    }
}
