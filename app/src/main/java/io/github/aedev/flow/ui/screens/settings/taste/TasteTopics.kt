package io.github.aedev.flow.ui.screens.settings.taste

import io.github.aedev.flow.data.recommendation.NeuroDisplayForms
import io.github.aedev.flow.data.recommendation.NeuroScoring
import io.github.aedev.flow.data.recommendation.UserBrain

private const val TASTE_MIN_SPECIFICITY = 0.3

/** How often the viewer watched, liked or searched [key]: the evidence behind a topic. */
internal fun evidenceOf(
    brain: UserBrain,
    key: String,
): Int = brain.topicEvidence[key]?.let { it.watchSignals + it.explicitSignals } ?: 0

/**
 * The interests Your taste lists as learned: the strongest unblocked topics that are not the
 * viewer's picks, each relative to the strongest of them, labelled as titles write them. Picks sit
 * apart ([tastePicks]): drawn beside learned topics they made every bar look full (#907).
 */
internal fun tasteTopics(
    brain: UserBrain,
    count: Int,
    pickKeys: Set<String>,
    specificity: (String) -> Double = { 1.0 },
): List<TasteTopic> {
    // A word every subject uses ("buy", "year") is no interest to show, however often titles had it (#907).
    val learned =
        brain.interests.topics.entries
            .filter { it.value > 0.0 && it.key !in brain.blockedTopics && NeuroScoring.stripDomainTag(it.key) !in pickKeys }
            .filter { specificity(it.key) >= TASTE_MIN_SPECIFICITY }
            .sortedByDescending { it.value }
            .take(count)
    val strongest = learned.firstOrNull()?.value?.takeIf { it > 0.0 } ?: 1.0
    return learned.map { (key, value) ->
        TasteTopic(
            key,
            NeuroDisplayForms.label(brain.displayForms, key),
            (value / strongest).toFloat(),
            preferred = false,
            evidence = evidenceOf(brain, key),
        )
    }
}

/** The viewer's picks as they chose them, the ones they watch most first. [picks] maps each to its keys. */
internal fun tastePicks(
    brain: UserBrain,
    picks: Map<String, List<String>>,
): List<TasteTopic> =
    picks
        .map { (label, keys) ->
            val shown = keys.singleOrNull()?.let { NeuroDisplayForms.label(brain.displayForms, it) } ?: label
            TasteTopic(label, shown, share = null, preferred = true, evidence = keys.maxOfOrNull { evidenceOf(brain, it) } ?: 0)
        }.sortedWith(compareByDescending<TasteTopic> { it.evidence }.thenBy { it.label })
