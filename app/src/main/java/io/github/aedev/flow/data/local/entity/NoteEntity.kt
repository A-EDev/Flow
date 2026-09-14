package io.github.aedev.flow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A note the user wrote about a channel or a video — why they unsubscribed, points worth keeping from
 * a video. [id] is the composite of kind and target so one table serves both surfaces and a lookup is
 * a primary-key hit.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val targetId: String,
    val kind: String,
    val text: String,
    val updatedAt: Long,
)
