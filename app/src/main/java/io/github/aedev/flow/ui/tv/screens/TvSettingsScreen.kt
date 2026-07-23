package io.github.aedev.flow.ui.tv.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.AppUiModePreferences
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.ui.tv.components.TvScreenScaffold
import io.github.aedev.flow.ui.tv.screens.settings.TvAboutSettingsPane
import io.github.aedev.flow.ui.tv.screens.settings.TvAppearanceSettingsPane
import io.github.aedev.flow.ui.tv.screens.settings.TvContentSettingsPane
import io.github.aedev.flow.ui.tv.screens.settings.TvFlowEngineSettingsPane
import io.github.aedev.flow.ui.tv.screens.settings.TvInterfaceSettingsPane
import io.github.aedev.flow.ui.tv.screens.settings.TvPlaybackSettingsPane
import io.github.aedev.flow.ui.tv.screens.settings.TvQualitySettingsPane
import io.github.aedev.flow.ui.tv.screens.settings.TvSettingsCategory
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/**
 * Two-pane TV settings: focusable category list on the left, the selected
 * category's pane on the right. Sync opens the device-sync flow as a route.
 */
@Composable
fun TvSettingsScreen(
    modifier: Modifier = Modifier,
    onOpenSync: () -> Unit = {},
) {
    val context = LocalContext.current
    val playerPreferences = remember { PlayerPreferences(context.applicationContext) }
    val modePreferences = remember { AppUiModePreferences(context.applicationContext) }
    val localDataManager = remember { LocalDataManager(context.applicationContext) }
    var selectedCategory by rememberSaveable { mutableStateOf(TvSettingsCategory.PLAYBACK) }
    val dimens = LocalTvDimens.current

    TvScreenScaffold(
        title = stringResource(R.string.settings),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.overscanHorizontal),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .verticalScroll(rememberScrollState())
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TvSettingsCategory.entries.forEach { category ->
                    TvSettingsCategoryItem(
                        category = category,
                        selected = category == selectedCategory,
                        onClick = {
                            if (category == TvSettingsCategory.SYNC) {
                                onOpenSync()
                            } else {
                                selectedCategory = category
                            }
                        },
                        onFocused = {
                            // Focus-follows-selection for panes; Sync needs an explicit click.
                            if (category != TvSettingsCategory.SYNC) {
                                selectedCategory = category
                            }
                        },
                    )
                }
            }

            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                when (selectedCategory) {
                    TvSettingsCategory.PLAYBACK -> TvPlaybackSettingsPane(playerPreferences)
                    TvSettingsCategory.QUALITY -> TvQualitySettingsPane(playerPreferences)
                    TvSettingsCategory.CONTENT -> TvContentSettingsPane(playerPreferences)
                    TvSettingsCategory.APPEARANCE -> TvAppearanceSettingsPane(localDataManager)
                    TvSettingsCategory.FLOW_ENGINE -> TvFlowEngineSettingsPane(playerPreferences)
                    TvSettingsCategory.INTERFACE -> TvInterfaceSettingsPane(modePreferences)
                    TvSettingsCategory.ABOUT -> TvAboutSettingsPane()
                    TvSettingsCategory.SYNC -> Unit
                }
            }
        }
    }
}

@Composable
private fun TvSettingsCategoryItem(
    category: TvSettingsCategory,
    selected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { state ->
            focused = state.isFocused
            if (state.isFocused) onFocused()
        },
        shape = MaterialTheme.shapes.medium,
        color = when {
            focused -> MaterialTheme.colorScheme.inverseSurface
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = when {
            focused -> MaterialTheme.colorScheme.inverseOnSurface
            selected -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(category.icon, contentDescription = null)
            Text(
                text = stringResource(category.labelRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
