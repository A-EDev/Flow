package io.github.aedev.flow.data.video.downloader.collection

import io.github.aedev.flow.data.local.entity.CollectionItemState
import io.github.aedev.flow.data.local.entity.DownloadCollectionItemEntity
import io.github.aedev.flow.data.local.entity.DownloadCollectionKind

/** A playlist or album as the page that downloads it describes it. */
data class CollectionSpec(
    val id: String,
    val kind: DownloadCollectionKind,
    val title: String,
    val author: String? = null,
    val authorId: String? = null,
    val thumbnailUrl: String? = null,
)

/** What one download of a collection's current list changes in its membership. Pure, so it is unit tested. */
internal data class CollectionPlan(
    /** Members to write, with their position in the list as it is now. */
    val members: List<DownloadCollectionItemEntity>,
    /** Videos to queue: new to the collection, or wanted and not yet downloaded. */
    val toQueue: List<String>,
) {
    companion object {
        /**
         * [ids] is the list as loaded now. A video the user took out stays out; one no longer in a
         * list that was [complete] is marked as removed upstream, never deleted, and a truncated
         * load marks nothing, since what it did not reach may still be there.
         */
        fun of(
            collectionId: String,
            ids: List<String>,
            existing: List<DownloadCollectionItemEntity>,
            isDownloaded: (String) -> Boolean,
            complete: Boolean,
            now: Long,
        ): CollectionPlan {
            val known = existing.associateBy { it.videoId }
            val listed = ids.distinct()
            val listedSet = listed.toSet()
            val members =
                listed.mapIndexed { position, videoId ->
                    val previous = known[videoId]
                    when {
                        previous == null -> {
                            DownloadCollectionItemEntity(
                                collectionId = collectionId,
                                videoId = videoId,
                                position = position,
                                addedAt = now,
                                ownsDownload = !isDownloaded(videoId),
                            )
                        }

                        previous.state == CollectionItemState.REMOVED_UPSTREAM -> {
                            previous.copy(position = position, state = CollectionItemState.WANTED)
                        }

                        else -> {
                            previous.copy(position = position)
                        }
                    }
                }
            val gone =
                if (complete) {
                    existing
                        .filter { it.videoId !in listedSet && it.state == CollectionItemState.WANTED }
                        .map { it.copy(state = CollectionItemState.REMOVED_UPSTREAM) }
                } else {
                    emptyList()
                }
            val toQueue =
                members
                    .filter { it.state == CollectionItemState.WANTED && !isDownloaded(it.videoId) }
                    .map { it.videoId }
            return CollectionPlan(members + gone, toQueue)
        }
    }
}
