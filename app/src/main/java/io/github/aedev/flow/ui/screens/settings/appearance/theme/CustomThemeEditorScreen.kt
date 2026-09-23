package io.github.aedev.flow.ui.screens.settings.appearance.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.nav
import io.github.aedev.flow.ui.components.settings.toggleGroup
import io.github.aedev.flow.ui.components.shared.FlowToggleOption
import io.github.aedev.flow.ui.screens.settings.appearance.themeVariantLabel
import io.github.aedev.flow.ui.screens.settings.index.CustomThemeIndex
import io.github.aedev.flow.ui.theme.CustomTheme
import io.github.aedev.flow.ui.theme.ThemeVariant
import io.github.aedev.flow.ui.theme.toColorScheme
import kotlinx.coroutines.launch

private val SwatchSize = 32.dp
private val SwatchBorder = 1.dp
private val PreviewHeight = 132.dp
private val PreviewPadding = 12.dp
private val PreviewSpacing = 8.dp
private val PreviewBarHeight = 14.dp
private val PreviewCardHeight = 40.dp
private val PreviewLineHeight = 8.dp
private val PreviewFabSize = 24.dp
private const val PREVIEW_LINE_FRACTION = 0.6f
private const val OPAQUE = 0xFF000000L

/**
 * Edits one custom theme, style by style, over the thirteen roles Flow Desktop's editor offers.
 * Edits stay a draft until Save; saving a theme that is not the one in use offers to switch to it.
 */
@Composable
internal fun CustomThemeEditorScreen(
    themeId: String?,
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: CustomThemesViewModel = hiltViewModel(),
) {
    val themes by viewModel.themes.collectAsStateWithLifecycle()
    val inUseId by viewModel.inUseId.collectAsStateWithLifecycle()
    val saved = themes?.firstOrNull { it.id == themeId }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by rememberSaveable { mutableStateOf(ThemeVariant.DARK) }
    var draft by remember(saved?.id) { mutableStateOf(saved) }
    var pickingRole by remember { mutableStateOf<ThemeRole?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    val current = draft ?: saved

    val savedMessage = stringResource(R.string.settings_custom_theme_saved)
    val useLabel = stringResource(R.string.settings_custom_theme_use)
    val variantOptions = ThemeVariant.entries.map { FlowToggleOption(it, stringResource(themeVariantLabel(it))) }
    val colors = current?.colorsFor(editing)
    val scheme = remember(colors, editing) { colors?.toColorScheme(editing) }

    SettingsPage(
        title = current?.name ?: stringResource(R.string.settings_custom_theme_editor_title),
        onBack = onBack,
        highlight = highlight,
        snackbarHostState = snackbarHostState,
        actions = {
            TextButton(
                enabled = current != null && current != saved,
                onClick = {
                    val theme = current ?: return@TextButton
                    viewModel.save(theme)
                    if (theme.id != inUseId) {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(savedMessage, actionLabel = useLabel)
                            if (result == SnackbarResult.ActionPerformed) viewModel.use(theme.id)
                        }
                    }
                },
            ) { Text(stringResource(R.string.appearance_customizer_save)) }
        },
    ) {
        if (current == null || colors == null || scheme == null) return@SettingsPage
        group(key = "custom_theme.details") {
            nav(CustomThemeIndex.name, value = current.name, icon = Icons.Outlined.Edit, showChevron = false, onClick = { renaming = true })
            toggleGroup(CustomThemeIndex.variant, variantOptions, editing, { editing = it })
        }
        item("custom_theme.preview") { CustomThemePreview(scheme) }
        ThemeRoleGroups.forEach { roleGroup ->
            group(key = "custom_theme.${roleGroup.key}", header = roleGroup.titleRes) {
                roleGroup.roles.forEach { role ->
                    row("custom_theme.${role.key}") { shape ->
                        ColorRoleRow(
                            label = stringResource(role.labelRes),
                            color = role.read(colors),
                            shape = shape,
                            onClick = { pickingRole = role },
                        )
                    }
                }
            }
        }
    }

    val theme = current ?: return
    pickingRole?.let { role ->
        ColorPickerDialog(
            title = stringResource(role.labelRes),
            initialArgb = role.read(theme.colorsFor(editing)).toArgb().toLong() and 0xFFFFFFFFL,
            allowAlpha = false,
            onDismiss = { pickingRole = null },
            onApply = { argb ->
                val picked = Color(argb or OPAQUE)
                draft = theme.withColors(editing, role.write(theme.colorsFor(editing), picked))
                pickingRole = null
            },
        )
    }
    if (renaming) {
        ThemeNameDialog(
            title = stringResource(R.string.settings_custom_theme_rename),
            initialName = theme.name,
            confirmLabel = stringResource(R.string.settings_custom_theme_rename),
            onConfirm = { name, _ ->
                draft = theme.copy(name = name.take(CustomTheme.MAX_NAME_LENGTH))
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColorRoleRow(
    label: String,
    color: Color,
    shape: Shape,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        verticalAlignment = Alignment.CenterVertically,
        shapes = ListItemDefaults.shapes(shape = shape),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        leadingContent = {
            Box(
                modifier =
                    Modifier
                        .size(SwatchSize)
                        .clip(CircleShape)
                        .background(color)
                        .border(SwatchBorder, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        },
        supportingContent = { Text((color.toArgb().toLong() and 0xFFFFFFFFL).toHexRgb()) },
    ) {
        Text(label)
    }
}

/**
 * A miniature screen painted with the style being edited: background, a top bar, a card with text,
 * and an accent button, so an edit can be judged in context before saving.
 */
@Composable
private fun CustomThemePreview(scheme: ColorScheme) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(PreviewHeight)
                .clip(MaterialTheme.shapes.large)
                .background(scheme.background)
                .border(SwatchBorder, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                .padding(PreviewPadding),
        verticalArrangement = Arrangement.spacedBy(PreviewSpacing),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(PreviewBarHeight)
                .clip(MaterialTheme.shapes.small)
                .background(scheme.surfaceContainer),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(PreviewCardHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(scheme.surfaceContainerHigh)
                .padding(PreviewSpacing),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(PREVIEW_LINE_FRACTION)
                    .height(PreviewLineHeight)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(scheme.onSurface),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(PreviewFabSize)
                    .clip(MaterialTheme.shapes.small)
                    .background(scheme.primary),
            )
        }
    }
}
