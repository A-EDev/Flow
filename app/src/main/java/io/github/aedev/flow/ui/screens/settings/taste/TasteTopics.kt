package io.github.aedev.flow.ui.screens.settings.taste

import io.github.aedev.flow.data.recommendation.NeuroClusters
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

private const val GROUP_LABEL_WORDS = 3

// A group is named by words of one subject; a commoner word still counts among its members.
private const val GROUP_NAME_MIN_SPECIFICITY = 0.5

/**
 * The learned interests in the groups the engine reads them in, strongest group first, each named
 * by its three strongest words: "Pixel · Android · Camera" says more than "Pixel" (#907). Picks and
 * words every subject uses stay out, as in [tasteTopics].
 */
internal fun tasteGroups(
    brain: UserBrain,
    clusters: List<NeuroClusters.TopicCluster>,
    pickKeys: Set<String>,
    specificity: (String) -> Double,
    count: Int,
): List<TasteGroup> {
    val weights = brain.interests.topics
    val groups =
        clusters
            .mapNotNull { cluster ->
                val members =
                    cluster.topics
                        .filter { key ->
                            (weights[key] ?: 0.0) > 0.0 &&
                                key !in brain.blockedTopics &&
                                NeuroScoring.stripDomainTag(key) !in pickKeys &&
                                specificity(key) >= TASTE_MIN_SPECIFICITY
                        }.sortedByDescending { weights.getValue(it) }
                if (members.isEmpty()) return@mapNotNull null
                cluster to members
            }.sortedByDescending { (cluster, _) -> cluster.mass }
            .take(count)
    val strongest =
        groups
            .firstOrNull()
            ?.first
            ?.mass
            ?.takeIf { it > 0.0 } ?: 1.0
    return groups.map { (cluster, members) ->
        val topics =
            members.map { key ->
                TasteTopic(
                    key,
                    NeuroDisplayForms.label(brain.displayForms, key),
                    null,
                    preferred = false,
                    evidence = evidenceOf(brain, key),
                )
            }
        TasteGroup(
            id = cluster.representative,
            label =
                topics
                    .filter { specificity(it.id) >= GROUP_NAME_MIN_SPECIFICITY }
                    .ifEmpty { topics }
                    .take(GROUP_LABEL_WORDS)
                    .joinToString(" · ") { it.label },
            share = (cluster.mass / strongest).toFloat(),
            evidence = topics.maxOf { it.evidence },
            members = topics,
        )
    }
}
