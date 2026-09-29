/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.sync.merge

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.sync.canonical.CanonicalMusicAffinity
import io.github.aedev.flow.sync.canonical.CanonicalMusicBrain
import io.github.aedev.flow.sync.canonical.CanonicalMusicTrackMeta
import io.github.aedev.flow.sync.canonical.GCounter
import io.github.aedev.flow.sync.canonical.Lww
import io.github.aedev.flow.sync.canonical.OrSet
import org.junit.Test

class MusicBrainMergerTest {
    private fun hlc(
        ms: Long,
        node: String = "aaaaaaaa",
    ) = "$ms:0:$node"

    @Test
    fun `merge is commutative and idempotent`() {
        val a =
            CanonicalMusicBrain(
                deviceId = "phone",
                hlc = hlc(100),
                totalPlays = GCounter(mapOf("phone" to 10L)),
                genreAffinity = mapOf("rap" to 0.4),
                seenArtists = OrSet().add("UCa", hlc(50)),
            )
        val b =
            CanonicalMusicBrain(
                deviceId = "desk",
                hlc = hlc(200),
                totalPlays = GCounter(mapOf("desk" to 7L)),
                genreAffinity = mapOf("rap" to 0.6, "pop" to 0.2),
                seenArtists = OrSet().add("UCb", hlc(60)),
            )

        val ab = MusicBrainMerger.merge(a, b)
        val ba = MusicBrainMerger.merge(b, a)

        assertThat(ab.totalPlays.sum()).isEqualTo(17L)
        assertThat(ab.genreAffinity["rap"]).isEqualTo(0.6)
        assertThat(ab.seenArtists.members()).containsExactly("UCa", "UCb")
        assertThat(ab.totalPlays).isEqualTo(ba.totalPlays)
        assertThat(ab.genreAffinity).isEqualTo(ba.genreAffinity)
        assertThat(ab.seenArtists.members()).isEqualTo(ba.seenArtists.members())

        val again = MusicBrainMerger.merge(ab, b)
        assertThat(again.totalPlays.sum()).isEqualTo(17L)
    }

    @Test
    fun `affinity plays never double-count and score follows the newer hlc`() {
        val a =
            CanonicalMusicBrain(
                artistAffinity =
                    mapOf(
                        "UCnf" to
                            CanonicalMusicAffinity(
                                plays = GCounter(mapOf("phone" to 5L)),
                                score = 0.7,
                                lastPlayed = 1000,
                                liked = false,
                                hlc = hlc(100),
                            ),
                    ),
            )
        val b =
            CanonicalMusicBrain(
                artistAffinity =
                    mapOf(
                        "UCnf" to
                            CanonicalMusicAffinity(
                                plays = GCounter(mapOf("phone" to 5L, "desk" to 3L)),
                                score = 0.9,
                                lastPlayed = 2000,
                                liked = true,
                                hlc = hlc(200),
                            ),
                    ),
            )

        val merged = MusicBrainMerger.merge(a, b).artistAffinity.getValue("UCnf")
        assertThat(merged.plays.sum()).isEqualTo(8L)
        assertThat(merged.score).isEqualTo(0.9)
        assertThat(merged.lastPlayed).isEqualTo(2000)
        assertThat(merged.liked).isTrue()
    }

    @Test
    fun `track plays union then keep the newest eight`() {
        val a = CanonicalMusicBrain(trackPlays = mapOf("t1" to (1L..6L).toList()))
        val b = CanonicalMusicBrain(trackPlays = mapOf("t1" to (4L..10L).toList()))

        val merged = MusicBrainMerger.merge(a, b).trackPlays.getValue("t1")
        assertThat(merged).containsExactly(3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L).inOrder()
    }

    @Test
    fun `an unblock tombstone beats an older block`() {
        val blockedOnDesk = CanonicalMusicBrain(blockedArtists = OrSet().add("UCa", hlc(100)))
        val unblockedOnPhone =
            CanonicalMusicBrain(
                blockedArtists = OrSet().add("UCa", hlc(100)).remove("UCa", hlc(200)),
            )

        val merged = MusicBrainMerger.merge(blockedOnDesk, unblockedOnPhone)
        assertThat(merged.blockedArtists.members()).isEmpty()
    }

    @Test
    fun `track meta ties break deterministically and appetite is lww`() {
        val a =
            CanonicalMusicBrain(
                trackMeta = mapOf("t1" to CanonicalMusicTrackMeta(title = "B", artist = "X")),
                discoveryAppetite = Lww(0.5, hlc(100)),
            )
        val b =
            CanonicalMusicBrain(
                trackMeta = mapOf("t1" to CanonicalMusicTrackMeta(title = "A", artist = "Z")),
                discoveryAppetite = Lww(0.3, hlc(200)),
            )

        val ab = MusicBrainMerger.merge(a, b)
        val ba = MusicBrainMerger.merge(b, a)
        assertThat(ab.trackMeta.getValue("t1").title).isEqualTo("B")
        assertThat(ba.trackMeta.getValue("t1").title).isEqualTo("B")
        assertThat(ab.discoveryAppetite!!.value).isEqualTo(0.3)

        val onlyLocal = MusicBrainMerger.merge(a, CanonicalMusicBrain())
        assertThat(onlyLocal.discoveryAppetite!!.value).isEqualTo(0.5)
    }
}
