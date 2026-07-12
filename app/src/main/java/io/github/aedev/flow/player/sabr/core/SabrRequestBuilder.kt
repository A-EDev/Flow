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

        val includeFollowUpState = playheadMs > 0 || buffered.isNotEmpty()

        val effectiveResolution =
            (if (state.stickyResolution > 0) state.stickyResolution else state.selectedVideoHeight)
                .coerceAtLeast(360)

        val request = VideoPlaybackAbrRequest(
            clientAbrState = ClientAbrState(
                playerTimeMs = playheadMs,
                bandwidthEstimateBps = if (includeFollowUpState) state.estimatedBandwidthBps else 0L,
                viewportWidthPx = if (includeFollowUpState) state.screenWidthPixels else 0,
                viewportHeightPx = if (includeFollowUpState) state.screenHeightPixels else 0,
                lastManualSelectedResolution = if (state.stickyResolution > 0) state.stickyResolution else 0,
                stickyResolution = effectiveResolution,
                timeSinceLastSeekMs = timeSinceSeekMs,
                visibility = state.visibility,
                playbackRate = state.playbackRate,
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
                sabrContexts = state.activeSabrContexts(),
                unsentSabrContextTypes = state.unsentSabrContextTypes()
            )
        )
        return request.encode()
    }
}
