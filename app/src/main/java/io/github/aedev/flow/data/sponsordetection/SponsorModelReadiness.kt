package io.github.aedev.flow.data.sponsordetection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class SponsorModelReadiness(
    val enabled: Boolean,
    val installed: Boolean,
) {
    val active: Boolean get() = enabled && installed
}

internal data class SponsorPlaybackSettings(
    val onlineEnabled: Boolean,
    val onDevice: SponsorModelReadiness,
) {
    val handlerEnabled: Boolean get() = onlineEnabled || onDevice.enabled
}

internal fun sponsorModelReadiness(
    enabled: Flow<Boolean>,
    state: Flow<SponsorModelState>,
): Flow<SponsorModelReadiness> =
    combine(enabled, state) { onDeviceEnabled, modelState ->
        SponsorModelReadiness(onDeviceEnabled, modelState is SponsorModelState.Installed)
    }.distinctUntilChanged()

internal fun sponsorPlaybackSettings(
    onlineEnabled: Flow<Boolean>,
    onDeviceEnabled: Flow<Boolean>,
    state: Flow<SponsorModelState>,
): Flow<SponsorPlaybackSettings> =
    combine(onlineEnabled, sponsorModelReadiness(onDeviceEnabled, state)) { online, onDevice ->
        SponsorPlaybackSettings(online, onDevice)
    }.distinctUntilChanged()

internal fun shouldRetrySkippedSponsorEvaluation(
    videoId: String?,
    currentVideoId: String?,
    isLive: Boolean,
    readiness: SponsorModelReadiness,
    state: SponsorDetectionUiState,
): Boolean =
    readiness.active &&
        !isLive &&
        videoId != null &&
        videoId == currentVideoId &&
        state.videoId == videoId &&
        state.status == SponsorDetectionStatus.SKIPPED &&
        state.errorMessage == ON_DEVICE_UNAVAILABLE_MESSAGE
