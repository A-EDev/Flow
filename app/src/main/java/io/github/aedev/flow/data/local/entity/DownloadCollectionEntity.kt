package io.github.aedev.flow.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A playlist or album downloaded as one item, saved into its own folder. */
@Entity(
    tableName = "download_collections",
    indices = [Index(value = ["folderLocation"], unique = true)],
)
data class DownloadCollectionEntity(
    /** The id the collection pages use: a `VL`/`PL` playlist, an `MPREb_` album, or a local playlist id. */
    @PrimaryKey
    val id: String,
    val kind: DownloadCollectionKind,
    val title: String,
    val author: String = "",
    val authorId: String? = null,
    val thumbnailUrl: String = "",
    val coverPath: String? = null,
    /** The folder name actually used, after sanitising and collision suffixes. */
    val folderName: String,
    /** The folder once created: an absolute path, or a document URI inside a picked folder. */
    val folderLocation: String? = null,
    val remoteItemCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null,
    /** Songs already downloaded elsewhere are copied into this folder too, not only referenced. */
    val copyIntoFolder: Boolean = false,
)

enum class DownloadCollectionKind {
    VIDEO_PLAYLIST,
    MUSIC_ALBUM,
    MUSIC_PLAYLIST,
    LOCAL_PLAYLIST,
    ;

    val isMusic: Boolean get() = this == MUSIC_ALBUM || this == MUSIC_PLAYLIST
}

/**
 * One video's membership of a downloaded collection. There is deliberately no foreign key to
 * `downloads`: membership exists before the download row does, outlives a deleted file, and one
 * video can belong to several collections.
 */
@Entity(
    tableName = "download_collection_items",
    primaryKeys = ["collectionId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = DownloadCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["videoId"]),
        Index(value = ["collectionId", "position"]),
    ],
)
data class DownloadCollectionItemEntity(
    val collectionId: String,
    val videoId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
    val state: CollectionItemState = CollectionItemState.WANTED,
    /** This collection's download created the file, so deleting the collection may delete it. */
    val ownsDownload: Boolean = false,
)

enum class CollectionItemState {
    WANTED,

    /** The user removed it from this collection; a sync never brings it back. */
    EXCLUDED,

    /** No longer in the playlist upstream; the file is kept until the user cleans up. */
    REMOVED_UPSTREAM,
}
