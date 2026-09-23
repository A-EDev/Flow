package io.github.aedev.flow.ui.screens.settings.index

import android.os.Build
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingAvailability
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination

internal object DownloadsIndex {
    private val page = SettingsDestination.DOWNLOADS

    private fun entry(
        key: String,
        title: Int,
        section: Int,
        summary: Int? = null,
        availability: SettingAvailability = SettingAvailability.Always,
    ) = SettingEntry(
        key = "downloads.$key",
        title = title,
        summary = summary,
        section = section,
        keywords = R.string.settings_keywords_downloads,
        availability = availability,
        destination = page,
    )

    val videoLocation = entry("video_location", R.string.video_download_location_label, R.string.storage_header)
    val musicLocation = entry("music_location", R.string.music_download_location_label, R.string.storage_header)
    val usage = entry("usage", R.string.internal_storage_label, R.string.storage_header)
    val cacheSize = entry("cache_size", R.string.cache_size_header, R.string.storage_header, summary = R.string.cache_size_desc)
    val quickQuality =
        entry(
            "quick_quality",
            R.string.settings_quick_download_quality,
            R.string.settings_section_download_defaults,
            summary = R.string.settings_quick_download_quality_summary,
        )
    val codec = entry("codec", R.string.default_download_codec_label, R.string.settings_section_download_defaults)
    val menuStyle =
        entry(
            "menu_style",
            R.string.download_menu_style_title,
            R.string.settings_section_download_defaults,
            summary = R.string.download_menu_style_subtitle,
        )
    val wifiOnly =
        entry(
            "wifi_only",
            R.string.download_over_wifi_only,
            R.string.settings_section_download_defaults,
            summary = R.string.reduce_data_usage_subtitle,
        )
    val threads = entry("threads", R.string.concurrent_threads_title, R.string.performance_header)
    val allFilesAccess =
        entry(
            "all_files",
            R.string.files_access_title,
            R.string.settings_section_storage_access,
            availability = SettingAvailability.MinSdk(Build.VERSION_CODES.R),
        )
    val videoAccess =
        entry(
            "media_video",
            R.string.media_access_title,
            R.string.settings_section_storage_access,
            availability = SettingAvailability.MinSdk(Build.VERSION_CODES.TIRAMISU),
        )
    val audioAccess =
        entry(
            "media_audio",
            R.string.audio_access_title,
            R.string.settings_section_storage_access,
            availability = SettingAvailability.MinSdk(Build.VERSION_CODES.TIRAMISU),
        )

    val all =
        listOf(
            videoLocation,
            musicLocation,
            usage,
            cacheSize,
            quickQuality,
            codec,
            menuStyle,
            wifiOnly,
            threads,
            allFilesAccess,
            videoAccess,
            audioAccess,
        )
}
