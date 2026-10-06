package io.github.aedev.flow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A note the user wrote about a channel or a video — why they unsubscribed, points worth keeping from
 * a video. [id] is the composite of kind and target so one table serves both surfaces and a lookup is
 * a primary-key hit.
 *
 * [title] onwards describe what the note is about, saved with it so the Notes page lists notes
 * without a network call. Null on notes written before they were kept, until filled in once.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val targetId: String,
    val kind: String,
    val text: String,
    val updatedAt: Long,
    val title: String? = null,
    val channelName: String? = null,
    val channelId: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int? = null,
    val channelAvatarUrl: String? = null,
    val channelHandle: String? = null,
    val subscriberCountText: String? = null,
    /** Where the viewer placed the note by hand; null until they reorder. */
    val position: Int? = null,
)
