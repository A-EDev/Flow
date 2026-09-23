package io.github.aedev.flow.ui.screens.settings

import androidx.compose.runtime.Composable
import io.github.aedev.flow.ui.components.settings.SettingsDestination
import io.github.aedev.flow.ui.components.settings.SettingsTarget
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
    legacyAppearance: @Composable (onBack: () -> Unit) -> Unit,
) {
    val back = onBack ?: {}
    when (target.destination) {
        SettingsDestination.HOME,
        SettingsDestination.APPEARANCE,
        SettingsDestination.THEME,
        SettingsDestination.CUSTOM_THEME,
        -> legacyAppearance(back)

        SettingsDestination.NAVIGATION_BAR,
        SettingsDestination.CONTENT,
        -> ContentSettingsScreen(onBackClick = back)

        SettingsDestination.DATE_TIME -> DateTimeSettingsScreen(onNavigateBack = back)

        SettingsDestination.PLAYER_APPEARANCE -> PlayerAppearanceScreen(onNavigateBack = back)

        SettingsDestination.LANGUAGE_REGION -> ContentSettingsScreen(onBackClick = back)

        SettingsDestination.PLAYBACK -> PlayerSettingsScreen(onNavigateBack = back)

        SettingsDestination.BUFFER -> BufferSettingsScreen(onNavigateBack = back)

        SettingsDestination.QUALITY -> VideoQualitySettingsScreen(onNavigateBack = back)

        SettingsDestination.TOPICS -> UserPreferencesScreen(onNavigateBack = back)

        SettingsDestination.INTEGRATIONS -> SponsorBlockSettingsScreen(onNavigateBack = back)

        SettingsDestination.BACKUP -> ImportDataScreen(onNavigateBack = back)

        SettingsDestination.SYNC -> SyncScreen(onNavigateBack = back)

        SettingsDestination.HISTORY -> SearchHistorySettingsScreen(onNavigateBack = back)

        SettingsDestination.DOWNLOADS -> DownloadSettingsScreen(onNavigateBack = back)

        SettingsDestination.NOTIFICATIONS -> NotificationSettingsScreen(onNavigateBack = back)

        SettingsDestination.NETWORK -> ProxySettingsScreen(onNavigateBack = back)

        SettingsDestination.WELLBEING -> TimeManagementScreen(onNavigateBack = back)

        SettingsDestination.ABOUT -> AboutScreen(onNavigateBack = back, onNavigateToDonations = onOpenDonations)

        SettingsDestination.DIAGNOSTICS -> DiagnosticsScreen(onNavigateBack = back)
    }
}
