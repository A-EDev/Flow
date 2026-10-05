package io.github.aedev.flow.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
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
) : UriHandler {
    override fun openUri(uri: String) {
        if (!navigator.openYouTubeUrl(uri)) platform.openUri(unwrapYouTubeRedirect(uri))
    }
}

/** Opens [url] in the app when it is a YouTube link Flow has a page for; false for anything else. */
internal fun MediaNavigator.openYouTubeUrl(url: String): Boolean = parseYouTubeLink(unwrapYouTubeRedirect(url))?.let(::openLink) == true

/** YouTube wraps outbound links in `youtube.com/redirect?q=…`; the target opens directly instead. */
internal fun unwrapYouTubeRedirect(url: String): String = if (YOUTUBE_REDIRECT.containsMatchIn(url)) unwrapRedirectUrl(url) else url

/** The shell's navigator and the link handler built on it, provided together. */
@Composable
internal fun mediaNavigationLocals(navigator: MediaNavigator): Array<ProvidedValue<*>> {
    val platform = LocalUriHandler.current
    val uriHandler = remember(navigator, platform) { FlowUriHandler(navigator, platform) }
    return arrayOf(LocalMediaNavigator provides navigator, LocalUriHandler provides uriHandler)
}

private val YOUTUBE_REDIRECT = Regex("""^https?://(?:[a-z0-9-]+\.)*youtube\.com/redirect\?""", RegexOption.IGNORE_CASE)
