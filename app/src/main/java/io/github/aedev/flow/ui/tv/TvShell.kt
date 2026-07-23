package io.github.aedev.flow.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.screens.home.HomeViewModel
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.ui.screens.music.MusicViewModel
import io.github.aedev.flow.ui.screens.search.SearchViewModel
import io.github.aedev.flow.ui.screens.subscriptions.SubscriptionsViewModel
import io.github.aedev.flow.ui.tv.components.TvNavRail
import io.github.aedev.flow.ui.tv.components.TvNowPlayingStrip
import io.github.aedev.flow.ui.tv.navigation.TvBackAction
import io.github.aedev.flow.ui.tv.navigation.TvBackModel
import io.github.aedev.flow.ui.tv.navigation.TvDestination
import io.github.aedev.flow.ui.tv.navigation.TvNavHost
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/**
 * TV browse shell: content + optional music strip laid out against the collapsed
 * rail width; the rail overlays on top so its focus expansion never reflows content.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TvShell(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    musicViewModel: MusicViewModel,
    musicPlayerViewModel: MusicPlayerViewModel,
    subscriptionsViewModel: SubscriptionsViewModel,
    searchViewModel: SearchViewModel,
    onPlayVideo: (Video) -> Unit,
    onPlayPlaylist: (List<Video>, String) -> Unit,
    activeMusicTrack: MusicTrack?,
    onExpandMusic: () -> Unit,
    onDismissMusic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalTvDimens.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = TvDestination.fromRoute(currentRoute)
    val isOnDetailRoute = currentRoute != null && TvDestination.entries.none { it.route == currentRoute }
    var railHasFocus by remember { mutableStateOf(false) }
    val railFocusRequester = remember { FocusRequester() }

    val tabHistory = remember { mutableStateListOf<TvDestination>() }

    fun navigateToTab(destination: TvDestination) {
        navController.navigate(destination.route) {
            popUpTo(TvDestination.HOME.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun selectTab(destination: TvDestination) {
        if (destination != currentTab) {
            tabHistory.remove(destination)
            if (!isOnDetailRoute) {
                tabHistory.remove(currentTab)
                tabHistory.add(currentTab)
            }
        }
        navigateToTab(destination)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = dimens.railCollapsedWidth)
                .focusProperties {
                    @OptIn(ExperimentalComposeUiApi::class)
                    exit = { direction ->
                        if (direction == FocusDirection.Left) {
                            railFocusRequester
                        } else {
                            FocusRequester.Default
                        }
                    }
                }
                .focusGroup(),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TvNavHost(
                    navController = navController,
                    homeViewModel = homeViewModel,
                    musicViewModel = musicViewModel,
                    musicPlayerViewModel = musicPlayerViewModel,
                    subscriptionsViewModel = subscriptionsViewModel,
                    searchViewModel = searchViewModel,
                    onPlayVideo = onPlayVideo,
                    onPlayPlaylist = onPlayPlaylist,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            activeMusicTrack?.let { track ->
                TvNowPlayingStrip(
                    track = track,
                    onExpand = onExpandMusic,
                    onDismiss = onDismissMusic,
                )
            }
        }
        TvNavRail(
            selected = currentTab,
            onSelected = ::selectTab,
            onFocusChanged = { railHasFocus = it },
            selectedFocusRequester = railFocusRequester,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight(),
        )

        val backAction = TvBackModel.resolve(
            isOnDetailRoute = isOnDetailRoute,
            hasTabHistory = tabHistory.isNotEmpty(),
            currentTab = currentTab,
            railHasFocus = railHasFocus,
        )
        BackHandler(enabled = backAction != TvBackAction.EXIT) {
            when (backAction) {
                TvBackAction.POP_DETAIL -> navController.popBackStack()
                TvBackAction.POP_TAB -> navigateToTab(tabHistory.removeAt(tabHistory.lastIndex))
                TvBackAction.GO_HOME -> navigateToTab(TvDestination.HOME)
                TvBackAction.FOCUS_RAIL -> runCatching { railFocusRequester.requestFocus() }
                TvBackAction.EXIT -> Unit
            }
        }
    }
}
