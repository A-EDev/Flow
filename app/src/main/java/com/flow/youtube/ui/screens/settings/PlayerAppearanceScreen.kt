package com.flow.youtube.ui.screens.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.flow.youtube.R
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.data.local.SliderStyle
import com.flow.youtube.data.local.SponsorBlockAction
import com.flow.youtube.ui.screens.music.player.components.PlayerSliderTrack
import com.flow.youtube.ui.screens.music.player.components.SquigglySlider
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerAppearanceScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPreferences = remember { PlayerPreferences(context) }
    
    val currentSliderStyle by playerPreferences.sliderStyle.collectAsState(initial = SliderStyle.DEFAULT)
    val swipeGesturesEnabled by playerPreferences.swipeGesturesEnabled.collectAsState(initial = true)
    val sbSubmitEnabled by playerPreferences.sbSubmitEnabled.collectAsState(initial = false)
    val sbUserId by playerPreferences.sbUserId.collectAsState(initial = null)

    var showUserIdDialog by remember { mutableStateOf(false) }

    // SponsorBlock per-category actions
    val sbCategoriesAndLabels = listOf(
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
    val sbActions = sbCategoriesAndLabels.associate { (category, _) ->
        category to playerPreferences.sbActionForCategory(category).collectAsState(initial = SponsorBlockAction.SKIP).value
    }
    val sbColors = sbCategoriesAndLabels.associate { (category, _) ->
        category to playerPreferences.sbColorForCategory(category).collectAsState(initial = null).value
    }

    var showStyleSheet by remember { mutableStateOf(false) }

    // User ID dialog
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

    if (showStyleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStyleSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.player_appearance_style_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                
                LazyColumn {
                    items(SliderStyle.values()) { style ->
                        val isSelected = currentSliderStyle == style
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        playerPreferences.setSliderStyle(style)
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(getStyleLabelResInScreen(style)),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                PreviewPlayerSlider(style = style)
                            }
                        }
                        
                        if (style != SliderStyle.values().last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }


    Scaffold(
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
                        text = stringResource(R.string.player_appearance_title),
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
            item {
                SectionHeader(text = stringResource(R.string.player_appearance_header))
            }
            
            item {
                SettingsGroup {
                    SettingsItem(
                        icon = painterResource(R.drawable.ic_progress_bar_style),
                        title = stringResource(R.string.player_appearance_style_title),
                        subtitle = stringResource(getStyleLabelResInScreen(currentSliderStyle)),
                        onClick = { showStyleSheet = true }
                    )
                }
            }
            
            item {
                Text(
                    text = stringResource(R.string.player_appearance_style_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(text = stringResource(R.string.player_appearance_gestures_header))
            }

            item {
                SettingsGroup {
                    SettingsToggleItem(
                        icon = painterResource(R.drawable.ic_swipe_gesture),
                        title = stringResource(R.string.player_appearance_swipe_gestures_title),
                        subtitle = stringResource(R.string.player_appearance_swipe_gestures_subtitle),
                        checked = swipeGesturesEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                playerPreferences.setSwipeGesturesEnabled(enabled)
                            }
                        }
                    )
                }
            }

            // SponsorBlock Segment Actions section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(text = stringResource(R.string.sb_segments_header))
            }

            item {
                SettingsGroup {
                    sbCategoriesAndLabels.forEachIndexed { index, (category, labelRes) ->
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
                        if (index < sbCategoriesAndLabels.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            // Contribute to SponsorBlock section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(text = stringResource(R.string.sb_contribute_header))
            }

            item {
                SettingsGroup {
                    SettingsToggleItem(
                        icon = painterResource(R.drawable.ic_block),
                        title = stringResource(R.string.sb_contribute_toggle_title),
                        subtitle = stringResource(R.string.sb_contribute_toggle_subtitle),
                        checked = sbSubmitEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                playerPreferences.setSbSubmitEnabled(enabled)
                            }
                        }
                    )
                    if (sbSubmitEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
}



@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewPlayerSlider(style: SliderStyle) {
    val progress = 0.4f 
    val duration = 100f 
    val position = duration * progress 
    when (style) {
        SliderStyle.METROLIST -> {
            Slider(
                value = position,
                onValueChange = {},
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )
        }
        SliderStyle.METROLIST_SLIM -> {
            Slider(
                value = position,
                onValueChange = {},
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )
        }
        SliderStyle.SQUIGGLY -> {
            SquigglySlider(
                value = position,
                onValueChange = {},
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    thumbColor = MaterialTheme.colorScheme.primary
                ),
                isPlaying = true
            )
        }
        SliderStyle.SLIM -> {
             Slider(
                value = position,
                onValueChange = {},
                valueRange = 0f..duration,
                thumb = { Spacer(modifier = Modifier.size(0.dp)) }, 
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        trackHeight = 4.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
            )
        }
        SliderStyle.DEFAULT -> {
            val animatedTrackHeight = 12.dp
            
            Slider(
                value = position,
                onValueChange = {},
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .shadow(8.dp, CircleShape)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                },
                track = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(animatedTrackHeight)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            MaterialTheme.colorScheme.primary
                                        )
                                    )
                                )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )
        }
    }
}


/**
 * A row showing a SponsorBlock category label with a dropdown to select the action
 * and an optional color swatch the user can tap to change the segment highlight colour.
 */
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

    // Preset colors for SB segments
    val presetColors = remember {
    listOf(
        // Original basic colors
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
        Color(0xFF006400), // dark green (Exclusive Access)
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
                    androidx.compose.foundation.layout.FlowRow(
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
                    // Reset to default
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
        // Color swatch
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

        // Action dropdown
        Box {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionLabel(selectedAction),
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
                                text = actionLabel(action),
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
private fun actionLabel(action: SponsorBlockAction): String = when (action) {
    SponsorBlockAction.SKIP -> stringResource(R.string.sb_action_skip)
    SponsorBlockAction.MUTE -> stringResource(R.string.sb_action_mute)
    SponsorBlockAction.SHOW_TOAST -> stringResource(R.string.sb_action_show_toast)
    SponsorBlockAction.IGNORE -> stringResource(R.string.sb_action_ignore)
}

private fun getStyleLabelResInScreen(style: SliderStyle): Int {    return when (style) {
        SliderStyle.DEFAULT -> R.string.style_default
        SliderStyle.METROLIST -> R.string.style_metrolist
        SliderStyle.METROLIST_SLIM -> R.string.style_metrolist_slim
        SliderStyle.SQUIGGLY -> R.string.style_squiggly
        SliderStyle.SLIM -> R.string.style_slim
    }
}
