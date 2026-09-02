package io.github.aedev.flow.ui.screens.music

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.pages.MoodAndGenres
import io.github.aedev.flow.ui.components.MoodAndGenresButton
import io.github.aedev.flow.ui.components.ShimmerHost
import io.github.aedev.flow.ui.components.ShimmerMoodButton
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.music.common.MusicEmptyState
import io.github.aedev.flow.ui.components.music.common.MusicErrorState
import io.github.aedev.flow.ui.components.music.header.MusicSectionHeader
import io.github.aedev.flow.ui.components.music.section.MoodCategorySection

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

    val localConfiguration = LocalConfiguration.current
    val itemsPerRow = if (localConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2

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
                    MusicErrorState(
                        error = error ?: stringResource(R.string.unknown_error),
                        onRetry = { viewModel.retry() },
                    )
                }

                moodAndGenresList.isNullOrEmpty() -> {
                    MusicEmptyState(title = stringResource(R.string.empty_moods_genres))
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
