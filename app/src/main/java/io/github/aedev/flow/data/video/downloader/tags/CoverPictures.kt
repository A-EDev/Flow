package io.github.aedev.flow.data.video.downloader.tags

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.extractor.metadata.flac.PictureFrame
import androidx.media3.extractor.metadata.id3.ApicFrame

/**
 * The file's front cover when it marks one, otherwise its first picture. MP3 carries pictures as
 * ID3 `APIC` frames; FLAC, Opus and Vorbis as picture blocks, which Media3 reads into [PictureFrame].
 */
@OptIn(UnstableApi::class)
internal fun List<Metadata.Entry>.coverPicture(): ByteArray? {
    val pictures =
        mapNotNull { entry ->
            when (entry) {
                is ApicFrame -> entry.pictureType to entry.pictureData
                is PictureFrame -> entry.pictureType to entry.pictureData
                else -> null
            }
        }.filter { (_, data) -> data.isNotEmpty() }
    return (pictures.firstOrNull { (type, _) -> type == FRONT_COVER } ?: pictures.firstOrNull())?.second
}

/** Every metadata entry of every track Media3 found in a file. */
@OptIn(UnstableApi::class)
internal fun TrackGroupArray.metadataEntries(): List<Metadata.Entry> {
    val groups = this
    return buildList {
        for (groupIndex in 0 until groups.length) {
            val group = groups[groupIndex]
            for (trackIndex in 0 until group.length) {
                val metadata = group.getFormat(trackIndex).metadata ?: continue
                for (entryIndex in 0 until metadata.length()) add(metadata[entryIndex])
            }
        }
    }
}

/** The picture type both ID3 and FLAC give a front cover. */
private const val FRONT_COVER = 3
