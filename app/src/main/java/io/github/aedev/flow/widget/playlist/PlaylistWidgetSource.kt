package io.github.aedev.flow.widget.playlist

import androidx.glance.appwidget.GlanceAppWidget
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.PlaylistInfo
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

@Singleton
class PlaylistWidgetSource
    @Inject
    constructor(
        private val playlists: PlaylistRepository,
    ) : WidgetContentSource {
        override val key = WidgetContentKey.PLAYLISTS
        override val widget: GlanceAppWidget get() = PlaylistWidget()

        /** Every playlist the widget can show, videos and music together, as the picker lists them. */
        fun options(): Flow<List<WidgetPlaylist>> =
            combine(playlists.getAllPlaylistsFlow(), playlists.getMusicPlaylistsFlow()) { videos, music ->
                videos.map { it.toWidget(isMusic = false) } + music.map { it.toWidget(isMusic = true) }
            }

        suspend fun playlist(id: String): WidgetPlaylist? {
            val option = options().first().firstOrNull { it.id == id } ?: return null
            if (!option.coverUrl.isNullOrBlank()) return option
            val first = playlists.getPlaylistVideosFlow(id).first().firstOrNull()
            return option.copy(coverUrl = first?.thumbnailUrl)
        }

        override fun changes(): Flow<Unit> =
            options()
                .map(::signatureOf)
                .distinctUntilChanged()
                .map { }

        override suspend fun signature(): String = signatureOf(options().first())

        private fun signatureOf(options: List<WidgetPlaylist>) =
            options.joinToString(",") { "${it.id}:${it.count}:${it.name}:${it.coverUrl}" }

        private fun PlaylistInfo.toWidget(isMusic: Boolean) =
            WidgetPlaylist(id = id, name = name, count = videoCount, coverUrl = thumbnailUrl.takeIf { it.isNotBlank() }, isMusic = isMusic)
    }
