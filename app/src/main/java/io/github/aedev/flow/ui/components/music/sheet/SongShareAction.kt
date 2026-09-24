package io.github.aedev.flow.ui.components.music.sheet

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack

/** Raises the system share sheet for a song, with the same message wherever it is shared from. */
@Composable
fun rememberSongShareAction(): (MusicTrack) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { track: MusicTrack ->
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, track.title)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        context.getString(R.string.share_message_template, track.title, track.artist, track.videoId),
                    )
                }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_song)))
        }
    }
}
