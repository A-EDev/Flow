/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Schema 18: keys read again by key spec 1, chance pairs out, onboarding links out. Schema 20: stems (#907). */
class NeuroKeyMigrationTest {
    private val stemming = NeuroTokenizer()

    // Maintenance reads keys up to schema 18 by key spec 1.
    private val tokenizer = stemming.atKeySpec(1)

    private fun evidence(
        signals: Int,
        videos: Int = 0,
        explicit: Int = 0,
        score: Double = signals * 0.5,
    ) = TopicEvidence(
        positiveSignals = signals,
        explicitSignals = explicit,
        positiveScore = score,
        videoIds = (0 until videos).mapTo(LinkedHashSet()) { "v$it" },
        firstSeenAt = 10L,
        lastSeenAt = 20L,
    )

    private val chancePairs = listOf("big tech's", "tech's privacy", "privacy promises")

    private fun schema17(): UserBrain {
        val global =
            mapOf(
                "lo-fi" to 0.40,
                "lofi" to 0.20,
                "tech's" to 0.10,
                "tech" to 0.30,
                "guitar playalong" to 0.12,
                "hip hop" to 0.10,
                "machine learn" to 0.08,
                "worth upgrading" to 0.35,
                "encryption work" to 0.05,
            ) + chancePairs.associateWith { 0.05 }
        return UserBrain(
            schemaVersion = 17,
            totalInteractions = 400,
            globalVector = ContentVector(topics = global),
            timeVectors = mapOf(TimeBucket.WEEKDAY_EVENING to ContentVector(topics = mapOf("big tech's" to 0.1, "lo-fi" to 0.2))),
            channelTopicProfiles = mapOf("ch" to mapOf("privacy promises" to 0.4, "tech's" to 0.3)),
            topicEvidence =
                mapOf(
                    "tech's" to evidence(2, videos = 1),
                    "tech" to evidence(3, videos = 2),
                    "guitar playalong" to evidence(4, videos = 4),
                    "encryption work" to evidence(1, explicit = 1),
                    "big tech's" to evidence(1, videos = 1),
                ),
            idfWordFrequency = mapOf("big tech's" to 2, "tech's" to 3, "tech" to 5),
            idfTotalDocuments = 200,
            preferredTopics = setOf("Cooking", "Gaming", "Anime"),
        )
    }

    @Test
    fun `spellings of one key merge into the key spec's`() {
        val migrated = NeuroKeyMigration.run(schema17(), tokenizer)

        assertThat(migrated.globalVector.topics).containsEntry("lofi", 0.40)
        assertThat(migrated.globalVector.topics).containsEntry("tech", 0.30)
        assertThat(migrated.globalVector.topics.keys).containsNoneOf("lo-fi", "tech's")
        assertThat(migrated.timeVectors.getValue(TimeBucket.WEEKDAY_EVENING).topics).containsEntry("lofi", 0.2)
        assertThat(migrated.channelTopicProfiles.getValue("ch")).containsEntry("tech", 0.3)
        assertThat(migrated.topicEvidence.getValue("tech").positiveSignals).isEqualTo(5)
        assertThat(migrated.topicEvidence.getValue("tech").videoIds).containsExactly("v0", "v1")
    }

    @Test
    fun `a key holding punctuation splits into its words, and one with none left goes`() {
        val brain = schema17().copy(globalVector = ContentVector(topics = mapOf("style(강남스타일" to 0.2, "m/v" to 0.2, "psy" to 0.1)))

        val topics = NeuroKeyMigration.run(brain, tokenizer).globalVector.topics

        assertThat(topics).containsExactly("style", 0.2, "강남스타일", 0.2, "psy", 0.1)
    }

    @Test
    fun `a word-level rejection pattern keeps its mark`() {
        val brain =
            schema17().copy(
                rejectionPatterns =
                    mapOf(
                        "~lo-fi" to RejectionSignal(2, 5L),
                        "lo-fi" to RejectionSignal(1, 4L),
                        "~guitar" to RejectionSignal(1, 3L),
                    ),
            )

        val patterns = NeuroKeyMigration.run(brain, tokenizer).rejectionPatterns

        assertThat(patterns.keys).containsExactly("~lofi", "lofi", "~guitar")
    }

    @Test
    fun `chance pairs leave every vector, profile and record, but document frequency keeps them`() {
        val migrated = NeuroKeyMigration.run(schema17(), tokenizer)
        val dropped = listOf("big tech", "tech privacy", "privacy promises").map { tokenizer.keysOf(it).single() }

        assertThat(migrated.globalVector.topics.keys).containsNoneIn(dropped)
        assertThat(
            migrated.timeVectors
                .getValue(TimeBucket.WEEKDAY_EVENING)
                .topics.keys,
        ).containsNoneIn(dropped)
        assertThat(migrated.channelTopicProfiles.getValue("ch").keys).containsNoneIn(dropped)
        assertThat(migrated.topicEvidence.keys).containsNoneIn(dropped)
        assertThat(migrated.idfWordFrequency).containsEntry("big tech's", 2)
    }

    @Test
    fun `a link whose end is a chance pair goes, even when no vector holds the pair`() {
        val brain = schema17().copy(topicAffinities = mapOf("built chatgpt|chatgpt scratch" to 0.21, "chatgpt|tech" to 0.2))

        val links = NeuroKeyMigration.run(brain, tokenizer).topicAffinities.keys

        assertThat(links).containsExactly("chatgpt|tech")
    }

    @Test
    fun `a pair with proof stays`() {
        val topics = NeuroKeyMigration.run(schema17(), tokenizer).globalVector.topics

        // Four videos, an expression, a compound, a strong weight and a typed search.
        assertThat(topics.keys).containsAtLeast("guitar playalong", "hip hop", "machine learn", "worth upgrading", "encryption work")
    }

    @Test
    fun `onboarding links between picks go, links the viewer grew stay`() {
        val (cooking, gaming, anime) = listOf("Cooking", "Gaming", "Anime").map { tokenizer.keysOf(it).single() }
        val brain =
            schema17().copy(
                topicAffinities =
                    mapOf(
                        NeuroScoring.makeAffinityKey(cooking, gaming) to 0.3,
                        NeuroScoring.makeAffinityKey(gaming, anime) to 0.42,
                        NeuroScoring.makeAffinityKey("guitar", "tabs") to 0.3,
                    ),
            )

        val edges = NeuroKeyMigration.run(brain, tokenizer).topicAffinities.keys

        assertThat(edges)
            .containsExactly(NeuroScoring.makeAffinityKey(gaming, anime), NeuroScoring.makeAffinityKey("guitar", "tabs"))
    }

    @Test
    fun `a pick learned under its whole label moves to the key titles give it`() {
        val brain =
            schema17().copy(
                preferredTopics = setOf("Machine Learning", "Health & Fitness"),
                globalVector = ContentVector(topics = mapOf("machine learning" to 0.38, "health & fitness" to 0.3)),
            )

        val topics = NeuroKeyMigration.run(brain, tokenizer).globalVector.topics

        assertThat(topics).containsExactly("machine learn", 0.38, "health", 0.3, "fitness", 0.3)
    }

    @Test
    fun `a pick with a stop word in it reads as the keys titles give it`() {
        assertThat(tokenizer.keysOf("Health & Fitness")).containsExactly("health", "fitness").inOrder()
        assertThat(tokenizer.keysOf("Call of Duty")).containsExactly("call of duty")
        assertThat(tokenizer.keysOf("Lo-Fi")).containsExactly("lofi")
    }

    @Test
    fun `the cleanup after a sync can run any number of times without counting twice`() {
        val synced =
            schema17().copy(
                schemaVersion = NeuroKeyMigration.SCHEMA_VERSION,
                topicEvidence = mapOf("tech's" to evidence(2, score = 1.0), "tech" to evidence(3, score = 1.5)),
            )

        val once = NeuroKeyMigration.afterSync(synced, stemming)
        val twice =
            NeuroKeyMigration.afterSync(
                once.copy(topicEvidence = once.topicEvidence + ("tech's" to evidence(2, score = 1.0))),
                stemming,
            )

        assertThat(once.topicEvidence.getValue("tech").positiveSignals).isEqualTo(3)
        assertThat(twice.topicEvidence).isEqualTo(once.topicEvidence)
        assertThat(once.globalVector.topics.keys).containsAtLeastElementsIn(chancePairs.map { stemming.keysOf(it).single() })
    }

    @Test
    fun `schema 20 stems every key, joins the forms of one word and sums their document frequency`() {
        val brain =
            UserBrain(
                schemaVersion = NeuroMaintenance.STOP_LIST_SCHEMA_VERSION,
                globalVector = ContentVector(topics = mapOf("guitars" to 0.2, "guitar" to 0.4, "kayaking" to 0.3, "rock:music" to 0.1)),
                topicEvidence = mapOf("guitars" to evidence(2, videos = 1), "guitar" to evidence(3, videos = 2)),
                idfWordFrequency = mapOf("guitars" to 4, "guitar" to 6, "kayaking" to 2),
                displayForms = mapOf("kayaking" to DisplayForm("Kayaking", votes = 3)),
                rejectionPatterns = mapOf("~guitars" to RejectionSignal(1, 1L)),
                topicAffinities = mapOf("guitars|kayaking" to 0.4),
            )

        val migrated = NeuroKeyMigration.toKeySpec2(brain, stemming)

        assertThat(migrated.globalVector.topics).containsExactly("guitar", 0.4, "kayak", 0.3, "rock:music", 0.1)
        assertThat(migrated.topicEvidence.getValue("guitar").positiveSignals).isEqualTo(5)
        assertThat(migrated.idfWordFrequency).containsExactly("guitar", 10, "kayak", 2)
        assertThat(migrated.displayForms.getValue("kayak").form).isEqualTo("Kayaking")
        assertThat(migrated.displayForms).doesNotContainKey("guitar")
        assertThat(migrated.rejectionPatterns.keys).containsExactly("~guitar")
        assertThat(migrated.topicAffinities.keys).containsExactly(NeuroScoring.makeAffinityKey("guitar", "kayak"))
        assertThat(NeuroKeyMigration.toKeySpec2(migrated, stemming)).isEqualTo(migrated)
    }

    @Test
    fun `a key known only from a link keeps its spelling, so searches never send a stem`() {
        val brain =
            UserBrain(
                schemaVersion = 19,
                globalVector = ContentVector(topics = mapOf("design" to 0.3)),
                topicAffinities =
                    mapOf("claude|design" to 0.2),
            )

        val migrated = NeuroKeyMigration.toKeySpec2(brain, stemming)

        assertThat(migrated.topicAffinities.keys).containsExactly("claud|design")
        assertThat(NeuroDisplayForms.searchText(migrated.displayForms, "design claud")).isEqualTo("design Claude")
    }

    @Test
    fun `a key that stems keeps the label it had`() {
        val brain = UserBrain(globalVector = ContentVector(topics = mapOf("restoring vintage guitars" to 0.05, "vintage" to 0.2)))

        val forms = NeuroKeyMigration.toKeySpec2(brain, stemming).displayForms

        assertThat(
            NeuroDisplayForms.label(forms, stemming.keysOf("restoring vintage guitars").single()),
        ).isEqualTo("Restoring Vintage Guitars")
        assertThat(NeuroDisplayForms.label(forms, stemming.keysOf("vintage").single())).isEqualTo("Vintage")
    }

    @Test
    fun `a stop word learned before it was one leaves before keys are stemmed`() {
        val brain = UserBrain(schemaVersion = 19, globalVector = ContentVector(topics = mapOf("youtube" to 0.05, "android" to 0.4)))

        val migrated = NeuroMaintenance.runIfNeeded(brain, stemming)

        assertThat(migrated.globalVector.topics.keys).containsExactly("android")
    }

    @Test
    fun `maintenance brings a schema 17 brain up to date once`() {
        val migrated = NeuroMaintenance.runIfNeeded(schema17(), stemming)

        assertThat(migrated.schemaVersion).isEqualTo(NeuroMaintenance.TARGET_SCHEMA_VERSION)
        assertThat(NeuroMaintenance.runIfNeeded(migrated, stemming)).isSameInstanceAs(migrated)
    }
}
