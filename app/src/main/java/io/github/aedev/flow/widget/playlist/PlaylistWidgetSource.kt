package io.github.aedev.flow.widget.playlist

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.YouTubePlaylistRepository
import io.github.aedev.flow.widget.core.refresh.WidgetContentKey
import io.github.aedev.flow.widget.core.refresh.WidgetContentSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A playlist as its widget draws it; [coverUrl] falls back to the first item when the playlist has none. */
data class WidgetPlaylist(
    val id: String,
    val name: String,
    val count: Int,
    val coverUrl: String?,
    val isMusic: Boolean,
)

/** What one Playlist widget draws: the playlist, or null once it is gone, and as many rows as the list holds. */
data class WidgetPlaylistContent(
    val playlist: WidgetPlaylist?,
    val items: List<Video>,
)

@Singleton
class PlaylistWidgetSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playlists: PlaylistRepository,
        private val remotePlaylists: YouTubePlaylistRepository,
    ) : WidgetContentSource {
        override val key = WidgetContentKey.PLAYLISTS
        override val widget: GlanceAppWidget get() = PlaylistWidget()

        /** Every playlist the widget can show, Watch later first, then videos and music, as the picker lists them. */
        fun options(): Flow<List<WidgetPlaylist>> =
            combine(
                playlists.getWatchLaterVideosFlow(),
                playlists.getAllPlaylistsFlow(),
                playlists.getMusicPlaylistsFlow(),
            ) { watchLater, videos, music ->
                listOf(watchLaterOf(watchLater)) + videos.map { it.toWidget(isMusic = false) } + music.map { it.toWidget(isMusic = true) }
            }

        /** One playlist and its first rows, re-emitted whenever either changes. */
        fun content(id: String): Flow<WidgetPlaylistContent> =
            combine(options(), playlists.getPlaylistVideosFlow(id)) { options, videos ->
                val option = options.firstOrNull { it.id == id }
                WidgetPlaylistContent(
                    playlist =
                        option?.let {
                            if (it.coverUrl.isNullOrBlank()) {
                                it.copy(
                                    coverUrl = videos.firstOrNull()?.thumbnailUrl,
                                )
                            } else {
                                it
                            }
                        },
                    items = videos.take(MAX_ITEMS),
                )
            }.distinctUntilChanged()

        /**
         * Re-reads a saved YouTube playlist from YouTube into its local copy, the same sync its page runs
         * when opened. Playlists that live only in Flow have nothing newer anywhere else.
         */
        suspend fun refresh(id: String) {
            val entity = playlists.getPlaylistEntity(id) ?: return
            if (entity.isUserCreated || entity.isMusic) return
            val remote = remotePlaylists.complete(id) ?: return
            playlists.syncSavedPlaylistVideos(id, remote.videos)
        }

        // Room re-emits the playlist lists on any playlist or entry write; only a change to a placed
        // widget's own playlist gets past the signature.
        override fun changes(): Flow<Unit> =
            options()
                .map { signature() }
                .distinctUntilChanged()
                .map { }

        override suspend fun signature(): String =
            chosenPlaylistIds()
                .map { id ->
                    val content = content(id).first()
                    val playlist = content.playlist?.let { "${it.name}:${it.count}:${it.coverUrl}" }
                    "$id=$playlist=" + content.items.joinToString(",") { "${it.id}:${it.title}:${it.thumbnailUrl}" }
                }.joinToString(";")

        private suspend fun chosenPlaylistIds(): List<String> =
            GlanceAppWidgetManager(context)
                .getGlanceIds(PlaylistWidget::class.java)
                .map { PlaylistWidgetConfig.load(context, it) ?: DEFAULT_PLAYLIST_ID }
                .distinct()

        private fun watchLaterOf(videos: List<Video>) =
            WidgetPlaylist(
                id = PlaylistRepository.WATCH_LATER_ID,
                name = context.getString(R.string.watch_later),
                count = videos.size,
                coverUrl = videos.firstOrNull()?.thumbnailUrl?.takeIf { it.isNotBlank() },
                isMusic = false,
            )

        private fun PlaylistInfo.toWidget(isMusic: Boolean) =
            WidgetPlaylist(id = id, name = name, count = videoCount, coverUrl = thumbnailUrl.takeIf { it.isNotBlank() }, isMusic = isMusic)

        companion object {
            /** What a widget shows before a playlist is chosen, and what the picker starts on. */
            const val DEFAULT_PLAYLIST_ID = PlaylistRepository.WATCH_LATER_ID

            // Each row carries its own thumbnail bitmap in the RemoteViews, which has a size cap.
            const val MAX_ITEMS = 12
        }
    }
