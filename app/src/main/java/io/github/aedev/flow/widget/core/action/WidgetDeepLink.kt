package io.github.aedev.flow.widget.core.action

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.aedev.flow.MainActivity
import io.github.aedev.flow.ui.ON_REPEAT_SHUFFLE_ROUTE
import io.github.aedev.flow.ui.musicCollectionRoute
import io.github.aedev.flow.ui.musicPlayerRoute
import io.github.aedev.flow.ui.widgetPlaylistRoute
import io.github.aedev.flow.widget.config.WidgetConfigActivity

/**
 * Single source of truth for widget-tap intents into [MainActivity].
 *
 * Each intent carries a unique data Uri so PendingIntents built from different
 * targets never collapse into one another.
 */
object WidgetDeepLink {
    const val EXTRA_WIDGET_ROUTE = "widget_route"

    // Navigation routes that already exist in FlowNavigation.
    const val ROUTE_SEARCH = "search"
    const val ROUTE_DOWNLOADS = "downloads"
    const val ROUTE_HISTORY = "history"
    const val ROUTE_RECOGNIZE = "musicRecognize"
    const val ROUTE_MUSIC = "music"
    const val ROUTE_SHORTS = "shorts"
    const val ROUTE_SUBSCRIPTIONS = "subscriptions"
    const val ROUTE_LIBRARY = "library"

    fun openApp(context: Context): Intent = base(context, "open")

    /** Expands the full music player over whatever screen the app opens on. */
    fun openMusicPlayer(context: Context): Intent = base(context, "music_player").putExtra("open_music_player", true)

    /** Navigates to an existing FlowNavigation route (search, downloads, history, …). */
    fun openRoute(
        context: Context,
        route: String,
    ): Intent = base(context, "route/$route").putExtra(EXTRA_WIDGET_ROUTE, route)

    fun playSong(
        context: Context,
        videoId: String,
    ): Intent = openRoute(context, musicPlayerRoute(videoId))

    fun shuffleOnRepeat(context: Context): Intent = openRoute(context, ON_REPEAT_SHUFFLE_ROUTE)

    /** Plays a playlist from the top, or from [startVideoId] when a row of it was tapped. */
    fun playPlaylist(
        context: Context,
        playlistId: String,
        shuffle: Boolean,
        startVideoId: String? = null,
    ): Intent = openRoute(context, widgetPlaylistRoute(playlistId, shuffle, startVideoId))

    /** The playlist's own page: music playlists open in the music collection page. */
    fun openPlaylist(
        context: Context,
        playlistId: String,
        isMusic: Boolean,
    ): Intent = openRoute(context, if (isMusic) musicCollectionRoute(playlistId) ?: ROUTE_LIBRARY else "playlist/$playlistId")

    /** The launcher's configure screen for one widget, reached from the widget itself. */
    fun configure(
        context: Context,
        appWidgetId: Int,
    ): Intent =
        Intent(context, WidgetConfigActivity::class.java).apply {
            data = Uri.parse("flow://widget/configure/$appWidgetId")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Opens the video player on [videoId] via the existing deeplink playback path. */
    fun playVideo(
        context: Context,
        videoId: String,
    ): Intent = base(context, "video/$videoId").putExtra("video_id", videoId)

    /** Opens [videoId] in the Shorts player, which continues into the feed like any Shorts link. */
    fun playShort(
        context: Context,
        videoId: String,
    ): Intent =
        base(context, "short/$videoId")
            .putExtra("video_id", videoId)
            .putExtra("is_short", true)

    private fun base(
        context: Context,
        path: String,
    ): Intent =
        Intent(context, MainActivity::class.java).apply {
            // Custom action (not ACTION_VIEW): MainActivity.handleIntent must read our
            // extras, not try to parse the uniqueness-only data Uri as a YouTube URL.
            action = "io.github.aedev.flow.widget.OPEN"
            data = Uri.parse("flow://widget/$path")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
