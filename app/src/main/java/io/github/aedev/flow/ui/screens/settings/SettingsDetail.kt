package io.github.aedev.flow.ui.screens.settings

import androidx.compose.runtime.Composable
import io.github.aedev.flow.ui.components.settings.SettingsDestination
import io.github.aedev.flow.ui.components.settings.SettingsTarget
import io.github.aedev.flow.ui.screens.settings.appearance.AppearanceScreen
import io.github.aedev.flow.ui.screens.settings.appearance.DateTimeScreen
import io.github.aedev.flow.ui.screens.settings.appearance.NavigationBarScreen
import io.github.aedev.flow.ui.screens.settings.appearance.player.PlayerAppearanceScreen
import io.github.aedev.flow.ui.screens.settings.appearance.theme.CustomThemeScreen
import io.github.aedev.flow.ui.screens.settings.appearance.theme.ThemeScreen
import io.github.aedev.flow.ui.screens.settings.backup.BackupScreen
import io.github.aedev.flow.ui.screens.settings.content.ContentSettingsScreen
import io.github.aedev.flow.ui.screens.settings.diagnostics.DiagnosticsScreen
import io.github.aedev.flow.ui.screens.settings.downloads.DownloadSettingsScreen
import io.github.aedev.flow.ui.screens.settings.history.HistorySettingsScreen
import io.github.aedev.flow.ui.screens.settings.integrations.IntegrationsScreen
import io.github.aedev.flow.ui.screens.settings.network.NetworkSettingsScreen
import io.github.aedev.flow.ui.screens.settings.notifications.NotificationSettingsScreen
import io.github.aedev.flow.ui.screens.settings.playback.BufferSettingsScreen
import io.github.aedev.flow.ui.screens.settings.playback.PlaybackSettingsScreen
import io.github.aedev.flow.ui.screens.settings.quality.QualitySettingsScreen
import io.github.aedev.flow.ui.screens.settings.region.LanguageRegionScreen
import io.github.aedev.flow.ui.screens.settings.topics.TopicPreferencesScreen
import io.github.aedev.flow.ui.screens.settings.wellbeing.WellbeingScreen
import io.github.aedev.flow.ui.screens.sync.SyncScreen

/**
 * The page a [SettingsTarget] opens. [onBack] is null for the root page beside the settings list,
 * where there is nothing to go back to; [onNavigate] opens a sub-page over this one.
 */
@Composable
internal fun SettingsDetail(
    target: SettingsTarget,
    onBack: (() -> Unit)?,
    onNavigate: (SettingsTarget) -> Unit,
    onOpenDonations: () -> Unit,
) {
    val back = onBack ?: {}
    when (target.destination) {
        SettingsDestination.HOME,
        SettingsDestination.APPEARANCE,
        -> AppearanceScreen(onBack = onBack, highlight = target.highlight, onNavigate = onNavigate)

        SettingsDestination.THEME -> ThemeScreen(onBack = onBack, highlight = target.highlight, onNavigate = onNavigate)

        SettingsDestination.CUSTOM_THEME -> CustomThemeScreen(onBack = onBack, highlight = target.highlight)

        SettingsDestination.NAVIGATION_BAR -> NavigationBarScreen(onBack = onBack, highlight = target.highlight)

        SettingsDestination.CONTENT -> ContentSettingsScreen(onBack = onBack, highlight = target.highlight)

        SettingsDestination.DATE_TIME -> DateTimeScreen(onBack = onBack, highlight = target.highlight)

        SettingsDestination.PLAYER_APPEARANCE -> PlayerAppearanceScreen(onBack = onBack, highlight = target.highlight)

        SettingsDestination.LANGUAGE_REGION -> LanguageRegionScreen(onBack = onBack, highlight = target.highlight)

        SettingsDestination.PLAYBACK -> PlaybackSettingsScreen(onBack = onBack, highlight = target.highlight, onNavigate = onNavigate)

        SettingsDestination.BUFFER -> BufferSettingsScreen(onBack = onBack, highlight = target.highlight)

        SettingsDestination.QUALITY -> QualitySettingsScreen(onBack = onBack, highlight = target.highlight, tab = target.tab)

        SettingsDestination.TOPICS -> TopicPreferencesScreen(onBack = onBack, highlight = target.highlight, tab = target.tab)

        SettingsDestination.INTEGRATIONS -> IntegrationsScreen(onBack = onBack, highlight = target.highlight)

        SettingsDestination.BACKUP -> BackupScreen(onBack = onBack, highlight = target.highlight, tab = target.tab)

        SettingsDestination.SYNC -> SyncScreen(onNavigateBack = back)

        SettingsDestination.HISTORY -> HistorySettingsScreen(onBack = onBack, highlight = target.highlight)

        SettingsDestination.DOWNLOADS -> DownloadSettingsScreen(onBack = onBack, highlight = target.highlight)

        SettingsDestination.NOTIFICATIONS -> NotificationSettingsScreen(onBack = onBack, highlight = target.highlight)

        SettingsDestination.NETWORK -> NetworkSettingsScreen(onBack = onBack, highlight = target.highlight)

        SettingsDestination.WELLBEING -> WellbeingScreen(onBack = onBack, highlight = target.highlight)

        SettingsDestination.ABOUT -> AboutScreen(onNavigateBack = back, onNavigateToDonations = onOpenDonations)

        SettingsDestination.DIAGNOSTICS -> DiagnosticsScreen(onBack = onBack, highlight = target.highlight)
    }
}
