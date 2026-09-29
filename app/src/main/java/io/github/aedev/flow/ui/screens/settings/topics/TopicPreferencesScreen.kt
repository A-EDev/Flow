package io.github.aedev.flow.ui.screens.settings.topics

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.recommendation.NeuroTopicCatalog
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.SettingsTabs
import io.github.aedev.flow.ui.components.settings.notice
import io.github.aedev.flow.ui.components.shared.FlowConnectedToggleGroup
import io.github.aedev.flow.ui.components.shared.FlowPillChip
import io.github.aedev.flow.ui.components.shared.FlowPillSection
import io.github.aedev.flow.ui.components.shared.FlowToggleOption
import io.github.aedev.flow.ui.components.topicCategoryIcon
import io.github.aedev.flow.ui.components.topicCategoryNameRes
import io.github.aedev.flow.ui.screens.settings.index.TopicsIndex

private val TabsBottomPadding = 8.dp
private val SectionVerticalPadding = 12.dp

/**
 * The topics recommendations lean towards and the ones they avoid. Interests are the same pill
 * clouds as onboarding; blocked topics are typed or picked from suggestions.
 */
@Composable
internal fun TopicPreferencesScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    tab: String?,
    viewModel: TopicPreferencesViewModel = hiltViewModel(),
) {
    val interests by viewModel.interests.collectAsStateWithLifecycle()
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(tab ?: SettingsTabs.TOPICS_INTERESTS) }
    val suggestions = stringArrayResource(R.array.settings_block_suggestions).toList()
    val customInterests = interests.filterNot { it in viewModel.catalogueTopics }.sorted()

    SettingsPage(
        title = stringResource(R.string.settings_topics_title),
        onBack = onBack,
        highlight = highlight,
        header = {
            FlowConnectedToggleGroup(
                options =
                    listOf(
                        FlowToggleOption(
                            SettingsTabs.TOPICS_INTERESTS,
                            stringResource(R.string.settings_topics_tab_interests),
                            Icons.Outlined.Favorite,
                        ),
                        FlowToggleOption(
                            SettingsTabs.TOPICS_BLOCKED,
                            stringResource(R.string.settings_topics_tab_blocked),
                            Icons.Outlined.Block,
                        ),
                    ),
                selected = selectedTab,
                onSelected = { selectedTab = it },
                modifier = Modifier.padding(bottom = TabsBottomPadding),
            )
        },
    ) {
        if (selectedTab == SettingsTabs.TOPICS_BLOCKED) {
            notice("topics.blocked.notice", text = { stringResource(R.string.hidden_content_desc) }, icon = Icons.Outlined.Security)
            item(TopicsIndex.block.key) {
                TopicInput(
                    label = stringResource(R.string.block_topic_title),
                    placeholder = stringResource(R.string.block_topic_placeholder),
                    actionLabel = stringResource(R.string.create),
                    onSubmit = viewModel::block,
                )
            }
            val remaining = unblockedSuggestions(suggestions, blocked)
            if (remaining.isNotEmpty()) {
                item(TopicsIndex.quickAdd.key) {
                    TopicChipSection(
                        title = stringResource(R.string.quick_add),
                        modifier = Modifier.padding(vertical = SectionVerticalPadding),
                    ) {
                        TopicSuggestionChips(remaining, onClick = viewModel::block)
                    }
                }
            }
            if (blocked.isNotEmpty()) {
                item("topics.blocked.list") {
                    TopicChipSection(
                        title = stringResource(R.string.currently_blocked),
                        modifier = Modifier.padding(vertical = SectionVerticalPadding),
                    ) {
                        RemovableTopicChips(blocked.sorted(), R.string.desc_unblock_topic, onRemove = viewModel::unblock)
                    }
                }
            }
        } else {
            notice("topics.interests.notice", text = { stringResource(R.string.your_interests_desc) }, icon = Icons.Outlined.TipsAndUpdates)
            NeuroTopicCatalog.TOPIC_CATEGORIES.forEach { category ->
                item("topics.category.${category.name}") {
                    FlowPillSection(
                        title = stringResource(topicCategoryNameRes(category.name)),
                        icon = topicCategoryIcon(category.icon),
                        selectedCount = category.topics.count(interests::contains),
                        modifier = Modifier.padding(vertical = SectionVerticalPadding),
                    ) {
                        category.topics.forEach { topic ->
                            FlowPillChip(label = topic, selected = topic in interests, onClick = { viewModel.toggleInterest(topic) })
                        }
                    }
                }
            }
            if (customInterests.isNotEmpty()) {
                item("topics.custom") {
                    TopicChipSection(
                        title = stringResource(R.string.settings_topics_your_topics),
                        modifier = Modifier.padding(vertical = SectionVerticalPadding),
                    ) {
                        RemovableTopicChips(customInterests, R.string.desc_remove_topic, onRemove = viewModel::removeInterest)
                    }
                }
            }
            item(TopicsIndex.addInterest.key) {
                TopicInput(
                    label = stringResource(R.string.ui_custom_interest_title),
                    placeholder = stringResource(R.string.ui_custom_interest_hint),
                    actionLabel = stringResource(R.string.ui_add_interest),
                    onSubmit = viewModel::addInterest,
                )
            }
        }
    }
}
