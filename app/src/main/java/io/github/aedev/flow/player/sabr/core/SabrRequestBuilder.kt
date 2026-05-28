package io.github.aedev.flow.player.sabr.core

import io.github.aedev.flow.player.sabr.proto.ClientAbrState
import io.github.aedev.flow.player.sabr.proto.ClientScreenInfo
import io.github.aedev.flow.player.sabr.proto.VideoPlaybackAbrRequest

object SabrRequestBuilder {

    fun buildInitialRequest(state: SabrSessionState): ByteArray {
        val request = VideoPlaybackAbrRequest(
            clientAbrState = ClientAbrState(
                playheadPositionMs = 0,
                isPlaying = false,
                estimatedBandwidthBps = state.estimatedBandwidthBps,
                playerWidthPixels = state.screenWidthPixels,
                playerHeightPixels = state.screenHeightPixels
            ),
            selectedVideoFormatId = state.selectedVideoFormatId,
            selectedAudioFormatId = state.selectedAudioFormatId,
            videoPlaybackUstreamerConfig = state.ustreamerConfig,
            poToken = state.poToken,
            clientScreenInfo = ClientScreenInfo(
                screenWidthPixels = state.screenWidthPixels,
                screenHeightPixels = state.screenHeightPixels,
                screenDensity = state.screenDensity
            ),
            requestNumber = ++state.requestSequence
        )
        return request.encode()
    }

    fun buildFollowUpRequest(state: SabrSessionState): ByteArray {
        val allBufferedRanges = state.audioBufferedRanges + state.videoBufferedRanges

        val request = VideoPlaybackAbrRequest(
            clientAbrState = ClientAbrState(
                playheadPositionMs = state.playheadPositionMs,
                bufferedRanges = allBufferedRanges,
                isPlaying = true,
                estimatedBandwidthBps = state.estimatedBandwidthBps,
                playerWidthPixels = state.screenWidthPixels,
                playerHeightPixels = state.screenHeightPixels
            ),
            selectedVideoFormatId = state.selectedVideoFormatId,
            selectedAudioFormatId = state.selectedAudioFormatId,
            videoPlaybackUstreamerConfig = state.ustreamerConfig,
            poToken = state.poToken,
            playbackCookie = state.playbackCookie,
            clientScreenInfo = ClientScreenInfo(
                screenWidthPixels = state.screenWidthPixels,
                screenHeightPixels = state.screenHeightPixels,
                screenDensity = state.screenDensity
            ),
            sabrContext = state.sabrContext,
            requestNumber = ++state.requestSequence
        )
        return request.encode()
    }
}
