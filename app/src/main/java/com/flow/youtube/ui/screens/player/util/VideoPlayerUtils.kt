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
            
            // Start the optimized parallel download service
            com.flow.youtube.data.video.downloader.FlowDownloadService.startDownload(
                context, 
                video, 
                url, 
                qualityLabel
            )
            
            Toast.makeText(context, "Started high-speed download: ${video.title}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failedTo start: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
