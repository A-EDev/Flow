package io.github.aedev.flow.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.aedev.flow.ui.components.settings.SettingsDestination
import io.github.aedev.flow.ui.components.settings.SettingsTarget
import io.github.aedev.flow.ui.screens.settings.home.SettingsHomeScreen
import kotlinx.coroutines.launch

/**
 * The single Settings route: the settings list and the open page as a list-detail pair. On a phone
 * they are one pane at a time; from an expanded window they sit side by side.
 *
 * Sub-pages (Theme under Appearance, Buffer under Player) stack inside the detail pane rather than
 * through the pane navigator, so a sub-page never replaces the settings list beside it.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingsHost(
    start: SettingsTarget?,
    onExit: () -> Unit,
    onOpenDonations: () -> Unit,
    onOpenRecap: () -> Unit,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(scaffoldDirective = rememberSettingsScaffoldDirective())
    val scope = rememberCoroutineScope()
    var stack by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var startConsumed by rememberSaveable { mutableStateOf(false) }

    val twoPane =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
            navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val current = stack.lastOrNull()?.let(SettingsTarget::decode) ?: SettingsTarget(SettingsDestination.APPEARANCE)

    fun open(target: SettingsTarget) {
        stack = target.destination.path
            .dropLast(1)
            .map { SettingsTarget(it).encode() } + target.encode()
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, target.destination.root.id) }
    }

    fun push(target: SettingsTarget) {
        val settled =
            stack.dropLast(1) +
                listOfNotNull(
                    stack
                        .lastOrNull()
                        ?.let(SettingsTarget::decode)
                        ?.copy(highlight = null)
                        ?.encode(),
                )
        stack = settled + target.encode()
    }

    fun back() {
        if (stack.size > 1) {
            stack = stack.dropLast(1)
        } else {
            scope.launch { if (!navigator.navigateBack()) onExit() }
        }
    }

    LaunchedEffect(start) {
        if (start != null && !startConsumed) {
            startConsumed = true
            open(start)
        }
    }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                SettingsHomeScreen(
                    selected = if (twoPane) current.destination.root else null,
                    onOpen = ::open,
                    onOpenDonations = onOpenDonations,
                    onBack = onExit,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                AnimatedContent(
                    targetState = current,
                    transitionSpec = { fadeIn(spec) togetherWith fadeOut(spec) },
                    contentKey = { it.destination },
                    label = "settingsDetail",
                ) { target ->
                    SettingsDetail(
                        target = target,
                        onBack = if (twoPane && stack.size <= 1) null else ::back,
                        onNavigate = ::push,
                        onOpenRecap = onOpenRecap,
                    )
                }
            }
        },
    )

    BackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }
}

/**
 * The default directive with auto-focus off. On every navigation the scaffold would otherwise
 * request focus on the destination pane, which lands on its first text field and opens the keyboard.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun rememberSettingsScaffoldDirective(): PaneScaffoldDirective {
    val defaults = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    return remember(defaults) {
        PaneScaffoldDirective(
            maxHorizontalPartitions = defaults.maxHorizontalPartitions,
            horizontalPartitionSpacerSize = defaults.horizontalPartitionSpacerSize,
            maxVerticalPartitions = defaults.maxVerticalPartitions,
            verticalPartitionSpacerSize = defaults.verticalPartitionSpacerSize,
            defaultPanePreferredWidth = defaults.defaultPanePreferredWidth,
            defaultPanePreferredHeight = defaults.defaultPanePreferredHeight,
            excludedBounds = defaults.excludedBounds,
            shouldAutoFocusCurrentDestination = false,
        )
    }
}
