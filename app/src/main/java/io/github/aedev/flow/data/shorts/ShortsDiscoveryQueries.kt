package io.github.aedev.flow.data.shorts

/**
 * The searches one discovery refresh runs, from the engine's learnt interests alone: its strongest
 * topics, its strongest topic pairs, and the queries it generates itself. Nothing is authored here,
 * and nothing is appended to a query: the Shorts filter on the search keeps the results to reels.
 */
internal fun discoveryQueriesFrom(
    topics: List<String>,
    topicPairs: List<String>,
    generated: List<String>,
    blocked: Collection<String>,
    limit: Int,
    order: (List<String>) -> List<String> = { it.shuffled() },
): List<String> =
    order(
        (topics + topicPairs + generated)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { query -> blocked.none { query.contains(it, ignoreCase = true) } },
    ).take(limit)
