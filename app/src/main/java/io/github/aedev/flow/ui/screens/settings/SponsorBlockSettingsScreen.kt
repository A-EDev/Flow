package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ThumbDownOffAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SponsorBlockAction
import kotlinx.coroutines.launch

private val SB_CATEGORIES_AND_LABELS = listOf(
    "sponsor"          to R.string.sb_category_sponsor,
    "intro"            to R.string.sb_category_intro,
    "outro"            to R.string.sb_category_outro,
    "selfpromo"        to R.string.sb_category_selfpromo,
    "interaction"      to R.string.sb_category_interaction,
    "music_offtopic"   to R.string.sb_category_music_offtopic,
    "filler"           to R.string.sb_category_filler,
    "preview"          to R.string.sb_category_preview,
    "exclusive_access" to R.string.sb_category_exclusive_access
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SponsorBlockSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPreferences = remember { PlayerPreferences(context) }

    val sponsorBlockEnabled by playerPreferences.sponsorBlockEnabled.collectAsState(initial = false)
    val deArrowEnabled by playerPreferences.deArrowEnabled.collectAsState(initial = false)
    val deArrowBadgeEnabled by playerPreferences.deArrowBadgeEnabled.collectAsState(initial = false)
    val rytdEnabled by playerPreferences.rytdEnabled.collectAsState(initial = true)

    val sbActions = SB_CATEGORIES_AND_LABELS.associate { (category, _) ->
        category to playerPreferences.sbActionForCategory(category).collectAsState(initial = SponsorBlockAction.SKIP).value
    }
    val sbColors = SB_CATEGORIES_AND_LABELS.associate { (category, _) ->
        category to playerPreferences.sbColorForCategory(category).collectAsState(initial = null).value
    }

    val sbSubmitEnabled by playerPreferences.sbSubmitEnabled.collectAsState(initial = false)
    val sbUserId by playerPreferences.sbUserId.collectAsState(initial = null)

    var showUserIdDialog by remember { mutableStateOf(false) }

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
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.sb_settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SectionHeader(text = stringResource(R.string.sb_settings_general_header)) }

            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = painterResource(R.drawable.ic_block),
                        title = stringResource(R.string.player_settings_sponsorblock),
                        subtitle = stringResource(R.string.player_settings_sponsorblock_subtitle),
                        checked = sponsorBlockEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setSponsorBlockEnabled(it) } }
                    )
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsSwitchItem(
                        icon = Icons.Outlined.AutoFixHigh,
                        title = stringResource(R.string.player_settings_dearrow),
                        subtitle = stringResource(R.string.player_settings_dearrow_subtitle),
                        checked = deArrowEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setDeArrowEnabled(it) } }
                    )
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsSwitchItem(
                        icon = Icons.Outlined.AutoFixHigh,
                        title = stringResource(R.string.dearrow_badge_toggle),
                        subtitle = stringResource(R.string.dearrow_badge_toggle_subtitle),
                        checked = deArrowBadgeEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setDeArrowBadgeEnabled(it) } }
                    )
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsSwitchItem(
                        icon = Icons.Outlined.ThumbDownOffAlt,
                        title = stringResource(R.string.player_settings_rytd_title),
                        subtitle = stringResource(R.string.player_settings_rytd_subtitle),
                        checked = rytdEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setRytdEnabled(it) } }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(text = stringResource(R.string.sb_segments_header))
            }

            item {
                SettingsGroup {
                    SB_CATEGORIES_AND_LABELS.forEachIndexed { index, (category, labelRes) ->
                        SponsorBlockCategoryRow(
                            label = stringResource(labelRes),
                            selectedAction = sbActions[category] ?: SponsorBlockAction.SKIP,
                            customColorArgb = sbColors[category],
                            onActionSelected = { action ->
                                coroutineScope.launch {
                                    playerPreferences.setSbActionForCategory(category, action)
                                }
                            },
                            onColorChanged = { colorArgb ->
                                coroutineScope.launch {
                                    playerPreferences.setSbColorForCategory(category, colorArgb)
                                }
                            }
                        )
                        if (index < SB_CATEGORIES_AND_LABELS.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(text = stringResource(R.string.sb_contribute_header))
            }

            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = painterResource(R.drawable.ic_block),
                        title = stringResource(R.string.sb_contribute_toggle_title),
                        subtitle = stringResource(R.string.sb_contribute_toggle_subtitle),
                        checked = sbSubmitEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch { playerPreferences.setSbSubmitEnabled(enabled) }
                        }
                    )
                    if (sbSubmitEnabled) {
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showUserIdDialog = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.sb_user_id_title),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = sbUserId?.let { it.take(8) + "…" }
                                        ?: stringResource(R.string.sb_user_id_not_set),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showUserIdDialog) {
        var inputId by remember { mutableStateOf(sbUserId ?: "") }
        AlertDialog(
            onDismissRequest = { showUserIdDialog = false },
            title = { Text(stringResource(R.string.sb_user_id_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.sb_user_id_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inputId,
                        onValueChange = { inputId = it },
                        label = { Text(stringResource(R.string.sb_user_id_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        val id = inputId.trim().ifBlank { playerPreferences.getOrCreateSbUserId() }
                        playerPreferences.setSbUserId(id)
                    }
                    showUserIdDialog = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showUserIdDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SponsorBlockCategoryRow(
    label: String,
    selectedAction: SponsorBlockAction,
    customColorArgb: Int?,
    onActionSelected: (SponsorBlockAction) -> Unit,
    onColorChanged: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    val presetColors = remember {
        listOf(
            Color(0xFF00D400), // green
            Color(0xFFFFFF00), // yellow
            Color(0xFF0000FF), // blue
            Color(0xFFFF0000), // red
            Color(0xFFFF7700), // orange
            Color(0xFFFF69B4), // pink
            Color(0xFF7700FF), // purple
            Color(0xFF00FFFF), // cyan
            Color(0xFFFFFFFF), // white
            Color(0xFF008080), // teal
            Color(0xFF3F51B5), // indigo
            Color(0xFFFFC107), // amber
            Color(0xFFCDDC39), // lime
            Color(0xFF673AB7), // deep purple
            Color(0xFFFF5722), // deep orange
            Color(0xFFE91E63), // magenta / rose
            Color(0xFF006400), // dark green
            Color(0xFF8B4513), // brown
            Color(0xFF808080), // gray
            Color(0xFFC0C0C0), // silver
            Color(0xFFFFD700), // gold
            Color(0xFF40E0D0), // turquoise
            Color(0xFF4B0082), // indigo / dark violet
        )
    }

    if (showColorPicker) {
        Dialog(onDismissRequest = { showColorPicker = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sb_color_picker_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        presetColors.forEach { color ->
                            val isSelected = customColorArgb == color.toArgb()
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable {
                                        onColorChanged(color.toArgb())
                                        showColorPicker = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.Black.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            onColorChanged(null)
                            showColorPicker = false
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.sb_color_reset))
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val swatchColor = customColorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .clickable { showColorPicker = true }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Box {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sbActionLabel(selectedAction),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 180.dp)
            ) {
                SponsorBlockAction.values().forEach { action ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = sbActionLabel(action),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        onClick = {
                            onActionSelected(action)
                            expanded = false
                        },
                        trailingIcon = if (action == selectedAction) ({
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }) else null
                    )
                }
            }
        }
    }
}

@Composable
private fun sbActionLabel(action: SponsorBlockAction): String = when (action) {
    SponsorBlockAction.SKIP -> stringResource(R.string.sb_action_skip)
    SponsorBlockAction.MUTE -> stringResource(R.string.sb_action_mute)
    SponsorBlockAction.SHOW_TOAST -> stringResource(R.string.sb_action_show_toast)
    SponsorBlockAction.IGNORE -> stringResource(R.string.sb_action_ignore)
}
