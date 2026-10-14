/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import org.tartarus.snowball.SnowballStemmer
import org.tartarus.snowball.ext.englishStemmer
import org.tartarus.snowball.ext.frenchStemmer
import org.tartarus.snowball.ext.germanStemmer
import org.tartarus.snowball.ext.italianStemmer
import org.tartarus.snowball.ext.portugueseStemmer
import org.tartarus.snowball.ext.russianStemmer
import org.tartarus.snowball.ext.spanishStemmer

/**
 * Stage 8 of [NeuroKeySpec] 2: a word's key is its Snowball stem, so "croquetas" and "croqueta" or
 * "guitars" and "guitar" are one interest (#907). The stemmer comes from the word's script and the
 * viewer's [UserBrain.languageProfile], never from guessing each title's language, so one word has
 * one key for one person on every device.
 *
 * Words in `resources/neuro/keys/unstemmed.txt` keep their spec-1 form: the engine's own word lists
 * (topics, domains, polysemous words) are written in it. So do words holding digits or marks, words
 * whose stem would be under three letters, and words whose stem is not its own stem, so a key is
 * always its own key.
 */
internal object NeuroStemmer {
    const val MIN_STEM = 3

    /** Profiles with a stemmer for words in Latin script. */
    internal val LatinStemmers = setOf("en", "es", "pt", "fr", "de", "it")

    // Latin-script languages without a stemmer: their words stay as written. Viewers whose language
    // is written in another script read Latin words mostly in English.
    internal val OtherLatinLanguages =
        setOf(
            "tr",
            "id",
            "ms",
            "pl",
            "cs",
            "sk",
            "sl",
            "hr",
            "bs",
            "sr",
            "ro",
            "hu",
            "nl",
            "sv",
            "da",
            "no",
            "nb",
            "fi",
            "et",
            "lv",
            "lt",
            "vi",
            "az",
            "uz",
            "sq",
            "ca",
            "eu",
            "gl",
            "ga",
            "cy",
            "is",
            "mt",
            "tl",
            "fil",
            "sw",
            "af",
            "eo",
        )

    // Cyrillic languages other than Russian: their words stay as written.
    internal val OtherCyrillicLanguages = setOf("uk", "be", "bg", "sr", "mk", "kk", "ky", "mn", "tg")

    private val unstemmed: Set<String> by lazy {
        NeuroStemmer::class.java
            .getResourceAsStream("/neuro/keys/unstemmed.txt")
            ?.bufferedReader(Charsets.UTF_8)
            ?.useLines { lines -> lines.filter { it.isNotBlank() && !it.startsWith("#") }.toHashSet() }
            ?: emptySet()
    }

    // Snowball stemmers keep state between calls, so each thread has its own.
    private val stemmers = ThreadLocal.withInitial { HashMap<String, SnowballStemmer>() }

    fun isUnstemmed(word: String): Boolean = word in unstemmed

    /** [word], already folded and lemmatized, as a key spec 2 key under [profile]. */
    fun key(
        word: String,
        profile: String,
    ): String {
        if (word in unstemmed || word.any { !it.isLetter() }) return word
        val language = languageOf(word, profile) ?: return word
        val stemmer = stemmers.get().getOrPut(language) { create(language) }
        val stem = stemmer.stemOf(word)
        // A stem that stems again ("casero", "caser", "cas") would drift on every pass, so the word stays.
        return if (stem.length < MIN_STEM || stemmer.stemOf(stem) != stem) word else stem
    }

    private fun SnowballStemmer.stemOf(word: String): String {
        current = word
        stem()
        return current
    }

    /** The stemmer for [word] under [profile], or null when the word stays as written. */
    fun languageOf(
        word: String,
        profile: String,
    ): String? =
        when (Character.UnicodeScript.of(word[0].code)) {
            Character.UnicodeScript.LATIN -> {
                when (profile) {
                    in LatinStemmers -> profile
                    in OtherLatinLanguages -> null
                    else -> "en"
                }
            }

            Character.UnicodeScript.CYRILLIC -> {
                if (profile in OtherCyrillicLanguages) null else "ru"
            }

            else -> {
                null
            }
        }

    private fun create(language: String): SnowballStemmer =
        when (language) {
            "en" -> englishStemmer()
            "es" -> spanishStemmer()
            "pt" -> portugueseStemmer()
            "fr" -> frenchStemmer()
            "de" -> germanStemmer()
            "it" -> italianStemmer()
            "ru" -> russianStemmer()
            else -> error("No stemmer for $language")
        }
}
