package io.github.aedev.flow.ui.screens.channel

import androidx.annotation.StringRes
import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind

/**
 * The channel screen's tabs, addressed by identity rather than by position.
 *
 * [ChannelUiState.selectedTab] persists the ordinal, so the order here is part of the saved state —
 * append new tabs at the end. [visible] is what the pager actually renders, which is a subset once
 * the Shorts master switch is off.
 */
enum class ChannelTab(
    @StringRes val titleRes: Int,
    val kind: ChannelTabKind,
) {
    Videos(R.string.tab_videos, ChannelTabKind.Videos),
    Shorts(R.string.tab_shorts, ChannelTabKind.Shorts),
    Live(R.string.tab_live, ChannelTabKind.Live),
    Playlists(R.string.tab_playlists, ChannelTabKind.Playlists),
    Posts(R.string.tab_posts, ChannelTabKind.Posts),
    About(R.string.tab_about, ChannelTabKind.Unknown),
    ;

    companion object {
        fun from(ordinal: Int): ChannelTab = entries.getOrElse(ordinal) { Videos }

        fun visible(shortsEnabled: Boolean): List<ChannelTab> = if (shortsEnabled) entries else entries.filterNot { it == Shorts }
    }
}
