package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination

internal object NetworkIndex {
    private val page = SettingsDestination.NETWORK

    private fun entry(
        key: String,
        title: Int,
        section: Int,
        summary: Int? = null,
    ) = SettingEntry(
        key = "network.$key",
        title = title,
        summary = summary,
        section = section,
        keywords = R.string.settings_keywords_network,
        destination = page,
    )

    val enabled = entry("enabled", R.string.proxy_settings_enabled, R.string.proxy_settings_title, R.string.proxy_settings_enabled_subtitle)
    val type = entry("type", R.string.proxy_settings_type, R.string.proxy_settings_title)
    val host = entry("host", R.string.proxy_settings_host, R.string.settings_section_proxy_server)
    val port = entry("port", R.string.proxy_settings_port, R.string.settings_section_proxy_server)
    val username = entry("username", R.string.proxy_settings_username, R.string.settings_section_proxy_sign_in)
    val password = entry("password", R.string.proxy_settings_password, R.string.settings_section_proxy_sign_in)

    val all = listOf(enabled, type, host, port, username, password)
}
