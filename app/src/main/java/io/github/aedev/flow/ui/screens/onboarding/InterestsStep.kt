package io.github.aedev.flow.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.recommendation.NeuroTopicCatalog
import io.github.aedev.flow.data.recommendation.TopicCategory
import io.github.aedev.flow.ui.components.shared.FlowPillChip
import io.github.aedev.flow.ui.components.shared.FlowPillSection
import io.github.aedev.flow.ui.components.topicCategoryIcon
import io.github.aedev.flow.ui.components.topicCategoryNameRes
import kotlinx.coroutines.delay

@Composable
internal fun InterestsStep(
    selectedTopics: Set<String>,
    onTopicToggle: (String) -> Unit,
) {
    val categories = NeuroTopicCatalog.TOPIC_CATEGORIES
    var visibleSections by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        for (i in 1..categories.size) {
            delay(STAGGER_DELAY_MS)
            visibleSections = i
        }
    }

    val remaining = (MIN_TOPICS - selectedTopics.size).coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            StepHeader(
                title = stringResource(R.string.onboarding_interests_title),
                subtitle =
                    if (remaining > 0) {
                        stringResource(R.string.onboarding_interests_hint, MIN_TOPICS, remaining)
                    } else {
                        stringResource(R.string.onboarding_interests_ready)
                    },
            )
        }

        itemsIndexed(categories, key = { _, category -> category.name }) { index, category ->
            AnimatedVisibility(
                visible = index < visibleSections,
                enter = fadeIn(tween(280)) + slideInVertically(tween(320)) { it / 6 },
                modifier = Modifier.animateItem(),
            ) {
                InterestCategorySection(
                    category = category,
                    selectedTopics = selectedTopics,
                    onTopicToggle = onTopicToggle,
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun InterestCategorySection(
    category: TopicCategory,
    selectedTopics: Set<String>,
    onTopicToggle: (String) -> Unit,
) {
    FlowPillSection(
        title = stringResource(topicCategoryNameRes(category.name)),
        icon = topicCategoryIcon(category.icon),
        selectedCount = category.topics.count(selectedTopics::contains),
    ) {
        category.topics.forEach { topic ->
            FlowPillChip(
                label = topic,
                selected = selectedTopics.contains(topic),
                onClick = { onTopicToggle(topic) },
            )
        }
    }
}
