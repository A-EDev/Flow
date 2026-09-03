/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.music.model

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.music.primaryArtistKey
import org.junit.Test

/**
 * Issue #996: release builds that lose MusicTrack.artists' generic signature make Gson
 * deserialize the artist entries as LinkedTreeMaps inside a List<MusicArtist>-typed field.
 * The first element access then throws ClassCastException and kills the music feed.
 * [withTypedArtists] must repair such a list without touching healthy ones.
 */
class MusicTrackTypedArtistsTest {
    private fun track(artists: List<MusicArtist> = emptyList()) =
        MusicTrack(
            videoId = "v1",
            title = "Song",
            artist = "Fallback Name",
            thumbnailUrl = "",
            duration = 200,
            channelId = "UCfallback",
            artists = artists,
        )

    @Suppress("UNCHECKED_CAST")
    private fun poisonedTrack(): MusicTrack {
        // Exactly what Gson produces when the generic signature is gone: untyped maps
        // smuggled into the typed list through erasure.
        val poison = listOf(mapOf("name" to "Gremlin", "id" to "UCgremlin")) as List<MusicArtist>
        return track(artists = poison)
    }

    @Test
    fun `poisoned artists crash on element access without the repair`() {
        val poisoned = poisonedTrack()
        var thrown = false
        try {
            poisoned.primaryArtistKey()
        } catch (e: ClassCastException) {
            thrown = true
        }
        assertThat(thrown).isTrue()
    }

    @Test
    fun `withTypedArtists drops poisoned entries and restores the string fallbacks`() {
        val repaired = poisonedTrack().withTypedArtists()
        assertThat(repaired.artists).isEmpty()
        assertThat(repaired.primaryArtistKey()).isEqualTo("UCfallback")
    }

    @Test
    fun `withTypedArtists keeps a healthy list untouched without copying`() {
        val healthy = track(artists = listOf(MusicArtist(name = "Real", id = "UCreal")))
        assertThat(healthy.withTypedArtists()).isSameInstanceAs(healthy)
        assertThat(track().withTypedArtists().artists).isEmpty()
    }

    @Test
    fun `withTypedArtists keeps typed entries when only some are poisoned`() {
        @Suppress("UNCHECKED_CAST")
        val mixed = (listOf(MusicArtist(name = "Real", id = "UCreal"), mapOf("name" to "Fake")) as List<MusicArtist>)
        val repaired = track(artists = mixed).withTypedArtists()
        assertThat(repaired.artists).containsExactly(MusicArtist(name = "Real", id = "UCreal"))
    }
}
