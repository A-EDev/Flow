package io.github.aedev.flow.ui.tv.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.ui.theme.ThemeCatalog
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant
import io.github.aedev.flow.ui.tv.components.TvSectionHeader
import io.github.aedev.flow.ui.tv.components.TvSelectionRow
import kotlinx.coroutines.launch

/**
 * Curated TV theme picker: the core theme modes plus the light/dark/AMOLED
 * variant. The full palette grid stays on mobile; both share the same
 * DataStore, so palettes chosen on the phone apply here too.
 */
private val TV_THEME_MODES: List<Pair<ThemeMode, Int>> =
    listOf(
        ThemeMode.SYSTEM,
        ThemeMode.MATERIAL_YOU,
        ThemeMode.LIGHT,
        ThemeMode.DARK,
        ThemeMode.OLED,
        ThemeMode.MONOCHROME,
    ).map { it to ThemeCatalog.nameRes(it) }

private val TV_THEME_VARIANTS: List<Pair<ThemeVariant, Int>> =
    listOf(
        ThemeVariant.LIGHT to R.string.tv_theme_variant_light,
        ThemeVariant.DARK to R.string.tv_theme_variant_dark,
        ThemeVariant.AMOLED to R.string.tv_theme_variant_amoled,
    )

@Composable
fun TvAppearanceSettingsPane(
    localDataManager: LocalDataManager,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val themeMode by localDataManager.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val themeVariant by localDataManager.themeVariant.collectAsStateWithLifecycle(initialValue = ThemeVariant.DARK)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "mode-header") {
            TvSectionHeader(title = stringResource(R.string.tv_theme_mode_title))
        }
        items(count = TV_THEME_MODES.size, key = { "mode:${TV_THEME_MODES[it].first.name}" }) { index ->
            val (mode, labelRes) = TV_THEME_MODES[index]
            TvSelectionRow(
                label = stringResource(labelRes),
                selected = themeMode == mode,
                onClick = {
                    scope.launch { localDataManager.setThemeMode(mode) }
                },
            )
        }
        item(key = "variant-header") {
            TvSectionHeader(title = stringResource(R.string.tv_theme_variant_title))
        }
        items(count = TV_THEME_VARIANTS.size, key = { "variant:${TV_THEME_VARIANTS[it].first.name}" }) { index ->
            val (variant, labelRes) = TV_THEME_VARIANTS[index]
            TvSelectionRow(
                label = stringResource(labelRes),
                selected = themeVariant == variant,
                onClick = {
                    scope.launch { localDataManager.setThemeVariant(variant) }
                },
            )
        }
    }
}
