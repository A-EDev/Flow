package io.github.aedev.flow.data.sponsordetection

sealed interface SponsorModelState {
    data object NotInstalled : SponsorModelState

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : SponsorModelState

    data class Installed(
        val sizeBytes: Long,
    ) : SponsorModelState

    data object Failed : SponsorModelState
}
