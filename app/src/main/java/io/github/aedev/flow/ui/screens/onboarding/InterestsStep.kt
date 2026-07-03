package io.github.aedev.flow.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.recommendation.NeuroTopicCatalog
import io.github.aedev.flow.data.recommendation.TopicCategory
import io.github.aedev.flow.ui.components.topicCategoryIcon
import kotlinx.coroutines.delay

@Composable
internal fun InterestsStep(
    selectedTopics: Set<String>,
    onTopicToggle: (String) -> Unit
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
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        item {
            StepHeader(
                title = stringResource(R.string.onboarding_interests_title),
                subtitle = if (remaining > 0)
                    stringResource(R.string.onboarding_interests_hint, MIN_TOPICS, remaining)
                else
                    stringResource(R.string.onboarding_interests_ready)
            )
        }

        itemsIndexed(categories, key = { _, category -> category.name }) { index, category ->
            AnimatedVisibility(
                visible = index < visibleSections,
                enter = fadeIn(tween(280)) + slideInVertically(tween(320)) { it / 6 },
                modifier = Modifier.animateItem()
            ) {
                InterestCategorySection(
                    category = category,
                    selectedTopics = selectedTopics,
                    onTopicToggle = onTopicToggle
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestCategorySection(
    category: TopicCategory,
    selectedTopics: Set<String>,
    onTopicToggle: (String) -> Unit
) {
    val selectedCount = category.topics.count(selectedTopics::contains)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = topicCategoryIcon(category.icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(getCategoryNameResId(category.name)).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selectedCount > 0) {
                Text(
                    text = selectedCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            category.topics.forEach { topic ->
                TopicPill(
                    label = topic,
                    selected = selectedTopics.contains(topic),
                    onClick = { onTopicToggle(topic) }
                )
            }
        }
    }
}

@Composable
private fun TopicPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else null,
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

private fun getCategoryNameResId(categoryName: String): Int = when {
    categoryName.contains("Gaming") -> R.string.category_gaming
    categoryName.contains("Music") -> R.string.category_music
    categoryName.contains("Technology") -> R.string.category_technology
    categoryName.contains("Entertainment") -> R.string.category_entertainment
    categoryName.contains("Education") -> R.string.category_education
    categoryName.contains("Health & Fitness") -> R.string.category_health_fitness
    categoryName.contains("Lifestyle") -> R.string.category_lifestyle
    categoryName.contains("Creative") -> R.string.category_creative
    categoryName.contains("Science & Nature") -> R.string.category_science_nature
    else -> R.string.category_news_current_events
}
