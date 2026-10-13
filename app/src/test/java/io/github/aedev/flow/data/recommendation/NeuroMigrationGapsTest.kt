/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.io.ByteArrayInputStream

/** Brains that arrive from an older format, a file or an import still get every maintenance step (#907). */
class NeuroMigrationGapsTest {
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `a brain is saved with its own schema, so an unmaintained one is maintained on the next start`() {
        val storage = NeuroStorage(mockk(relaxed = true))

        val saved = with(storage) { UserBrain(schemaVersion = 15).toSerializable() }

        assertThat(saved.schemaVersion).isEqualTo(15)
    }

    @Test
    fun `a file without a schema is maintained in full`() {
        assertThat(NeuroStorage.declaredSchema(json("""{"global":{}}"""))).isEqualTo(NeuroStorage.LEGACY_SCHEMA_VERSION)
        assertThat(NeuroStorage.declaredSchema(json("""{"schemaVersion":16}"""))).isEqualTo(16)
    }

    @Test
    fun `only an export from before time buckets takes the legacy parser`() {
        val emptyBuckets = """{"schemaVersion":17,"timeVectors":{"WEEKDAY_MORNING":{"topics":{}}},"global":{"topics":{"chess":0.4}}}"""

        assertThat(NeuroStorage.isLegacyFormat(json(emptyBuckets))).isFalse()
        assertThat(NeuroStorage.isLegacyFormat(json("""{"morning":{"topics":{"chess":0.4}}}"""))).isTrue()
    }

    @Test
    fun `evidence that a re-key merges adds up instead of losing one record`() {
        val styled =
            TopicEvidence(
                positiveSignals = 2,
                watchSignals = 1,
                positiveScore = 1.5,
                videoIds = setOf("a"),
                firstSeenAt = 20L,
                lastSeenAt = 30L,
            )
        val plain =
            TopicEvidence(
                positiveSignals = 3,
                watchSignals = 2,
                positiveScore = 2.0,
                videoIds = setOf("b"),
                firstSeenAt = 10L,
                lastSeenAt = 25L,
            )
        val brain = UserBrain(topicEvidence = mapOf("𝙥𝙝𝙤𝙣𝙠" to styled, "phonk" to plain))

        val merged = NeuroMaintenance.refoldKeys(brain).topicEvidence.getValue("phonk")

        assertThat(merged.positiveSignals).isEqualTo(5)
        assertThat(merged.watchSignals).isEqualTo(3)
        assertThat(merged.positiveScore).isWithin(1e-9).of(3.5)
        assertThat(merged.videoIds).containsExactly("a", "b")
        assertThat(merged.firstSeenAt).isEqualTo(10L)
        assertThat(merged.lastSeenAt).isEqualTo(30L)
    }

    @Test
    fun `an imported brain keeps the word counts its maintenance re-keyed`() =
        runBlocking {
            val imported =
                UserBrain(
                    schemaVersion = 16,
                    totalInteractions = 40,
                    globalVector = ContentVector(topics = mapOf("phonk" to 0.4)),
                    idfWordFrequency = mapOf("𝙥𝙝𝙤𝙣𝙠" to 4),
                    idfTotalDocuments = 10,
                )
            val storage: NeuroStorage =
                mockk(relaxed = true) {
                    coEvery { load() } returns UserBrain()
                    coEvery { importFromStream(any()) } returns imported
                }
            val contentStore: NeuroContentStore = mockk(relaxed = true) { every { topicsFor(any()) } returns null }
            val engine = FlowNeuroEngine(mockk(relaxed = true), storage, contentStore, learningPaused = { false })

            assertThat(engine.importBrainFromStream(ByteArrayInputStream(ByteArray(0)))).isTrue()
            engine.onVideoInteraction(
                Video("v1", "Night drive mix", "Channel", "ch", "", 600, 1L, uploadDate = ""),
                InteractionType.CLICK,
            )
            val words = engine.getBrainSnapshot().idfWordFrequency
            engine.shutdown()

            assertThat(words).containsKey("phonk")
            assertThat(words).doesNotContainKey("𝙥𝙝𝙤𝙣𝙠")
        }
}
