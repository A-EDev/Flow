package io.github.aedev.flow.ui.screens.settings.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.R
import io.github.aedev.flow.data.recommendation.FlowPersona
import io.github.aedev.flow.ui.components.settings.SettingsDestination
import io.github.aedev.flow.ui.components.settings.SettingsGroupScope
import io.github.aedev.flow.ui.components.settings.SettingsListScope
import io.github.aedev.flow.ui.components.settings.SettingsTarget
import io.github.aedev.flow.ui.components.settings.nav
import io.github.aedev.flow.ui.components.settings.switch
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.components.shared.FlowSwitchRow
import io.github.aedev.flow.ui.screens.settings.index.DestinationIndex
import io.github.aedev.flow.ui.screens.settings.index.HomeIndex

@Immutable
internal data class SettingsHomeState(
    val selected: SettingsDestination?,
    val persona: FlowPersona?,
    val deepFlow: DeepFlowState,
    /** Shown on the update row while a check the user started is running; null when idle. */
    val updateCheckingLabel: String?,
)

internal class SettingsHomeActions(
    val onOpen: (SettingsTarget) -> Unit,
    val onOpenPersona: () -> Unit,
    val onOpenDonations: () -> Unit,
    val onResetPersona: () -> Unit,
    val onDeepFlowChange: (Boolean) -> Unit,
    val onDurationClick: () -> Unit,
    val onSaveHistoryChange: (Boolean) -> Unit,
    val onCheckForUpdates: () -> Unit,
)

/** The settings list when no search is running. */
internal fun SettingsListScope.homeContent(
    state: SettingsHomeState,
    actions: SettingsHomeActions,
) {
    item(HomeIndex.persona.key) {
        PersonaEntryCard(persona = state.persona, onOpen = actions.onOpenPersona, onReset = actions.onResetPersona)
    }

    fun SettingsGroupScope.page(destination: SettingsDestination) =
        nav(
            entry = DestinationIndex.entry(destination),
            onClick = { actions.onOpen(SettingsTarget(destination)) },
            icon = destinationIcon(destination),
            selected = state.selected == destination,
            showChevron = state.selected == null,
        )

    group(key = "home.engine", header = R.string.settings_flow_engine_header) {
        row(HomeIndex.deepFlow.key) { shape ->
            FlowSwitchRow(
                title = stringResource(HomeIndex.deepFlow.title),
                supportingText = deepFlowStatus(state.deepFlow) ?: stringResource(R.string.deep_flow_mode_subtitle),
                checked = state.deepFlow.active,
                onCheckedChange = actions.onDeepFlowChange,
                leadingIcon = HomeRowIcons.DeepFlow,
                shape = shape,
            )
        }
        row(HomeIndex.deepFlowDuration.key) { shape ->
            FlowNavRow(
                title = stringResource(HomeIndex.deepFlowDuration.title),
                supportingText = deepFlowDurationLabel(state.deepFlow.expireHours),
                onClick = actions.onDurationClick,
                leadingIcon = HomeRowIcons.DeepFlowDuration,
                shape = shape,
            )
        }
        switch(
            HomeIndex.deepFlowHistory,
            checked = state.deepFlow.saveToHistory,
            onCheckedChange = actions.onSaveHistoryChange,
            icon = HomeRowIcons.DeepFlowHistory,
        )
        page(SettingsDestination.TOPICS)
    }
    group(key = "home.look", header = R.string.settings_group_look) {
        page(SettingsDestination.APPEARANCE)
        page(SettingsDestination.LANGUAGE_REGION)
    }
    group(key = "home.watching", header = R.string.settings_group_watching) {
        page(SettingsDestination.PLAYBACK)
        page(SettingsDestination.QUALITY)
        page(SettingsDestination.CONTENT)
        page(SettingsDestination.INTEGRATIONS)
    }
    group(key = "home.data", header = R.string.settings_group_data) {
        page(SettingsDestination.BACKUP)
        page(SettingsDestination.SYNC)
        page(SettingsDestination.HISTORY)
        page(SettingsDestination.DOWNLOADS)
    }
    group(key = "home.system", header = R.string.settings_group_system) {
        page(SettingsDestination.NOTIFICATIONS)
        page(SettingsDestination.NETWORK)
        page(SettingsDestination.WELLBEING)
    }
    group(key = "home.about", header = R.string.settings_header_about) {
        page(SettingsDestination.ABOUT)
        page(SettingsDestination.DIAGNOSTICS)
        if (BuildConfig.UPDATER_ENABLED) {
            nav(
                HomeIndex.checkForUpdates,
                onClick = actions.onCheckForUpdates,
                value = state.updateCheckingLabel,
                enabled = state.updateCheckingLabel == null,
                showChevron = false,
                icon = HomeRowIcons.Updates,
            )
        }
        nav(HomeIndex.support, onClick = actions.onOpenDonations, icon = HomeRowIcons.Support)
    }
}
