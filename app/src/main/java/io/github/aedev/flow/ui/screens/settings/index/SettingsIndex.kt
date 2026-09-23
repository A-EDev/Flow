package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.ui.components.settings.SettingEntry

/** Every option in Settings, in the order the pages show them. */
internal object SettingsIndex {
    val all: List<SettingEntry> by lazy {
        HomeIndex.all +
            TasteIndex.all +
            DestinationIndex.all +
            AppearanceIndex.all +
            ThemeIndex.all +
            CustomThemeIndex.all +
            NavigationBarIndex.all +
            DateTimeIndex.all +
            PlayerAppearanceIndex.all +
            LanguageRegionIndex.all +
            PlaybackIndex.all +
            BufferIndex.all +
            QualityIndex.all +
            ContentIndex.all +
            TopicsIndex.all +
            IntegrationsIndex.all +
            BackupIndex.all +
            DownloadsIndex.all +
            NotificationsIndex.all +
            NetworkIndex.all +
            HistoryIndex.all +
            WellbeingIndex.all +
            DiagnosticsIndex.all +
            AboutIndex.all
    }
}
