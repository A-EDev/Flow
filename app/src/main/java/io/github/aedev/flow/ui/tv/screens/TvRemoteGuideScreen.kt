package io.github.aedev.flow.ui.tv.screens

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingAvailability
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination
import io.github.aedev.flow.ui.components.settings.SettingsListScope
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.info

private class TvGuideEntry(
    @StringRes val keyRes: Int,
    @StringRes val descriptionRes: Int,
)

private val navigationEntries =
    listOf(
        TvGuideEntry(R.string.tv_guide_key_dpad, R.string.tv_guide_dpad),
        TvGuideEntry(R.string.tv_guide_key_ok, R.string.tv_guide_ok),
        TvGuideEntry(R.string.tv_guide_key_back, R.string.tv_guide_back),
        TvGuideEntry(R.string.tv_guide_key_left_edge, R.string.tv_guide_left_edge),
        TvGuideEntry(R.string.tv_guide_key_rail, R.string.tv_guide_rail),
    )

private val searchEntries =
    listOf(
        TvGuideEntry(R.string.tv_guide_key_ok, R.string.tv_guide_search_field),
        TvGuideEntry(R.string.tv_guide_key_voice, R.string.tv_guide_search_voice),
        TvGuideEntry(R.string.tv_guide_key_down, R.string.tv_guide_search_down),
        TvGuideEntry(R.string.tv_guide_key_ok, R.string.tv_guide_search_open),
    )

private val playbackEntries =
    listOf(
        TvGuideEntry(R.string.tv_guide_key_ok, R.string.tv_guide_player_toggle),
        TvGuideEntry(R.string.tv_guide_key_left_right, R.string.tv_guide_player_seek),
        TvGuideEntry(R.string.tv_guide_key_hold, R.string.tv_guide_player_hold),
        TvGuideEntry(R.string.tv_guide_key_up, R.string.tv_guide_player_up),
        TvGuideEntry(R.string.tv_guide_key_down, R.string.tv_guide_player_down),
        TvGuideEntry(R.string.tv_guide_key_media, R.string.tv_guide_player_media),
        TvGuideEntry(R.string.tv_guide_key_back, R.string.tv_guide_player_back),
    )

/** Every remote interaction the TV interface supports, as a settings page under About. */
@Composable
fun TvRemoteGuideScreen(onBack: (() -> Unit)?) {
    SettingsPage(
        title = stringResource(R.string.tv_remote_guide_title),
        subtitle = stringResource(R.string.tv_remote_guide_subtitle),
        onBack = onBack,
    ) {
        guideGroup("guide.navigation", R.string.tv_guide_navigation_title, navigationEntries)
        guideGroup("guide.search", R.string.tv_guide_search_title, searchEntries)
        guideGroup("guide.playback", R.string.tv_guide_playback_title, playbackEntries)
    }
}

private fun SettingsListScope.guideGroup(
    key: String,
    @StringRes title: Int,
    entries: List<TvGuideEntry>,
) = group(key = key, header = title) {
    entries.forEachIndexed { index, entry ->
        info(
            SettingEntry(
                key = "$key.$index",
                title = entry.keyRes,
                summary = entry.descriptionRes,
                destination = SettingsDestination.REMOTE_GUIDE,
                availability = SettingAvailability.TvOnly,
            ),
            value = null,
        )
    }
}
