package io.github.aedev.flow.widget.quickactions

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aedev.flow.R
import io.github.aedev.flow.widget.core.action.WidgetDeepLink

/** A page the Quick Actions widget can jump to. [key] is persisted per widget, so it never changes. */
enum class QuickShortcut(
    val key: String,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val route: String,
) {
    DOWNLOADS("downloads", R.drawable.ic_widget_download, R.string.library_downloads_label, WidgetDeepLink.ROUTE_DOWNLOADS),
    HISTORY("history", R.drawable.ic_widget_history, R.string.library_history_label, WidgetDeepLink.ROUTE_HISTORY),
    RECOGNIZE("recognize", R.drawable.ic_widget_mic, R.string.recognize_music, WidgetDeepLink.ROUTE_RECOGNIZE),
    MUSIC("music", R.drawable.ic_widget_music, R.string.nav_music, WidgetDeepLink.ROUTE_MUSIC),
    SHORTS("shorts", R.drawable.ic_shorts, R.string.nav_shorts, WidgetDeepLink.ROUTE_SHORTS),
    SUBSCRIPTIONS("subscriptions", R.drawable.ic_widget_subscriptions, R.string.nav_subs, WidgetDeepLink.ROUTE_SUBSCRIPTIONS),
    LIBRARY("library", R.drawable.ic_widget_library, R.string.nav_library, WidgetDeepLink.ROUTE_LIBRARY),
    ;

    companion object {
        const val MAX = 4
        val DEFAULT = listOf(DOWNLOADS, HISTORY, RECOGNIZE)
        val KEY = stringPreferencesKey("quick_shortcuts")

        fun decode(stored: String?): List<QuickShortcut> =
            stored
                ?.split(',')
                ?.mapNotNull { key -> entries.firstOrNull { it.key == key } }
                ?.distinct()
                ?.take(MAX)
                ?: DEFAULT

        fun encode(shortcuts: List<QuickShortcut>): String = shortcuts.distinct().take(MAX).joinToString(",") { it.key }

        fun from(prefs: Preferences): List<QuickShortcut> = decode(prefs[KEY])
    }
}
