package io.github.aedev.flow.ui.components.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/**
 * One option in Settings, declared once. The row on its page is drawn from this entry and the
 * settings search runs over it, so the two cannot disagree about what an option is called or where
 * it lives.
 *
 * @param key stable id; also the lazy-list key of the option's row, which is how a search result
 *   finds the row to scroll to.
 * @param keywords a translatable, comma-separated list of other words people search with.
 * @param revealVia the key of the row to highlight instead while this option is hidden behind it,
 *   such as a switch that has to be on first.
 */
@Immutable
data class SettingEntry(
    val key: String,
    @StringRes val title: Int,
    val destination: SettingsDestination,
    @StringRes val summary: Int? = null,
    @StringRes val keywords: Int? = null,
    @StringRes val section: Int? = null,
    val tab: String? = null,
    val revealVia: String? = null,
    val availability: SettingAvailability = SettingAvailability.Always,
) {
    val target: SettingsTarget
        get() = SettingsTarget(destination = destination, tab = tab, highlight = revealVia ?: key)
}

/** Whether an option exists on this build and device at all. */
sealed interface SettingAvailability {
    data object Always : SettingAvailability

    /** Only in builds that ship the in-app updater and other github-flavor features. */
    data object GithubOnly : SettingAvailability

    data class MinSdk(
        val sdk: Int,
    ) : SettingAvailability

    fun isAvailable(
        githubFeatures: Boolean,
        sdk: Int,
    ): Boolean =
        when (this) {
            Always -> true
            GithubOnly -> githubFeatures
            is MinSdk -> sdk >= this.sdk
        }
}
