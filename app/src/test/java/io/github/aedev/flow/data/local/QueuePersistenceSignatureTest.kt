package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.music.model.MusicTrack
import org.junit.Test

/**
 * The auto-save guard must trip on every field a restore depends on, including same-size content changes.
 */
class QueuePersistenceSignatureTest {
    @Test
    fun `restore-relevant scalar changes are detected`() {
        val base = state()

        assertThat(queueAutoSaveSignature(state(currentPosition = 5_000L))).isNotEqualTo(queueAutoSaveSignature(base))
        assertThat(queueAutoSaveSignature(state(currentTrackId = "other"))).isNotEqualTo(queueAutoSaveSignature(base))
        assertThat(queueAutoSaveSignature(state(currentIndex = 1))).isNotEqualTo(queueAutoSaveSignature(base))
        assertThat(queueAutoSaveSignature(state(shuffleEnabled = true))).isNotEqualTo(queueAutoSaveSignature(base))
        assertThat(queueAutoSaveSignature(state(repeatMode = 2))).isNotEqualTo(queueAutoSaveSignature(base))
    }

    @Test
    fun `same-size queue and automix content changes are detected`() {
        val base = state()
        val queueReplacement = state(queue = listOf(track("changed"), track("queue-1")))
        val queueReordered = state(queue = listOf(track("queue-1"), track("queue-0")))
        val automixReplacement = state(automix = listOf(track("changed-automix")))

        assertThat(queueAutoSaveSignature(queueReplacement)).isNotEqualTo(queueAutoSaveSignature(base))
        assertThat(queueAutoSaveSignature(queueReordered)).isNotEqualTo(queueAutoSaveSignature(base))
        assertThat(queueAutoSaveSignature(automixReplacement)).isNotEqualTo(queueAutoSaveSignature(base))
    }

    @Test
    fun `track metadata is part of the structural snapshot`() {
        val base = state()
        val changedMetadata = track("queue-0").copy(title = "Renamed")

        assertThat(queueAutoSaveSignature(state(queue = listOf(changedMetadata, track("queue-1")))))
            .isNotEqualTo(queueAutoSaveSignature(base))
    }

    @Test
    fun `an unchanged queue produces the same signature`() {
        assertThat(queueAutoSaveSignature(state())).isEqualTo(queueAutoSaveSignature(state()))
        assertThat(queueAutoSaveSignature(state(savedAt = 1L)))
            .isEqualTo(queueAutoSaveSignature(state(savedAt = 2L)))
    }

    private fun state(
        currentPosition: Long = 1_000L,
        currentIndex: Int = 0,
        currentTrackId: String? = "track-0",
        shuffleEnabled: Boolean = false,
        repeatMode: Int = 0,
        savedAt: Long = 0L,
        queue: List<MusicTrack> = listOf(track("queue-0"), track("queue-1")),
        automix: List<MusicTrack> = listOf(track("automix-0")),
    ) = QueuePersistence.QueueState(
        queue = queue,
        currentIndex = currentIndex,
        currentPosition = currentPosition,
        currentTrackId = currentTrackId,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        savedAt = savedAt,
        automix = automix,
    )

    private fun track(id: String) =
        MusicTrack(
            videoId = id,
            title = id,
            artist = "artist",
            thumbnailUrl = "",
            duration = 1,
        )
}
