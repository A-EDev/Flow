package com.flow.youtube.ui.screens.player.util

import android.content.Context
import android.widget.Toast
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.video.DownloadedVideo
import com.flow.youtube.data.video.VideoDownloadManager
import kotlinx.coroutines.launch
import java.io.File

object VideoPlayerUtils {
    fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    fun startDownload(context: Context, video: Video, url: String, qualityLabel: String) {
        try {
            val extension = "mp4"
            val fileName = "${video.title}_$qualityLabel.$extension".replace(Regex("[^a-zA-Z0-9\\s.-]"), "_")
            
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                .setTitle(video.title)
                .setDescription("Downloading video...")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MOVIES, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val downloadId = downloadManager.enqueue(request)
            
            // Save metadata for the Downloads screen
            val filePath = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), fileName).absolutePath
            
            // Use a coroutine scope to save metadata
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
            scope.launch {
                VideoDownloadManager.getInstance(context).saveDownloadedVideo(
                    DownloadedVideo(
                        video = video,
                        filePath = filePath,
                        downloadId = downloadId,
                        quality = qualityLabel
                    )
                )
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
