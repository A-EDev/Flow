package io.github.aedev.flow.ui.screens.library

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.localmedia.HiddenReason
import io.github.aedev.flow.data.localmedia.LocalMediaItem
import io.github.aedev.flow.data.localmedia.LocalMediaSettings
import io.github.aedev.flow.data.localmedia.hiddenReason
import org.junit.Test

class LocalMediaFiltersTest {
    private val now = 100L * 24 * 60 * 60 * 1000

    private fun video(
        id: Long,
        title: String = "Clip $id",
        minutes: Long = 5,
        width: Int = 1920,
        height: Int = 1080,
        addedDaysAgo: Long = 30,
        folder: String = "Camera",
        size: Long = 100,
    ) = LocalMediaItem(
        id = id,
        isVideo = true,
        contentUri = "content://media/external/video/media/$id",
        title = title,
        durationMs = minutes * 60_000,
        sizeBytes = size,
        dateAddedMs = now - addedDaysAgo * 24 * 60 * 60 * 1000,
        width = width,
        height = height,
        folderId = folder.lowercase(),
        folderName = folder,
        path = "DCIM/$folder/",
    )

    private fun song(
        id: Long,
        seconds: Long,
        path: String = "Music/",
    ) = LocalMediaItem(
        id = id,
        isVideo = false,
        contentUri = "content://media/external/audio/media/$id",
        title = "Song $id",
        durationMs = seconds * 1000,
        sizeBytes = 10,
        dateAddedMs = now,
        path = path,
    )

    @Test
    fun `length bands follow YouTube search`() {
        val items = listOf(video(1, minutes = 2), video(2, minutes = 10), video(3, minutes = 45))
        assertThat(
            items.applyLocalFilters(LocalFilters(length = LengthFilter.SHORT), LocalPlayback(), now).map { it.id },
        ).containsExactly(1L)
        assertThat(
            items.applyLocalFilters(LocalFilters(length = LengthFilter.MEDIUM), LocalPlayback(), now).map { it.id },
        ).containsExactly(2L)
        assertThat(
            items.applyLocalFilters(LocalFilters(length = LengthFilter.LONG), LocalPlayback(), now).map { it.id },
        ).containsExactly(3L)
    }

    @Test
    fun `quality uses the short side, so portrait Full HD counts as Full HD`() {
        val items =
            listOf(video(1, width = 1080, height = 1920), video(2, width = 1280, height = 720), video(3, width = 3840, height = 2160))
        assertThat(
            items.applyLocalFilters(LocalFilters(quality = QualityFilter.FULL_HD), LocalPlayback(), now).map { it.id },
        ).containsExactly(1L)
        assertThat(
            items.applyLocalFilters(LocalFilters(quality = QualityFilter.UHD), LocalPlayback(), now).map { it.id },
        ).containsExactly(3L)
        assertThat(items.applyLocalFilters(LocalFilters(portraitOnly = true), LocalPlayback(), now).map { it.id }).containsExactly(1L)
    }

    @Test
    fun `search reads title and folder`() {
        val items = listOf(video(1, title = "Lisbon tram"), video(2, folder = "Screen recordings"))
        assertThat(items.applyLocalFilters(LocalFilters(query = "tram"), LocalPlayback(), now).map { it.id }).containsExactly(1L)
        assertThat(items.applyLocalFilters(LocalFilters(query = "screen"), LocalPlayback(), now).map { it.id }).containsExactly(2L)
    }

    @Test
    fun `watch state separates new, in progress and watched`() {
        val playback =
            LocalPlayback(
                fraction = mapOf("local_2" to 0.4f, "local_3" to 0.95f),
                lastPlayedMs =
                    mapOf(
                        "local_2" to 5L,
                        "local_3" to 6L,
                    ),
            )
        assertThat(video(1, addedDaysAgo = 2).watchState(playback, now)).isEqualTo(WatchState.NEW)
        assertThat(video(2).watchState(playback, now)).isEqualTo(WatchState.IN_PROGRESS)
        assertThat(video(3).watchState(playback, now)).isEqualTo(WatchState.WATCHED)
        assertThat(video(4, addedDaysAgo = 30).watchState(playback, now)).isEqualTo(WatchState.UNWATCHED)
        assertThat(listOf(video(2), video(3)).continueWatching(playback, now).map { it.id }).containsExactly(2L)
    }

    @Test
    fun `folders group files and put the newest folder first`() {
        val folders =
            listOf(
                video(1, folder = "Movies", addedDaysAgo = 9),
                video(2, folder = "Camera", addedDaysAgo = 1),
                video(3, folder = "Movies"),
            ).folders()
        assertThat(folders.map { it.name }).containsExactly("Camera", "Movies").inOrder()
        assertThat(folders.last().items).hasSize(2)
    }

    @Test
    fun `short audio and app folders are hidden by the default settings only`() {
        val settings = LocalMediaSettings()
        assertThat(song(1, seconds = 20).hiddenReason(settings)).isEqualTo(HiddenReason.TOO_SHORT)
        assertThat(song(2, seconds = 200, path = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/").hiddenReason(settings))
            .isEqualTo(HiddenReason.APP_AUDIO)
        assertThat(song(3, seconds = 200).hiddenReason(settings)).isNull()
        val showAll = LocalMediaSettings(hideAppAudio = false, minAudioSeconds = 0)
        assertThat(song(1, seconds = 20).hiddenReason(showAll)).isNull()
        assertThat(video(4).hiddenReason(LocalMediaSettings(hiddenFolderIds = setOf("camera")))).isEqualTo(HiddenReason.FOLDER)
    }
}
