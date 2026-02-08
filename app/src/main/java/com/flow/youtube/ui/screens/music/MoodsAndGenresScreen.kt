package com.flow.youtube.ui.screens.music

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.stringResource
import com.flow.youtube.R
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.youtube.innertube.pages.MoodAndGenres
import com.flow.youtube.ui.components.MoodAndGenresButton
import com.flow.youtube.ui.components.NavigationTitle
import com.flow.youtube.ui.components.ShimmerHost
import com.flow.youtube.ui.components.ShimmerMoodButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodsAndGenresScreen(
    onBackClick: () -> Unit,
    onGenreClick: (MoodAndGenres.Item) -> Unit,
    viewModel: MoodsAndGenresViewModel = hiltViewModel()
) {
    val moodAndGenresList by viewModel.moodAndGenres.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    val localConfiguration = LocalConfiguration.current
    val itemsPerRow = if (localConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.section_moods_and_genres), 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack, 
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading && moodAndGenresList == null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        item(key = "shimmer_loading") {
                            ShimmerHost(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                            ) {
                                repeat(8) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        repeat(itemsPerRow) {
                                            ShimmerMoodButton(
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                error != null && moodAndGenresList == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = error ?: stringResource(R.string.unknown_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retry() }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
                
                moodAndGenresList.isNullOrEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.empty_moods_genres),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        moodAndGenresList?.forEachIndexed { index, moodCategory ->
                            item(key = "category_$index") {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    NavigationTitle(
                                        title = moodCategory.title
                                    )
                                    
                                    moodCategory.items.chunked(itemsPerRow).forEach { row ->
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            row.forEach { item ->
                                                MoodAndGenresButton(
                                                    title = item.title,
                                                    onClick = { onGenreClick(item) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            
                                            repeat(itemsPerRow - row.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
