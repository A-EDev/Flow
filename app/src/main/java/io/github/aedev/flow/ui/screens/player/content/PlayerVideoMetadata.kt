package io.github.aedev.flow.ui.screens.player.content

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.VideoCollaborator
import io.github.aedev.flow.data.model.needsCollaboratorResolution
import io.github.aedev.flow.data.model.toVideo
import io.github.aedev.flow.data.model.uploadDateMillis
import io.github.aedev.flow.data.repository.VideoCollaboratorResolver
import io.github.aedev.flow.ui.components.rememberDeArrowResult
import io.github.aedev.flow.ui.components.shared.rememberDateDisplaySettings
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState
import io.github.aedev.flow.utils.DateContext
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Display values derived from the played [Video] and the loaded stream info.
 */
@Immutable
internal data class PlayerVideoMetadata(
    val resolvedVideoTitle: String,
    val resolvedCollaborators: List<VideoCollaborator>,
    val resolvedChannelName: String,
    val streamUploadDate: String?,
    val dialogVideo: Video,
)

@Composable
internal fun rememberPlayerVideoMetadata(
    video: Video,
    uiState: VideoPlayerUiState,
    deArrowEnabled: Boolean,
    context: Context,
): PlayerVideoMetadata {
    val deArrowResult = rememberDeArrowResult(video.id, deArrowEnabled)
    val resolvedVideoTitle = deArrowResult?.title ?: uiState.streamInfo?.name ?: video.title
    val needsCollaboratorResolution = video.needsCollaboratorResolution()
    val resolvedCollaborators by produceState(
        initialValue = video.collaborators,
        key1 = video.id,
        key2 = video.collaborators,
        key3 = needsCollaboratorResolution,
    ) {
        value =
            if (needsCollaboratorResolution) {
                VideoCollaboratorResolver.resolve(video.id)
            } else {
                video.collaborators
            }
    }
    val resolvedChannelName =
        remember(video.channelName, uiState.streamInfo?.uploaderName, resolvedCollaborators) {
            resolvedCollaborators
                .map { it.name }
                .filter { it.isNotBlank() }
                .takeIf { it.size > 1 }
                ?.joinToString(" ${context.getString(R.string.conjunction_and)} ")
                ?: uiState.streamInfo?.uploaderName
                ?: video.channelName
        }
    val streamUploadDate =
        uiState.streamInfo?.let { streamInfo ->
            val rawDate =
                streamInfo.textualUploadDate?.takeIf { it.isNotBlank() }
                    ?: streamInfo.uploadDate?.toString()
            val isArchivedLivestream = streamInfo.streamType == StreamType.POST_LIVE_STREAM
            when {
                rawDate.isNullOrBlank() -> {
                    null
                }

                isArchivedLivestream && !rawDate.startsWith("Streamed", ignoreCase = true) -> {
                    context.getString(R.string.streamed_date_template, rawDate)
                }

                else -> {
                    rawDate
                }
            }
        }
    val dateSettings = rememberDateDisplaySettings()
    val dialogVideo =
        remember(video, uiState.streamInfo, uiState.channelAvatarUrl, resolvedVideoTitle, streamUploadDate, dateSettings) {
            uiState.streamInfo?.let { streamInfo ->
                streamInfo.toVideo(
                    base = video,
                    title = resolvedVideoTitle,
                    uploadDateText =
                        streamUploadDate
                            ?: streamInfo.uploadDateMillis
                                ?.let { dateSettings.format(date = null, context = DateContext.WATCH, timestampFallbackMs = it) }
                                ?.takeIf { it.isNotBlank() }
                            ?: video.uploadDate,
                    channelAvatarUrl = uiState.channelAvatarUrl,
                    likeCount = streamInfo.likeCount,
                    timestamp = video.timestamp,
                    isMusic = video.isMusic,
                )
            } ?: video
        }

    return PlayerVideoMetadata(
        resolvedVideoTitle = resolvedVideoTitle,
        resolvedCollaborators = resolvedCollaborators,
        resolvedChannelName = resolvedChannelName,
        streamUploadDate = streamUploadDate,
        dialogVideo = dialogVideo,
    )
}
