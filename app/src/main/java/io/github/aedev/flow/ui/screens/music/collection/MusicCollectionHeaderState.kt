package io.github.aedev.flow.ui.screens.music.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack

private const val SECONDS_PER_HOUR = 3_600
private const val SECONDS_PER_MINUTE = 60
private val SavableKinds = setOf(MusicCollectionKind.ALBUM, MusicCollectionKind.PLAYLIST, MusicCollectionKind.SAVED)

/**
 * What the header shows for this collection. An album's year arrives in its description, so it
 * joins the metadata line instead; the length is YouTube's when it gives one, else the songs' sum.
 */
@Composable
internal fun rememberCollectionHeaderState(
    state: MusicCollectionUiState,
    tracks: List<MusicTrack>,
    downloadProgress: Float?,
): CollectionHeaderState {
    val details = requireNotNull(state.details)
    val isAlbum = state.kind == MusicCollectionKind.ALBUM
    val totalSeconds = remember(tracks) { tracks.sumOf { it.duration.coerceAtLeast(0) } }
    val length = details.durationText?.takeIf(String::isNotBlank) ?: totalLengthLabel(totalSeconds)
    val separator = stringResource(R.string.metadata_separator)
    val metadata =
        listOfNotNull(
            details.description?.takeIf { isAlbum && it.isNotBlank() },
            pluralStringResource(R.plurals.songs_count_template, tracks.size, tracks.size),
            length.takeUnless { state.isLoadingMore },
        ).joinToString(" $separator ")
    return CollectionHeaderState(
        kindLabel = kindLabel(state.kind),
        title = details.title,
        author = details.author,
        authorId = details.authorId,
        metadata = metadata,
        description =
            details.description
                .orEmpty()
                .takeUnless { isAlbum }
                .orEmpty(),
        artworkUrl = details.thumbnailUrl.ifBlank { tracks.firstOrNull()?.thumbnailUrl.orEmpty() },
        isSaved = state.isSaved,
        canSave = state.kind in SavableKinds,
        canShare = state.kind != null,
        downloadProgress = downloadProgress,
    )
}

@Composable
private fun kindLabel(kind: MusicCollectionKind?): String =
    stringResource(
        when (kind) {
            MusicCollectionKind.ALBUM -> R.string.music_kind_album
            MusicCollectionKind.OWN -> R.string.playlist_type_yours
            MusicCollectionKind.SAVED -> R.string.music_kind_saved
            MusicCollectionKind.DAILY_MIX -> R.string.section_daily_mix_label
            MusicCollectionKind.LIKED -> R.string.playlist_type_builtin
            MusicCollectionKind.PLAYLIST, null -> R.string.playlist
        },
    )

@Composable
private fun totalLengthLabel(totalSeconds: Int): String? {
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    return when {
        hours > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes)
        minutes > 0 -> pluralStringResource(R.plurals.duration_minutes, minutes, minutes)
        else -> null
    }
}
