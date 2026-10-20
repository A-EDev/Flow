/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

/**
 * What kind of video a title announces ("review", "tutorial", "playalong"). A format word is never a
 * topic: it ends a chunk ([NeuroKeySpec] stage 5) and is learned as a preference of its own (#907).
 * Chinese surfaces are Hans.
 */
internal object NeuroFormats {
    /** Format id to the folded words that announce it, in the languages Flow can vouch for. */
    internal val SURFACES: Map<String, List<String>> =
        mapOf(
            "review" to listOf("review", "reviews", "reseña", "análisis", "análise", "обзор", "inceleme", "评测", "测评", "点评"),
            "unboxing" to listOf("unboxing", "déballage", "распаковка", "开箱"),
            "reaction" to listOf("reaction", "reactions", "reacting", "reacción", "reação", "réaction", "реакция", "tepki"),
            "tutorial" to listOf("tutorial", "tutorials", "tuto", "tutoriel", "урок", "教程", "教学"),
            "explainer" to listOf("explained", "explainer", "解说", "讲解"),
            "vlog" to listOf("vlog", "vlogs"),
            "podcast" to listOf("podcast", "podcasts", "подкаст", "播客"),
            "live" to listOf("live", "livestream", "直播"),
            "compilation" to listOf("compilation", "compilación", "compilação", "подборка", "合集", "混剪"),
            "playalong" to listOf("playalong"),
            "cover" to listOf("cover", "covers", "кавер", "翻唱"),
            "remix" to listOf("remix", "remixes", "ремикс", "混音"),
            "gameplay" to listOf("gameplay", "геймплей", "实况"),
            "walkthrough" to listOf("walkthrough", "playthrough", "прохождение", "攻略", "通关"),
            "highlights" to listOf("highlights", "resumen", "хайлайты", "集锦"),
            "trailer" to listOf("trailer", "teaser", "tráiler", "трейлер", "fragman", "预告", "预告片"),
            "interview" to listOf("interview", "interviews", "entrevista", "интервью", "röportaj", "采访", "访谈", "专访"),
            "documentary" to listOf("documentary", "documental", "documentário", "documentaire", "belgesel", "纪录片"),
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
