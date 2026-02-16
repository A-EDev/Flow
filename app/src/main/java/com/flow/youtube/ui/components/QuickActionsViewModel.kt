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
                android.util.Log.d("QuickActionsViewModel", "Toggling Watch Later for video: ${video.id}")
                val isInWatchLater = playlistRepository.isInWatchLater(video.id)
                android.util.Log.d("QuickActionsViewModel", "Is currently in Watch Later: $isInWatchLater")
                
                if (isInWatchLater) {
                    playlistRepository.removeFromWatchLater(video.id)
                    android.util.Log.d("QuickActionsViewModel", "Removed from Watch Later")
                    Toast.makeText(context, "Removed from Watch Later", Toast.LENGTH_SHORT).show()
                } else {
                    playlistRepository.addToWatchLater(video)
                    android.util.Log.d("QuickActionsViewModel", "Added to Watch Later")
                    Toast.makeText(context, "Added to Watch Later", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("QuickActionsViewModel", "Error toggling Watch Later", e)
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

    fun downloadVideo(video: Video) {
        viewModelScope.launch {
            try {
                com.flow.youtube.ui.screens.player.util.VideoPlayerUtils.promptStoragePermissionIfNeeded(context)

                Toast.makeText(context, "Fetching download links...", Toast.LENGTH_SHORT).show()
                val streamInfo = withContext(Dispatchers.IO) {
                    repository.getVideoStreamInfo(video.id)
                }

                if (streamInfo != null) {
                    val combinedStreams = streamInfo.videoStreams
                        ?.filterIsInstance<org.schabi.newpipe.extractor.stream.VideoStream>()
                        ?: emptyList()
                    val videoOnlyStreams = streamInfo.videoOnlyStreams
                        ?.filterIsInstance<org.schabi.newpipe.extractor.stream.VideoStream>()
                        ?: emptyList()

                    val bestCombined = combinedStreams.maxByOrNull { it.height }
                    val bestVideoOnly = videoOnlyStreams.maxByOrNull { it.height }
                    
                    val selectedStream: org.schabi.newpipe.extractor.stream.VideoStream?
                    val audioUrl: String?

                    if (bestVideoOnly != null && bestCombined != null && bestVideoOnly.height > bestCombined.height) {
                        selectedStream = bestVideoOnly
                        
                        val isMp4Video = selectedStream.format?.name?.contains("mp4", ignoreCase = true) == true
                        
                        val compatibleAudio = if (isMp4Video) {
                            streamInfo.audioStreams?.filter { 
                                it.format?.name?.contains("m4a", ignoreCase = true) == true 
                            }?.maxByOrNull { it.averageBitrate }
                        } else {
                            streamInfo.audioStreams?.filter { 
                                it.format?.name?.contains("webm", ignoreCase = true) == true 
                            }?.maxByOrNull { it.averageBitrate }
                        }
                        
                        audioUrl = (compatibleAudio ?: streamInfo.audioStreams?.maxByOrNull { it.averageBitrate })?.url
                    } else if (bestCombined != null) {
                        selectedStream = bestCombined
                        audioUrl = null
                    } else if (bestVideoOnly != null) {
                        selectedStream = bestVideoOnly
                        val isMp4Video = selectedStream.format?.name?.contains("mp4", ignoreCase = true) == true
                        val compatibleAudio = if (isMp4Video) {
                            streamInfo.audioStreams?.filter { 
                                it.format?.name?.contains("m4a", ignoreCase = true) == true 
                            }?.maxByOrNull { it.averageBitrate }
                        } else {
                            streamInfo.audioStreams?.filter { 
                                it.format?.name?.contains("webm", ignoreCase = true) == true 
                            }?.maxByOrNull { it.averageBitrate }
                        }
                        audioUrl = (compatibleAudio ?: streamInfo.audioStreams?.maxByOrNull { it.averageBitrate })?.url
                    } else {
                        selectedStream = null
                        audioUrl = null
                    }

                    if (selectedStream != null && selectedStream.url != null) {
                        val fullVideo = Video(
                            id = video.id,
                            title = video.title.ifBlank { streamInfo.name ?: "Unknown" },
                            channelName = video.channelName.ifBlank { streamInfo.uploaderName ?: "" },
                            channelId = video.channelId.ifBlank { streamInfo.uploaderUrl?.substringAfterLast("/") ?: "local" },
                            thumbnailUrl = video.thumbnailUrl.ifBlank { streamInfo.thumbnails?.maxByOrNull { it.height }?.url ?: "" },
                            duration = if (video.duration > 0) video.duration else streamInfo.duration.toInt(),
                            viewCount = video.viewCount,
                            uploadDate = video.uploadDate,
                            description = video.description.ifBlank { streamInfo.description?.content ?: "" }
                        )

                        com.flow.youtube.data.video.downloader.FlowDownloadService.startDownload(
                            context = context,
                            video = fullVideo,
                            url = selectedStream.url!!,
                            quality = "${selectedStream.height}p",
                            audioUrl = audioUrl
                        )
                        Toast.makeText(context, "Download started: ${fullVideo.title}", Toast.LENGTH_SHORT).show()
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
}
