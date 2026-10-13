package io.github.aedev.flow.ui.screens.settings.taste

import io.github.aedev.flow.data.recommendation.NeuroDisplayForms
import io.github.aedev.flow.data.recommendation.NeuroScoring
import io.github.aedev.flow.data.recommendation.UserBrain

/**
 * The interests Your taste lists: the strongest unblocked topics, each relative to the strongest,
 * labelled as titles write them. [pickKeys] are the keys of the viewer's picks.
 */
internal fun tasteTopics(
    brain: UserBrain,
    count: Int,
    pickKeys: Set<String>,
): List<TasteTopic> {
    val strongest =
        brain.globalVector.topics.values
            .maxOrNull()
            ?.takeIf { it > 0.0 } ?: 1.0
    return brain.globalVector.topics.entries
        .filter { it.value > 0.0 && it.key !in brain.blockedTopics }
        .sortedByDescending { it.value }
        .take(count)
        .map { (key, value) ->
            TasteTopic(
                key,
                NeuroDisplayForms.label(brain.displayForms, key),
                (value / strongest).toFloat(),
                NeuroScoring.stripDomainTag(key) in pickKeys,
            )
        }
}
