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
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.toggleGroup
import io.github.aedev.flow.ui.components.shared.FlowToggleOption
import io.github.aedev.flow.ui.screens.settings.appearance.themeVariantLabel
import io.github.aedev.flow.ui.screens.settings.index.CustomThemeIndex
import io.github.aedev.flow.ui.theme.CustomColorRole
import io.github.aedev.flow.ui.theme.CustomThemeColors
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant
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

/**
 * Edits the three custom palettes, one per style, role by role. Edits stay a draft until Save, and
 * saving a palette that is not the one in use offers to switch to it.
 */
@Composable
internal fun CustomThemeScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by rememberSaveable { mutableStateOf(if (settings.followsSystem) ThemeVariant.DARK else settings.variant) }
    val drafts = remember { mutableStateMapOf<ThemeVariant, CustomThemeColors>() }
    val draft = drafts[editing] ?: settings.palettes.forVariant(editing)
    val dirty = drafts[editing]?.let { it != settings.palettes.forVariant(editing) } == true
    var pickingRole by remember { mutableStateOf<Pair<CustomColorRole, Int>?>(null) }

    val savedMessage = stringResource(R.string.settings_custom_theme_saved)
    val useLabel = stringResource(R.string.settings_custom_theme_use)
    val variantOptions = ThemeVariant.entries.map { FlowToggleOption(it, stringResource(themeVariantLabel(it))) }

    SettingsPage(
        title = stringResource(R.string.appearance_customizer_title),
        onBack = onBack,
        highlight = highlight,
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = { drafts[editing] = CustomThemeColors.default(editing) }) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.appearance_customizer_reset))
            }
            TextButton(
                enabled = dirty,
                onClick = {
                    val variant = editing
                    viewModel.saveCustomPalette(variant, draft)
                    drafts.remove(variant)
                    if (settings.mode != ThemeMode.CUSTOM || settings.variant != variant) {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(savedMessage, actionLabel = useLabel)
                            if (result == SnackbarResult.ActionPerformed) viewModel.useCustomTheme(variant)
                        }
                    }
                },
            ) { Text(stringResource(R.string.appearance_customizer_save)) }
        },
    ) {
        group(key = "custom_theme.editing") {
            toggleGroup(CustomThemeIndex.variant, variantOptions, editing, { editing = it })
        }
        item("custom_theme.preview") { CustomThemePreview(draft) }
        CustomRoleGroups.forEach { roleGroup ->
            group(key = "custom_theme.${roleGroup.key}", header = roleGroup.titleRes) {
                roleGroup.roles.forEach { role ->
                    row("custom_theme.${role.first.name}") { shape ->
                        ColorRoleRow(
                            label = stringResource(role.second),
                            argb = draft.colorOf(role.first),
                            shape = shape,
                            onClick = { pickingRole = role },
                        )
                    }
                }
            }
        }
    }

    pickingRole?.let { (role, labelRes) ->
        ColorPickerDialog(
            title = stringResource(labelRes),
            initialArgb = draft.colorOf(role),
            onDismiss = { pickingRole = null },
            onApply = { argb ->
                drafts[editing] = draft.withColor(role, argb)
                pickingRole = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColorRoleRow(
    label: String,
    argb: Long,
    shape: Shape,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.shapes(shape = shape),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        leadingContent = {
            Box(
                modifier =
                    Modifier
                        .size(SwatchSize)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(SwatchBorder, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        },
        supportingContent = { Text(argb.toHexArgb()) },
    ) {
        Text(label)
    }
}

/**
 * A miniature screen painted with the draft: background, a top bar, a card, text and an action
 * button, so an edit can be judged in context before saving.
 */
@Composable
private fun CustomThemePreview(colors: CustomThemeColors) {
    fun role(role: CustomColorRole) = Color(colors.colorOf(role))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(PreviewHeight)
                .clip(MaterialTheme.shapes.large)
                .background(role(CustomColorRole.BACKGROUND))
                .border(SwatchBorder, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                .padding(PreviewPadding),
        verticalArrangement = Arrangement.spacedBy(PreviewSpacing),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(PreviewBarHeight)
                .clip(MaterialTheme.shapes.small)
                .background(role(CustomColorRole.SURFACE_CONTAINER)),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(PreviewCardHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(role(CustomColorRole.SURFACE_CONTAINER_HIGH))
                .padding(PreviewSpacing),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(PREVIEW_LINE_FRACTION)
                    .height(PreviewLineHeight)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(role(CustomColorRole.ON_SURFACE)),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(PreviewFabSize)
                    .clip(MaterialTheme.shapes.small)
                    .background(role(CustomColorRole.PRIMARY_CONTAINER)),
            )
        }
    }
}
