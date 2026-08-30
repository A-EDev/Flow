package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.BufferProfile
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import kotlinx.coroutines.launch

@Composable
fun BufferSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPreferences = remember { PlayerPreferences(context) }

    val minBufferMs by playerPreferences.minBufferMs.collectAsStateWithLifecycle(initialValue = 30000)
    val maxBufferMs by playerPreferences.maxBufferMs.collectAsStateWithLifecycle(initialValue = 100000)
    val bufferForPlaybackMs by playerPreferences.bufferForPlaybackMs.collectAsStateWithLifecycle(initialValue = 1000)
    val bufferForPlaybackAfterRebufferMs by
        playerPreferences.bufferForPlaybackAfterRebufferMs.collectAsStateWithLifecycle(initialValue = 2500)
    val currentBufferProfile by
        playerPreferences.bufferProfile.collectAsStateWithLifecycle(initialValue = BufferProfile.STABLE)
    val cacheSizeMb by playerPreferences.mediaCacheSizeMb.collectAsStateWithLifecycle(initialValue = 500)

    var tempMinBuffer by remember { mutableFloatStateOf(minBufferMs.toFloat()) }
    var tempMaxBuffer by remember { mutableFloatStateOf(maxBufferMs.toFloat()) }
    var tempPlaybackBuffer by remember { mutableFloatStateOf(bufferForPlaybackMs.toFloat()) }
    var tempRebuffer by remember { mutableFloatStateOf(bufferForPlaybackAfterRebufferMs.toFloat()) }

    LaunchedEffect(currentBufferProfile, minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs) {
        if (currentBufferProfile != BufferProfile.CUSTOM) {
            tempMinBuffer = currentBufferProfile.minBuffer.toFloat()
            tempMaxBuffer = currentBufferProfile.maxBuffer.toFloat()
            tempPlaybackBuffer = currentBufferProfile.playbackBuffer.toFloat()
            tempRebuffer = currentBufferProfile.rebufferBuffer.toFloat()
        } else {
            if (minBufferMs.toFloat() != tempMinBuffer) tempMinBuffer = minBufferMs.toFloat()
            if (maxBufferMs.toFloat() != tempMaxBuffer) tempMaxBuffer = maxBufferMs.toFloat()
            if (bufferForPlaybackMs.toFloat() != tempPlaybackBuffer) tempPlaybackBuffer = bufferForPlaybackMs.toFloat()
            if (bufferForPlaybackAfterRebufferMs.toFloat() != tempRebuffer) tempRebuffer = bufferForPlaybackAfterRebufferMs.toFloat()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.buffer_settings_title),
                onBack = onNavigateBack,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            item(key = "description") {
                Text(
                    text = stringResource(R.string.buffer_settings_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item(key = "profile-header") {
                SectionHeader(text = stringResource(R.string.buffer_settings_header_profile))
            }

            item(key = "profile-options") {
                SettingsGroup {
                    val profiles = BufferProfile.entries.filter { it != BufferProfile.CUSTOM }
                    profiles.forEachIndexed { index, profile ->
                        ProfileSelectionItem(
                            title = stringResource(getProfileNameRes(profile)),
                            subtitle = getProfileDescriptionRes(profile)?.let { stringResource(it) }.orEmpty(),
                            isSelected = currentBufferProfile == profile,
                            onClick = {
                                coroutineScope.launch {
                                    playerPreferences.setBufferProfile(profile)
                                    playerPreferences.setMinBufferMs(profile.minBuffer)
                                    playerPreferences.setMaxBufferMs(profile.maxBuffer)
                                    playerPreferences.setBufferForPlaybackMs(profile.playbackBuffer)
                                    playerPreferences.setBufferForPlaybackAfterRebufferMs(profile.rebufferBuffer)
                                }
                            },
                        )
                        if (index < profiles.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }

            item(key = "custom-header") {
                SectionHeader(text = stringResource(R.string.buffer_settings_header_custom))
            }

            item(key = "custom-option") {
                SettingsGroup {
                    ProfileSelectionItem(
                        title = stringResource(R.string.buffer_profile_custom),
                        subtitle = stringResource(R.string.buffer_profile_custom_desc),
                        isSelected = currentBufferProfile == BufferProfile.CUSTOM,
                        onClick = { coroutineScope.launch { playerPreferences.setBufferProfile(BufferProfile.CUSTOM) } },
                    )
                }
            }

            if (currentBufferProfile == BufferProfile.CUSTOM) {
                item(key = "custom-controls") {
                    SettingsGroup {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.buffer_custom_mode_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Spacer(Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.buffer_label_min, tempMinBuffer.toInt() / 1000),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Slider(
                                value = tempMinBuffer,
                                onValueChange = { tempMinBuffer = it },
                                onValueChangeFinished = {
                                    coroutineScope.launch { playerPreferences.setMinBufferMs(tempMinBuffer.toInt()) }
                                },
                                valueRange = 1000f..60000f,
                                steps = 59,
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.buffer_label_max, tempMaxBuffer.toInt() / 1000),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Slider(
                                value = tempMaxBuffer,
                                onValueChange = { tempMaxBuffer = it },
                                onValueChangeFinished = {
                                    coroutineScope.launch { playerPreferences.setMaxBufferMs(tempMaxBuffer.toInt()) }
                                },
                                valueRange = 30000f..180000f,
                                steps = 30,
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.buffer_label_playback, tempPlaybackBuffer.toInt() / 1000),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Slider(
                                value = tempPlaybackBuffer,
                                onValueChange = { tempPlaybackBuffer = it },
                                onValueChangeFinished = {
                                    coroutineScope.launch {
                                        playerPreferences.setBufferForPlaybackMs(tempPlaybackBuffer.toInt())
                                    }
                                },
                                valueRange = 500f..5000f,
                                steps = 9,
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.buffer_label_rebuffer, tempRebuffer.toInt() / 1000),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Slider(
                                value = tempRebuffer,
                                onValueChange = { tempRebuffer = it },
                                onValueChangeFinished = {
                                    coroutineScope.launch {
                                        playerPreferences.setBufferForPlaybackAfterRebufferMs(tempRebuffer.toInt())
                                    }
                                },
                                valueRange = 1000f..10000f,
                                steps = 9,
                            )
                        }
                    }
                }
            } else {
                item(key = "custom-hint") {
                    Text(
                        text = stringResource(R.string.buffer_switch_to_custom),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }

            item(key = "cache-header") {
                SectionHeader(text = stringResource(R.string.cache_size_header))
            }
            item(key = "cache-options") {
                Text(
                    text = stringResource(R.string.cache_size_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                val cacheOptions =
                    listOf(
                        100 to stringResource(R.string.cache_size_100mb),
                        200 to stringResource(R.string.cache_size_200mb),
                        500 to stringResource(R.string.cache_size_500mb),
                        0 to stringResource(R.string.cache_size_unlimited),
                    )
                Column {
                    cacheOptions.forEach { (sizeMb, label) ->
                        ProfileSelectionItem(
                            title = label,
                            subtitle = "",
                            isSelected = cacheSizeMb == sizeMb,
                            onClick = { coroutineScope.launch { playerPreferences.setMediaCacheSizeMb(sizeMb) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileSelectionItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = isSelected,
                    onClick = onClick,
                    role = Role.RadioButton,
                ).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun getProfileNameRes(profile: BufferProfile): Int =
    when (profile) {
        BufferProfile.STABLE -> R.string.buffer_profile_stable
        BufferProfile.AGGRESSIVE -> R.string.buffer_profile_aggressive
        BufferProfile.DATASAVER -> R.string.buffer_profile_datasaver
        BufferProfile.CUSTOM -> R.string.buffer_profile_custom
        else -> R.string.buffer_profile_stable
    }

private fun getProfileDescriptionRes(profile: BufferProfile): Int? =
    when (profile) {
        BufferProfile.STABLE -> R.string.buffer_desc_stable
        BufferProfile.AGGRESSIVE -> R.string.buffer_desc_aggressive
        BufferProfile.DATASAVER -> R.string.buffer_desc_datasaver
        else -> null
    }
