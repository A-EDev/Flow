package io.github.aedev.flow.ui.tv.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.rememberFeedGridLayout
import io.github.aedev.flow.ui.components.shared.card.LocalVideoCardFocusListener
import io.github.aedev.flow.ui.components.shared.dpadFocusGroup
import io.github.aedev.flow.ui.screens.home.HomeFeedBody
import io.github.aedev.flow.ui.screens.home.HomeFeedEffects
import io.github.aedev.flow.ui.screens.home.HomeViewModel
import io.github.aedev.flow.ui.screens.home.chips.HomeChipRow
import io.github.aedev.flow.ui.screens.home.rememberHomeGridStates
import io.github.aedev.flow.ui.tv.components.TvSpotlight
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

private const val SPOTLIGHT_SETTLE_MS = 300L

/** Grid cells sit edge to edge and each card insets itself, so this much of the margin is already there. */
private val CardInset = 16.dp

/**
 * TV Home: the spotlight over Home's own chips and feed, with the phone's cards, shelves, states and
 * prefetch. No page title (the rail says where you are) and no Refresh button: selecting Home on the
 * rail again scrolls up and then refreshes, as reselecting the tab does on a phone.
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun TvHomeScreen(
    viewModel: HomeViewModel,
    onVideoClick: (Video) -> Unit,
    onPlayPlaylist: (List<Video>, String) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    settings: TvHomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val dimens = LocalTvDimens.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chips by viewModel.chips.collectAsStateWithLifecycle()
    val feedEnabled by settings.feedEnabled.collectAsStateWithLifecycle()
    val refreshOnReselect by settings.refreshOnReselect.collectAsStateWithLifecycle()
    val gridStates = rememberHomeGridStates()

    LaunchedEffect(viewModel) {
        viewModel.initialize(context.applicationContext)
    }
    HomeFeedEffects(
        viewModel = viewModel,
        uiState = uiState,
        chips = chips,
        gridStates = gridStates,
        homeFeedEnabled = feedEnabled,
        refreshHomeOnReselect = refreshOnReselect,
    )

    var focusedVideo by remember { mutableStateOf<Video?>(null) }
    var settledVideo by remember { mutableStateOf<Video?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { focusedVideo }
            .debounce(SPOTLIGHT_SETTLE_MS)
            .collect { settledVideo = it }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(top = dimens.overscanVertical),
    ) {
        TvSpotlight(
            video = settledVideo ?: uiState.videos.firstOrNull(),
            modifier =
                Modifier
                    .padding(start = CardInset, end = dimens.overscanHorizontal)
                    .height(dimens.spotlightHeight),
        )
        if (feedEnabled && chips.chips.size > 1) {
            HomeChipRow(
                state = chips,
                scrollState = rememberTopAppBarState(),
                onSelect = viewModel::selectChip,
                modifier = Modifier.dpadFocusGroup(),
            )
        }
        CompositionLocalProvider(LocalVideoCardFocusListener provides { focusedVideo = it }) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = dimens.overscanHorizontal - CardInset),
            ) {
                HomeFeedBody(
                    viewModel = viewModel,
                    uiState = uiState,
                    chips = chips,
                    gridStates = gridStates,
                    feedLayout =
                        rememberFeedGridLayout(
                            maxWidth = maxWidth,
                            maxAutoColumns = dimens.feedMaxColumns,
                            minCellWidth = dimens.feedCardMinWidth,
                        ),
                    isListView = false,
                    homeFeedEnabled = feedEnabled,
                    onVideoClick = onVideoClick,
                    onPlayMix = onPlayPlaylist,
                    onShortClick = {},
                    onNavigateToHistory = onOpenLibrary,
                    onOpenShortsFeed = {},
                    showShorts = false,
                )
            }
        }
    }
}
