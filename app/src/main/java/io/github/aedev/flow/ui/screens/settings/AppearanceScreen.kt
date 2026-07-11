@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.aedev.flow.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.*
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

private data class ThemeInfo(
    val mode: ThemeMode,
    @StringRes val displayNameRes: Int,
    @StringRes val subtitleRes: Int,
    val category: ThemeCategory,
    val primaryColor: Color,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val onSurfaceColor: Color,
    val accentColor: Color = Color.Unspecified,
    val surfaceVariantColor: Color = Color.Unspecified
)

private enum class ThemeCategory(
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    LIGHT(R.string.appearance_category_light, Icons.Outlined.LightMode),
    DARK(R.string.appearance_category_dark, Icons.Outlined.DarkMode),
    CUSTOM(R.string.appearance_category_custom, Icons.Outlined.AutoAwesome)
}

private enum class SystemThemeSlot {
    LIGHT, DARK
}

private data class CustomRoleField(
    val role: CustomColorRole,
    @StringRes val labelRes: Int
)

private val CUSTOM_ROLE_FIELDS = listOf(
    CustomRoleField(CustomColorRole.PRIMARY, R.string.appearance_role_primary),
    CustomRoleField(CustomColorRole.ON_PRIMARY, R.string.appearance_role_on_primary),
    CustomRoleField(CustomColorRole.PRIMARY_CONTAINER, R.string.appearance_role_primary_container),
    CustomRoleField(CustomColorRole.ON_PRIMARY_CONTAINER, R.string.appearance_role_on_primary_container),
    CustomRoleField(CustomColorRole.INVERSE_PRIMARY, R.string.appearance_role_inverse_primary),
    CustomRoleField(CustomColorRole.SECONDARY, R.string.appearance_role_secondary),
    CustomRoleField(CustomColorRole.ON_SECONDARY, R.string.appearance_role_on_secondary),
    CustomRoleField(CustomColorRole.SECONDARY_CONTAINER, R.string.appearance_role_secondary_container),
    CustomRoleField(CustomColorRole.ON_SECONDARY_CONTAINER, R.string.appearance_role_on_secondary_container),
    CustomRoleField(CustomColorRole.TERTIARY, R.string.appearance_role_tertiary),
    CustomRoleField(CustomColorRole.ON_TERTIARY, R.string.appearance_role_on_tertiary),
    CustomRoleField(CustomColorRole.TERTIARY_CONTAINER, R.string.appearance_role_tertiary_container),
    CustomRoleField(CustomColorRole.ON_TERTIARY_CONTAINER, R.string.appearance_role_on_tertiary_container),
    CustomRoleField(CustomColorRole.BACKGROUND, R.string.appearance_role_background),
    CustomRoleField(CustomColorRole.ON_BACKGROUND, R.string.appearance_role_on_background),
    CustomRoleField(CustomColorRole.SURFACE, R.string.appearance_role_surface),
    CustomRoleField(CustomColorRole.ON_SURFACE, R.string.appearance_role_on_surface),
    CustomRoleField(CustomColorRole.SURFACE_VARIANT, R.string.appearance_role_surface_variant),
    CustomRoleField(CustomColorRole.ON_SURFACE_VARIANT, R.string.appearance_role_on_surface_variant),
    CustomRoleField(CustomColorRole.SURFACE_TINT, R.string.appearance_role_surface_tint),
    CustomRoleField(CustomColorRole.INVERSE_SURFACE, R.string.appearance_role_inverse_surface),
    CustomRoleField(CustomColorRole.INVERSE_ON_SURFACE, R.string.appearance_role_inverse_on_surface),
    CustomRoleField(CustomColorRole.ERROR, R.string.appearance_role_error),
    CustomRoleField(CustomColorRole.ON_ERROR, R.string.appearance_role_on_error),
    CustomRoleField(CustomColorRole.ERROR_CONTAINER, R.string.appearance_role_error_container),
    CustomRoleField(CustomColorRole.ON_ERROR_CONTAINER, R.string.appearance_role_on_error_container),
    CustomRoleField(CustomColorRole.OUTLINE, R.string.appearance_role_outline),
    CustomRoleField(CustomColorRole.OUTLINE_VARIANT, R.string.appearance_role_outline_variant),
    CustomRoleField(CustomColorRole.SCRIM, R.string.appearance_role_scrim),
    CustomRoleField(CustomColorRole.SURFACE_BRIGHT, R.string.appearance_role_surface_bright),
    CustomRoleField(CustomColorRole.SURFACE_DIM, R.string.appearance_role_surface_dim),
    CustomRoleField(CustomColorRole.SURFACE_CONTAINER_LOWEST, R.string.appearance_role_surface_container_lowest),
    CustomRoleField(CustomColorRole.SURFACE_CONTAINER_LOW, R.string.appearance_role_surface_container_low),
    CustomRoleField(CustomColorRole.SURFACE_CONTAINER, R.string.appearance_role_surface_container),
    CustomRoleField(CustomColorRole.SURFACE_CONTAINER_HIGH, R.string.appearance_role_surface_container_high),
    CustomRoleField(CustomColorRole.SURFACE_CONTAINER_HIGHEST, R.string.appearance_role_surface_container_highest)
)

private fun ThemeInfo.forVariant(variant: ThemeVariant): ThemeInfo {
    if (mode == ThemeMode.CUSTOM) return this
    if (mode == ThemeMode.DARK) {
        return when (variant) {
            ThemeVariant.LIGHT -> copy(
                backgroundColor = LightThemeColors.Background,
                surfaceColor = LightThemeColors.Surface,
                onSurfaceColor = LightThemeColors.Text,
                accentColor = LightThemeColors.Secondary,
                surfaceVariantColor = LightThemeColors.Border
            )
            ThemeVariant.DARK -> this
            ThemeVariant.AMOLED -> copy(
                backgroundColor = OLEDThemeColors.Background,
                surfaceColor = OLEDThemeColors.Surface,
                onSurfaceColor = OLEDThemeColors.Text,
                accentColor = OLEDThemeColors.Secondary,
                surfaceVariantColor = OLEDThemeColors.Border
            )
        }
    }
    fun blend(first: Color, second: Color, ratio: Float): Color =
        Color(ColorUtils.blendARGB(first.toArgb(), second.toArgb(), ratio))

    return when (variant) {
        ThemeVariant.LIGHT -> if (category == ThemeCategory.LIGHT) {
            this
        } else {
            copy(
                backgroundColor = blend(primaryColor, Color.White, 0.88f),
                surfaceColor = blend(primaryColor, Color.White, 0.80f),
                onSurfaceColor = blend(primaryColor, Color.Black, 0.82f),
                surfaceVariantColor = blend(primaryColor, Color.White, 0.70f)
            )
        }
        ThemeVariant.DARK -> copy(
            backgroundColor = blend(primaryColor, Color.Black, 0.92f),
            surfaceColor = blend(primaryColor, Color.Black, 0.86f),
            onSurfaceColor = blend(primaryColor, Color.White, 0.88f),
            surfaceVariantColor = blend(primaryColor, Color.Black, 0.76f)
        )
        ThemeVariant.AMOLED -> copy(
            backgroundColor = Color.Black,
            surfaceColor = Color.Black,
            onSurfaceColor = Color.White,
            surfaceVariantColor = blend(primaryColor, Color.Black, 0.88f)
        )
    }
}

@Composable
fun AppearanceScreen(
    currentTheme: ThemeMode,
    themeVariant: ThemeVariant,
    customThemePalettes: CustomThemePalettes,
    systemLightThemeMode: ThemeMode,
    systemDarkThemeMode: ThemeMode,
    systemDarkThemeVariant: ThemeVariant,
    onThemeChange: (ThemeMode) -> Unit,
    onThemeVariantChange: (ThemeVariant) -> Unit,
    onCustomThemePalettesChange: (CustomThemePalettes) -> Unit,
    onSystemLightThemeChange: (ThemeMode) -> Unit,
    onSystemDarkThemeChange: (ThemeMode) -> Unit,
    onSystemDarkThemeVariantChange: (ThemeVariant) -> Unit,
    onNavigateBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    var showCustomizer by remember { mutableStateOf(false) }
    var customizerVariant by remember { mutableStateOf(themeVariant) }
    var applyCustomAsCurrent by remember { mutableStateOf(true) }
    var systemThemeSlot by remember { mutableStateOf<SystemThemeSlot?>(null) }
    var showAppliedSnackbar by remember { mutableStateOf(false) }
    var lastAppliedTheme by remember { mutableStateOf("") }

    val activeCustomColors = customThemePalettes.forVariant(themeVariant)
    val allThemes = remember(activeCustomColors, themeVariant) {
        buildThemeCatalog(activeCustomColors).map { it.forVariant(themeVariant) }
    }
    val systemLightThemes = remember(customThemePalettes.light) {
        buildThemeCatalog(customThemePalettes.light)
            .filterNot { it.mode == ThemeMode.SYSTEM }
            .map { it.forVariant(ThemeVariant.LIGHT) }
    }
    val systemDarkThemes = remember(customThemePalettes, systemDarkThemeVariant) {
        buildThemeCatalog(customThemePalettes.forVariant(systemDarkThemeVariant))
            .filterNot { it.mode == ThemeMode.SYSTEM }
            .map { it.forVariant(systemDarkThemeVariant) }
    }
    val currentThemeInfo = remember(currentTheme, allThemes) {
        allThemes.firstOrNull { it.mode == currentTheme } ?: allThemes.first()
    }
    val systemLightThemeInfo = remember(systemLightThemeMode, systemLightThemes) {
        systemLightThemes.firstOrNull { it.mode == systemLightThemeMode } ?: systemLightThemes.first()
    }
    val systemDarkThemeInfo = remember(systemDarkThemeMode, systemDarkThemes) {
        systemDarkThemes.firstOrNull { it.mode == systemDarkThemeMode } ?: systemDarkThemes.first()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(showAppliedSnackbar) {
        if (showAppliedSnackbar) {
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.appearance_applied_toast, lastAppliedTheme),
                duration = SnackbarDuration.Short
            )
            showAppliedSnackbar = false
        }
    }

    if (showCustomizer) {
        CustomThemeEditorDialog(
            initialColors = customThemePalettes.forVariant(customizerVariant),
            variant = customizerVariant,
            onDismiss = { showCustomizer = false },
            onSave = { colors ->
                onCustomThemePalettesChange(customThemePalettes.withPalette(customizerVariant, colors))
                if (applyCustomAsCurrent) {
                    onThemeChange(ThemeMode.CUSTOM)
                    onThemeVariantChange(customizerVariant)
                }
                lastAppliedTheme = context.getString(R.string.theme_name_custom)
                showAppliedSnackbar = true
                showCustomizer = false
            }
        )
    }

    val activeSystemThemeSlot = systemThemeSlot
    if (activeSystemThemeSlot != null) {
        SystemThemePickerDialog(
            titleRes = if (activeSystemThemeSlot == SystemThemeSlot.LIGHT) {
                R.string.appearance_system_light_theme
            } else {
                R.string.appearance_system_dark_theme
            },
            themes = if (activeSystemThemeSlot == SystemThemeSlot.LIGHT) systemLightThemes else systemDarkThemes,
            selectedMode = if (activeSystemThemeSlot == SystemThemeSlot.LIGHT) {
                systemLightThemeInfo.mode
            } else {
                systemDarkThemeInfo.mode
            },
            onDismiss = { systemThemeSlot = null },
            onSelect = { mode ->
                if (activeSystemThemeSlot == SystemThemeSlot.LIGHT) {
                    onSystemLightThemeChange(mode)
                } else {
                    onSystemDarkThemeChange(mode)
                }
                systemThemeSlot = null
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.appearance_navigate_back)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.appearance_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.appearance_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = {
                        customizerVariant = themeVariant
                        applyCustomAsCurrent = true
                        showCustomizer = true
                    }) {
                        Icon(
                            Icons.Outlined.Palette,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.appearance_customize_theme)
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 176.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ThemeVariantSelector(
                    selected = themeVariant,
                    onSelect = onThemeVariantChange
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                CurrentThemeHero(
                    themeInfo = currentThemeInfo,
                    onCustomize = if (currentThemeInfo.mode == ThemeMode.CUSTOM) ({
                        customizerVariant = themeVariant
                        applyCustomAsCurrent = true
                        showCustomizer = true
                    }) else null
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SystemDefaultThemesCard(
                    lightThemeInfo = systemLightThemeInfo,
                    darkThemeInfo = systemDarkThemeInfo,
                    darkVariant = systemDarkThemeVariant,
                    onLightClick = { systemThemeSlot = SystemThemeSlot.LIGHT },
                    onDarkClick = { systemThemeSlot = SystemThemeSlot.DARK },
                    onDarkVariantChange = onSystemDarkThemeVariantChange,
                    onCustomizeLight = {
                        customizerVariant = ThemeVariant.LIGHT
                        applyCustomAsCurrent = false
                        showCustomizer = true
                    },
                    onCustomizeDark = {
                        customizerVariant = systemDarkThemeVariant
                        applyCustomAsCurrent = false
                        showCustomizer = true
                    }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                val countText = context.getString(R.string.appearance_themes_count_all, allThemes.size)
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(
                items = allThemes,
                key = { it.mode.name }
            ) { themeInfo ->
                ThemeCard(
                    themeInfo = themeInfo,
                    isSelected = currentTheme == themeInfo.mode,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onThemeChange(themeInfo.mode)
                        lastAppliedTheme = context.getString(themeInfo.displayNameRes)
                        showAppliedSnackbar = true
                    },
                    onCustomize = if (themeInfo.mode == ThemeMode.CUSTOM) ({
                        customizerVariant = themeVariant
                        applyCustomAsCurrent = true
                        showCustomizer = true
                    }) else null
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(56.dp))
            }
        }
    }
}

@Composable
private fun ThemeVariantSelector(
    selected: ThemeVariant,
    onSelect: (ThemeVariant) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.appearance_variant_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemeVariant.entries.forEachIndexed { index, variant ->
                SegmentedButton(
                    selected = selected == variant,
                    onClick = { onSelect(variant) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeVariant.entries.size),
                    label = {
                        Text(
                            text = androidx.compose.ui.res.stringResource(
                                when (variant) {
                                    ThemeVariant.LIGHT -> R.string.appearance_variant_light
                                    ThemeVariant.DARK -> R.string.appearance_variant_dark
                                    ThemeVariant.AMOLED -> R.string.appearance_variant_amoled
                                }
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun CurrentThemeHero(
    themeInfo: ThemeInfo,
    onCustomize: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = themeInfo.primaryColor.copy(alpha = 0.16f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.appearance_current_theme),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = themeInfo.primaryColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                if (onCustomize != null) {
                    TextButton(onClick = onCustomize) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = androidx.compose.ui.res.stringResource(R.string.appearance_customize_theme))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = androidx.compose.ui.res.stringResource(themeInfo.displayNameRes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = androidx.compose.ui.res.stringResource(themeInfo.subtitleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            MiniThemePreview(themeInfo = themeInfo)

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorDot(themeInfo.primaryColor)
                ColorDot(themeInfo.surfaceColor)
                ColorDot(themeInfo.backgroundColor)
                if (themeInfo.accentColor != Color.Unspecified) {
                    ColorDot(themeInfo.accentColor)
                }
            }
        }
    }
}

@Composable
private fun SystemDefaultThemesCard(
    lightThemeInfo: ThemeInfo,
    darkThemeInfo: ThemeInfo,
    darkVariant: ThemeVariant,
    onLightClick: () -> Unit,
    onDarkClick: () -> Unit,
    onDarkVariantChange: (ThemeVariant) -> Unit,
    onCustomizeLight: () -> Unit,
    onCustomizeDark: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.appearance_system_defaults_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.appearance_system_defaults_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            SystemThemeChoiceRow(
                icon = Icons.Outlined.LightMode,
                labelRes = R.string.appearance_system_light_theme,
                themeInfo = lightThemeInfo,
                onClick = onLightClick,
                onCustomize = onCustomizeLight
            )
            Spacer(modifier = Modifier.height(8.dp))
            SystemThemeChoiceRow(
                icon = Icons.Outlined.DarkMode,
                labelRes = R.string.appearance_system_dark_theme,
                themeInfo = darkThemeInfo,
                onClick = onDarkClick,
                onCustomize = onCustomizeDark
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.appearance_system_dark_surface),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                SingleChoiceSegmentedButtonRow {
                    listOf(ThemeVariant.DARK, ThemeVariant.AMOLED).forEachIndexed { index, variant ->
                        SegmentedButton(
                            selected = darkVariant == variant,
                            onClick = { onDarkVariantChange(variant) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            label = {
                                Text(
                                    androidx.compose.ui.res.stringResource(
                                        if (variant == ThemeVariant.DARK) R.string.appearance_variant_dark
                                        else R.string.appearance_variant_amoled
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemThemeChoiceRow(
    icon: ImageVector,
    @StringRes labelRes: Int,
    themeInfo: ThemeInfo,
    onClick: () -> Unit,
    onCustomize: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = androidx.compose.ui.res.stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = androidx.compose.ui.res.stringResource(themeInfo.displayNameRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ColorDot(themeInfo.primaryColor)
        Spacer(modifier = Modifier.width(6.dp))
        ColorDot(themeInfo.backgroundColor)
        IconButton(onClick = onCustomize) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.appearance_customize_theme)
            )
        }
    }
}

@Composable
private fun SystemThemePickerDialog(
    @StringRes titleRes: Int,
    themes: List<ThemeInfo>,
    selectedMode: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = themes,
                        key = { it.mode.name }
                    ) { themeInfo ->
                        ThemePickerRow(
                            themeInfo = themeInfo,
                            isSelected = themeInfo.mode == selectedMode,
                            onClick = { onSelect(themeInfo.mode) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = androidx.compose.ui.res.stringResource(android.R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePickerRow(
    themeInfo: ThemeInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .background(
                if (isSelected) themeInfo.primaryColor.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.large
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ColorDot(themeInfo.primaryColor)
        Spacer(modifier = Modifier.width(8.dp))
        ColorDot(themeInfo.surfaceColor)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = androidx.compose.ui.res.stringResource(themeInfo.displayNameRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = androidx.compose.ui.res.stringResource(themeInfo.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.appearance_selected),
                tint = themeInfo.primaryColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MiniThemePreview(themeInfo: ThemeInfo) {
    val surfaceVariant = if (themeInfo.surfaceVariantColor != Color.Unspecified) {
        themeInfo.surfaceVariantColor
    } else {
        themeInfo.surfaceColor.copy(alpha = 0.9f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(MaterialTheme.shapes.large)
            .background(themeInfo.backgroundColor)
            .border(1.dp, themeInfo.onSurfaceColor.copy(alpha = 0.16f), MaterialTheme.shapes.large)
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(11.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(themeInfo.surfaceColor)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(surfaceVariant)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(7.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(themeInfo.onSurfaceColor.copy(alpha = 0.24f))
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(14.dp)
                .clip(CircleShape)
                .background(themeInfo.primaryColor)
        )
    }
}

@Composable
private fun ColorDot(color: Color) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape)
    )
}

@Composable
private fun ThemeCard(
    themeInfo: ThemeInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onCustomize: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ColorDot(themeInfo.primaryColor)
                    ColorDot(themeInfo.surfaceColor)
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.appearance_selected),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            MiniThemePreview(themeInfo = themeInfo)
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = androidx.compose.ui.res.stringResource(themeInfo.displayNameRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = androidx.compose.ui.res.stringResource(themeInfo.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (onCustomize != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onCustomize,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = androidx.compose.ui.res.stringResource(R.string.appearance_customize_theme))
                }
            }
        }
    }
}

@Composable
private fun CustomThemeEditorDialog(
    initialColors: CustomThemeColors,
    variant: ThemeVariant,
    onDismiss: () -> Unit,
    onSave: (CustomThemeColors) -> Unit
) {
    var draft by remember(initialColors) { mutableStateOf(initialColors) }
    var activePickerRole by remember { mutableStateOf<CustomColorRole?>(null) }
    val hexInputs = remember(initialColors) {
        mutableStateMapOf<CustomColorRole, String>().apply {
            CUSTOM_ROLE_FIELDS.forEach { field ->
                this[field.role] = draft.colorOf(field.role).toHexArgbString()
            }
        }
    }

    val activePickerField = activePickerRole?.let { role ->
        CUSTOM_ROLE_FIELDS.firstOrNull { it.role == role }
    }

    if (activePickerField != null) {
        ColorPickerDialog(
            title = androidx.compose.ui.res.stringResource(activePickerField.labelRes),
            initialArgb = draft.colorOf(activePickerField.role),
            onDismiss = { activePickerRole = null },
            onApply = { argb ->
                draft = draft.withColor(activePickerField.role, argb)
                hexInputs[activePickerField.role] = argb.toHexArgbString()
                activePickerRole = null
            }
        )
    }

    val allValid = CUSTOM_ROLE_FIELDS.all { field ->
        parseHexColorToArgbLong(hexInputs[field.role].orEmpty()) != null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        R.string.appearance_customizer_variant_title,
                        androidx.compose.ui.res.stringResource(
                            when (variant) {
                                ThemeVariant.LIGHT -> R.string.appearance_variant_light
                                ThemeVariant.DARK -> R.string.appearance_variant_dark
                                ThemeVariant.AMOLED -> R.string.appearance_variant_amoled
                            }
                        )
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.appearance_customizer_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                CustomThemePreviewCard(draft)

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(CUSTOM_ROLE_FIELDS.size) { index ->
                        val field = CUSTOM_ROLE_FIELDS[index]
                        val input = hexInputs[field.role].orEmpty()
                        val parsedColor = parseHexColorToArgbLong(input)
                        val hasError = input.isNotBlank() && parsedColor == null

                        OutlinedTextField(
                            value = input,
                            onValueChange = { value ->
                                val sanitized = sanitizeHexInput(value)
                                hexInputs[field.role] = sanitized
                                parseHexColorToArgbLong(sanitized)?.let { argb ->
                                    draft = draft.withColor(field.role, argb)
                                }
                            },
                            singleLine = true,
                            isError = hasError,
                            label = { Text(androidx.compose.ui.res.stringResource(field.labelRes)) },
                            trailingIcon = {
                                val previewColor = parsedColor?.let { Color(it) } ?: Color.Transparent
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                        .background(previewColor, CircleShape)
                                        .clickable {
                                            activePickerRole = field.role
                                        }
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            supportingText = {
                                if (index == 0) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.appearance_customizer_format_hint))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            val reset = CustomThemeColors.default(variant)
                            draft = reset
                            CUSTOM_ROLE_FIELDS.forEach { field ->
                                hexInputs[field.role] = reset.colorOf(field.role).toHexArgbString()
                            }
                        }
                    ) {
                        Text(text = androidx.compose.ui.res.stringResource(R.string.appearance_customizer_reset))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(onClick = onDismiss) {
                        Text(text = androidx.compose.ui.res.stringResource(android.R.string.cancel))
                    }
                    Button(
                        onClick = { onSave(draft) },
                        enabled = allValid
                    ) {
                        Text(text = androidx.compose.ui.res.stringResource(R.string.appearance_customizer_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    initialArgb: Long,
    onDismiss: () -> Unit,
    onApply: (Long) -> Unit
) {
    val initialColorInt = ((initialArgb and 0xFFFFFFFFL).toInt())
    val initialHsv = remember(initialArgb) {
        FloatArray(3).also { hsv ->
            android.graphics.Color.colorToHSV(initialColorInt, hsv)
        }
    }

    var hue by remember(initialArgb) { mutableStateOf(initialHsv[0]) }
    var saturation by remember(initialArgb) { mutableStateOf(initialHsv[1]) }
    var value by remember(initialArgb) { mutableStateOf(initialHsv[2]) }
    var alpha by remember(initialArgb) {
        mutableStateOf(android.graphics.Color.alpha(initialColorInt) / 255f)
    }

    val previewArgb = hsvaToArgbLong(
        hue = hue,
        saturation = saturation,
        value = value,
        alpha = alpha
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color(previewArgb))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = previewArgb.toHexArgbString(),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = androidx.compose.ui.res.stringResource(R.string.appearance_color_hue), style = MaterialTheme.typography.labelMedium)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)

                Text(text = androidx.compose.ui.res.stringResource(R.string.appearance_color_saturation), style = MaterialTheme.typography.labelMedium)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)

                Text(text = androidx.compose.ui.res.stringResource(R.string.appearance_color_brightness), style = MaterialTheme.typography.labelMedium)
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)

                Text(text = androidx.compose.ui.res.stringResource(R.string.appearance_color_alpha), style = MaterialTheme.typography.labelMedium)
                Slider(value = alpha, onValueChange = { alpha = it }, valueRange = 0f..1f)

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = androidx.compose.ui.res.stringResource(android.R.string.cancel))
                    }
                    Button(onClick = { onApply(previewArgb) }) {
                        Text(text = androidx.compose.ui.res.stringResource(R.string.appearance_customizer_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomThemePreviewCard(colors: CustomThemeColors) {
    val previewTheme = ThemeInfo(
        mode = ThemeMode.CUSTOM,
        displayNameRes = R.string.theme_name_custom,
        subtitleRes = R.string.theme_desc_custom,
        category = ThemeCategory.CUSTOM,
        primaryColor = Color(colors.primary),
        backgroundColor = Color(colors.background),
        surfaceColor = Color(colors.surface),
        onSurfaceColor = Color(colors.onSurface),
        accentColor = Color(colors.secondary),
        surfaceVariantColor = Color(colors.surfaceVariant)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.appearance_customize_theme),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            MiniThemePreview(previewTheme)
        }
    }
}

private fun buildThemeCatalog(customThemeColors: CustomThemeColors): List<ThemeInfo> {
    return listOf(
        ThemeInfo(
            mode = ThemeMode.SYSTEM,
            displayNameRes = R.string.theme_name_system_default,
            subtitleRes = R.string.theme_desc_system_default,
            category = ThemeCategory.CUSTOM,
            primaryColor = YouTubeRed,
            backgroundColor = Color(0xFF1C1C1C),
            surfaceColor = Color(0xFF252525),
            onSurfaceColor = Color.White,
            surfaceVariantColor = Color(0xFF333333)
        ),
        ThemeInfo(
            mode = ThemeMode.MATERIAL_YOU,
            displayNameRes = R.string.theme_name_material_you,
            subtitleRes = R.string.theme_desc_material_you,
            category = ThemeCategory.CUSTOM,
            primaryColor = Color(0xFF6750A4),
            backgroundColor = Color(0xFFFFFBFE),
            surfaceColor = Color(0xFFFFFBFE),
            onSurfaceColor = Color(0xFF1C1B1F),
            accentColor = Color(0xFF625B71),
            surfaceVariantColor = Color(0xFFE7E0EC)
        ),
        ThemeInfo(
            mode = ThemeMode.CUSTOM,
            displayNameRes = R.string.theme_name_custom,
            subtitleRes = R.string.theme_desc_custom,
            category = ThemeCategory.CUSTOM,
            primaryColor = Color(customThemeColors.primary),
            backgroundColor = Color(customThemeColors.background),
            surfaceColor = Color(customThemeColors.surface),
            onSurfaceColor = Color(customThemeColors.onSurface),
            accentColor = Color(customThemeColors.secondary),
            surfaceVariantColor = Color(customThemeColors.surfaceVariant)
        ),

        ThemeInfo(
            mode = ThemeMode.MINT_LIGHT,
            displayNameRes = R.string.theme_name_mint_fresh,
            subtitleRes = R.string.theme_desc_mint_fresh,
            category = ThemeCategory.LIGHT,
            primaryColor = MintLightThemeColors.Primary,
            backgroundColor = MintLightThemeColors.Background,
            surfaceColor = MintLightThemeColors.Surface,
            onSurfaceColor = MintLightThemeColors.Text,
            accentColor = MintLightThemeColors.Secondary,
            surfaceVariantColor = MintLightThemeColors.Border
        ),
        ThemeInfo(
            mode = ThemeMode.ROSE_LIGHT,
            displayNameRes = R.string.theme_name_rose_petal,
            subtitleRes = R.string.theme_desc_rose_petal,
            category = ThemeCategory.LIGHT,
            primaryColor = RoseLightThemeColors.Primary,
            backgroundColor = RoseLightThemeColors.Background,
            surfaceColor = RoseLightThemeColors.Surface,
            onSurfaceColor = RoseLightThemeColors.Text,
            accentColor = RoseLightThemeColors.Secondary,
            surfaceVariantColor = RoseLightThemeColors.Border
        ),
        ThemeInfo(
            mode = ThemeMode.SKY_LIGHT,
            displayNameRes = R.string.theme_name_sky_blue,
            subtitleRes = R.string.theme_desc_sky_blue,
            category = ThemeCategory.LIGHT,
            primaryColor = SkyLightThemeColors.Primary,
            backgroundColor = SkyLightThemeColors.Background,
            surfaceColor = SkyLightThemeColors.Surface,
            onSurfaceColor = SkyLightThemeColors.Text,
            accentColor = SkyLightThemeColors.Secondary,
            surfaceVariantColor = SkyLightThemeColors.Border
        ),
        ThemeInfo(
            mode = ThemeMode.CREAM_LIGHT,
            displayNameRes = R.string.theme_name_cream_paper,
            subtitleRes = R.string.theme_desc_cream_paper,
            category = ThemeCategory.LIGHT,
            primaryColor = CreamLightThemeColors.Primary,
            backgroundColor = CreamLightThemeColors.Background,
            surfaceColor = CreamLightThemeColors.Surface,
            onSurfaceColor = CreamLightThemeColors.Text,
            accentColor = CreamLightThemeColors.Secondary,
            surfaceVariantColor = CreamLightThemeColors.Border
        ),

        ThemeInfo(
            mode = ThemeMode.DARK,
            displayNameRes = R.string.theme_name_classic_dark,
            subtitleRes = R.string.theme_desc_classic_dark,
            category = ThemeCategory.DARK,
            primaryColor = YouTubeRed,
            backgroundColor = DarkBackground,
            surfaceColor = DarkSurface,
            onSurfaceColor = TextPrimary,
            surfaceVariantColor = DarkSurfaceVariant
        ),
        ThemeInfo(
            mode = ThemeMode.MONOCHROME,
            displayNameRes = R.string.theme_name_monochrome,
            subtitleRes = R.string.theme_desc_monochrome,
            category = ThemeCategory.DARK,
            primaryColor = Color.White,
            backgroundColor = Color.Black,
            surfaceColor = Color.Black,
            onSurfaceColor = Color.White,
            accentColor = Color(0xFF777777),
            surfaceVariantColor = Color(0xFF111111)
        ),
        ThemeInfo(
            mode = ThemeMode.MIDNIGHT_BLACK,
            displayNameRes = R.string.theme_name_midnight,
            subtitleRes = R.string.theme_desc_midnight,
            category = ThemeCategory.DARK,
            primaryColor = MidnightBlackThemeColors.Primary,
            backgroundColor = MidnightBlackThemeColors.Background,
            surfaceColor = MidnightBlackThemeColors.Surface,
            onSurfaceColor = MidnightBlackThemeColors.Text,
            accentColor = MidnightBlackThemeColors.Secondary,
            surfaceVariantColor = MidnightBlackThemeColors.Border
        ),
        ThemeInfo(
            mode = ThemeMode.OCEAN_BLUE,
            displayNameRes = R.string.theme_name_deep_ocean,
            subtitleRes = R.string.theme_desc_deep_ocean,
            category = ThemeCategory.DARK,
            primaryColor = OceanBlueThemeColors.Primary,
            backgroundColor = OceanBlueThemeColors.Background,
            surfaceColor = OceanBlueThemeColors.Surface,
            onSurfaceColor = OceanBlueThemeColors.Text,
            accentColor = OceanBlueThemeColors.Secondary,
            surfaceVariantColor = OceanBlueThemeColors.Border
        ),
        ThemeInfo(
            mode = ThemeMode.FOREST_GREEN,
            displayNameRes = R.string.theme_name_forest,
            subtitleRes = R.string.theme_desc_forest,
            category = ThemeCategory.DARK,
            primaryColor = ForestGreenThemeColors.Primary,
            backgroundColor = ForestGreenThemeColors.Background,
            surfaceColor = ForestGreenThemeColors.Surface,
            onSurfaceColor = ForestGreenThemeColors.Text,
            accentColor = ForestGreenThemeColors.Secondary,
            surfaceVariantColor = ForestGreenThemeColors.Border
        ),
        ThemeInfo(
            mode = ThemeMode.LAVENDER_MIST,
            displayNameRes = R.string.theme_name_lavender,
            subtitleRes = R.string.theme_desc_lavender,
            category = ThemeCategory.DARK,
            primaryColor = Color(0xFFB39DDB),
            backgroundColor = Color(0xFF120F1A),
            surfaceColor = Color(0xFF1F1A2E),
            onSurfaceColor = Color(0xFFEDE7F6),
            accentColor = Color(0xFF9575CD),
            surfaceVariantColor = Color(0xFF2A2235)
        ),
        ThemeInfo(
            mode = ThemeMode.SUNSET_ORANGE,
            displayNameRes = R.string.theme_name_sunset,
            subtitleRes = R.string.theme_desc_sunset,
            category = ThemeCategory.DARK,
            primaryColor = SunsetOrangeThemeColors.Primary,
            backgroundColor = SunsetOrangeThemeColors.Background,
            surfaceColor = SunsetOrangeThemeColors.Surface,
            onSurfaceColor = SunsetOrangeThemeColors.Text,
            accentColor = SunsetOrangeThemeColors.Secondary,
            surfaceVariantColor = SunsetOrangeThemeColors.Border
        ),
        ThemeInfo(
            mode = ThemeMode.PURPLE_NEBULA,
            displayNameRes = R.string.theme_name_nebula,
            subtitleRes = R.string.theme_desc_nebula,
            category = ThemeCategory.DARK,
            primaryColor = PurpleNebulaThemeColors.Primary,
            backgroundColor = PurpleNebulaThemeColors.Background,
            surfaceColor = PurpleNebulaThemeColors.Surface,
            onSurfaceColor = PurpleNebulaThemeColors.Text,
            accentColor = PurpleNebulaThemeColors.Secondary,
            surfaceVariantColor = PurpleNebulaThemeColors.Border
        ),
        ThemeInfo(
            mode = ThemeMode.ROSE_GOLD,
            displayNameRes = R.string.theme_name_rose_gold,
            subtitleRes = R.string.theme_desc_rose_gold,
            category = ThemeCategory.DARK,
            primaryColor = RoseGoldThemeColors.Primary,
            backgroundColor = RoseGoldThemeColors.Background,
            surfaceColor = RoseGoldThemeColors.Surface,
            onSurfaceColor = RoseGoldThemeColors.Text,
            accentColor = RoseGoldThemeColors.Secondary,
            surfaceVariantColor = RoseGoldThemeColors.Border
        ),
        ThemeInfo(
            mode = ThemeMode.ARCTIC_ICE,
            displayNameRes = R.string.theme_name_arctic,
            subtitleRes = R.string.theme_desc_arctic,
            category = ThemeCategory.DARK,
            primaryColor = ArcticIceThemeColors.Primary,
            backgroundColor = ArcticIceThemeColors.Background,
            surfaceColor = ArcticIceThemeColors.Surface,
            onSurfaceColor = ArcticIceThemeColors.Text,
            accentColor = ArcticIceThemeColors.Secondary,
            surfaceVariantColor = ArcticIceThemeColors.Border
        ),
        ThemeInfo(
            mode = ThemeMode.MINTY_FRESH,
            displayNameRes = R.string.theme_name_mint_night,
            subtitleRes = R.string.theme_desc_mint_night,
            category = ThemeCategory.DARK,
            primaryColor = Color(0xFF80CBC4),
            backgroundColor = Color(0xFF0F1A18),
            surfaceColor = Color(0xFF1A2E2B),
            onSurfaceColor = Color(0xFFE0F2F1),
            accentColor = Color(0xFF4DB6AC),
            surfaceVariantColor = Color(0xFF1E302D)
        ),

        ThemeInfo(
            mode = ThemeMode.CRIMSON_RED,
            displayNameRes = R.string.theme_name_crimson,
            subtitleRes = R.string.theme_desc_crimson,
            category = ThemeCategory.CUSTOM,
            primaryColor = CrimsonRedThemeColors.Primary,
            backgroundColor = CrimsonRedThemeColors.Background,
            surfaceColor = CrimsonRedThemeColors.Surface,
            onSurfaceColor = CrimsonRedThemeColors.Text,
            accentColor = CrimsonRedThemeColors.Secondary,
            surfaceVariantColor = CrimsonRedThemeColors.Border
        ),
        ThemeInfo(
            mode = ThemeMode.COSMIC_VOID,
            displayNameRes = R.string.theme_name_cosmic_void,
            subtitleRes = R.string.theme_desc_cosmic_void,
            category = ThemeCategory.CUSTOM,
            primaryColor = Color(0xFF7C4DFF),
            backgroundColor = Color(0xFF050505),
            surfaceColor = Color(0xFF121212),
            onSurfaceColor = Color(0xFFE0E0E0),
            accentColor = Color(0xFF651FFF),
            surfaceVariantColor = Color(0xFF1A1225)
        ),
        ThemeInfo(
            mode = ThemeMode.SOLAR_FLARE,
            displayNameRes = R.string.theme_name_solar_flare,
            subtitleRes = R.string.theme_desc_solar_flare,
            category = ThemeCategory.CUSTOM,
            primaryColor = Color(0xFFFFD740),
            backgroundColor = Color(0xFF1A1500),
            surfaceColor = Color(0xFF2E2600),
            onSurfaceColor = Color(0xFFFFFDE7),
            accentColor = Color(0xFFFFAB00),
            surfaceVariantColor = Color(0xFF352A10)
        ),
        ThemeInfo(
            mode = ThemeMode.CYBERPUNK,
            displayNameRes = R.string.theme_name_cyberpunk,
            subtitleRes = R.string.theme_desc_cyberpunk,
            category = ThemeCategory.CUSTOM,
            primaryColor = Color(0xFFFF00FF),
            backgroundColor = Color(0xFF0D001A),
            surfaceColor = Color(0xFF1F0033),
            onSurfaceColor = Color(0xFFE0E0E0),
            accentColor = Color(0xFF00FFFF),
            surfaceVariantColor = Color(0xFF200F35)
        ),
        ThemeInfo(
            mode = ThemeMode.ROYAL_GOLD,
            displayNameRes = R.string.theme_name_royal_gold,
            subtitleRes = R.string.theme_desc_royal_gold,
            category = ThemeCategory.CUSTOM,
            primaryColor = RoyalGoldThemeColors.Primary,
            backgroundColor = RoyalGoldThemeColors.Background,
            surfaceColor = RoyalGoldThemeColors.Surface,
            onSurfaceColor = RoyalGoldThemeColors.Text,
            accentColor = RoyalGoldThemeColors.Secondary,
            surfaceVariantColor = RoyalGoldThemeColors.Border
        ),
        ThemeInfo(
            mode = ThemeMode.NORDIC_HORIZON,
            displayNameRes = R.string.theme_name_nordic,
            subtitleRes = R.string.theme_desc_nordic,
            category = ThemeCategory.CUSTOM,
            primaryColor = NordicHorizonThemeColors.Primary,
            backgroundColor = NordicHorizonThemeColors.Background,
            surfaceColor = NordicHorizonThemeColors.Surface,
            onSurfaceColor = NordicHorizonThemeColors.Text,
            accentColor = NordicHorizonThemeColors.Secondary,
            surfaceVariantColor = NordicHorizonThemeColors.Border
        ),
        ThemeInfo(
            mode = ThemeMode.ESPRESSO,
            displayNameRes = R.string.theme_name_espresso,
            subtitleRes = R.string.theme_desc_espresso,
            category = ThemeCategory.CUSTOM,
            primaryColor = EspressoThemeColors.Primary,
            backgroundColor = EspressoThemeColors.Background,
            surfaceColor = EspressoThemeColors.Surface,
            onSurfaceColor = EspressoThemeColors.Text,
            accentColor = EspressoThemeColors.Secondary,
            surfaceVariantColor = EspressoThemeColors.Border
        ),
        ThemeInfo(
            mode = ThemeMode.GUNMETAL,
            displayNameRes = R.string.theme_name_gunmetal,
            subtitleRes = R.string.theme_desc_gunmetal,
            category = ThemeCategory.CUSTOM,
            primaryColor = GunmetalThemeColors.Primary,
            backgroundColor = GunmetalThemeColors.Background,
            surfaceColor = GunmetalThemeColors.Surface,
            onSurfaceColor = GunmetalThemeColors.Text,
            accentColor = GunmetalThemeColors.Secondary,
            surfaceVariantColor = GunmetalThemeColors.Border
        )
    )
}

private fun sanitizeHexInput(raw: String): String {
    val trimmed = raw.trim().uppercase()
    val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    val filteredBody = withHash.drop(1).filter { it.isDigit() || it in 'A'..'F' }
    return "#${filteredBody.take(8)}"
}

private fun parseHexColorToArgbLong(input: String): Long? {
    val body = input.trim().removePrefix("#")
    val normalized = when (body.length) {
        6 -> "FF$body"
        8 -> body
        else -> return null
    }
    return normalized.toLongOrNull(16)
}

private fun hsvaToArgbLong(
    hue: Float,
    saturation: Float,
    value: Float,
    alpha: Float
): Long {
    val argbInt = android.graphics.Color.HSVToColor(
        (alpha * 255f).roundToInt().coerceIn(0, 255),
        floatArrayOf(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
    )
    return argbInt.toLong() and 0xFFFFFFFFL
}

private fun Long.toHexArgbString(): String {
    return "#%08X".format(this and 0xFFFFFFFFL)
}
