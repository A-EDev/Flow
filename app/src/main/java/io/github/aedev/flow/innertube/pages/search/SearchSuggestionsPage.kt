package io.github.aedev.flow.innertube.pages.search

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * One typeahead row. [entity] is set on the few suggestions YouTube attaches a knowledge panel to —
 * a person, a film, a place — which the UI shows as a subtitle.
 */
data class SearchSuggestion(
    val text: String,
    val entity: SearchSuggestionEntity? = null,
)

data class SearchSuggestionEntity(
    val title: String,
    val description: String = "",
    val thumbnailUrl: String = "",
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * The suggest host answers with a bare JSON array — `[query, [[text, _, _, entity?], …]]` — and
 * historically wrapped it in a JSONP callback, so the payload is read from the first bracket.
 */
fun parseSearchSuggestions(body: String): List<SearchSuggestion> {
    val start = body.indexOf('[')
    val end = body.lastIndexOf(']')
    if (start < 0 || end <= start) return emptyList()

    val root = runCatching { json.parseToJsonElement(body.substring(start, end + 1)) }.getOrNull() as? JsonArray
    val rows = root?.getOrNull(1) as? JsonArray ?: return emptyList()

    return rows
        .mapNotNull { row ->
            when (row) {
                is JsonPrimitive -> {
                    row.contentOrNull?.let { SearchSuggestion(it) }
                }

                is JsonArray -> {
                    val text = (row.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    SearchSuggestion(text, row.firstNotNullOfOrNull { (it as? JsonObject)?.toEntity() })
                }

                else -> {
                    null
                }
            }
        }.distinctBy { it.text.lowercase() }
}

/** The knowledge-panel keys are two-letter codes: zao title, zaf description, zai thumbnail. */
private fun JsonObject.toEntity(): SearchSuggestionEntity? {
    val title = (this["zao"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) ?: return null
    return SearchSuggestionEntity(
        title = title,
        description = (this["zaf"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
        thumbnailUrl = (this["zai"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
    )
}
