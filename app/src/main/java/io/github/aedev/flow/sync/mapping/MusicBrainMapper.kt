package io.github.aedev.flow.sync.mapping

import io.github.aedev.flow.data.recommendation.music.MusicBrainStorage
import io.github.aedev.flow.sync.canonical.CanonicalMusicAffinity
import io.github.aedev.flow.sync.canonical.CanonicalMusicBrain
import io.github.aedev.flow.sync.canonical.CanonicalMusicTrackMeta
import io.github.aedev.flow.sync.canonical.GCounter
import io.github.aedev.flow.sync.canonical.Lww
import io.github.aedev.flow.sync.merge.MusicBrainCrdtState
import kotlinx.serialization.json.Json

/**
 * Maps the music brain's persisted document to/from the `music_brain` wire model.
 * Device-local fields (`backfilled`, `lastRotationDecay`, `artistRelated`, per-artist
 * `display`) never go on the wire and are preserved from the local brain on write-back.
 */
internal object MusicBrainMapper {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun parse(bytes: ByteArray): MusicBrainStorage.SerializableMusicBrain =
        json.decodeFromString(
            MusicBrainStorage.SerializableMusicBrain.serializer(),
            bytes.decodeToString(),
        )

    fun serialize(brain: MusicBrainStorage.SerializableMusicBrain): ByteArray =
        json
            .encodeToString(MusicBrainStorage.SerializableMusicBrain.serializer(), brain)
            .encodeToByteArray()

    fun toCanonical(
        local: MusicBrainStorage.SerializableMusicBrain,
        myDevice: String,
        hlc: String,
        sidecar: MusicBrainCrdtState,
    ): CanonicalMusicBrain =
        CanonicalMusicBrain(
            schema = local.schemaVersion,
            deviceId = myDevice,
            hlc = hlc,
            artistAffinity =
                local.artistAffinity.mapValues { (key, a) ->
                    CanonicalMusicAffinity(
                        plays = GCounter(sidecar.artistPlays[key] ?: mapOf(myDevice to a.plays.toLong())),
                        score = a.score,
                        lastPlayed = a.lastPlayed,
                        liked = a.liked,
                        hlc = sidecar.scoreHlcs[key] ?: hlc,
                    )
                },
            genreAffinity = local.genreAffinity,
            artistCooc = local.artistCooc,
            recentRotation = local.recentRotation,
            timeBuckets = local.timeBuckets,
            trackPlays = local.trackPlays,
            trackMeta =
                local.trackMeta.mapValues { (_, m) ->
                    CanonicalMusicTrackMeta(m.title, m.artist, m.artistKey, m.thumbnail)
                },
            totalPlays = GCounter(sidecar.totalPlays),
            seenArtists = sidecar.seenArtists,
            blockedArtists = sidecar.blockedArtists,
            dislikedArtists =
                local.dislikedArtists.mapValues { (key, at) ->
                    Lww(at, sidecar.dislikedHlcs[key] ?: hlc)
                },
            discoveryAppetite = Lww(local.discoveryAppetite, sidecar.appetiteHlc.ifEmpty { hlc }),
        )

    /**
     * Merged canonical → the brain's persisted document. Wire-absent, device-local
     * fields come from [local]: per-artist display names, the fans-also-like graph,
     * the backfill flag, and the rotation-decay day stamp.
     */
    fun writeBack(
        merged: CanonicalMusicBrain,
        local: MusicBrainStorage.SerializableMusicBrain,
    ): MusicBrainStorage.SerializableMusicBrain =
        MusicBrainStorage.SerializableMusicBrain(
            schemaVersion = maxOf(merged.schema, local.schemaVersion),
            artistAffinity =
                merged.artistAffinity.mapValues { (key, a) ->
                    MusicBrainStorage.SerializableAffinity(
                        plays = a.plays.sum().toInt(),
                        score = a.score,
                        lastPlayed = a.lastPlayed,
                        liked = a.liked,
                        display = local.artistAffinity[key]?.display.orEmpty(),
                    )
                },
            genreAffinity = merged.genreAffinity,
            trackPlays = merged.trackPlays,
            trackMeta =
                merged.trackMeta.mapValues { (_, m) ->
                    MusicBrainStorage.SerializableTrackMeta(m.title, m.artist, m.artistKey, m.thumbnail)
                },
            recentRotation = merged.recentRotation,
            artistCooc = merged.artistCooc,
            artistRelated = local.artistRelated,
            timeBuckets = merged.timeBuckets,
            seenArtists = merged.seenArtists.members().toList(),
            dislikedArtists = merged.dislikedArtists.mapValues { it.value.value },
            blockedArtists = merged.blockedArtists.members().toList(),
            discoveryAppetite = merged.discoveryAppetite?.value ?: local.discoveryAppetite,
            totalPlays = merged.totalPlays.sum().toInt(),
            lastRotationDecay = local.lastRotationDecay,
            backfilled = local.backfilled,
        )
}
