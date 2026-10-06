package io.github.aedev.flow.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.aedev.flow.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun get(id: String): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT COUNT(*) FROM notes")
    fun observeCount(): Flow<Int>

    /** Fills in what a note is about without touching its text or edit time. */
    @Query(
        "UPDATE notes SET title = :title, channelName = :channelName, channelId = :channelId, " +
            "thumbnailUrl = :thumbnailUrl, durationSeconds = :durationSeconds, channelAvatarUrl = :channelAvatarUrl, " +
            "channelHandle = :channelHandle, subscriberCountText = :subscriberCountText WHERE id = :id",
    )
    suspend fun updateSubject(
        id: String,
        title: String,
        channelName: String,
        channelId: String,
        thumbnailUrl: String,
        durationSeconds: Int,
        channelAvatarUrl: String,
        channelHandle: String,
        subscriberCountText: String,
    )

    @Query("UPDATE notes SET position = :position WHERE id = :id")
    suspend fun updatePosition(
        id: String,
        position: Int,
    )

    /** The viewer's hand-made order, in one write. */
    @Transaction
    suspend fun updatePositions(ids: List<String>) {
        ids.forEachIndexed { index, id -> updatePosition(id, index) }
    }

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
