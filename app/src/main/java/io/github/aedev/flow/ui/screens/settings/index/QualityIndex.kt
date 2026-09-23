package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination
import io.github.aedev.flow.ui.components.settings.SettingsTabs

internal object QualityIndex {
    private val page = SettingsDestination.QUALITY

    private fun entry(
        key: String,
        title: Int,
        tab: String,
        section: Int,
        keywords: Int = R.string.settings_keywords_quality,
        revealVia: String? = null,
    ) = SettingEntry(
        key = "quality.$key",
        title = title,
        tab = tab,
        section = section,
        keywords = keywords,
        revealVia = revealVia,
        destination = page,
    )

    val videoWifi = entry("video.wifi", R.string.settings_quality_wifi, SettingsTabs.QUALITY_VIDEO, R.string.settings_quality_tab_video)
    val videoMobile =
        entry("video.mobile", R.string.settings_quality_mobile, SettingsTabs.QUALITY_VIDEO, R.string.settings_quality_tab_video)
    val codec =
        entry(
            "video.codec",
            R.string.settings_quality_preferred_codec,
            SettingsTabs.QUALITY_VIDEO,
            R.string.settings_quality_tab_video,
            keywords = R.string.settings_keywords_codec,
        )
    val fallbackCodec =
        entry(
            "video.fallback_codec",
            R.string.player_settings_video_codec_fallback,
            SettingsTabs.QUALITY_VIDEO,
            R.string.settings_quality_tab_video,
            keywords = R.string.settings_keywords_codec,
            revealVia = "quality.video.codec",
        )
    val shortsWifi = entry("shorts.wifi", R.string.settings_quality_wifi, SettingsTabs.QUALITY_SHORTS, R.string.settings_quality_tab_shorts)
    val shortsMobile =
        entry("shorts.mobile", R.string.settings_quality_mobile, SettingsTabs.QUALITY_SHORTS, R.string.settings_quality_tab_shorts)
    val music = entry("music", R.string.music_quality_header, SettingsTabs.QUALITY_MUSIC, R.string.settings_quality_tab_music)

    val all = listOf(videoWifi, videoMobile, codec, fallbackCodec, shortsWifi, shortsMobile, music)
}
