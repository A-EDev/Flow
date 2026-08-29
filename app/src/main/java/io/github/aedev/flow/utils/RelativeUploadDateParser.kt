/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.utils

/**
 * Parses relative upload-date text ("3 weeks ago", "Streamed 2 days ago") into an
 * epoch-millis timestamp. Returns null when the text carries no parsable age —
 * callers must NOT substitute "now" for unknown, or every stale item reads as new.
 */
object RelativeUploadDateParser {
    fun parse(
        textualDate: String?,
        now: Long = System.currentTimeMillis(),
    ): Long? {
        val raw = textualDate?.trim().orEmpty()
        if (raw.isBlank()) return null

        val normalized =
            raw
                .lowercase()
                .replace("streamed", "")
                .replace("premiered", "")
                .replace("ago", "")
                .trim()

        if (normalized.contains("just now") || normalized.contains("today")) return now
        if (normalized.contains("yesterday")) return now - DAY_MS

        val value =
            Regex("(\\d+)")
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?: return null
        // Word units MUST be checked before compact suffixes: after stripping
        // "ago", "3 days" ends with "s" and would otherwise parse as seconds.
        // (This exact bug shipped in the original shorts-path parser.)
        val unitMillis =
            when {
                normalized.contains("second") -> 1_000L
                normalized.contains("minute") -> 60_000L
                normalized.contains("hour") -> 3_600_000L
                normalized.contains("day") -> DAY_MS
                normalized.contains("week") -> 7L * DAY_MS
                normalized.contains("month") -> 30L * DAY_MS
                normalized.contains("year") -> 365L * DAY_MS
                normalized.endsWith("mo") -> 30L * DAY_MS
                normalized.endsWith("s") -> 1_000L
                normalized.endsWith("m") -> 60_000L
                normalized.endsWith("h") -> 3_600_000L
                normalized.endsWith("d") -> DAY_MS
                normalized.endsWith("w") -> 7L * DAY_MS
                normalized.endsWith("y") -> 365L * DAY_MS
                else -> return null
            }

        return now - (value * unitMillis)
    }

    private const val DAY_MS = 86_400_000L
}
