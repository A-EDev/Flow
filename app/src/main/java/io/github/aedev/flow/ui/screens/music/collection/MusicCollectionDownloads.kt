package io.github.aedev.flow.ui.screens.music.collection

import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.dao.DownloadCollectionSummary
import io.github.aedev.flow.data.local.entity.DownloadCollectionKind
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.data.video.BackgroundDownloadQueuer
import io.github.aedev.flow.data.video.downloader.collection.CollectionSpec
import io.github.aedev.flow.data.video.downloader.collection.DownloadedCollections
import io.github.aedev.flow.data.video.downloader.collection.OfflineCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A music page's downloads: the whole album or playlist saved as one item in its own folder, a
 * selection saved as loose songs, the progress of either, and the message once it is queued.
 */
internal class MusicCollectionDownloads(
    private val scope: CoroutineScope,
    private val collectionId: String,
    private val queuer: BackgroundDownloadQueuer,
    collections: DownloadedCollections,
    private val send: suspend (CollectionMessage) -> Unit,
) {
    /** How far a download of this page has got, or null while none runs. */
    val progress: StateFlow<Float?> =
        queuer.batches
            .map { batches ->
                batches[collectionId]?.takeUnless { it.isFinished }?.let { if (it.total == 0) 1f else it.processed.toFloat() / it.total }
            }.stateIn(scope, SharingStarted.WhileSubscribed(SHARING_TIMEOUT_MS), null)

    /** This page as a downloaded collection, or null when it was never downloaded as one. */
    val downloaded: StateFlow<DownloadCollectionSummary?> =
        collections.observe(collectionId).stateIn(scope, SharingStarted.WhileSubscribed(SHARING_TIMEOUT_MS), null)

    init {
        scope.launch {
            queuer.batches.collect { batches ->
                batches.values.filter { it.isFinished && it.collectionId.startsWith(collectionId) }.forEach { batch ->
                    queuer.clearBatch(batch.collectionId)
                    send(
                        if (batch.queued > 0) {
                            CollectionMessage(
                                pluralRes = R.plurals.songs_download_queued,
                                count = batch.queued,
                                args = listOf(batch.queued),
                            )
                        } else {
                            CollectionMessage(stringRes = R.string.songs_download_nothing_new)
                        },
                    )
                }
            }
        }
    }

    /** The whole collection, as one item in its own folder; a second download picks up what is new. */
    fun downloadAll(
        kind: MusicCollectionKind?,
        details: PlaylistDetails,
    ) {
        val collectionKind =
            if (kind ==
                MusicCollectionKind.ALBUM
            ) {
                DownloadCollectionKind.MUSIC_ALBUM
            } else {
                DownloadCollectionKind.MUSIC_PLAYLIST
            }
        queuer.queueCollectionSongs(
            spec =
                CollectionSpec(
                    id = collectionId,
                    kind = collectionKind,
                    title = details.title,
                    author = details.author.takeIf { it.isNotBlank() },
                    thumbnailUrl = details.thumbnailUrl.takeIf { it.isNotBlank() },
                ),
            tracks = details.tracks,
            complete = details.continuation == null,
        )
    }

    /** Picked songs, saved as loose downloads under their own batch so they never borrow the page's progress. */
    fun downloadSongs(songs: List<MusicTrack>) {
        queuer.queueSongs("$collectionId:${System.currentTimeMillis()}", songs)
    }

    private companion object {
        const val SHARING_TIMEOUT_MS = 5_000L
    }
}

/** A downloaded album or playlist as the page shows it with no network. */
internal fun OfflineCollection.toPlaylistDetails(): PlaylistDetails =
    PlaylistDetails(
        id = collection.id,
        title = collection.title,
        thumbnailUrl = collection.thumbnailUrl.ifBlank { tracks.firstOrNull()?.thumbnailUrl.orEmpty() },
        author = collection.author,
        trackCount = tracks.size,
        tracks = tracks,
    )
