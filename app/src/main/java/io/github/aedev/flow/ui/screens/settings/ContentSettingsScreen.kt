package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar

@Composable
fun ContentSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences = remember { PlayerPreferences(context) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.content_settings_title),
                onBack = onBackClick,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ContentDisplaySection(preferences = preferences, coroutineScope = coroutineScope)
            }

            item {
                ContentDownloadMenuSection(preferences = preferences, coroutineScope = coroutineScope)
            }

            item {
                ContentHomeLayoutSection(preferences = preferences, coroutineScope = coroutineScope)
            }

            item {
                ContentNotesSection(preferences = preferences, coroutineScope = coroutineScope)
            }

            item {
                ContentHomeFeedSection(preferences = preferences, coroutineScope = coroutineScope)
            }

            item {
                ContentShortsSection(preferences = preferences, coroutineScope = coroutineScope)
            }

            item {
                MusicRecommendationsSection(preferences = preferences, coroutineScope = coroutineScope)
            }

            item {
                ContentLibrarySection(preferences = preferences, coroutineScope = coroutineScope)
            }

            item {
                ContentComponentsSection(preferences = preferences, coroutineScope = coroutineScope)
            }

            item {
                ContentNavigationSection(preferences = preferences, coroutineScope = coroutineScope)
            }

            item {
                ContentPlayerSection(preferences = preferences, coroutineScope = coroutineScope)
            }

            item {
                ContentTitleLinesSection(preferences = preferences, coroutineScope = coroutineScope)
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
