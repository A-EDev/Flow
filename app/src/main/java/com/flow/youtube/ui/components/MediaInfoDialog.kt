package com.flow.youtube.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.screens.music.MusicTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoDialog(
    track: MusicTrack? = null,
    video: Video? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var mediaInfo by remember { mutableStateOf<com.flow.youtube.innertube.models.MediaInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(track, video) {
        isLoading = true
        val videoId = track?.videoId ?: video?.id
        if (videoId != null) {
            mediaInfo = com.flow.youtube.data.newmusic.InnertubeMusicService.getMediaInfo(videoId)
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Track Information",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Detailed technical and social metrics",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Divider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Content
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (track != null || video != null) {
                    val currentTitle = track?.title ?: video?.title
                    val currentArtist = track?.artist ?: video?.channelName
                    val currentId = track?.videoId ?: video?.id
                    
                    val info = mediaInfo

                    val details = mutableListOf<Pair<String, String?>>()
                    
                    // Basic Info
                    details.add("Title" to (info?.title ?: currentTitle))
                    details.add("Artist" to (info?.author ?: currentArtist))
                    if (track?.album?.isNotEmpty() == true) {
                        details.add("Album" to track.album)
                    }
                    
                    // Social
                    if (info?.viewCount != null) details.add("Views" to info.viewCount.toString())
                    else if (track?.views != null && track.views > 0) details.add("Views" to track.views.toString())
                    else if (video?.viewCount != null) details.add("Views" to video.viewCount.toString())

                    if (info?.like != null) details.add("Likes" to info.like.toString())
                    else if (video?.likeCount != null) details.add("Likes" to video.likeCount.toString())
                    
                    if (info?.dislike != null) details.add("Dislikes" to info.dislike.toString())
                    
                    if (info?.subscribers != null) details.add("Subscribers" to info.subscribers)

                    // Technical Info
                    details.add("Video ID" to currentId)
                    if (info?.authorId != null) details.add("Channel ID" to info.authorId)
                    else if (track?.channelId?.isNotEmpty() == true) details.add("Channel ID" to track.channelId)

                    if (info?.uploadDate != null) details.add("Uploaded" to info.uploadDate)
                    else if (video?.uploadDate != null) details.add("Uploaded" to video.uploadDate)
                    
                    // Stream Info
                    if (info?.videoId_tag != null) details.add("iTag" to info.videoId_tag.toString())
                    if (info?.mimeType != null) details.add("Mime Type" to info.mimeType)
                    if (info?.bitrate != null) details.add("Bitrate" to "${info.bitrate / 1000} kbps")
                    if (info?.sampleRate != null) details.add("Sample Rate" to "${info.sampleRate} Hz")
                    if (info?.contentLength != null) details.add("File Size" to formatFileSize(info.contentLength.toLongOrNull()))
                    if (info?.qualityLabel != null) details.add("Quality" to info.qualityLabel)
                    
                    // Fallback Duration
                    val duration = track?.duration ?: video?.duration
                    if (duration != null) details.add("Duration" to formatDuration(duration))

                    items(details.filter { it.second != null }) { (label, value) ->
                        InfoItem(label, value!!)
                    }
                    
                    if (isLoading) {
                        item {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(label, value)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            imageVector = Icons.Outlined.Info, // Generic icon or passing specific icons would be better
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null) return "Unknown"
    val mb = bytes / (1024.0 * 1024.0)
    return "%.2f MB".format(mb)
}
