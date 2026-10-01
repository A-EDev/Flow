/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import kotlin.math.ln

/**
 * Learns which word pairs are phrases from the viewer's own history, the gensim Phrases way:
 * a pair is a phrase once it has appeared often enough and its normalized pointwise mutual
 * information says its words belong together ("lofi hip", "guitar playalong"), rather than meeting
 * by chance ("review after"). The counts are the document frequencies the engine already keeps for
 * IDF, so this adds no state of its own.
 */
internal object NeuroPhrases {
    const val MIN_COUNT = 3
    const val MIN_NPMI = 0.5
    const val MIN_DOCUMENTS = 20

    /** NPMI of [bigram] in [idf], from -1 (never together) to 1 (only together); null when unknown. */
    fun npmi(
        bigram: String,
        idf: IdfSnapshot,
    ): Double? {
        val space = bigram.indexOf(' ')
        if (space < 0 || idf.totalDocs <= 0) return null
        val joint = idf.wordFrequency[bigram] ?: return null
        val n = idf.totalDocs.toDouble()
        // A word claimed by a known phrase is not counted alone, so it occurred at least as often as the pair.
        val first = maxOf(idf.wordFrequency[bigram.substring(0, space)] ?: 0, joint)
        val second = maxOf(idf.wordFrequency[bigram.substring(space + 1)] ?: 0, joint)
        val pJoint = (joint / n).coerceAtMost(1.0)
        if (pJoint >= 1.0) return 1.0
        return ln(pJoint / ((first / n) * (second / n))) / -ln(pJoint)
    }

    fun isPhrase(
        bigram: String,
        idf: IdfSnapshot,
    ): Boolean {
        if (idf.totalDocs < MIN_DOCUMENTS) return false
        if ((idf.wordFrequency[bigram] ?: 0) < MIN_COUNT) return false
        return (npmi(bigram, idf) ?: return false) >= MIN_NPMI
    }
}
