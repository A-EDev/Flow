package io.github.aedev.flow.ui.screens.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.stats.RecapPeriod
import io.github.aedev.flow.data.stats.RecapSummary
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.shared.FlowConnectedToggleGroup
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowFilterChip
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.FlowToggleOption
import java.time.format.TextStyle
import java.util.Locale

private val CardMinWidth = 360.dp
private val GridPadding = 16.dp
private val GridSpacing = 12.dp
private val ControlSpacing = 12.dp
private val LoadingHeight = 320.dp

private enum class RecapScope { MONTH, YEAR, ALL }

/**
 * Everything the recap ledgers hold for one period: always available, any month, any year. Cards
 * flow into as many columns as the width allows; every chart draws once and then holds still.
 */
@Composable
internal fun RecapScreen(
    onBack: () -> Unit,
    onPlayStory: (RecapPeriod) -> Unit,
    startAt: RecapPeriod?,
    viewModel: RecapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    LaunchedEffect(startAt, state.months) {
        if (startAt != null && state.months.isNotEmpty()) viewModel.openAt(startAt)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FlowTopBar(title = stringResource(R.string.recap_title), onBack = onBack) },
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(CardMinWidth),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(GridPadding),
            verticalItemSpacing = GridSpacing,
            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        ) {
            when {
                state.loading -> {
                    item(key = "loading", span = StaggeredGridItemSpan.FullLine) {
                        FlowLoadingIndicator(Modifier.fillMaxWidth().height(LoadingHeight))
                    }
                }

                state.isEmpty -> {
                    item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                        FlowEmptyState(
                            title = stringResource(R.string.recap_empty_title),
                            subtitle = stringResource(R.string.recap_empty_body),
                            icon = Icons.Outlined.Insights,
                        )
                    }
                }

                else -> {
                    item(key = "controls", span = StaggeredGridItemSpan.FullLine) {
                        RecapControls(state, locale, viewModel::selectPeriod, viewModel::selectSource)
                    }
                    state.summary?.let { summary -> recapCards(state, summary, locale, onPlayStory) }
                }
            }
        }
    }
}

private fun LazyStaggeredGridScope.recapCards(
    state: RecapUiState,
    summary: RecapSummary,
    locale: Locale,
    onPlayStory: (RecapPeriod) -> Unit,
) {
    if (summary.isEmpty) {
        item(key = "period-empty", span = StaggeredGridItemSpan.FullLine) {
            FlowEmptyState(title = stringResource(R.string.recap_period_empty), icon = Icons.Outlined.Insights)
        }
        return
    }
    val source = state.source
    val period = summary.period
    val showVideo = source != RecapSource.MUSIC && !summary.video.isEmpty
    val showMusic = source != RecapSource.VIDEO && !summary.music.isEmpty
    val activity =
        when (source) {
            RecapSource.ALL -> summary.combined
            RecapSource.VIDEO -> summary.video.activity
            RecapSource.MUSIC -> summary.music.activity
        }
    val canPlay = period != RecapPeriod.AllTime

    item(key = "overview", span = StaggeredGridItemSpan.FullLine) {
        OverviewCard(summary, activity, source, onPlayStory = if (canPlay) ({ onPlayStory(period) }) else null)
    }
    if (summary.insights.isNotEmpty()) item(key = "insights") { InsightsCard(summary.insights) }
    item(key = "time") { TimeCard(period, activity, locale) }
    item(key = "clock") { ClockCard(activity, key = period to source) }
    if (period is RecapPeriod.Month) item(key = "calendar") { CalendarCard(period.month, activity, locale) }
    item(key = "weekdays") { WeekdayCard(activity, key = period to source, locale = locale) }
    if (showVideo) {
        val video = summary.video
        if (video.topChannels.isNotEmpty()) {
            item(
                key = "channels",
            ) { RankCard(stringResource(R.string.recap_top_channels), video.topChannels, { viewsLabel(it) }, portraits = true) }
        }
        if (video.topVideos.isNotEmpty()) {
            item(key = "videos") { RankCard(stringResource(R.string.recap_top_videos), video.topVideos, { timesLabel(it) }) }
        }
        if (video.topTopics.isNotEmpty()) item(key = "topics") { TopicsCard(video) }
    }
    if (showMusic) {
        val music = summary.music
        if (music.topArtists.isNotEmpty()) {
            item(
                key = "artists",
            ) { RankCard(stringResource(R.string.recap_top_artists), music.topArtists, { playsLabel(it) }, portraits = true) }
        }
        if (music.topTracks.isNotEmpty()) {
            item(key = "tracks") { RankCard(stringResource(R.string.recap_top_tracks), music.topTracks, { playsLabel(it) }) }
        }
        if (music.topGenres.isNotEmpty()) {
            item(key = "genres") { RankCard(stringResource(R.string.recap_top_genres), music.topGenres, { playsLabel(it) }) }
        }
    }
    if (showVideo || showMusic) {
        item(key = "formats") { FormatsCard(summary.video, summary.music.takeIf { showMusic }, key = period to source) }
        item(key = "discoveries") { DiscoveriesCard(summary.video.takeIf { showVideo }, summary.music.takeIf { showMusic }) }
        item(key = "passed") { PassedOnCard(summary.video.takeIf { showVideo }, summary.music.takeIf { showMusic }) }
    }
    if (showVideo) {
        item(key = "library") { LibraryCard(summary.video) }
        if (summary.video.sponsorSavedMs > 0L) item(key = "sponsor") { SponsorCard(summary.video) }
    }
    if (period == RecapPeriod.AllTime && state.history.isNotEmpty() && source != RecapSource.MUSIC) {
        item(key = "history") { HistoryCard(state.history) }
    }
}

@Composable
private fun RecapControls(
    state: RecapUiState,
    locale: Locale,
    onPeriod: (RecapPeriod) -> Unit,
    onSource: (RecapSource) -> Unit,
) {
    val scope =
        when (state.period) {
            is RecapPeriod.Month -> RecapScope.MONTH
            is RecapPeriod.Year -> RecapScope.YEAR
            RecapPeriod.AllTime -> RecapScope.ALL
        }
    val scopes =
        listOf(
            FlowToggleOption(RecapScope.MONTH, stringResource(R.string.recap_period_month)),
            FlowToggleOption(RecapScope.YEAR, stringResource(R.string.recap_period_year)),
            FlowToggleOption(RecapScope.ALL, stringResource(R.string.recap_period_all)),
        )
    val sources =
        listOf(
            FlowToggleOption(RecapSource.ALL, stringResource(R.string.recap_source_all)),
            FlowToggleOption(RecapSource.VIDEO, stringResource(R.string.recap_source_video)),
            FlowToggleOption(RecapSource.MUSIC, stringResource(R.string.recap_source_music)),
        )
    Column(verticalArrangement = Arrangement.spacedBy(ControlSpacing)) {
        FlowConnectedToggleGroup(options = scopes, selected = scope, onSelected = { selected ->
            when (selected) {
                RecapScope.MONTH -> state.months.firstOrNull()?.let { onPeriod(RecapPeriod.Month(it)) }
                RecapScope.YEAR -> state.years.firstOrNull()?.let { onPeriod(RecapPeriod.Year(it)) }
                RecapScope.ALL -> onPeriod(RecapPeriod.AllTime)
            }
        })
        when (val period = state.period) {
            is RecapPeriod.Month -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(ControlSpacing)) {
                    items(state.months, key = { it.toString() }) { month ->
                        FlowFilterChip(
                            label = "${month.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale)} ${month.year}",
                            selected = month == period.month,
                            onClick = { onPeriod(RecapPeriod.Month(month)) },
                        )
                    }
                }
            }

            is RecapPeriod.Year -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(ControlSpacing)) {
                    items(state.years, key = { it }) { year ->
                        FlowFilterChip(
                            label = year.toString(),
                            selected = year == period.year,
                            onClick = { onPeriod(RecapPeriod.Year(year)) },
                        )
                    }
                }
            }

            RecapPeriod.AllTime -> {
                Unit
            }
        }
        FlowConnectedToggleGroup(options = sources, selected = state.source, onSelected = onSource)
    }
}
