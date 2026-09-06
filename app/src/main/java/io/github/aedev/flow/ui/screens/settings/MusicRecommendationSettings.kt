/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.CONTENT_LANGUAGE_FOLLOW_APP
import io.github.aedev.flow.data.local.PlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.ServiceList
import java.util.Locale

@Composable
fun MusicRecommendationsSection(
    preferences: PlayerPreferences,
    coroutineScope: CoroutineScope,
) {
    val endlessRadioEnabled by preferences.musicEndlessRadioEnabled.collectAsState(initial = true)
    val contentLanguage by preferences.contentLanguage.collectAsState(initial = CONTENT_LANGUAGE_FOLLOW_APP)
    val contentCountry by preferences.trendingRegion.collectAsState(initial = "US")
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showCountryDialog by remember { mutableStateOf(false) }

    val followAppLabel = stringResource(R.string.music_content_language_follow_app)
    val languageOptions =
        remember(followAppLabel) {
            listOf(PickerOption(key = CONTENT_LANGUAGE_FOLLOW_APP, label = followAppLabel)) + contentLanguageOptions()
        }
    val regionOptions = remember { regionPickerOptions() }

    SectionHeader(text = stringResource(R.string.music_prefs_section_title))
    SettingsGroup {
        SettingsSwitchItem(
            icon = Icons.Outlined.Radio,
            title = stringResource(R.string.music_endless_radio_title),
            subtitle = stringResource(R.string.music_endless_radio_desc),
            checked = endlessRadioEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch { preferences.setMusicEndlessRadioEnabled(enabled) }
            },
        )
        HorizontalDivider(
            Modifier.padding(start = 56.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
        SettingsItem(
            icon = Icons.Outlined.Translate,
            title = stringResource(R.string.music_content_language_title),
            subtitle = languageOptions.firstOrNull { it.key == contentLanguage }?.label ?: followAppLabel,
            onClick = { showLanguageDialog = true },
        )
        HorizontalDivider(
            Modifier.padding(start = 56.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
        SettingsItem(
            icon = Icons.Outlined.Public,
            title = stringResource(R.string.music_content_country_title),
            subtitle = REGION_NAMES[contentCountry] ?: contentCountry,
            onClick = { showCountryDialog = true },
        )
    }

    if (showLanguageDialog) {
        SearchablePickerDialog(
            title = stringResource(R.string.music_content_language_dialog_title),
            options = languageOptions,
            selectedKey = contentLanguage,
            onSelect = { code ->
                coroutineScope.launch { preferences.setContentLanguage(code) }
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showCountryDialog) {
        SearchablePickerDialog(
            title = stringResource(R.string.settings_region_dialog_title),
            options = regionOptions,
            selectedKey = contentCountry,
            onSelect = { code ->
                coroutineScope.launch { preferences.setTrendingRegion(code) }
                showCountryDialog = false
            },
            onDismiss = { showCountryDialog = false },
        )
    }
}

private fun contentLanguageOptions(): List<PickerOption> {
    val displayLocale = Locale.getDefault()
    return ServiceList.YouTube.supportedLocalizations
        .map { localization ->
            val locale = Locale.forLanguageTag(localization.localizationCode)
            val native = locale.getDisplayName(locale)
            PickerOption(
                key = localization.localizationCode,
                label = native,
                secondaryLabel = locale.getDisplayName(displayLocale).takeIf { it != native },
            )
        }.sortedBy { it.label.lowercase(displayLocale) }
}
