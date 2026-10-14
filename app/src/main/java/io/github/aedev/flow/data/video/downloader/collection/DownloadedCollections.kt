package io.github.aedev.flow.data.video.downloader.collection

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.dao.DownloadCollectionDao
import io.github.aedev.flow.data.local.dao.DownloadCollectionSummary
import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.local.entity.CollectionItemState
import io.github.aedev.flow.data.local.entity.DownloadCollectionEntity
import io.github.aedev.flow.data.local.entity.DownloadCollectionKind
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.DownloadManager
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.data.video.storage.DownloadFiles
import io.github.aedev.flow.data.video.storage.DownloadNaming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playlists and albums downloaded as one item: their membership, the folder they are saved in,
 * and what of them is on the device, for the Downloads shelf and for a page opened offline.
 */
@Singleton
class DownloadedCollections
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val collectionDao: DownloadCollectionDao,
        private val downloadDao: DownloadDao,
        private val downloads: VideoDownloadManager,
        private val musicDownloads: DownloadManager,
    ) {
        val summaries: Flow<List<DownloadCollectionSummary>> = collectionDao.observeSummaries()

        fun observe(id: String): Flow<DownloadCollectionSummary?> = summaries.map { list -> list.firstOrNull { it.collection.id == id } }

        /**
         * Records [spec] with [ids] as its current list and returns the videos to queue. The folder
         * name is fixed the first time, so a renamed playlist keeps writing into the same folder.
         */
        suspend fun prepare(
            spec: CollectionSpec,
            ids: List<String>,
            complete: Boolean,
        ): List<String> {
            val existing = collectionDao.getCollection(spec.id)
            val folderName =
                existing?.folderName ?: run {
                    val taken = collectionDao.folderNames().toSet()
                    val name = DownloadNaming.folderName(spec.title, spec.author, spec.kind == DownloadCollectionKind.MUSIC_ALBUM, spec.id)
                    DownloadNaming.unique(name, hasExtension = false) { it in taken }
                }
            val collection =
                (existing ?: DownloadCollectionEntity(id = spec.id, kind = spec.kind, title = spec.title, folderName = folderName)).copy(
                    title = spec.title.ifBlank { existing?.title.orEmpty() },
                    author = spec.author ?: existing?.author.orEmpty(),
                    authorId = spec.authorId ?: existing?.authorId,
                    thumbnailUrl = spec.thumbnailUrl ?: existing?.thumbnailUrl.orEmpty(),
                    remoteItemCount = ids.size,
                    lastSyncedAt = System.currentTimeMillis(),
                )
            val downloaded = ids.filterTo(HashSet()) { isDownloadedOrQueued(it) }
            val plan =
                CollectionPlan.of(
                    collectionId = spec.id,
                    ids = ids,
                    existing = collectionDao.itemsOf(spec.id),
                    isDownloaded = { it in downloaded },
                    complete = complete,
                    now = System.currentTimeMillis(),
                )
            collectionDao.upsertWithItems(collection, plan.members)
            return plan.toQueue
        }

        /**
         * What of the collection is on the device, in list order, for a page opened with no
         * network; null when [id] was never downloaded as a collection.
         */
        suspend fun offline(id: String): OfflineCollection? {
            val collection = collectionDao.getCollection(id) ?: return null
            val wanted = wantedIds(id)
            return if (collection.kind.isMusic) {
                val byId = musicDownloads.downloadedTracks.first().associateBy { it.track.videoId }
                OfflineCollection(collection, tracks = wanted.mapNotNull { byId[it]?.track }, videos = emptyList())
            } else {
                OfflineCollection(collection, tracks = emptyList(), videos = wanted.mapNotNull { downloads.findLocalCopy(it)?.video })
            }
        }

        suspend fun collection(id: String): DownloadCollectionEntity? = collectionDao.getCollection(id)

        /**
         * Removes the collection. With [deleteFiles], a download goes only when this collection
         * created it and no other collection still holds it; the folder goes once it is empty.
         */
        suspend fun remove(
            id: String,
            deleteFiles: Boolean,
        ) {
            val collection = collectionDao.getCollection(id) ?: return
            val members = collectionDao.itemsOf(id)
            collectionDao.deleteCollection(id)
            if (!deleteFiles) return
            members
                .filter { it.ownsDownload && collectionDao.membershipsOf(it.videoId).isEmpty() }
                .forEach { downloads.deleteDownload(it.videoId) }
            collection.folderLocation?.let { DownloadFiles.deleteIfEmpty(context, it) }
        }

        private suspend fun wantedIds(id: String): List<String> =
            collectionDao.itemsOf(id).filter { it.state != CollectionItemState.EXCLUDED }.map { it.videoId }

        private suspend fun isDownloadedOrQueued(videoId: String): Boolean =
            when (downloadDao.getDownloadWithItems(videoId)?.overallStatus) {
                DownloadItemStatus.COMPLETED, DownloadItemStatus.PENDING, DownloadItemStatus.DOWNLOADING, DownloadItemStatus.PAUSED -> true
                else -> false
            }
    }

/** A downloaded collection as it can be shown offline: songs for music, videos otherwise. */
data class OfflineCollection(
    val collection: DownloadCollectionEntity,
    val tracks: List<MusicTrack>,
    val videos: List<Video>,
)
