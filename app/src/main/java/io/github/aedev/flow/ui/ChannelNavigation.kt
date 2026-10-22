package io.github.aedev.flow.ui

import androidx.navigation.NavHostController

internal fun NavHostController.navigateToYoutubeChannel(channelIdOrHandle: String) {
    youtubeChannelRoute(channelIdOrHandle)?.let(::openPage)
}
