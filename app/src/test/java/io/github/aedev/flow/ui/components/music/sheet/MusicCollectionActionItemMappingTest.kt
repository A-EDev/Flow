/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.sheet

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.music.model.MusicPlaylist
import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.Artist
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import org.junit.Test

/**
 * The home feed, the artist page, search and the artist catalogue all open the same collection
 * sheet. One mapping feeds them, so the sheet cannot disagree with itself about what an album is.
 */
class MusicCollectionActionItemMappingTest {
    @Test
    fun aPlaylistModelKeepsItsIdentityAndKind() {
        val playlist = MusicPlaylist(id = "PL1", title = "Mix", thumbnailUrl = "thumb", author = "Flow", trackCount = 12)

        val album = playlist.toCollectionActionItem(isAlbum = true)
        val list = playlist.toCollectionActionItem(isAlbum = false)

        assertThat(album.id).isEqualTo("PL1")
        assertThat(album.title).isEqualTo("Mix")
        assertThat(album.subtitle).isEqualTo("Flow")
        assertThat(album.thumbnailUrl).isEqualTo("thumb")
        assertThat(album.isAlbum).isTrue()
        assertThat(album.trackCount).isEqualTo(12)
        assertThat(album.description).isEqualTo("Flow")
        assertThat(list.isAlbum).isFalse()
        assertThat(album.shareUrl).contains("browse/PL1")
        assertThat(list.shareUrl).contains("list=PL1")
    }

    @Test
    fun aSearchAlbumBecomesAnAlbumItem() {
        val item =
            AlbumItem(
                browseId = "MPREb1",
                playlistId = "OLAK1",
                title = "Record",
                artists = listOf(Artist(name = "Someone", id = "UC1")),
                year = 2024,
                thumbnail = "art",
                explicit = false,
            )

        val action = item.toCollectionActionItem()

        assertThat(action).isNotNull()
        assertThat(action!!.isAlbum).isTrue()
        assertThat(action.subtitle).isEqualTo("Someone")
        assertThat(action.description).isEqualTo("2024")
    }

    @Test
    fun aSearchPlaylistBecomesAPlaylistItem() {
        val item =
            PlaylistItem(
                id = "VL1",
                title = "Community mix",
                author = Artist(name = "Curator", id = null),
                songCountText = null,
                thumbnail = "art",
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
            )

        val action = item.toCollectionActionItem()

        assertThat(action).isNotNull()
        assertThat(action!!.isAlbum).isFalse()
        assertThat(action.subtitle).isEqualTo("Curator")
    }

    @Test
    fun anArtistHasNoCollectionSheet() {
        val item =
            ArtistItem(
                id = "UC1",
                title = "Someone",
                thumbnail = "art",
                shuffleEndpoint = null,
                radioEndpoint = null,
            )

        assertThat(item.toCollectionActionItem()).isNull()
    }
}
