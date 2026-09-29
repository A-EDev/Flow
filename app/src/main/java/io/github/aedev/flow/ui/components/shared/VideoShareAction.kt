package io.github.aedev.flow.ui.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.utils.shareVideo

/** The user's "share links without text" preference, for every share affordance to honour. */
@Composable
fun rememberShareLinksWithoutText(): State<Boolean> {
    val context = LocalContext.current
    val preferences = remember(context) { PlayerPreferences(context) }
    return preferences.shareWithoutText.collectAsStateWithLifecycle(initialValue = false)
}

/**
 * A share callback that already honours the user's "share without text" preference, so a component
 * outside the player package can raise the same chooser the player's info row does without
 * importing a feature package or re-deriving the payload.
 */
@Composable
fun rememberVideoShareAction(): (videoId: String, title: String, isShort: Boolean) -> Unit {
    val context = LocalContext.current
    val linkOnly = rememberShareLinksWithoutText()
    return remember(context) {
        { videoId: String, title: String, isShort: Boolean -> shareVideo(context, videoId, title, linkOnly.value, isShort) }
    }
}
