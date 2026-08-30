/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicBrainLearnTest {
    private val now = 1_700_000_000_000L

    private fun signal(
        trackId: String = "t1",
        artistKey: String = "UCartist1",
        display: String = "Artist One",
        pct: Double = 1.0,
        like: Boolean = false,
    ) = MusicSignal(
        trackId = trackId,
        artistKey = artistKey,
        artistDisplay = display,
        percentPlayed = pct,
        isExplicitLike = like,
        title = "Song $trackId",
        thumbnail = "thumb",
    )

    private fun listen(
        brain: MusicBrain,
        sig: MusicSignal,
        at: Long = now,
        coArtist: String? = null,
    ): Boolean = MusicBrainLearn.applyMusicSignal(brain, sig, MusicBrainLearn.newlyCrossed(0.0, sig.percentPlayed), at, coArtist)

    @Test
    fun `full listen counts one play and pushes one timestamp`() {
        val brain = MusicBrain()
        val counted = listen(brain, signal())
        assertThat(counted).isTrue()
        assertThat(brain.artistAffinity["UCartist1"]!!.plays).isEqualTo(1)
        assertThat(brain.trackPlays["t1"]).hasSize(1)
        assertThat(brain.trackMeta).containsKey("t1")
        assertThat(brain.totalPlays).isEqualTo(1)
    }

    @Test
    fun `second listen of the same track adds a second timestamp`() {
        val brain = MusicBrain()
        listen(brain, signal(), at = now)
        listen(brain, signal(), at = now + 10_000)
        assertThat(brain.trackPlays["t1"]).hasSize(2)
        assertThat(brain.artistAffinity["UCartist1"]!!.plays).isEqualTo(2)
    }

    @Test
    fun `a 20 percent sample marks the artist seen but counts no play`() {
        val brain = MusicBrain()
        val counted = listen(brain, signal(pct = 0.2))
        assertThat(counted).isFalse()
        assertThat(brain.seenArtists).contains("UCartist1")
        assertThat(brain.artistAffinity["UCartist1"]!!.plays).isEqualTo(0)
        assertThat(brain.trackPlays).isEmpty()
        assertThat(brain.totalPlays).isEqualTo(0)
    }

    @Test
    fun `a sub-15 percent tick learns nothing`() {
        val brain = MusicBrain()
        val counted = listen(brain, signal(pct = 0.1))
        assertThat(counted).isFalse()
        assertThat(brain.seenArtists).isEmpty()
        assertThat(brain.artistAffinity).isEmpty()
    }

    @Test
    fun `explicit like counts at any progress and floors the score`() {
        val brain = MusicBrain()
        val counted = listen(brain, signal(pct = 0.0, like = true))
        assertThat(counted).isTrue()
        val e = brain.artistAffinity["UCartist1"]!!
        assertThat(e.liked).isTrue()
        assertThat(e.score).isAtLeast(MusicBrainParams.LIKE_SCORE_FLOOR)
        assertThat(e.plays).isEqualTo(1)
    }

    @Test
    fun `track ring is bounded at eight newest plays`() {
        val brain = MusicBrain()
        repeat(12) { i -> listen(brain, signal(), at = now + i * 1000L) }
        val ring = brain.trackPlays["t1"]!!
        assertThat(ring).hasSize(MusicBrainParams.TRACK_RING)
        assertThat(ring.first()).isEqualTo(now + 4000L)
        assertThat(ring.last()).isEqualTo(now + 11_000L)
    }

    @Test
    fun `session co-occurrence records an unordered pair`() {
        val brain = MusicBrain()
        listen(brain, signal(trackId = "a", artistKey = "UCa", display = "A"))
        listen(brain, signal(trackId = "b", artistKey = "UCb", display = "B"), coArtist = "UCa")
        assertThat(brain.artistCooc[musicPairKey("UCa", "UCb")]).isEqualTo(1.0)
    }

    @Test
    fun `dislike is a cooldown and escalates to a hard block on repeat`() {
        val brain = MusicBrain()
        listen(brain, signal())
        MusicBrainLearn.applyDislike(brain, "UCartist1", now)
        assertThat(brain.dislikedArtists).containsKey("UCartist1")
        assertThat(brain.blockedArtists).doesNotContain("UCartist1")

        MusicBrainLearn.applyDislike(brain, "UCartist1", now + 1000)
        assertThat(brain.blockedArtists).contains("UCartist1")
        assertThat(brain.dislikedArtists).doesNotContainKey("UCartist1")
    }

    @Test
    fun `a counted listen forgives a dislike`() {
        val brain = MusicBrain()
        MusicBrainLearn.applyDislike(brain, "UCartist1", now)
        listen(brain, signal(), at = now + 1000)
        assertThat(brain.dislikedArtists).doesNotContainKey("UCartist1")
    }

    @Test
    fun `block preserves history so unblocking warms back up`() {
        val brain = MusicBrain()
        repeat(5) { listen(brain, signal(), at = now + it * 1000L) }
        val playsBefore = brain.artistAffinity["UCartist1"]!!.plays
        MusicBrainLearn.blockArtist(brain, "UCartist1")
        assertThat(brain.artistAffinity["UCartist1"]!!.score).isEqualTo(0.0)
        MusicBrainLearn.unblockArtist(brain, "UCartist1")
        assertThat(brain.blockedArtists).isEmpty()
        assertThat(brain.artistAffinity["UCartist1"]!!.plays).isEqualTo(playsBefore)
        assertThat(brain.trackPlays["t1"]).isNotEmpty()
    }

    @Test
    fun `discovery appetite rises with novel engagement then regresses`() {
        val brain = MusicBrain()
        repeat(10) { i -> listen(brain, signal(trackId = "t$i", artistKey = "UCnew$i", display = "N$i"), at = now + i * 1000L) }
        val afterNovel = brain.discoveryAppetite
        assertThat(afterNovel).isGreaterThan(MusicBrainParams.DISCOVERY_NEUTRAL)

        repeat(40) { i -> listen(brain, signal(trackId = "t0", artistKey = "UCnew0", display = "N0"), at = now + 100_000 + i * 1000L) }
        assertThat(brain.discoveryAppetite).isLessThan(afterNovel)
    }

    @Test
    fun `rotation decays daily and drops dust`() {
        val brain = MusicBrain()
        listen(brain, signal(), at = now)
        val before = brain.recentRotation["UCartist1"]!!
        // 30 elapsed days: 0.85^30 ≈ 0.0076 — below the prune floor, entry dropped.
        MusicBrainLearn.decayRotationIfDue(brain, now + 30L * 86_400_000L)
        assertThat(brain.recentRotation).doesNotContainKey("UCartist1")
        assertThat(before).isGreaterThan(0.0)
    }

    @Test
    fun `live id-keyed play folds an existing name-keyed entry into the id`() {
        val brain = MusicBrain()
        listen(brain, signal(trackId = "old", artistKey = "artist one", display = "Artist One"), at = now)
        assertThat(brain.artistAffinity).containsKey("artist one")

        listen(brain, signal(trackId = "new", artistKey = "UCartist1", display = "Artist One"), at = now + 1000)
        assertThat(brain.artistAffinity).doesNotContainKey("artist one")
        assertThat(brain.artistAffinity["UCartist1"]!!.plays).isEqualTo(2)
    }

    @Test
    fun `prune keeps the strongest artists`() {
        val brain = MusicBrain()
        repeat(MusicBrainParams.ARTIST_AFFINITY_MAX + 10) { i ->
            brain.artistAffinity["UCa$i"] = MusicAffinity(plays = 1, score = i / 1000.0)
        }
        MusicBrainLearn.prune(brain)
        assertThat(brain.artistAffinity).hasSize(MusicBrainParams.ARTIST_AFFINITY_KEEP)
        assertThat(brain.artistAffinity).containsKey("UCa${MusicBrainParams.ARTIST_AFFINITY_MAX + 9}")
        assertThat(brain.artistAffinity).doesNotContainKey("UCa0")
    }

    @Test
    fun `artist key prefers ids and lowercases names`() {
        assertThat(musicArtistKey("UCabc", "Drake")).isEqualTo("UCabc")
        assertThat(musicArtistKey("  ", "Drake ")).isEqualTo("drake")
        assertThat(musicArtistKey(null, null)).isEmpty()
        assertThat(isIdKeyedArtist("UCabc")).isTrue()
        assertThat(isIdKeyedArtist("drake")).isFalse()
    }

    @Test
    fun `time buckets map hours and weekends`() {
        assertThat(MusicTimeBucket.fromParts(8, false)).isEqualTo(MusicTimeBucket.WEEKDAY_MORNING)
        assertThat(MusicTimeBucket.fromParts(13, false)).isEqualTo(MusicTimeBucket.WEEKDAY_AFTERNOON)
        assertThat(MusicTimeBucket.fromParts(20, true)).isEqualTo(MusicTimeBucket.WEEKEND_EVENING)
        assertThat(MusicTimeBucket.fromParts(2, true)).isEqualTo(MusicTimeBucket.WEEKEND_NIGHT)
        assertThat(MusicTimeBucket.fromWire("WeekdayMorning")).isEqualTo(MusicTimeBucket.WEEKDAY_MORNING)
    }

    @Test
    fun `a listen stores the artist display name for stats`() {
        val brain = MusicBrain()
        listen(brain, signal(display = "Artist One"))
        assertThat(brain.artistAffinity["UCartist1"]!!.display).isEqualTo("Artist One")
    }

    @Test
    fun `recordArtistRelated replaces edges and caps their count`() {
        val brain = MusicBrain()
        MusicBrainLearn.recordArtistRelated(brain, "UCa", listOf("UCb", "UCc", "UCa", "UCb"))
        assertThat(brain.artistRelated["UCa"]).containsExactly("UCb", "UCc").inOrder()

        val many = (0 until 20).map { "UCr$it" }
        MusicBrainLearn.recordArtistRelated(brain, "UCa", many)
        assertThat(brain.artistRelated["UCa"]).hasSize(MusicBrainParams.ARTIST_RELATED_EDGES)
        assertThat(brain.artistRelated["UCa"]!!.first()).isEqualTo("UCr0")
    }
}
