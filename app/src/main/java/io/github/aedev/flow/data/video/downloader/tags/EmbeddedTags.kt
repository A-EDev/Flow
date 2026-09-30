package io.github.aedev.flow.data.video.downloader.tags

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.extractor.metadata.id3.ApicFrame
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment

/**
 * What a media file says about itself. [flow] is set only when the file carries Flow's own ids;
 * the display fields and [cover] are filled from whatever standard tags the file has.
 */
@OptIn(UnstableApi::class)
class EmbeddedTags(
    val flow: DownloadTags?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val cover: ByteArray?,
    /** The description Flow wrote, or a Vorbis `DESCRIPTION`; MP4 `desc` needs [Mp4TextAtoms]. */
    val description: String? = null,
    /** `©cmt`, ID3 `COMM` or a Vorbis `COMMENT`; yt-dlp puts the watch URL here by default. */
    val comment: String? = null,
    /** Lyrics from `©lyr`, ID3 `USLT` or a Vorbis `LYRICS` comment, synced or not. */
    val lyrics: String? = null,
) {
    fun withFallback(
        title: String?,
        artist: String?,
        album: String?,
        cover: ByteArray?,
    ): EmbeddedTags =
        EmbeddedTags(
            flow = flow,
            title = this.title ?: title,
            artist = this.artist ?: artist,
            album = this.album ?: album,
            cover = this.cover ?: cover,
            description = description,
            comment = comment,
            lyrics = lyrics,
        )

    companion object {
        /** Folds Media3 metadata entries (every track's, in any order) into one result. */
        fun fromEntries(entries: List<Metadata.Entry>): EmbeddedTags {
            val fields = linkedMapOf<String, String>()
            val frames = linkedMapOf<String, String>()
            var comment: String? = null
            var cover: ByteArray? = null
            val vorbis = linkedMapOf<String, String>()
            var id3Lyrics: String? = null
            entries.forEach { entry ->
                when (entry) {
                    is MdtaMetadataEntry -> {
                        if (entry.typeIndicator == MdtaMetadataEntry.TYPE_INDICATOR_STRING) {
                            FlowTagFields.fieldFromMdtaKey(entry.key)?.let { fields.putIfAbsent(it, String(entry.value, Charsets.UTF_8)) }
                        }
                    }

                    is InternalFrame -> {
                        if (entry.domain == FlowTagFields.NAMESPACE) fields.putIfAbsent(entry.description, entry.text)
                    }

                    is TextInformationFrame -> {
                        entry.values.firstOrNull()?.let { frames.putIfAbsent(entry.id, it) }
                    }

                    is CommentFrame -> {
                        if (comment == null) comment = entry.text
                    }

                    is ApicFrame -> {
                        if (cover == null) cover = entry.pictureData
                    }

                    is VorbisComment -> {
                        vorbis.putIfAbsent(entry.key.uppercase(), entry.value)
                    }

                    is BinaryFrame -> {
                        if (entry.id == Id3Lyrics.FRAME_ID && id3Lyrics == null) id3Lyrics = Id3Lyrics.decode(entry.data)
                    }
                }
            }
            val title = frames[FRAME_TITLE] ?: fields[FlowTagFields.TITLE]
            if (title != null) fields.putIfAbsent(FlowTagFields.TITLE, title)
            val flow = FlowTagFields.decode(fields)?.fillFromFrames(frames, comment)
            return EmbeddedTags(
                flow = flow,
                title = title,
                artist = frames[FRAME_ARTIST] ?: flow?.displayArtist(),
                album = frames[FRAME_ALBUM] ?: flow?.album,
                cover = cover,
                description = (flow?.description ?: vorbis[VORBIS_DESCRIPTION])?.takeIf(String::isNotBlank),
                comment = (comment ?: vorbis[VORBIS_COMMENT])?.takeIf(String::isNotBlank),
                lyrics =
                    listOf(flow?.lyrics, frames[FRAME_LYRICS], id3Lyrics, vorbis[VORBIS_LYRICS], vorbis[VORBIS_UNSYNCED_LYRICS])
                        .firstOrNull { !it.isNullOrBlank() },
            )
        }

        private fun DownloadTags.fillFromFrames(
            frames: Map<String, String>,
            comment: String?,
        ): DownloadTags {
            val track = frames[FRAME_TRACK]?.split('/')?.map { part -> part.trim().toIntOrNull()?.takeIf { it > 0 } }
            return copy(
                artists = artists.ifEmpty { listOfNotNull(frames[FRAME_ARTIST]) },
                album = album ?: frames[FRAME_ALBUM],
                albumArtist = albumArtist ?: frames[FRAME_ALBUM_ARTIST],
                trackNumber = trackNumber ?: track?.getOrNull(0),
                trackTotal = trackTotal ?: track?.getOrNull(1),
                releaseDate = releaseDate ?: frames[FRAME_DATE],
                sourceUrl = sourceUrl ?: comment,
                lyrics = lyrics ?: frames[FRAME_LYRICS],
            )
        }

        private const val FRAME_TITLE = "TIT2"
        private const val FRAME_ARTIST = "TPE1"
        private const val FRAME_ALBUM = "TALB"
        private const val FRAME_ALBUM_ARTIST = "TPE2"
        private const val FRAME_TRACK = "TRCK"
        private const val FRAME_DATE = "TDRC"
        private const val FRAME_LYRICS = "USLT"
        private const val VORBIS_DESCRIPTION = "DESCRIPTION"
        private const val VORBIS_COMMENT = "COMMENT"
        private const val VORBIS_LYRICS = "LYRICS"
        private const val VORBIS_UNSYNCED_LYRICS = "UNSYNCEDLYRICS"
    }
}
