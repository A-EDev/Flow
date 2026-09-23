package io.github.aedev.flow.ui.screens.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.stats.RecapPeriod
import io.github.aedev.flow.data.stats.RecapSummary
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.stats.rememberStatEntrance
import io.github.aedev.flow.ui.components.stats.spentTimeLabel
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

private val PagePadding = 24.dp
private val IndicatorHeight = 4.dp
private val IndicatorGap = 4.dp
private val ControlSpacing = 12.dp

/**
 * The recap as a story: one idea per page, moved through by swipe or by the buttons, never on a
 * timer. Only the page on screen is composed, and its chart plays its entrance once.
 */
@Composable
internal fun RecapStoryScreen(
    period: RecapPeriod,
    onClose: () -> Unit,
    viewModel: RecapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(period, state.months) { if (state.months.isNotEmpty()) viewModel.openAt(period) }
    val summary = state.summary?.takeIf { it.period == period && state.source == RecapSource.ALL }
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .safeDrawingPadding(),
    ) {
        if (summary == null) {
            FlowLoadingIndicator()
        } else {
            StoryPager(summary, periodLabel(period, locale), locale, onClose)
        }
    }
}

@Composable
private fun StoryPager(
    summary: RecapSummary,
    periodLabel: String,
    locale: Locale,
    onClose: () -> Unit,
) {
    val pages = remember(summary) { storyPages(summary) }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val summaryLayer = rememberGraphicsLayer()
    val shareTitle = stringResource(R.string.recap_story_share_title)
    val shareText = stringResource(R.string.recap_story_share_text, periodLabel, spentTimeLabel(summary.combined.totalMs))
    val pageLabel = stringResource(R.string.recap_story_page, pager.currentPage + 1, pages.size)

    Column(Modifier.fillMaxSize().padding(PagePadding), verticalArrangement = Arrangement.spacedBy(ControlSpacing)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).semantics { contentDescription = pageLabel },
                horizontalArrangement = Arrangement.spacedBy(IndicatorGap),
            ) {
                pages.indices.forEach { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(IndicatorHeight)
                            .background(
                                if (index <=
                                    pager.currentPage
                                ) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                MaterialTheme.shapes.extraSmall,
                            ),
                    )
                }
            }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.recap_story_close)) }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 0,
            key = { pages[it].toString() },
        ) { index ->
            val entrance = rememberStatEntrance(index)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (pages[index] == StoryPage.Summary) {
                    StorySummaryCard(
                        summary = summary,
                        periodLabel = periodLabel,
                        modifier =
                            Modifier.drawWithContent {
                                summaryLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(summaryLayer)
                            },
                    )
                } else {
                    StoryPageContent(pages[index], summary, periodLabel, entrance, locale)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ControlSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                enabled = pager.currentPage > 0,
            ) { Text(stringResource(R.string.recap_story_previous)) }
            Box(Modifier.weight(1f))
            if (pager.currentPage == pages.lastIndex) {
                Button(onClick = {
                    scope.launch {
                        val image = runCatching { summaryLayer.toImageBitmap() }.getOrNull()
                        shareRecap(context, image, shareText, shareTitle)
                    }
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Text(stringResource(R.string.recap_story_share), modifier = Modifier.padding(start = IndicatorGap * 2))
                }
            } else {
                Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                    Text(stringResource(R.string.recap_story_next))
                }
            }
        }
    }
}

/** "September 2026" for a month, "2026" for a year. */
internal fun periodLabel(
    period: RecapPeriod,
    locale: Locale,
): String =
    when (period) {
        is RecapPeriod.Month -> "${period.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)} ${period.month.year}"
        is RecapPeriod.Year -> period.year.toString()
        RecapPeriod.AllTime -> ""
    }
