/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.music.model

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Test

/**
 * MusicTrack moved from io.github.aedev.flow.ui.screens.music to this package. Queues saved by
 * earlier installs live in DataStore as Gson JSON written by QueuePersistence, so the payload
 * emitted before the move must still deserialize here or every upgrading user loses their queue.
 */
class MusicTrackGsonCompatibilityTest {
    private val gson = Gson()
    private val listType = object : TypeToken<List<MusicTrack>>() {}.type

    private val legacyQueueJson =
        """
        [
          {
            "videoId": "dQw4w9WgXcQ",
            "title": "Never Gonna Give You Up",
            "artist": "Rick Astley",
            "thumbnailUrl": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg",
            "duration": 213,
            "views": 1500000000,
            "sourceUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "album": "Whenever You Need Somebody",
            "channelId": "UCuAXFkgsw1L7xaCfnd5JJOw",
            "isExplicit": false,
            "isVideoSong": true,
            "albumId": "MPREb_album",
            "artists": [{ "name": "Rick Astley", "id": "UCuAXFkgsw1L7xaCfnd5JJOw" }],
            "itemType": "SONG"
          }
        ]
        """.trimIndent()

    private val preArtistsQueueJson =
        """
        [
          {
            "videoId": "abc123",
            "title": "Older Save",
            "artist": "Someone",
            "thumbnailUrl": "",
            "duration": 180,
            "views": 0,
            "sourceUrl": "",
            "album": "",
            "channelId": "UCsomeone",
            "isExplicit": false,
            "isVideoSong": false,
            "itemType": "SONG"
          }
        ]
        """.trimIndent()

    @Test
    fun `a queue saved before the package move still deserializes`() {
        val queue: List<MusicTrack> = gson.fromJson(legacyQueueJson, listType)

        assertThat(queue).hasSize(1)
        val track = queue.single()
        assertThat(track.videoId).isEqualTo("dQw4w9WgXcQ")
        assertThat(track.title).isEqualTo("Never Gonna Give You Up")
        assertThat(track.artist).isEqualTo("Rick Astley")
        assertThat(track.duration).isEqualTo(213)
        assertThat(track.views).isEqualTo(1_500_000_000L)
        assertThat(track.album).isEqualTo("Whenever You Need Somebody")
        assertThat(track.channelId).isEqualTo("UCuAXFkgsw1L7xaCfnd5JJOw")
        assertThat(track.isExplicit).isFalse()
        assertThat(track.isVideoSong).isTrue()
        assertThat(track.albumId).isEqualTo("MPREb_album")
        assertThat(track.itemType).isEqualTo(MusicItemType.SONG)
        assertThat(track.artists).containsExactly(MusicArtist("Rick Astley", "UCuAXFkgsw1L7xaCfnd5JJOw"))
    }

    @Test
    fun `the restore path survives a save written before the artists field existed`() {
        val queue: List<MusicTrack> = gson.fromJson(preArtistsQueueJson, listType)
        val restored = queue.map { it.withTypedArtists() }

        assertThat(restored.single().videoId).isEqualTo("abc123")
        assertThat(restored.single().artists).isEmpty()
    }

    @Test
    fun `a round trip through Gson keys on field names not on the class name`() {
        val original: List<MusicTrack> = gson.fromJson(legacyQueueJson, listType)
        val reparsed: List<MusicTrack> = gson.fromJson(gson.toJson(original), listType)

        assertThat(reparsed).isEqualTo(original)
        assertThat(gson.toJson(original)).doesNotContain("io.github.aedev.flow")
    }

    @Test
    fun `every field Flow writes is round tripped so a fresh save reloads intact`() {
        val saved =
            MusicTrack(
                videoId = "v9",
                title = "Title",
                artist = "Artist",
                thumbnailUrl = "https://example.invalid/t.jpg",
                duration = 321,
                views = 42,
                sourceUrl = "https://example.invalid/watch",
                album = "Album",
                channelId = "UCchannel",
                isExplicit = true,
                isVideoSong = true,
                albumId = "MPREb_x",
                artists = listOf(MusicArtist("Artist", "UCchannel")),
                itemType = MusicItemType.ALBUM,
            )

        val reparsed: List<MusicTrack> = gson.fromJson(gson.toJson(listOf(saved)), listType)

        assertThat(reparsed.single()).isEqualTo(saved)
    }
}
