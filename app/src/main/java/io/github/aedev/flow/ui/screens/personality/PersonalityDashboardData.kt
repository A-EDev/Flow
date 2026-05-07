package io.github.aedev.flow.ui.screens.personality

import io.github.aedev.flow.data.recommendation.ContentVector
import io.github.aedev.flow.data.recommendation.TimeBucket
import io.github.aedev.flow.data.recommendation.TopicEvidence
import io.github.aedev.flow.data.recommendation.UserBrain
import java.util.Locale
import kotlin.math.roundToInt

internal data class TopicInsight(
    val name: String,
    val score: Double,
    val evidence: TopicEvidence?
)

internal data class TimeBucketInsight(
    val bucket: TimeBucket,
    val title: String,
    val window: String,
    val vector: ContentVector,
    val isCurrent: Boolean
)

internal fun UserBrain.topTopicInsights(limit: Int): List<TopicInsight> =
    globalVector.topics.entries
        .filter { it.value > 0.0 }
        .sortedByDescending { it.value }
        .take(limit)
        .map { (topic, score) ->
            TopicInsight(
                name = topic,
                score = score,
                evidence = topicEvidence[topic]
            )
        }

internal fun UserBrain.currentContextVector(): ContentVector =
    timeVectors[TimeBucket.current()] ?: ContentVector()

internal fun UserBrain.breadthScore(): Double =
    (globalVector.topics.size / 50.0).coerceIn(0.0, 1.0)

internal fun UserBrain.profileMaturity(): Double =
    (totalInteractions / 250.0).coerceIn(0.0, 1.0)

internal fun UserBrain.blockedFilterCount(): Int =
    blockedTopics.size + blockedChannels.size

internal fun UserBrain.suppressedItemCount(): Int =
    suppressedVideoIds.size + suppressedChannels.size

internal fun UserBrain.timeBucketInsights(): List<TimeBucketInsight> {
    val current = TimeBucket.current()
    return TimeBucket.entries.map { bucket ->
        TimeBucketInsight(
            bucket = bucket,
            title = bucket.displayTitle(),
            window = bucket.displayWindow(),
            vector = timeVectors[bucket] ?: ContentVector(),
            isCurrent = bucket == current
        )
    }
}

internal fun TimeBucket.displayTitle(): String = when (this) {
    TimeBucket.WEEKDAY_MORNING -> "Weekday morning"
    TimeBucket.WEEKDAY_AFTERNOON -> "Weekday afternoon"
    TimeBucket.WEEKDAY_EVENING -> "Weekday evening"
    TimeBucket.WEEKDAY_NIGHT -> "Weekday night"
    TimeBucket.WEEKEND_MORNING -> "Weekend morning"
    TimeBucket.WEEKEND_AFTERNOON -> "Weekend afternoon"
    TimeBucket.WEEKEND_EVENING -> "Weekend evening"
    TimeBucket.WEEKEND_NIGHT -> "Weekend night"
}

internal fun TimeBucket.displayWindow(): String = when (this) {
    TimeBucket.WEEKDAY_MORNING,
    TimeBucket.WEEKEND_MORNING -> "6 AM - 12 PM"
    TimeBucket.WEEKDAY_AFTERNOON,
    TimeBucket.WEEKEND_AFTERNOON -> "12 PM - 6 PM"
    TimeBucket.WEEKDAY_EVENING,
    TimeBucket.WEEKEND_EVENING -> "6 PM - 12 AM"
    TimeBucket.WEEKDAY_NIGHT,
    TimeBucket.WEEKEND_NIGHT -> "12 AM - 6 AM"
}

internal fun ContentVector.topTopicLabels(limit: Int): List<String> =
    topics.entries
        .sortedByDescending { it.value }
        .take(limit)
        .map { it.key.readableTopic() }

internal fun String.readableTopic(): String =
    replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

internal fun Double.percentLabel(): String =
    "${(this.coerceIn(0.0, 1.0) * 100).roundToInt()}%"

internal fun Double.topicWeightLabel(): String {
    val percent = coerceAtLeast(0.0) * 100.0
    return when {
        percent == 0.0 -> "0%"
        percent < 0.1 -> "<0.1%"
        percent < 10.0 -> String.format(Locale.US, "%.1f%%", percent)
        else -> "${percent.roundToInt()}%"
    }
}

internal fun compactCount(value: Int): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
    else -> value.toString()
}

internal fun shortChannelId(channelId: String): String =
    if (channelId.length <= 18) channelId else "${channelId.take(18)}..."

internal fun Set<String>.limitedFilterItems(maxItems: Int): List<String> =
    if (size <= maxItems) {
        sorted()
    } else {
        asSequence()
            .take(maxItems)
            .toList()
            .sorted()
    }
