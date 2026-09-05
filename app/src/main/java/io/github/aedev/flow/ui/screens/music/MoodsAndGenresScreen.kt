package io.github.aedev.flow.ui.screens.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.pages.MoodAndGenres
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.music.section.MoodCategorySection
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowErrorState
import io.github.aedev.flow.ui.components.shared.ShimmerHost
import io.github.aedev.flow.ui.components.shared.ShimmerMoodButton
import io.github.aedev.flow.ui.components.shared.flowGridColumns

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodsAndGenresScreen(
    onBackClick: () -> Unit,
    onGenreClick: (MoodAndGenres.Item) -> Unit,
    viewModel: MoodsAndGenresViewModel = hiltViewModel(),
) {
    val moodAndGenresList by viewModel.moodAndGenres.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val itemsPerRow = flowGridColumns(compact = 2, medium = 3, expanded = 4)

    Scaffold(
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.section_moods_and_genres),
                onBack = onBackClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            when {
                isLoading && moodAndGenresList == null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        item(key = "shimmer_loading") {
                            ShimmerHost(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                            ) {
                                repeat(8) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        repeat(itemsPerRow) {
                                            ShimmerMoodButton(
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                error != null && moodAndGenresList == null -> {
                    FlowErrorState(
                        error = error ?: stringResource(R.string.unknown_error),
                        onRetry = { viewModel.retry() },
                    )
                }

                moodAndGenresList.isNullOrEmpty() -> {
                    FlowEmptyState(title = stringResource(R.string.empty_moods_genres))
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        moodAndGenresList?.forEachIndexed { index, moodCategory ->
                            item(key = "category_$index") {
                                MoodCategorySection(
                                    title = moodCategory.title,
                                    items = moodCategory.items,
                                    itemsPerRow = itemsPerRow,
                                    onMoodClick = onGenreClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
