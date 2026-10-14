package io.github.aedev.flow.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.pages.unwrapRedirectUrl
import io.github.aedev.flow.ui.components.layout.navigation.LocalMediaNavigator
import io.github.aedev.flow.ui.components.layout.navigation.MediaNavigator
import io.github.aedev.flow.utils.parseYouTubeLink

/**
 * Opens a YouTube link on the page Flow has for it and hands every other link to [platform].
 *
 * Provided over the whole shell, so a link tapped in any text reaches the app instead of the
 * browser: descriptions, comments, posts, channel links, and every `LinkAnnotation.Url`, which
 * Compose opens through [LocalUriHandler].
 */
internal class FlowUriHandler(
    private val navigator: MediaNavigator,
    private val platform: UriHandler,
    private val onCannotOpen: (String) -> Unit = {},
) : UriHandler {
    override fun openUri(uri: String) {
        val target = withWebScheme(uri.trim())
        if (navigator.openYouTubeUrl(target)) return
        // The platform handler throws when no installed app takes the link; a tap must never crash.
        try {
            platform.openUri(unwrapYouTubeRedirect(target))
        } catch (_: IllegalArgumentException) {
            onCannotOpen(target)
        }
    }
}

/**
 * [uri] with `https://` in front when it is a bare web address such as `example.com/page`, which
 * channel links can carry; anything with a scheme is left alone.
 */
internal fun withWebScheme(uri: String): String {
    if (HAS_SCHEME.containsMatchIn(uri) || !BARE_HOST.containsMatchIn(uri)) return uri
    return "https://$uri"
}

/** Opens [url] in the app when it is a YouTube link Flow has a page for; false for anything else. */
internal fun MediaNavigator.openYouTubeUrl(url: String): Boolean = parseYouTubeLink(unwrapYouTubeRedirect(url))?.let(::openLink) == true

/** YouTube wraps outbound links in `youtube.com/redirect?q=…`; the target opens directly instead. */
internal fun unwrapYouTubeRedirect(url: String): String = if (YOUTUBE_REDIRECT.containsMatchIn(url)) unwrapRedirectUrl(url) else url

/** The shell's navigator and the link handler built on it, provided together. */
@Composable
internal fun mediaNavigationLocals(navigator: MediaNavigator): Array<ProvidedValue<*>> {
    val platform = LocalUriHandler.current
    val context = LocalContext.current
    val uriHandler =
        remember(navigator, platform, context) {
            FlowUriHandler(navigator, platform) {
                Toast.makeText(context, context.getString(R.string.link_cannot_open), Toast.LENGTH_SHORT).show()
            }
        }
    return arrayOf(LocalMediaNavigator provides navigator, LocalUriHandler provides uriHandler)
}

private val HAS_SCHEME = Regex("""^[a-z][a-z0-9+.-]*:(?!\d)""", RegexOption.IGNORE_CASE)
private val BARE_HOST = Regex("""^[a-z0-9-]+(?:\.[a-z0-9-]+)+(?::\d+)?(?:[/?#]|$)""", RegexOption.IGNORE_CASE)
private val YOUTUBE_REDIRECT = Regex("""^https?://(?:[a-z0-9-]+\.)*youtube\.com/redirect\?""", RegexOption.IGNORE_CASE)
