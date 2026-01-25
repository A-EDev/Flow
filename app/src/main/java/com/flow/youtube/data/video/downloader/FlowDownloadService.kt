package com.flow.youtube.data.video.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.flow.youtube.MainActivity
import com.flow.youtube.data.model.Video
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@AndroidEntryPoint
class FlowDownloadService : Service() {

    @Inject
    lateinit var parallelDownloader: ParallelDownloader

    @Inject
    lateinit var preferences: com.flow.youtube.data.local.PlayerPreferences
    
    // Lazy injection to avoid circular dependency if any (VideoDownloadManager depends on Context)
    private val downloadManager by lazy { com.flow.youtube.data.video.VideoDownloadManager.getInstance(this) }
    
    // Manage multiple active downloads
    private val activeMissions = ConcurrentHashMap<String, FlowDownloadMission>()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        const val CHANNEL_ID = "flow_downloads"
        const val ACTION_START_DOWNLOAD = "com.flow.youtube.START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.flow.youtube.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.flow.youtube.CANCEL_DOWNLOAD"
        
        fun startDownload(context: Context, video: Video, url: String, quality: String, audioUrl: String? = null) {
            val intent = Intent(context, FlowDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra("video_id", video.id)
                putExtra("video_title", video.title)
                putExtra("video_url", url)
                putExtra("video_audio_url", audioUrl)
                putExtra("video_quality", quality)
                putExtra("video_thumbnail", video.thumbnailUrl)
                putExtra("video_channel", video.channelName)
            }
            context.startForegroundService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val videoId = intent.getStringExtra("video_id") ?: return START_NOT_STICKY
                val title = intent.getStringExtra("video_title") ?: "Unknown Video"
                val url = intent.getStringExtra("video_url") ?: return START_NOT_STICKY
                val audioUrl = intent.getStringExtra("video_audio_url")
                val quality = intent.getStringExtra("video_quality") ?: "720p"
                val thumbnail = intent.getStringExtra("video_thumbnail") ?: ""
                val channel = intent.getStringExtra("video_channel") ?: "Unknown"
                
                startDownload(videoId, title, url, audioUrl, quality, thumbnail, channel)
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(videoId: String, title: String, url: String, audioUrl: String?, quality: String, thumbnail: String, channel: String) {
        val fileName = "${title.replace(Regex("[^a-zA-Z0-9.-]"), "_")}_$quality.mp4"
        
        val downloadDir = File(filesDir, "downloads")
        if (!downloadDir.exists()) downloadDir.mkdirs()
        
        val savePath = File(downloadDir, fileName).absolutePath
        
        val video = Video(
            id = videoId,
            title = title,
            channelName = channel,
            channelId = "local",
            thumbnailUrl = thumbnail, 
            duration = 0,
            viewCount = 0,
            uploadDate = System.currentTimeMillis().toString(),
            description = "Downloaded locally"
        )

        val mission = FlowDownloadMission(
            video = video,
            url = url,
            audioUrl = audioUrl,
            quality = quality,
            savePath = savePath,
            fileName = fileName,
            threads = 3
        )
        
        activeMissions[mission.id] = mission
        
        val notificationId = mission.id.hashCode().let { if (it == 0) 1 else it }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                notificationId,
                createNotification(mission),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(notificationId, createNotification(mission))
        }
        
        serviceScope.launch {
            val wifiOnly = preferences.downloadOverWifiOnly.firstOrNull() ?: false
            if (wifiOnly) {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val isWifi = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                
                if (!isWifi) {
                    stopForeground(false)
                    updateNotification(mission, false)
                     return@launch
                }
            }
            
            val downloadSuccess = parallelDownloader.start(mission) { progress ->
                updateNotification(mission)
            }
            
            if (downloadSuccess) {
                var finalSuccess = true
                
                // Mux if DASH
                if (mission.audioUrl != null) {
                    val videoTmp = "${mission.savePath}.video.tmp"
                    val audioTmp = "${mission.savePath}.audio.tmp"
                    
                    val muxSuccess = FlowStreamMuxer.mux(videoTmp, audioTmp, mission.savePath)
                    if (muxSuccess) {
                        // Cleanup
                        File(videoTmp).delete()
                        File(audioTmp).delete()
                    } else {
                        finalSuccess = false
                        mission.status = MissionStatus.FAILED
                        mission.error = "Muxing failed"
                    }
                }

                if (finalSuccess) {
                    mission.status = MissionStatus.FINISHED
                    mission.finishTime = System.currentTimeMillis()
                    
                    stopForeground(false)
                    updateNotification(mission, true)
                    
                    downloadManager.saveDownloadedVideo(
                        com.flow.youtube.data.video.DownloadedVideo(
                            video = video,
                            filePath = savePath,
                            downloadId = System.currentTimeMillis(),
                            quality = quality,
                            fileSize = mission.totalBytes + mission.audioTotalBytes
                        )
                    )
                } else {
                    stopForeground(false)
                    updateNotification(mission)
                }
            } else {
                stopForeground(false)
                updateNotification(mission)
            }
            
            activeMissions.remove(mission.id)
        }
    }

    private fun createNotification(mission: FlowDownloadMission, isComplete: Boolean = false): android.app.Notification {
        val progress = (mission.progress * 100).toInt()
        val contentText = if (isComplete) "Download Complete" else if (mission.isFailed()) "Download Failed" else "$progress% • ${formatBytes(mission.downloadedBytes)}/${formatBytes(mission.totalBytes)}"
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(mission.video.title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            
        if (!isComplete && !mission.isFailed()) {
            builder.setProgress(100, progress, false)
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", null) // TODO: Implement pending intents
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", null)
        } else {
             builder.setProgress(0, 0, false)
             builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
        }

        return builder.build()
    }

    private fun updateNotification(mission: FlowDownloadMission, isComplete: Boolean = false) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = mission.id.hashCode().let { if (it == 0) 1 else it }
        notificationManager.notify(notificationId, createNotification(mission, isComplete))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Flow Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024 * 1024.0)
        return String.format("%.1f MB", mb)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
