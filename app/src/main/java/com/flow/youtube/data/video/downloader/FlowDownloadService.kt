package com.flow.youtube.data.video.downloader

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
import com.flow.youtube.MainActivity
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.data.local.entity.DownloadFileType
import com.flow.youtube.data.local.entity.DownloadItemEntity
import com.flow.youtube.data.local.entity.DownloadItemStatus
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.video.DownloadProgressUpdate
import com.flow.youtube.data.video.VideoDownloadManager
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

    private val activeMissions = ConcurrentHashMap<String, FlowDownloadMission>()
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
        
        const val ACTION_START_DOWNLOAD = "com.flow.youtube.START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.flow.youtube.PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.flow.youtube.RESUME_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.flow.youtube.CANCEL_DOWNLOAD"

        /** Audio-only download mode flag — if set, only download audio stream */
        const val EXTRA_AUDIO_ONLY = "audio_only"

        fun startDownload(
            context: Context, 
            video: Video, 
            url: String, 
            quality: String, 
            audioUrl: String? = null,
            audioOnly: Boolean = false,
            userAgent: String? = null
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

                Log.d(TAG, "onStartCommand: handleStartDownload for '$title', audioOnly=$audioOnly, UA=$userAgent")
                handleStartDownload(
                    videoId, title, url, audioUrl, quality, 
                    thumbnail, channel, duration, audioOnly, userAgent
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
        duration: Int, audioOnly: Boolean, userAgent: String?
    ) {
        try {
            Log.d(TAG, "handleStartDownload: Checking directories...")
            val fileType = if (audioOnly) DownloadFileType.AUDIO else DownloadFileType.VIDEO
            val extension = if (audioOnly) "m4a" else "mp4"
            val downloadDir = downloadManager.getDownloadDir(fileType)
            Log.d(TAG, "handleStartDownload: downloadDir=${downloadDir.absolutePath}, exists=${downloadDir.exists()}, canWrite=${downloadDir.canWrite()}")

            val fileName = downloadManager.generateFileName(title, quality, extension)
            val savePath = File(downloadDir, fileName).absolutePath
            Log.d(TAG, "handleStartDownload: savePath=$savePath")

            val video = Video(
                id = videoId, title = title, channelName = channel, channelId = "local",
                thumbnailUrl = thumbnail, duration = duration, viewCount = 0,
                uploadDate = System.currentTimeMillis().toString(),
                description = "Downloaded locally"
            )

            val effectiveUrl = if (audioOnly && audioUrl != null) audioUrl else url
            val effectiveAudioUrl = if (audioOnly) null else audioUrl

            val threadCount = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                preferences.downloadThreads.firstOrNull() ?: 3
            }
            Log.d(TAG, "handleStartDownload: Using $threadCount download threads")

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
                            format = "m4a", quality = quality,
                            status = DownloadItemStatus.PENDING
                        ))
                    } else {
                        items.add(DownloadItemEntity(
                            videoId = videoId, fileType = DownloadFileType.VIDEO,
                            fileName = fileName, filePath = savePath,
                            format = "mp4", quality = quality,
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

                    Log.d(TAG, "Executing download...")
                    executeDownload(mission, videoId, audioOnly)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in download job for $videoId", e)
                    mission.status = MissionStatus.FAILED
                    mission.error = "Internal error: ${e.message}"
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

    private suspend fun executeDownload(mission: FlowDownloadMission, videoId: String, audioOnly: Boolean) {
        Log.d(TAG, "executeDownload: Starting execution for $videoId. AudioOnly=$audioOnly")
        
        try {
            updateAllItemStatuses(videoId, DownloadItemStatus.DOWNLOADING)
            Log.d(TAG, "executeDownload: Status updated to DOWNLOADING")

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
                    
                    // Update notification to show muxing phase
                    mission.error = "Merging audio & video..."
                    updateNotification(mission, videoId, isMuxing = true)
                    
                    val videoTmp = "${mission.savePath}.video.tmp"
                    val audioTmp = "${mission.savePath}.audio.tmp"
                    
                    val vFile = File(videoTmp)
                    val aFile = File(audioTmp)
                    
                    Log.d(TAG, "executeDownload: Muxing inputs - Video: ${vFile.length()} bytes, Audio: ${aFile.length()} bytes")

                    val muxSuccess = try {
                        FlowStreamMuxer.mux(videoTmp, audioTmp, mission.savePath)
                    } catch (e: Exception) {
                        Log.e(TAG, "executeDownload: Muxing threw exception", e)
                        false
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
                    
                    // Copy to public storage via MediaStore so the file is visible
                    // in the system file manager and playable by external apps
                    try {
                        val mimeType = if (audioOnly) "audio/mp4" else "video/mp4"
                        val mediaUri = downloadManager.copyToPublicStorage(
                            mission.savePath, mission.video.title, mimeType
                        )
                        if (mediaUri != null) {
                            Log.d(TAG, "executeDownload: Copied to public MediaStore: $mediaUri")
                        } else {
                            Log.d(TAG, "executeDownload: MediaStore copy skipped (pre-Q or file in public dir already)")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "executeDownload: MediaStore copy failed (non-fatal)", e)
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

                    stopForeground(false)
                    updateNotification(mission, videoId, isComplete = true)
                } else {
                    Log.e(TAG, "executeDownload: Final success check failed after download/mux")
                    stopForeground(false)
                    updateNotification(mission, videoId)
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
            Log.e(TAG, "executeDownload: Critical error", e)
            mission.status = MissionStatus.FAILED
            mission.error = e.message
            updateAllItemStatuses(videoId, DownloadItemStatus.FAILED)
            stopForeground(false)
            updateNotification(mission, videoId)
        } finally {
            Log.d(TAG, "executeDownload: Cleanup for $videoId")
            activeMissions.remove(videoId)
            itemIds.remove(videoId)
            downloadJobs.remove(videoId)
        }

        // Stop service if no more active downloads
        if (activeMissions.isEmpty()) {
            stopSelf()
        }
    }

    private fun handlePause(videoId: String) {
        val mission = activeMissions[videoId] ?: return
        mission.status = MissionStatus.PAUSED
        downloadJobs[videoId]?.cancel()

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
        
        // Stop foreground if no other active downloads
        if (activeMissions.values.none { it.status == MissionStatus.RUNNING }) {
            stopForeground(false)
        }
    }

    private fun handleResume(videoId: String) {
        val mission = activeMissions[videoId] ?: return
        mission.status = MissionStatus.RUNNING

        val job = serviceScope.launch {
            val audioOnly = mission.audioUrl == null && 
                mission.savePath.endsWith(".m4a")
            executeDownload(mission, videoId, audioOnly)
        }
        downloadJobs[videoId] = job
    }

    private fun handleCancel(videoId: String) {
        val mission = activeMissions[videoId]
        mission?.status = MissionStatus.FAILED
        downloadJobs[videoId]?.cancel()

        serviceScope.launch {
            updateAllItemStatuses(videoId, DownloadItemStatus.CANCELLED)
            // Clean up partial files
            mission?.let { m ->
                listOf(
                    m.savePath,
                    "${m.savePath}.video.tmp",
                    "${m.savePath}.audio.tmp"
                ).forEach { path ->
                    try { File(path).takeIf { it.exists() }?.delete() } catch (_: Exception) {}
                }
            }
            downloadManager.deleteDownload(videoId)
        }

        activeMissions.remove(videoId)
        downloadJobs.remove(videoId)
        itemIds.remove(videoId)

        // Cancel notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(getNotificationId(videoId))

        if (activeMissions.isEmpty()) {
            stopForeground(true)
            stopSelf()
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
