package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.local.PlayerPreferences
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.lyrics.PreferredLyricsProvider
import io.github.aedev.flow.ui.components.rememberFlowSheetState
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.Add

private val audioLanguageOptions = listOf(
    "original" to "Original (Native)",
    "en" to "English",
    "es" to "Spanish",
    "fr" to "French",
    "de" to "German",
    "pt" to "Portuguese",
    "it" to "Italian",
    "ru" to "Russian",
    "ja" to "Japanese",
    "ko" to "Korean",
    "zh" to "Chinese",
    "ar" to "Arabic",
    "hi" to "Hindi",
    "nl" to "Dutch",
    "pl" to "Polish",
    "tr" to "Turkish",
    "vi" to "Vietnamese",
    "th" to "Thai",
    "id" to "Indonesian"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPreferences = remember { PlayerPreferences(context) }
    
    val overlayCastEnabled by playerPreferences.overlayCastEnabled.collectAsState(initial = true)
    val overlayCcEnabled by playerPreferences.overlayCcEnabled.collectAsState(initial = false)
    val overlayPipEnabled by playerPreferences.overlayPipEnabled.collectAsState(initial = false)
    val autoPipEnabled by playerPreferences.autoPipEnabled.collectAsState(initial = false)
    val overlayAutoplayEnabled by playerPreferences.overlayAutoplayEnabled.collectAsState(initial = false)
    val overlaySleepTimerEnabled by playerPreferences.overlaySleepTimerEnabled.collectAsState(initial = true)
    
    val autoplayEnabled by playerPreferences.autoplayEnabled.collectAsState(initial = true)
    val skipSilenceEnabled by playerPreferences.skipSilenceEnabled.collectAsState(initial = false)
    val sponsorBlockEnabled by playerPreferences.sponsorBlockEnabled.collectAsState(initial = false)
    val deArrowEnabled by playerPreferences.deArrowEnabled.collectAsState(initial = false)
    val manualPipButtonEnabled by playerPreferences.manualPipButtonEnabled.collectAsState(initial = true)
    val backgroundPlayEnabled by playerPreferences.backgroundPlayEnabled.collectAsState(initial = false)
    val shortsBackgroundPlay by playerPreferences.shortsBackgroundPlay.collectAsState(initial = false)
    val preferredAudioLanguage by playerPreferences.preferredAudioLanguage.collectAsState(initial = "original")
    val playDuringCalls by playerPreferences.playDuringCalls.collectAsState(initial = false)
    val currentLyricsProvider by playerPreferences.preferredLyricsProvider.collectAsState(initial = "LRCLIB")
    val doubleTapSeekSeconds by playerPreferences.doubleTapSeekSeconds.collectAsState(initial = 10)
    val miniPlayerContinueWatchingEnabled by playerPreferences.miniPlayerContinueWatchingEnabled.collectAsState(initial = true)
    val videoLoopEnabled by playerPreferences.videoLoopEnabled.collectAsState(initial = false)
    val sbSubmitEnabled by playerPreferences.sbSubmitEnabled.collectAsState(initial = false)
    val sbUserId by playerPreferences.sbUserId.collectAsState(initial = null)
    val rememberPlaybackSpeed by playerPreferences.rememberPlaybackSpeed.collectAsState(initial = false)
    
    var showAudioLanguageDialog by remember { mutableStateOf(false) }
    var showLyricsProviderSheet by remember { mutableStateOf(false) }
    var showSeekDurationDialog by remember { mutableStateOf(false) }
    var showUserIdDialog by remember { mutableStateOf(false) }

    val customSpeedsEnabled by playerPreferences.customSpeedsEnabled.collectAsState(initial = false)
    val customSpeedPresetsRaw by playerPreferences.customSpeedPresets.collectAsState(initial = "")
    val speedSliderEnabled by playerPreferences.speedSliderEnabled.collectAsState(initial = false)
    var newSpeedInput by remember { mutableStateOf("") }
    var speedInputError by remember { mutableStateOf(false) }
    val parsedPresets = remember(customSpeedPresetsRaw) {
        customSpeedPresetsRaw
            .split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
            .filter { it in 0.1f..10.0f }
            .sortedBy { it }
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
                        text = stringResource(R.string.player_settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
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
                Text(
                    text = stringResource(R.string.player_settings_overlay_controls),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Cast,
                        title = stringResource(R.string.player_settings_overlay_cast),
                        subtitle = stringResource(R.string.player_settings_overlay_cast_subtitle),
                        checked = overlayCastEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setOverlayCastEnabled(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.ClosedCaption,
                        title = stringResource(R.string.player_settings_overlay_cc),
                        subtitle = stringResource(R.string.player_settings_overlay_cc_subtitle),
                        checked = overlayCcEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setOverlayCcEnabled(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.PictureInPicture,
                        title = stringResource(R.string.player_settings_overlay_pip),
                        subtitle = stringResource(R.string.player_settings_overlay_pip_subtitle),
                        checked = overlayPipEnabled,
                        onCheckedChange = { 
                            coroutineScope.launch { 
                                playerPreferences.setOverlayPipEnabled(it)
                                playerPreferences.setManualPipButtonEnabled(it)
                            } 
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.PictureInPictureAlt,
                        title = stringResource(R.string.player_settings_auto_pip_title),
                        subtitle = stringResource(R.string.player_settings_auto_pip_subtitle),
                        checked = autoPipEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setAutoPipEnabled(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Rounded.SlowMotionVideo,
                        title = stringResource(R.string.player_settings_overlay_autoplay),
                        subtitle = stringResource(R.string.player_settings_overlay_autoplay_subtitle),
                        checked = overlayAutoplayEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setOverlayAutoplayEnabled(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Bedtime,
                        title = stringResource(R.string.player_settings_overlay_sleep_timer),
                        subtitle = stringResource(R.string.player_settings_overlay_sleep_timer_subtitle),
                        checked = overlaySleepTimerEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setOverlaySleepTimerEnabled(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.PlayCircleOutline,
                        title = stringResource(R.string.player_settings_mini_player_continue_watching_title),
                        subtitle = stringResource(R.string.player_settings_mini_player_continue_watching_subtitle),
                        checked = miniPlayerContinueWatchingEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setMiniPlayerContinueWatchingEnabled(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Repeat,
                        title = stringResource(R.string.global_loop),
                        subtitle = stringResource(R.string.global_loop_subtitle),
                        checked = videoLoopEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setVideoLoopEnabled(it) } }
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.PlayCircle,
                        title = stringResource(R.string.player_settings_background_play),
                        subtitle = stringResource(R.string.player_settings_background_play_subtitle),
                        checked = backgroundPlayEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setBackgroundPlayEnabled(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.OndemandVideo,
                        title = stringResource(R.string.player_settings_shorts_background_play),
                        subtitle = stringResource(R.string.player_settings_shorts_background_play_subtitle),
                        checked = shortsBackgroundPlay,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setShortsBackgroundPlay(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.SkipNext,
                        title = stringResource(R.string.player_settings_autoplay),
                        subtitle = stringResource(R.string.player_settings_autoplay_subtitle),
                        checked = autoplayEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setAutoplayEnabled(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.GraphicEq,
                        title = stringResource(R.string.player_settings_skip_silence),
                        subtitle = stringResource(R.string.player_settings_skip_silence_subtitle),
                        checked = skipSilenceEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setSkipSilenceEnabled(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Speed,
                        title = stringResource(R.string.player_settings_remember_speed),
                        subtitle = stringResource(R.string.player_settings_remember_speed_subtitle),
                        checked = rememberPlaybackSpeed,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setRememberPlaybackSpeed(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = painterResource(R.drawable.ic_block),
                        title = stringResource(R.string.player_settings_sponsorblock),
                        subtitle = stringResource(R.string.player_settings_sponsorblock_subtitle),
                        checked = sponsorBlockEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setSponsorBlockEnabled(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.AutoFixHigh,
                        title = stringResource(R.string.player_settings_dearrow),
                        subtitle = stringResource(R.string.player_settings_dearrow_subtitle),
                        checked = deArrowEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setDeArrowEnabled(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Tune,
                        title = stringResource(R.string.player_settings_custom_speeds_title),
                        subtitle = stringResource(R.string.player_settings_custom_speeds_subtitle),
                        checked = customSpeedsEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setCustomSpeedsEnabled(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Rounded.SlowMotionVideo,
                        title = stringResource(R.string.player_settings_speed_slider_title),
                        subtitle = stringResource(R.string.player_settings_speed_slider_subtitle),
                        checked = speedSliderEnabled,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setSpeedSliderEnabled(it) } }
                    )
                }
                AnimatedVisibility(visible = customSpeedsEnabled && !speedSliderEnabled) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.player_settings_custom_speeds_header),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            if (parsedPresets.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.player_settings_custom_speeds_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            } else {
                                parsedPresets.forEachIndexed { index, speed ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${speed}x",
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = {
                                            val updated = parsedPresets.filter { it != speed }
                                            coroutineScope.launch {
                                                playerPreferences.setCustomSpeedPresets(updated.joinToString(","))
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Remove,
                                                contentDescription = stringResource(R.string.player_settings_custom_speeds_remove),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    if (index < parsedPresets.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newSpeedInput,
                                    onValueChange = {
                                        newSpeedInput = it
                                        speedInputError = false
                                    },
                                    placeholder = { Text(stringResource(R.string.player_settings_custom_speeds_add_hint)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    isError = speedInputError,
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledIconButton(
                                    onClick = {
                                        val value = newSpeedInput.trim().replace(",", ".").toFloatOrNull()
                                        if (value == null || value < 0.1f || value > 10.0f) {
                                            speedInputError = true
                                        } else {
                                            val updated = (parsedPresets + value).distinct().sortedBy { it }
                                            coroutineScope.launch {
                                                playerPreferences.setCustomSpeedPresets(updated.joinToString(","))
                                            }
                                            newSpeedInput = ""
                                            speedInputError = false
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.player_settings_custom_speeds_add)
                                    )
                                }
                            }
                            if (speedInputError) {
                                Text(
                                    text = stringResource(R.string.player_settings_custom_speeds_input_error),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // Audio Settings Section
            item {
                Text(
                    text = stringResource(R.string.player_settings_header_audio),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SettingsGroup {
                    SettingsClickItem(
                        icon = Icons.Outlined.VolumeUp,
                        title = stringResource(R.string.player_settings_audio_language),
                        subtitle = audioLanguageOptions.find { it.first == preferredAudioLanguage }?.second 
                            ?: stringResource(R.string.player_settings_audio_original),
                        onClick = { showAudioLanguageDialog = true }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Call,
                        title = stringResource(R.string.player_settings_play_during_calls),
                        subtitle = stringResource(R.string.player_settings_play_during_calls_subtitle),
                        checked = playDuringCalls,
                        onCheckedChange = { coroutineScope.launch { playerPreferences.setPlayDuringCalls(it) } }
                    )
                }
            }

            // Gestures Settings Section
            item {
                Text(
                    text = stringResource(R.string.player_settings_header_gestures),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SettingsGroup {
                    SettingsClickItem(
                        icon = Icons.Outlined.TouchApp,
                        title = stringResource(R.string.player_settings_double_tap_seek),
                        subtitle = stringResource(R.string.player_settings_double_tap_seek_subtitle_template, doubleTapSeekSeconds),
                        onClick = { showSeekDurationDialog = true }
                    )
                }
            }

            // Lyrics Settings Section
            item {
                SectionHeader(text = stringResource(R.string.lyrics_provider_title))
                SettingsGroup {
                    SettingsClickItem(
                        icon =painterResource(R.drawable.ic_lyrics),
                        title = stringResource(R.string.lyrics_provider_title),
                        subtitle = getLyricsProviderLabel(PreferredLyricsProvider.fromString(currentLyricsProvider)),
                        onClick = { showLyricsProviderSheet = true }
                    )
                }
                Text(
                    text = stringResource(R.string.lyrics_provider_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }

            // Contribute to SponsorBlock section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(text = stringResource(R.string.sb_contribute_header))
                SettingsGroup {
                    SettingsSwitchItem(
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
                        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
    
    // Audio Language Selection Dialog
    if (showAudioLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showAudioLanguageDialog = false },
            title = { 
                Text(
                    stringResource(R.string.player_settings_audio_language_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                ) 
            },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        stringResource(R.string.player_settings_audio_language_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    audioLanguageOptions.forEach { (code, displayName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        playerPreferences.setPreferredAudioLanguage(code)
                                    }
                                    showAudioLanguageDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preferredAudioLanguage == code,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (code == "original") {
                                    Text(
                                        text = stringResource(R.string.player_settings_audio_original_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioLanguageDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    // Lyrics Provider Selection Sheet
    if (showLyricsProviderSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLyricsProviderSheet = false },
            sheetState = rememberFlowSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.lyrics_provider_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )

                PreferredLyricsProvider.values().forEach { provider ->
                    val isSelected = currentLyricsProvider == provider.name
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    playerPreferences.setPreferredLyricsProvider(provider.name)
                                }
                                showLyricsProviderSheet = false
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = getLyricsProviderLabel(provider),
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
                    
                    if (provider != PreferredLyricsProvider.values().last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }

    // Seek Duration Selection Dialog
    if (showSeekDurationDialog) {
        val seekOptions = listOf(5, 10, 15, 20, 30)
        AlertDialog(
            onDismissRequest = { showSeekDurationDialog = false },
            title = {
                Text(
                    stringResource(R.string.player_settings_double_tap_seek_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.player_settings_double_tap_seek_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    seekOptions.forEach { seconds ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        playerPreferences.setDoubleTapSeekSeconds(seconds)
                                    }
                                    showSeekDurationDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = doubleTapSeekSeconds == seconds,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.player_settings_double_tap_seek_subtitle_template, seconds),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSeekDurationDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    // SponsorBlock User ID Dialog
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

@Composable
private fun getLyricsProviderLabel(provider: PreferredLyricsProvider): String {
    return when (provider) {
        PreferredLyricsProvider.LRCLIB -> stringResource(R.string.lyrics_provider_lrclib)
        PreferredLyricsProvider.BETTER_LYRICS -> stringResource(R.string.lyrics_provider_better_lyrics)
        PreferredLyricsProvider.SIMPMUSIC -> stringResource(R.string.lyrics_provider_simpmusic)
    }
}

@Composable
private fun SettingsClickItem(
    icon: Any,
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
        when (icon) {
            is ImageVector -> {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            is Painter -> {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
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
