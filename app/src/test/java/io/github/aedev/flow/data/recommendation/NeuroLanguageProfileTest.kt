/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark.watch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.util.Locale

/** The viewer's language: seeded from the app language, moved only by a window of titles (#907). */
class NeuroLanguageProfileTest {
    private val systemLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(systemLocale)

    private fun video(
        i: Int,
        title: String,
    ) = Video(
        id = "v$i",
        title = title,
        channelName = "Canal",
        channelId = "ch",
        thumbnailUrl = "",
        duration = 600,
        viewCount = 1000,
        uploadDate = "1 day ago",
    )

    @Test
    fun `a brain without a profile takes the app language, and a reset takes it again`() =
        runBlocking<Unit> {
            Locale.setDefault(Locale.forLanguageTag("es-MX"))
            val engine = NeuroLearningBenchmark.engine(UserBrain(schemaVersion = NeuroMaintenance.TARGET_SCHEMA_VERSION))
            engine.watch(video(0, "Cómo hacer tortilla para la cena"))
            assertThat(engine.getBrainSnapshot().languageProfile).isEqualTo("es")

            Locale.setDefault(Locale.FRENCH)
            engine.resetBrain()
            assertThat(engine.getBrainSnapshot().languageProfile).isEqualTo("fr")
            engine.shutdown()
        }

    @Test
    fun `a window of titles in another language moves the profile`() =
        runBlocking<Unit> {
            val engine =
                NeuroLearningBenchmark.engine(
                    UserBrain(schemaVersion = NeuroMaintenance.TARGET_SCHEMA_VERSION, languageProfile = "en"),
                )
            repeat(NeuroLanguages.PROFILE_WINDOW - 1) { engine.watch(video(it, "Cómo hacer la mejor receta de paella $it para la cena")) }
            assertThat(engine.getBrainSnapshot().languageProfile).isEqualTo("en")

            engine.watch(video(NeuroLanguages.PROFILE_WINDOW, "Cómo hacer la mejor receta de paella para la cena"))
            assertThat(engine.getBrainSnapshot().languageProfile).isEqualTo("es")
            engine.shutdown()
        }

    @Test
    fun `no language's stop list removes a catalog topic`() {
        val tokenizer = NeuroTokenizer()
        val topics = NeuroTopicCatalog.TOPIC_CATEGORIES.flatMap { it.topics }
        val english = topics.map(tokenizer::keysOf)
        NeuroLanguages.STOP_LIST_LANGUAGES.forEach { language ->
            tokenizer.languageProfile = language
            assertThat(topics.map(tokenizer::keysOf)).isEqualTo(english)
        }
    }
}
