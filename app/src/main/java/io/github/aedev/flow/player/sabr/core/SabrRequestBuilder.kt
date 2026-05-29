package io.github.aedev.flow.player.sabr.core

import io.github.aedev.flow.player.sabr.proto.ClientAbrState
import io.github.aedev.flow.player.sabr.proto.ClientInfo
import io.github.aedev.flow.player.sabr.proto.StreamerContext
import io.github.aedev.flow.player.sabr.proto.VideoPlaybackAbrRequest

object SabrRequestBuilder {

    fun buildInitialRequest(state: SabrSessionState): ByteArray =
        buildRequest(state, isInitial = true)

    fun buildFollowUpRequest(state: SabrSessionState): ByteArray =
        buildRequest(state, isInitial = false)

    private fun buildRequest(state: SabrSessionState, isInitial: Boolean): ByteArray {
        state.requestSequence++

        val playheadMs = if (isInitial) 0L else state.playheadPositionMs
        val selected = listOf(state.selectedVideoFormatId, state.selectedAudioFormatId)
        val buffered = if (isInitial) emptyList() else (state.videoBufferedRanges + state.audioBufferedRanges)

        val request = VideoPlaybackAbrRequest(
            clientAbrState = ClientAbrState(
                playerTimeMs = playheadMs,
                bandwidthEstimateBps = state.estimatedBandwidthBps,
                viewportWidthPx = state.screenWidthPixels,
                viewportHeightPx = state.screenHeightPixels
            ),
            selectedFormatIds = selected,
            bufferedRanges = buffered,
            playerTimeMs = playheadMs,
            videoPlaybackUstreamerConfig = state.ustreamerConfig,
            streamerContext = StreamerContext(
                clientInfo = ClientInfo(
                    clientName = state.clientNameId,
                    clientVersion = state.clientVersion,
                    osName = state.osName,
                    osVersion = state.osVersion
                ),
                poToken = state.poTokenBytes(),
                playbackCookie = state.playbackCookie
            )
        )
        return request.encode()
    }
}
