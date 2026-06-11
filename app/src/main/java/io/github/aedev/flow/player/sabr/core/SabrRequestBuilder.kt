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

        val playheadMs = state.playheadPositionMs
        val selected = listOfNotNull(
            state.selectedVideoFormatId.takeIf { state.selectedVideoItag in state.initializedFormats },
            state.selectedAudioFormatId.takeIf { state.selectedAudioItag in state.initializedFormats }
        )
        val buffered = if (isInitial) emptyList() else (state.videoBufferedRanges + state.audioBufferedRanges)
        val timeSinceSeekMs = if (state.lastSeekAtMs > 0) {
            (System.currentTimeMillis() - state.lastSeekAtMs).coerceAtLeast(0)
        } else 0L

        val request = VideoPlaybackAbrRequest(
            clientAbrState = ClientAbrState(
                playerTimeMs = playheadMs,
                bandwidthEstimateBps = state.estimatedBandwidthBps,
                viewportWidthPx = state.screenWidthPixels,
                viewportHeightPx = state.screenHeightPixels,
                lastManualSelectedResolution = state.stickyResolution,
                stickyResolution = state.stickyResolution,
                timeSinceLastSeekMs = timeSinceSeekMs,
                enabledTrackTypesBitfield = state.enabledTrackTypes,
                audioTrackId = state.audioTrackId
            ),
            selectedFormatIds = selected,
            bufferedRanges = buffered,
            playerTimeMs = playheadMs,
            videoPlaybackUstreamerConfig = state.ustreamerConfig,
            preferredAudioFormatIds = listOfNotNull(state.selectedAudioFormatId.takeIf { state.selectedAudioItag > 0 }),
            preferredVideoFormatIds = listOfNotNull(state.selectedVideoFormatId.takeIf { state.selectedVideoItag > 0 }),
            streamerContext = StreamerContext(
                clientInfo = ClientInfo(
                    clientName = state.clientNameId,
                    clientVersion = state.clientVersion,
                    osName = state.osName,
                    osVersion = state.osVersion
                ),
                poToken = state.poTokenBytes(),
                playbackCookie = state.playbackCookie,
                sabrContexts = state.activeSabrContexts()
            )
        )
        return request.encode()
    }
}
