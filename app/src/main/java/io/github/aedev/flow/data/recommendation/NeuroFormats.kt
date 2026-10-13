/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

/**
 * What kind of video a title announces ("review", "tutorial", "playalong"). A format word is never a
 * topic: it ends a chunk ([NeuroKeySpec] stage 5) and is learned as a preference of its own (#907).
 */
internal object NeuroFormats {
    /** Format id to the folded words that announce it, in the languages Flow can vouch for. */
    private val SURFACES: Map<String, List<String>> =
        mapOf(
            "review" to listOf("review", "reviews", "reseña", "análisis", "análise", "обзор", "inceleme"),
            "unboxing" to listOf("unboxing", "déballage", "распаковка"),
            "reaction" to listOf("reaction", "reactions", "reacting", "reacción", "reação", "réaction", "реакция", "tepki"),
            "tutorial" to listOf("tutorial", "tutorials", "tuto", "tutoriel", "урок"),
            "explainer" to listOf("explained", "explainer"),
            "vlog" to listOf("vlog", "vlogs"),
            "podcast" to listOf("podcast", "podcasts", "подкаст"),
            "live" to listOf("live", "livestream"),
            "compilation" to listOf("compilation", "compilación", "compilação", "подборка"),
            "playalong" to listOf("playalong"),
            "cover" to listOf("cover", "covers", "кавер"),
            "remix" to listOf("remix", "remixes", "ремикс"),
            "gameplay" to listOf("gameplay", "геймплей"),
            "walkthrough" to listOf("walkthrough", "playthrough", "прохождение"),
            "highlights" to listOf("highlights", "resumen", "хайлайты"),
            "trailer" to listOf("trailer", "teaser", "tráiler", "трейлер", "fragman"),
            "interview" to listOf("interview", "interviews", "entrevista", "интервью", "röportaj"),
            "documentary" to listOf("documentary", "documental", "documentário", "documentaire", "belgesel"),
            "asmr" to listOf("asmr"),
            "tier list" to listOf("tier list"),
            "versus" to listOf("versus", "vs"),
        )

    private val FORMAT_OF: Map<String, String> = SURFACES.flatMap { (id, words) -> words.map { it to id } }.toMap()

    /** The format [word] announces, or null; [word] is folded. */
    fun formatOf(word: String): String? = FORMAT_OF[word]

    /** The word a search uses for [format]. */
    fun searchWord(format: String): String = SURFACES[format]?.first() ?: format

    /** How far a viewer's liking for a format can move a candidate: a nudge, never a reason on its own. */
    const val MAX_SCORE_TERM = 0.04

    private const val LEARNING_RATE = 0.1
    private const val FADE = 0.01

    /** [affinity] after a positive signal on a video of [formats]; the others fade a little. */
    fun learn(
        affinity: Map<String, Double>,
        formats: Set<String>,
    ): Map<String, Double> {
        if (formats.isEmpty()) return affinity
        val updated = affinity.mapValues { (format, value) -> if (format in formats) value else value * (1.0 - FADE) }.toMutableMap()
        formats.forEach { format -> updated[format] = (updated[format] ?: 0.0) * (1.0 - LEARNING_RATE) + LEARNING_RATE }
        return updated.filterValues { it >= 0.01 }
    }

    /** The bounded scoring term for a candidate of [formats]. */
    fun scoreTerm(
        affinity: Map<String, Double>,
        formats: Set<String>,
    ): Double = (formats.maxOfOrNull { affinity[it] ?: 0.0 } ?: 0.0) * MAX_SCORE_TERM
}
