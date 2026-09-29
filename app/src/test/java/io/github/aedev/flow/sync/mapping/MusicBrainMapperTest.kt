/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.sync.mapping

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.music.MusicBrainStorage
import io.github.aedev.flow.sync.merge.MusicBrainCrdtState
import io.github.aedev.flow.sync.merge.MusicBrainMerger
import org.junit.Test

class MusicBrainMapperTest {
    private val hlc = "1000:0:aaaaaaaa"

    private fun localBrain() =
        MusicBrainStorage.SerializableMusicBrain(
            schemaVersion = 1,
            artistAffinity =
                mapOf(
                    "UCnf" to
                        MusicBrainStorage.SerializableAffinity(
                            plays = 5,
                            score = 0.7,
                            lastPlayed = 100,
                            liked = true,
                            display = "NF",
                        ),
                ),
            trackPlays = mapOf("t1" to listOf(1L, 2L)),
            trackMeta = mapOf("t1" to MusicBrainStorage.SerializableTrackMeta("Song", "NF", "UCnf", "thumb")),
            seenArtists = listOf("UCnf"),
            blockedArtists = listOf("UCbad"),
            dislikedArtists = mapOf("UCmeh" to 500L),
            artistRelated = mapOf("UCnf" to listOf("UCother")),
            discoveryAppetite = 0.4,
            totalPlays = 5,
            lastRotationDecay = 999L,
            backfilled = true,
        )

    private fun attributed() =
        MusicBrainCrdtState.attributeLocal(
            state = MusicBrainCrdtState(),
            myDevice = "phone",
            totalPlaysScalar = 5,
            artistPlayScalars = mapOf("UCnf" to 5L),
            artistScores = mapOf("UCnf" to 0.7),
            seenArtists = setOf("UCnf"),
            blockedArtists = setOf("UCbad"),
            dislikedArtists = mapOf("UCmeh" to 500L),
            appetite = 0.4,
            hlc = hlc,
        )

    @Test
    fun `round trip preserves synced state and device-local fields`() {
        val local = localBrain()
        val canonical = MusicBrainMapper.toCanonical(local, "phone", hlc, attributed())

        assertThat(canonical.totalPlays.sum()).isEqualTo(5L)
        assertThat(
            canonical.artistAffinity
                .getValue("UCnf")
                .plays
                .sum(),
        ).isEqualTo(5L)
        assertThat(canonical.blockedArtists.members()).containsExactly("UCbad")
        assertThat(canonical.dislikedArtists.getValue("UCmeh").value).isEqualTo(500L)
        assertThat(canonical.discoveryAppetite!!.value).isEqualTo(0.4)

        val back = MusicBrainMapper.writeBack(canonical, local)
        assertThat(back.artistAffinity.getValue("UCnf").plays).isEqualTo(5)
        assertThat(back.artistAffinity.getValue("UCnf").display).isEqualTo("NF")
        assertThat(back.artistRelated).isEqualTo(local.artistRelated)
        assertThat(back.backfilled).isTrue()
        assertThat(back.lastRotationDecay).isEqualTo(999L)
        assertThat(back.blockedArtists).containsExactly("UCbad")
        assertThat(back.totalPlays).isEqualTo(5)
    }

    @Test
    fun `re-attributing with no local activity adds nothing`() {
        val once = attributed()
        val twice =
            MusicBrainCrdtState.attributeLocal(
                state = once,
                myDevice = "phone",
                totalPlaysScalar = 5,
                artistPlayScalars = mapOf("UCnf" to 5L),
                artistScores = mapOf("UCnf" to 0.7),
                seenArtists = setOf("UCnf"),
                blockedArtists = setOf("UCbad"),
                dislikedArtists = mapOf("UCmeh" to 500L),
                appetite = 0.4,
                hlc = "2000:0:aaaaaaaa",
            )
        assertThat(twice.totalPlays).isEqualTo(once.totalPlays)
        assertThat(twice.artistPlays).isEqualTo(once.artistPlays)
        assertThat(twice.scoreHlcs).isEqualTo(once.scoreHlcs)
        assertThat(twice.appetiteHlc).isEqualTo(once.appetiteHlc)
    }

    @Test
    fun `merging both devices sums play counters through the mapper`() {
        val local = localBrain()
        val mine = MusicBrainMapper.toCanonical(local, "phone", hlc, attributed())
        val theirs =
            mine.copy(
                deviceId = "desk",
                totalPlays =
                    io.github.aedev.flow.sync.canonical
                        .GCounter(mapOf("desk" to 3L)),
                artistAffinity =
                    mapOf(
                        "UCnf" to
                            mine.artistAffinity
                                .getValue("UCnf")
                                .copy(
                                    plays =
                                        io.github.aedev.flow.sync.canonical
                                            .GCounter(mapOf("desk" to 3L)),
                                ),
                    ),
            )

        val merged = MusicBrainMerger.merge(mine, theirs)
        val back = MusicBrainMapper.writeBack(merged, local)
        assertThat(back.totalPlays).isEqualTo(8)
        assertThat(back.artistAffinity.getValue("UCnf").plays).isEqualTo(8)
    }
}
