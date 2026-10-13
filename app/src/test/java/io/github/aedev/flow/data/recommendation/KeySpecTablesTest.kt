/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertWithMessage
import io.github.aedev.flow.data.recommendation.eval.RealTitleCorpus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import java.io.File

/**
 * The tables [NeuroKeySpec] 2 is built from, as data Flow Desktop reads, so both apps read titles
 * from one source. The file must match the code; run with NEURO_WRITE_TABLES=1 to write it again.
 */
class KeySpecTablesTest {
    private val tokenizer = NeuroTokenizer()

    private fun strings(values: Iterable<String>) = JsonArray(values.map(::JsonPrimitive))

    private fun sorted(values: Iterable<String>) = strings(values.sorted())

    private fun map(values: Map<String, JsonElement>) = JsonObject(values.toSortedMap())

    private fun tables(): JsonObject =
        JsonObject(
            linkedMapOf(
                "keySpec" to JsonPrimitive(NeuroKeySpec.VERSION),
                "about" to
                    JsonPrimitive("Tables NeuroKeySpec reads titles by, written by KeySpecTablesTest. Flow Desktop mirrors this file."),
                "maxRunWords" to JsonPrimitive(NeuroKeySpec.MAX_RUN_WORDS),
                "maxKeyLength" to JsonPrimitive(NeuroKeySpec.MAX_KEY_LENGTH),
                "minStem" to JsonPrimitive(NeuroStemmer.MIN_STEM),
                "shortTopics" to sorted(NeuroText.SHORT_TOPICS),
                "neverAlone" to sorted(NeuroKeySpec.NEVER_ALONE),
                "keepWhole" to sorted(NeuroKeySpec.KEEP_WHOLE),
                "elisions" to strings(NeuroKeySpec.ELISIONS),
                "contractions" to sorted(NeuroKeySpec.CONTRACTIONS),
                "expressions" to map(NeuroKeySpec.EXPRESSIONS.mapValues { JsonPrimitive(it.value) }),
                "lemmas" to map(tokenizer.lemmas.mapValues { JsonPrimitive(it.value) }),
                "stopWords" to sorted(tokenizer.baseStopWords),
                "protectedWords" to sorted(tokenizer.protectedWords),
                "formats" to map(NeuroFormats.SURFACES.mapValues { strings(it.value) }),
                "functionWords" to map(NeuroLanguages.FunctionWords.mapValues { strings(it.value) }),
                "ownLetters" to map(NeuroLanguages.OwnLetterSets.mapValues { JsonPrimitive(it.value) }),
                "ukrainianWords" to sorted(NeuroLanguages.UkrainianWords),
                "elisionLanguages" to
                    map(
                        mapOf(
                            "fr" to sorted(NeuroLanguages.FrenchElisions),
                            "fr it" to sorted(NeuroLanguages.SharedElisions),
                            "it" to sorted(NeuroLanguages.ItalianElisions),
                        ),
                    ),
                "scriptLetters" to
                    map(
                        mapOf(
                            "persian" to JsonPrimitive(NeuroLanguages.PERSIAN_LETTERS.sorted().joinToString("")),
                            "urdu" to JsonPrimitive(NeuroLanguages.URDU_LETTERS.sorted().joinToString("")),
                            "ukrainian" to JsonPrimitive(NeuroLanguages.UKRAINIAN_LETTERS.sorted().joinToString("")),
                            "otherCyrillic" to JsonPrimitive(NeuroLanguages.OTHER_CYRILLIC_LETTERS.sorted().joinToString("")),
                            "vietnamese" to JsonPrimitive(NeuroLanguages.VIETNAMESE_LETTERS.sorted().joinToString("")),
                        ),
                    ),
                "stopListLanguages" to sorted(NeuroLanguages.STOP_LIST_LANGUAGES),
                "latinStemmers" to sorted(NeuroStemmer.LatinStemmers),
                "otherLatinLanguages" to sorted(NeuroStemmer.OtherLatinLanguages),
                "otherCyrillicLanguages" to sorted(NeuroStemmer.OtherCyrillicLanguages),
            ),
        )

    @Test
    fun `the tables Flow Desktop reads match the code`() {
        val json = Json { prettyPrint = true }
        val text = json.encodeToString(JsonObject.serializer(), tables()) + "\n"
        val file = File("src/test/resources/neuro/key_spec_tables.json")
        if (System.getenv("NEURO_WRITE_TABLES") == "1") file.writeText(text)

        assertWithMessage("run the test with NEURO_WRITE_TABLES=1").that(file.readText()).isEqualTo(text)
    }

    /** Words from titles in each language and the key each key language gives them, so Desktop can check its stems. */
    @Test
    fun `the stems Flow Desktop checks match the code`() {
        val titles =
            javaClass
                .getResourceAsStream("/neuro/language_titles.tsv")!!
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.substringBefore('\t') to it.substringAfter('\t') } + RealTitleCorpus.titles.map { "en" to it }
        val lines =
            listOf("en", "es", "pt", "fr", "de", "it", "ru").flatMap { language ->
                tokenizer.keyLanguage = language
                titles
                    .filter { (titleLanguage, _) -> titleLanguage == language || (language == "ru" && titleLanguage == "en") }
                    .flatMap { (_, title) -> NeuroText.words(NeuroText.fold(title)) }
                    .map { piece -> piece.trim { !NeuroText.isWordChar(it) } }
                    .filter { word -> word.length >= 2 && word.all(NeuroText::isWordChar) }
                    .toSortedSet()
                    .map { word -> "$language\t$word\t${tokenizer.keyOf(word)}" }
            }
        val text =
            "# key language, folded word, key (NeuroTokenizer.keyOf). Written by KeySpecTablesTest.\n" +
                lines.joinToString("\n", postfix = "\n")
        val file = File("src/test/resources/neuro/stem_golden.tsv")
        if (System.getenv("NEURO_WRITE_TABLES") == "1") file.writeText(text)

        assertWithMessage("run the test with NEURO_WRITE_TABLES=1").that(file.readText()).isEqualTo(text)

        val detected =
            titles.joinToString(
                "\n",
                "# label, title, detected language or nothing. Written by KeySpecTablesTest.\n",
                "\n",
            ) { (label, title) ->
                "$label\t$title\t${NeuroLanguages.detectText(title).orEmpty()}"
            }
        val detectedFile = File("src/test/resources/neuro/language_detected.tsv")
        if (System.getenv("NEURO_WRITE_TABLES") == "1") detectedFile.writeText(detected)

        assertWithMessage("run the test with NEURO_WRITE_TABLES=1").that(detectedFile.readText()).isEqualTo(detected)
    }
}
