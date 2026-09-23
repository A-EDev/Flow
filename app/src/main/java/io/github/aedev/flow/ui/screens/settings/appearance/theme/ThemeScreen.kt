package io.github.aedev.flow.ui.screens.settings.appearance.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsDestination
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.SettingsTarget
import io.github.aedev.flow.ui.components.settings.nav
import io.github.aedev.flow.ui.components.settings.switch
import io.github.aedev.flow.ui.components.settings.toggleGroup
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog
import io.github.aedev.flow.ui.components.shared.FlowToggleOption
import io.github.aedev.flow.ui.screens.settings.appearance.themeVariantLabel
import io.github.aedev.flow.ui.screens.settings.index.ThemeIndex
import io.github.aedev.flow.ui.theme.ThemeCatalog
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant

private val PaletteDialogMaxHeight = 440.dp
private val PaletteDialogSpacing = 8.dp

private enum class SystemSlot { LIGHT, DARK }

/**
 * The theme picker. With Follow system on, Flow switches between a light-mode and a dark-mode
 * palette with the device; with it off, one palette is shown in the chosen style.
 */
@Composable
internal fun ThemeScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    onNavigate: (SettingsTarget) -> Unit,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val swatches by viewModel.swatches.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()
    var slotDialog by rememberSaveable { mutableStateOf<SystemSlot?>(null) }

    val styleOptions =
        ThemeVariant.entries.map { FlowToggleOption(it, stringResource(themeVariantLabel(it))) }
    val darkStyleOptions = styleOptions.filter { it.value != ThemeVariant.LIGHT }
    val lightSlotName = stringResource(ThemeCatalog.nameRes(settings.systemLightMode))
    val darkSlotName = stringResource(ThemeCatalog.nameRes(settings.systemDarkMode))

    SettingsPage(
        title = stringResource(R.string.settings_item_theme),
        onBack = onBack,
        highlight = highlight,
    ) {
        group(key = "theme.mode") {
            switch(
                ThemeIndex.followSystem,
                checked = settings.followsSystem,
                onCheckedChange = { viewModel.setFollowSystem(it, isSystemDark) },
            )
            if (settings.followsSystem) {
                nav(
                    ThemeIndex.lightModeTheme,
                    value = lightSlotName,
                    icon = Icons.Outlined.LightMode,
                    onClick = { slotDialog = SystemSlot.LIGHT },
                )
                nav(
                    ThemeIndex.darkModeTheme,
                    value = darkSlotName,
                    icon = Icons.Outlined.DarkMode,
                    onClick = { slotDialog = SystemSlot.DARK },
                )
                toggleGroup(ThemeIndex.darkModeStyle, darkStyleOptions, settings.systemDarkVariant, viewModel::setSystemDarkVariant)
            } else {
                toggleGroup(ThemeIndex.style, styleOptions, settings.variant, viewModel::setVariant)
            }
        }
        if (!settings.followsSystem) {
            header(key = ThemeIndex.palettes.key, text = R.string.settings_theme_palettes)
            item("theme.palettes.grid") {
                ThemeCardGrid(
                    palettes = viewModel.palettes,
                    swatches = swatches[settings.variant].orEmpty(),
                    selected = settings.mode,
                    onSelect = viewModel::setTheme,
                    trailing = { mode ->
                        if (mode == ThemeMode.CUSTOM) {
                            { EditCustomButton { onNavigate(SettingsTarget(SettingsDestination.CUSTOM_THEME)) } }
                        } else {
                            null
                        }
                    },
                )
            }
        }
    }

    slotDialog?.let { slot ->
        ThemePaletteDialog(
            title =
                stringResource(
                    if (slot ==
                        SystemSlot.LIGHT
                    ) {
                        R.string.appearance_system_light_theme
                    } else {
                        R.string.appearance_system_dark_theme
                    },
                ),
            viewModel = viewModel,
            swatchVariant = if (slot == SystemSlot.LIGHT) ThemeVariant.LIGHT else settings.systemDarkVariant,
            selected = if (slot == SystemSlot.LIGHT) settings.systemLightMode else settings.systemDarkMode,
            onSelect = { mode ->
                if (slot == SystemSlot.LIGHT) viewModel.setSystemLightTheme(mode) else viewModel.setSystemDarkTheme(mode)
                slotDialog = null
            },
            onDismiss = { slotDialog = null },
        )
    }
}

@Composable
private fun EditCustomButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.settings_theme_edit_custom))
    }
}

/** Picks the palette for one Follow-system slot, drawn with the style that slot will use. */
@Composable
private fun ThemePaletteDialog(
    title: String,
    viewModel: ThemeViewModel,
    swatchVariant: ThemeVariant,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val swatches by viewModel.swatches.collectAsStateWithLifecycle()
    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = PaletteDialogMaxHeight),
                verticalArrangement = Arrangement.spacedBy(PaletteDialogSpacing),
            ) {
                items(viewModel.palettes, key = { it.mode.name }) { entry ->
                    ThemeCard(
                        entry = entry,
                        swatch = swatches[swatchVariant]?.get(entry.mode),
                        selected = entry.mode == selected,
                        onClick = { onSelect(entry.mode) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
