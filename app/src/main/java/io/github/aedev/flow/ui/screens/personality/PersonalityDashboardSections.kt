package io.github.aedev.flow.ui.screens.personality

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.recommendation.FlowPersona
import io.github.aedev.flow.data.recommendation.UserBrain

@Composable
internal fun PersonalityOverviewSection(
    brain: UserBrain,
    persona: FlowPersona?
) {
    val displayPersona = persona ?: FlowPersona.INITIATE

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Text(
                text = displayPersona.icon,
                fontSize = 100.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .alpha(0.18f)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayPersona.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = displayPersona.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                MetricBar(
                    label = "Profile maturity",
                    value = brain.profileMaturity(),
                    detail = "Higher maturity means the profile has enough signals to be stable"
                )
            }
        }
    }
}

@Composable
internal fun LearningStatsSection(brain: UserBrain) {
    DashboardSection(
        title = stringResource(R.string.learning_stats_title),
        subtitle = stringResource(R.string.learning_stats_subtitle),
        icon = Icons.Outlined.TrackChanges
    ) {
        MetricGrid(
            metrics = listOf(
                DashboardMetric(
                    label = stringResource(R.string.metric_interactions),
                    value = compactCount(brain.totalInteractions),
                    detail = stringResource(R.string.metric_interactions_detail),
                    icon = Icons.Outlined.TouchApp
                ),
                DashboardMetric(
                    label = stringResource(R.string.metric_topics),
                    value = compactCount(brain.globalVector.topics.size),
                    detail = stringResource(R.string.metric_topics_detail),
                    icon = Icons.Outlined.Category
                ),
                DashboardMetric(
                    label = stringResource(R.string.metric_evidence),
                    value = compactCount(brain.topicEvidence.size),
                    detail = stringResource(R.string.metric_evidence_detail),
                    icon = Icons.Outlined.TrackChanges
                ),
                DashboardMetric(
                    label = stringResource(R.string.metric_channels),
                    value = compactCount(brain.channelScores.size),
                    detail = stringResource(R.string.metric_channels_detail),
                    icon = Icons.Outlined.Subscriptions
                ),
                DashboardMetric(
                    label = stringResource(R.string.metric_history),
                    value = compactCount(brain.watchHistoryMap.size),
                    detail = stringResource(R.string.metric_history_detail),
                    icon = Icons.Outlined.VideoLibrary
                ),
                DashboardMetric(
                    label = stringResource(R.string.metric_feed_memory),
                    value = compactCount(brain.feedHistory.size),
                    detail = stringResource(R.string.metric_feed_memory_detail),
                    icon = Icons.Outlined.Equalizer
                ),
                DashboardMetric(
                    label = stringResource(R.string.metric_filters),
                    value = compactCount(brain.blockedFilterCount()),
                    detail = stringResource(R.string.metric_filters_detail),
                    icon = Icons.Outlined.Block
                ),
                DashboardMetric(
                    label = stringResource(R.string.metric_suppressed),
                    value = compactCount(brain.suppressedItemCount()),
                    detail = stringResource(R.string.metric_suppressed_detail),
                    icon = Icons.Outlined.Explore
                )
            )
        )
    }
}

@Composable
internal fun TasteShapeSection(brain: UserBrain) {
    val currentVector = brain.currentContextVector()

    DashboardSection(
        title = stringResource(R.string.taste_shape_title),
        subtitle = stringResource(R.string.taste_shape_subtitle),
        icon = Icons.Outlined.Equalizer
    ) {
        TasteRadarChart(
            globalVector = brain.globalVector,
            currentVector = currentVector,
            breadth = brain.breadthScore()
        )
        MetricBar(
            label = stringResource(R.string.taste_pacing),
            value = brain.globalVector.pacing,
            detail = stringResource(R.string.taste_pacing_context, currentVector.pacing.percentLabel())
        )
        MetricBar(
            label = stringResource(R.string.taste_complexity),
            value = brain.globalVector.complexity,
            detail = stringResource(R.string.taste_pacing_context, currentVector.complexity.percentLabel()),
            color = MaterialTheme.colorScheme.secondary
        )
        MetricBar(
            label = stringResource(R.string.taste_duration),
            value = brain.globalVector.duration,
            detail = stringResource(R.string.taste_duration_detail),
            color = MaterialTheme.colorScheme.tertiary
        )
        MetricBar(
            label = stringResource(R.string.taste_live_affinity),
            value = brain.globalVector.isLive,
            detail = stringResource(R.string.taste_live_affinity_detail)
        )
        MetricBar(
            label = stringResource(R.string.taste_topic_breadth),
            value = brain.breadthScore(),
            detail = stringResource(R.string.taste_topic_breadth_detail, brain.globalVector.topics.size),
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
internal fun InterestWeightsSection(brain: UserBrain) {
    val topics = remember(brain.globalVector.topics, brain.topicEvidence) {
        brain.topTopicInsights(limit = 12)
    }
    val maxScore = topics.maxOfOrNull { it.score }?.takeIf { it > 0.0 } ?: 1.0

    DashboardSection(
        title = stringResource(R.string.interest_weights_title),
        subtitle = stringResource(R.string.interest_weights_subtitle),
        icon = Icons.Outlined.Category
    ) {
        if (topics.isEmpty()) {
            EmptyPanelMessage(stringResource(R.string.interest_weights_empty))
        } else {
            TopicDistributionStrip(topics = topics)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            topics.forEachIndexed { index, topic ->
                CompactProgressRow(
                    title = topic.name.readableTopic(),
                    subtitle = topic.evidenceLabel(scoreLabel = stringResource(R.string.topic_score_prefix)),
                    value = topic.score / maxScore,
                    color = when {
                        index < 3 -> MaterialTheme.colorScheme.primary
                        index < 7 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                )
            }
        }
    }
}

@Composable
internal fun TimePatternsSection(brain: UserBrain) {
    val buckets = remember(brain.timeVectors) { brain.timeBucketInsights() }

    DashboardSection(
        title = stringResource(R.string.time_patterns_title),
        subtitle = stringResource(R.string.time_patterns_subtitle),
        icon = Icons.Outlined.Schedule
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(
                items = buckets,
                key = { it.bucket.name }
            ) { bucket ->
                TimeBucketCard(bucket = bucket)
            }
        }
    }
}

@Composable
private fun TimeBucketCard(bucket: TimeBucketInsight) {
    val topics = bucket.vector.topTopicLabels(limit = 4)

    Surface(
        modifier = Modifier
            .width(252.dp)
            .height(282.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (bucket.isCurrent) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
        }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = bucket.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (bucket.isCurrent) {
                    StatusChip(label = stringResource(R.string.time_bucket_label_now), color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                text = bucket.window,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeBucketStat(
                    label = stringResource(R.string.time_bucket_topics_count),
                    value = compactCount(bucket.vector.topics.size),
                    modifier = Modifier.weight(1f)
                )
                TimeBucketStat(
                    label = stringResource(R.string.time_bucket_pace),
                    value = bucket.vector.pacing.percentLabel(),
                    modifier = Modifier.weight(1f)
                )
            }

            SmallMetricLine(label = stringResource(R.string.time_bucket_complexity), value = bucket.vector.complexity)
            SmallMetricLine(label = stringResource(R.string.time_bucket_duration), value = bucket.vector.duration)

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(R.string.time_bucket_top_topics),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (topics.isEmpty()) {
                    Text(
                        text = stringResource(R.string.time_bucket_no_topics),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    topics.forEach { topic ->
                        Text(
                            text = topic,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeBucketStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SmallMetricLine(
    label: String,
    value: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(68.dp),
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value.coerceIn(0.0, 1.0).toFloat())
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            text = value.percentLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
    }
}

@Composable
internal fun ChannelMemorySection(
    brain: UserBrain,
    channelNames: Map<String, String>
) {
    val channels = remember(brain.channelScores, channelNames) {
        brain.channelScores.entries
            .sortedByDescending { it.value }
            .take(10)
    }

    DashboardSection(
        title = stringResource(R.string.channel_memory),
        subtitle = stringResource(R.string.channel_memory_subtitle),
        icon = Icons.Outlined.Subscriptions
    ) {
        if (channels.isEmpty()) {
            EmptyPanelMessage(stringResource(R.string.channel_memory_empty))
        } else {
            channels.forEachIndexed { index, (channelId, score) ->
                ChannelRow(
                    name = channelNames[channelId] ?: shortChannelId(channelId),
                    score = score
                )
                if (index < channels.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    name: String,
    score: Double
) {
    val (label, color) = when {
        score >= 0.68 -> stringResource(R.string.channel_affinity_strong) to MaterialTheme.colorScheme.primary
        score >= 0.48 -> stringResource(R.string.channel_affinity_balanced) to MaterialTheme.colorScheme.secondary
        else -> stringResource(R.string.channel_affinity_cooling) to MaterialTheme.colorScheme.tertiary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            CompactProgressRow(
                title = score.percentLabel(),
                subtitle = stringResource(R.string.channel_affinity_score),
                value = score,
                color = color
            )
        }
        StatusChip(label = label, color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiscoveryStatusSection(
    brain: UserBrain,
    queries: List<String>
) {
    DashboardSection(
        title = stringResource(R.string.discovery_status_title),
        subtitle = stringResource(R.string.discovery_status_subtitle),
        icon = Icons.Outlined.Code
    ) {
        MetricGrid(
            metrics = listOf(
                DashboardMetric(
                    label = stringResource(R.string.discovery_queries),
                    value = compactCount(queries.size),
                    detail = stringResource(R.string.discovery_queries_detail),
                    icon = Icons.Outlined.Code
                ),
                DashboardMetric(
                    label = stringResource(R.string.discovery_query_memory),
                    value = compactCount(brain.recentQueryTokens.size),
                    detail = stringResource(R.string.discovery_query_memory_detail),
                    icon = Icons.Outlined.TrackChanges
                ),
                DashboardMetric(
                    label = stringResource(R.string.discovery_rejections),
                    value = compactCount(brain.rejectionPatterns.size),
                    detail = stringResource(R.string.discovery_rejections_detail),
                    icon = Icons.Outlined.Block
                ),
                DashboardMetric(
                    label = stringResource(R.string.discovery_shorts_seen),
                    value = compactCount(brain.seenShortsHistory.size),
                    detail = stringResource(R.string.discovery_shorts_seen_detail),
                    icon = Icons.Outlined.VideoLibrary
                )
            )
        )

        if (queries.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                queries.take(10).forEach { query ->
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = query,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlockedContentSection(
    brain: UserBrain,
    channelNames: Map<String, String>,
    onUnblockTopic: (String) -> Unit,
    onUnblockChannel: (String) -> Unit
) {
    val visibleTopics = remember(brain.blockedTopics) { brain.blockedTopics.sorted() }
    val visibleChannels = remember(brain.blockedChannels) { brain.blockedChannels.sorted() }

    DashboardSection(
        title = stringResource(R.string.blocked_content_title),
        subtitle = stringResource(R.string.blocked_content_subtitle),
        icon = Icons.Outlined.Block
    ) {
        if (brain.blockedTopics.isNotEmpty()) {
            FilterHeader(
                title = stringResource(R.string.blocked_topics_header),
                visibleCount = visibleTopics.size,
                totalCount = brain.blockedTopics.size
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 230.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = visibleTopics,
                    key = { it }
                ) { topic ->
                    FilterItemRow(
                        title = topic.readableTopic(),
                        icon = Icons.Outlined.Block,
                        onRemove = { onUnblockTopic(topic) },
                        removeDescription = stringResource(R.string.blocked_item_unblock_topic)
                    )
                }
            }
        }

        if (brain.blockedChannels.isNotEmpty()) {
            FilterHeader(
                title = stringResource(R.string.blocked_channels_header),
                visibleCount = visibleChannels.size,
                totalCount = brain.blockedChannels.size
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 230.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = visibleChannels,
                    key = { it }
                ) { channelId ->
                    FilterItemRow(
                        title = channelNames[channelId] ?: shortChannelId(channelId),
                        icon = Icons.Outlined.Subscriptions,
                        onRemove = { onUnblockChannel(channelId) },
                        removeDescription = stringResource(R.string.blocked_item_unblock_channel)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterHeader(
    title: String,
    visibleCount: Int,
    totalCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        if (visibleCount < totalCount) {
            Text(
                text = stringResource(R.string.blocked_item_showing_count, visibleCount, totalCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FilterItemRow(
    title: String,
    icon: ImageVector,
    onRemove: () -> Unit,
    removeDescription: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = removeDescription,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
internal fun ProfileDataSection(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit
) {
    DashboardSection(
        title = stringResource(R.string.profile_data_title),
        subtitle = stringResource(R.string.profile_data_subtitle),
        icon = Icons.Outlined.VideoLibrary
    ) {
        ProfileActionRow(
            title = stringResource(R.string.profile_action_export),
            subtitle = stringResource(R.string.profile_action_export_detail),
            icon = Icons.Default.FileDownload,
            onClick = onExport
        )
        ProfileActionRow(
            title = stringResource(R.string.profile_action_import),
            subtitle = stringResource(R.string.profile_action_import_detail),
            icon = Icons.Default.FileUpload,
            onClick = onImport
        )
        ProfileActionRow(
            title = stringResource(R.string.profile_action_reset),
            subtitle = stringResource(R.string.profile_action_reset_detail),
            icon = Icons.Default.DeleteForever,
            isDestructive = true,
            onClick = onReset
        )
    }
}

@Composable
private fun ProfileActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = tint.copy(alpha = 0.12f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.padding(10.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ResetProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.reset_profile_dialog_title)) },
        text = {
            Text(stringResource(R.string.reset_profile_dialog_message))
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.reset_profile_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun TopicInsight.evidenceLabel(scoreLabel: String): String {
    val evidence = evidence ?: return "$scoreLabel ${score.topicWeightLabel()}"
    val signalCount = evidence.positiveSignals + evidence.watchSignals + evidence.explicitSignals
    return "$signalCount signals from ${evidence.videoIds.size} videos - ${score.topicWeightLabel()}"
}
