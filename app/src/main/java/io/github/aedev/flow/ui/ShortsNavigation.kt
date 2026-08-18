package io.github.aedev.flow.ui

import android.net.Uri
import androidx.navigation.NavController
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource

const val SHORTS_ROUTE_PATTERN = "shorts?src={src}"

const val SHORTS_ROUTE_ARG = "src"

const val SHORTS_ROUTE_KEY = "shorts"

fun NavController.openShorts(source: ShortsQueueSource) {
    navigate("shorts?$SHORTS_ROUTE_ARG=${Uri.encode(source.encode())}")
}
