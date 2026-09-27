package io.github.aedev.flow.widget.onrepeat

import androidx.glance.appwidget.GlanceAppWidget
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.audioMusicOnly
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.widget.core.refresh.WidgetContentKey
import io.github.aedev.flow.widget.core.refresh.WidgetContentSource
import javax.inject.Inject
import javax.inject.Singleton

/** The Music page's On Repeat shelf. The brain has no change stream, so a finished listen requests a refresh. */
@Singleton
class OnRepeatSource
    @Inject
    constructor(
        private val musicBrain: MusicBrainEngine,
    ) : WidgetContentSource {
        override val key = WidgetContentKey.ON_REPEAT
        override val widget: GlanceAppWidget get() = OnRepeatWidget()

        suspend fun tracks(): List<MusicTrack> = musicBrain.heavyRotationTracks(SHELF_POOL).audioMusicOnly().take(MAX_ITEMS)

        override suspend fun signature(): String = tracks().joinToString(",") { it.videoId }

        companion object {
            const val MAX_ITEMS = 8

            // The same pool the Music page filters its shelf from.
            private const val SHELF_POOL = 16
        }
    }
