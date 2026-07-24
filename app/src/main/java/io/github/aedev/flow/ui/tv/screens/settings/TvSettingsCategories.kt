package io.github.aedev.flow.ui.tv.screens.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.aedev.flow.R

/** Categories of the two-pane TV settings surface. Pure model — unit-testable. */
enum class TvSettingsCategory(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    PLAYBACK(R.string.playback_header, Icons.Outlined.PlayCircleOutline),
    QUALITY(R.string.quality, Icons.Outlined.Tune),
    CONTENT(R.string.settings_header_content_playback, Icons.Outlined.Shield),
    APPEARANCE(R.string.tv_settings_appearance, Icons.Outlined.Palette),
    FLOW_ENGINE(R.string.tv_settings_flow_engine, Icons.Outlined.Psychology),
    INTERFACE(R.string.interface_mode_title, Icons.Outlined.Tv),
    REMOTE_GUIDE(R.string.tv_remote_guide_title, Icons.Outlined.SettingsRemote),
    SYNC(R.string.tv_settings_sync, Icons.Outlined.Devices),
    ABOUT(R.string.about, Icons.Outlined.Info),
}
