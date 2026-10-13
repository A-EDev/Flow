/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.sync.mapping

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.NeuroStorage
import io.github.aedev.flow.sync.merge.BrainCrdtState
import kotlinx.serialization.json.Json
import org.junit.Test

/** A sync merge rewrites only what sync carries; every other brain field survives it. */
class BrainMapperOverlayTest {
    private val json = Json { encodeDefaults = true }

    private val local =
        NeuroStorage.SerializableBrain(
            schemaVersion = 17,
            channelScores = mapOf("UCa" to 0.4),
            interactions = 120,
            recentRelatedSeeds = mapOf("seed" to 10L),
            recentShortsSeeds = mapOf("reel" to 11L),
            staleQueries = mapOf("query" to 12L),
            clusterRotation = mapOf("android" to 13L),
            tagAffinities = mapOf("android|kotlin" to 0.3),
            timeBucketCounts = mapOf("WEEKDAY_NIGHT" to 7),
        )

    private fun localJson() = json.encodeToString(NeuroStorage.SerializableBrain.serializer(), local).toByteArray()

    private fun decode(bytes: ByteArray) = json.decodeFromString(NeuroStorage.SerializableBrain.serializer(), String(bytes))

    @Test
    fun `fields sync does not carry survive a merge`() {
        val merged = BrainMapper.parse(localJson()).copy(channelScores = mapOf("UCa" to 0.9))

        val written = decode(BrainMapper.serializeOver(localJson(), merged))

        assertThat(written.channelScores).containsExactly("UCa", 0.9)
        assertThat(written.copy(channelScores = local.channelScores)).isEqualTo(local)
    }

    @Test
    fun `an unchanged merge writes back the brain exactly`() {
        val written = decode(BrainMapper.serializeOver(localJson(), BrainMapper.parse(localJson())))

        assertThat(written).isEqualTo(local)
    }

    @Test
    fun `a peer's schema never stamps this brain, so its own migrations still run`() {
        val peer = BrainMapper.toCanonical(BrainMapper.parse(localJson()), "peer", "1", BrainCrdtState()).copy(schema = 18)

        assertThat(BrainMapper.writeBack(peer, BrainMapper.parse(localJson())).schemaVersion).isEqualTo(17)
    }
}
