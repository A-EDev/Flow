package io.github.aedev.flow.ui.screens.library

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.localmedia.LocalLibrary
import io.github.aedev.flow.data.localmedia.LocalMediaItem
import io.github.aedev.flow.data.localmedia.LocalMediaSettings
import io.github.aedev.flow.ui.components.shared.MediaKind
import org.junit.Test

class LocalMediaStateTest {
    private fun video(
        id: Long,
        folder: String,
    ) = LocalMediaItem(
        id = id,
        isVideo = true,
        contentUri = "content://media/external/video/media/$id",
        title = "Clip $id",
        durationMs = 60_000,
        sizeBytes = 10,
        dateAddedMs = id,
        folderId = folder,
        folderName = folder,
    )

    private val library = LocalLibrary(videos = listOf(video(1, "camera"), video(2, "camera"), video(3, "whatsapp")))

    @Test
    fun `hidden folders leave the list and are counted`() {
        val state =
            buildState(library, LocalMediaSettings(hiddenFolderIds = setOf("whatsapp")), LocalPlayback(), LocalMediaSelection(), nowMs = 10)

        assertThat(state.items.map { it.id }).containsExactly(2L, 1L).inOrder()
        assertThat(state.hiddenCount).isEqualTo(1)
        assertThat(state.folders.map { it.id }).containsExactly("camera")
    }

    @Test
    fun `an open folder shows only its files`() {
        val state =
            buildState(
                library,
                LocalMediaSettings(),
                LocalPlayback(),
                LocalMediaSelection(view = LocalView.FOLDERS, openFolderId = "whatsapp"),
                nowMs = 10,
            )

        assertThat(state.openFolder?.id).isEqualTo("whatsapp")
        assertThat(state.items.map { it.id }).containsExactly(3L)
    }

    @Test
    fun `the music tab reads the music list`() {
        val state = buildState(library, LocalMediaSettings(), LocalPlayback(), LocalMediaSelection(kind = MediaKind.Music), nowMs = 10)

        assertThat(state.items).isEmpty()
        assertThat(state.totalCount).isEqualTo(0)
    }
}
