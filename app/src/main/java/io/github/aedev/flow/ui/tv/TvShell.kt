package io.github.aedev.flow.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.screens.home.HomeViewModel
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel
import io.github.aedev.flow.ui.screens.music.MusicViewModel
import io.github.aedev.flow.ui.screens.search.SearchViewModel
import io.github.aedev.flow.ui.screens.subscriptions.SubscriptionsViewModel
import io.github.aedev.flow.ui.tv.components.TvNavRail
import io.github.aedev.flow.ui.tv.components.TvNowPlayingStrip
import io.github.aedev.flow.ui.tv.navigation.TvBackAction
import io.github.aedev.flow.ui.tv.navigation.TvBackModel
import io.github.aedev.flow.ui.tv.navigation.TvNavHost
import io.github.aedev.flow.ui.tv.navigation.TvNavigation
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/**
 * TV browse shell: content + optional music strip laid out against the collapsed rail width; the
 * rail lies over them, so its focus expansion never reflows the page. Coming back from the rail
 * returns focus to the item it left.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TvShell(
    navigation: TvNavigation,
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
    val rootRoutes = navigation.rootRoutes
    val isOnDetailRoute = currentRoute != null && currentRoute !in rootRoutes
    var lastRoot by remember { mutableStateOf(navigation.startTab.route) }
    LaunchedEffect(currentRoute) {
        if (currentRoute != null && currentRoute in rootRoutes) lastRoot = currentRoute
    }
    val currentRoot = currentRoute?.takeIf { it in rootRoutes } ?: lastRoot
    var railHasFocus by remember { mutableStateOf(false) }
    val railFocusRequester = remember { FocusRequester() }
    val rootHistory = remember { mutableStateListOf<String>() }

    fun navigateToRoot(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun selectRoot(route: String) {
        if (route != currentRoot) {
            rootHistory.remove(route)
            if (!isOnDetailRoute) {
                rootHistory.remove(currentRoot)
                rootHistory.add(currentRoot)
            }
        }
        navigateToRoot(route)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = dimens.railCollapsedWidth)
                    .focusRestorer()
                    .focusGroup(),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TvNavHost(
                    navController = navController,
                    startRoute = navigation.startTab.route,
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
        AnimatedVisibility(
            visible = railHasFocus,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(WideNavigationRailDefaults.colors().modalScrimColor),
            )
        }
        TvNavRail(
            tabs = navigation.tabs,
            selectedRoute = currentRoot,
            onSelect = ::selectRoot,
            onFocusChanged = { railHasFocus = it },
            focusRequester = railFocusRequester,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        val backAction =
            TvBackModel.resolve(
                isOnDetailRoute = isOnDetailRoute,
                hasTabHistory = rootHistory.isNotEmpty(),
                isOnStartTab = currentRoot == navigation.startTab.route,
                railHasFocus = railHasFocus,
            )
        BackHandler(enabled = backAction != TvBackAction.EXIT) {
            when (backAction) {
                TvBackAction.POP_DETAIL -> navController.popBackStack()
                TvBackAction.POP_TAB -> navigateToRoot(rootHistory.removeAt(rootHistory.lastIndex))
                TvBackAction.GO_START -> navigateToRoot(navigation.startTab.route)
                TvBackAction.FOCUS_RAIL -> railFocusRequester.requestFocus()
                TvBackAction.EXIT -> Unit
            }
        }
    }
}
