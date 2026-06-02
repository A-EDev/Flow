package io.github.aedev.flow.innertube.models.body

import io.github.aedev.flow.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetLiveChatBody(
    val context: Context,
    val continuation: String,
    val currentPlayerState: CurrentPlayerState? = null,
) {
    @Serializable
    data class CurrentPlayerState(
        val playerOffsetMs: String,
    )
}
