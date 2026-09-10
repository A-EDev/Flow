package io.github.aedev.flow.data.model

import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * The channel id YouTube encodes as the last segment of the uploader URL. The extractor exposes no
 * id of its own, so this is the only handle the player has on the channel behind a stream.
 */
val StreamInfo.uploaderChannelId: String?
    get() = uploaderUrl?.substringAfterLast("/")

val StreamInfo.bestThumbnailUrl: String?
    get() = thumbnails.maxByOrNull { it.height }?.url

/** The publication instant behind [StreamInfo.getUploadDate], for the date formatters. */
val StreamInfo.uploadDateMillis: Long?
    get() =
        runCatching {
            uploadDate
                ?.offsetDateTime()
                ?.toInstant()
                ?.toEpochMilli()
        }.getOrNull()

/**
 * Rebuilds [base] from the freshly extracted stream, falling back to the cached video for every
 * field the extractor left empty.
 *
 * Every parameter defaults to what the extractor itself reports, so a caller only names the fields
 * it resolves differently: a DeArrow [title], an upload date already formatted for the user's date
 * preference, the separately fetched channel avatar, or the cached [Video]'s own [timestamp] and
 * [isMusic] where those must survive the rebuild.
 */
fun StreamInfo.toVideo(
    base: Video,
    title: String = name ?: base.title,
    uploadDateText: String = this.uploadDate?.toString() ?: base.uploadDate,
    channelAvatarUrl: String? = null,
    likeCount: Long = 0L,
    timestamp: Long = System.currentTimeMillis(),
    isMusic: Boolean = false,
): Video =
    Video(
        id = id ?: base.id,
        title = title,
        channelName = uploaderName ?: base.channelName,
        channelId = uploaderChannelId ?: base.channelId,
        thumbnailUrl = bestThumbnailUrl ?: base.thumbnailUrl,
        duration = duration.toInt(),
        viewCount = viewCount,
        likeCount = likeCount,
        uploadDate = uploadDateText,
        description = description?.content ?: base.description,
        channelThumbnailUrl = channelAvatarUrl ?: base.channelThumbnailUrl,
        timestamp = timestamp,
        isMusic = isMusic,
    )
