package io.github.aedev.flow.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.recommendation.NeuroTopicCatalog
import io.github.aedev.flow.data.recommendation.TopicCategory
import io.github.aedev.flow.ui.components.shared.FlowPillChip
import io.github.aedev.flow.ui.components.shared.FlowPillSection
import io.github.aedev.flow.ui.components.topicCategoryIcon
import io.github.aedev.flow.ui.components.topicCategoryNameRes
import kotlinx.coroutines.delay

private val SectionSpacing = 28.dp

/**
 * The interests cloud. Sections stagger in on the first visit only; coming back to the step shows
 * them at once, since [revealed] survives in the screen.
 */
@Composable
internal fun InterestsStep(
    selectedTopics: Set<String>,
    onTopicToggle: (String) -> Unit,
    header: LazyListScope.() -> Unit,
    revealed: Boolean,
    onRevealed: () -> Unit,
    contentPadding: PaddingValues,
) {
    val categories = NeuroTopicCatalog.TOPIC_CATEGORIES
    var visibleSections by remember { mutableIntStateOf(if (revealed) categories.size else 0) }
    LaunchedEffect(Unit) {
        if (revealed) return@LaunchedEffect
        for (i in 1..categories.size) {
            delay(STAGGER_DELAY_MS)
            visibleSections = i
        }
        onRevealed()
    }

    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        header()

        itemsIndexed(categories, key = { _, category -> category.name }) { index, category ->
            AnimatedVisibility(
                visible = index < visibleSections,
                enter = fadeIn(effects) + slideInVertically(spatial) { it / 6 },
                modifier = Modifier.animateItem(),
            ) {
                InterestCategorySection(
                    category = category,
                    selectedTopics = selectedTopics,
                    onTopicToggle = onTopicToggle,
                )
            }
        }
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
