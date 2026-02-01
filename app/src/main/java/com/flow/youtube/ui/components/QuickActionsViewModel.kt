package com.flow.youtube.ui.components

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.PlaylistRepository
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import com.flow.youtube.data.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class QuickActionsViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val playlistRepository: PlaylistRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val watchLaterIds = playlistRepository.getWatchLaterIdsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggleWatchLater(video: Video) {
        viewModelScope.launch {
            try {
                val isInWatchLater = playlistRepository.isInWatchLater(video.id)
                if (isInWatchLater) {
                    playlistRepository.removeFromWatchLater(video.id)
                    Toast.makeText(context, "Removed from Watch Later", Toast.LENGTH_SHORT).show()
                } else {
                    playlistRepository.addToWatchLater(video)
                    Toast.makeText(context, "Added to Watch Later", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Mark a video as "Not Interested" - this strongly penalizes the video's topics
     * and channel in the FlowNeuroEngine, making similar content much less likely to appear.
     */
    fun markNotInterested(video: Video) {
        viewModelScope.launch {
            try {
                FlowNeuroEngine.markNotInterested(context, video)
                Toast.makeText(
                    context, 
                    "Got it! You'll see less content like this.", 
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun downloadVideo(videoId: String, title: String) {
        viewModelScope.launch {
            try {
                Toast.makeText(context, "Fetching download links...", Toast.LENGTH_SHORT).show()
                val streamInfo = withContext(Dispatchers.IO) {
                    repository.getVideoStreamInfo(videoId)
                }

                if (streamInfo != null) {
                    val stream = streamInfo.videoStreams.maxByOrNull { it.height }
                    if (stream != null && stream.url != null) {
                        startDownload(context, title, stream.url!!, "mp4")
                        Toast.makeText(context, "Downloading started...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No suitable stream found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Failed to fetch video info", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startDownload(context: Context, title: String, url: String, extension: String) {
        try {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                .setTitle(title)
                .setDescription("Downloading video...")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MOVIES, "$title.$extension")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
