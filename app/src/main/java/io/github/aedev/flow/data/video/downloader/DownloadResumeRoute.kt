package io.github.aedev.flow.data.video.downloader

/** The placeholder URL a SABR download carries; its real session parameters are never stored. */
internal const val SABR_MISSION_URL_SCHEME = "sabr://"

internal fun sabrMissionUrl(videoId: String): String = "$SABR_MISSION_URL_SCHEME$videoId"

/** How a resume request continues a download. */
internal enum class DownloadResumeRoute {
    /** Continue the byte ranges from the stored URLs. */
    DIRECT,

    /** Resolve a fresh SABR session and start over; the old PoToken and config are stale by now. */
    SABR_RERESOLVE,

    /** The service no longer holds the download, so it starts over with fresh URLs. */
    REQUEUE,
}

internal fun downloadResumeRoute(missionUrl: String?): DownloadResumeRoute =
    when {
        missionUrl == null -> DownloadResumeRoute.REQUEUE
        missionUrl.startsWith(SABR_MISSION_URL_SCHEME) -> DownloadResumeRoute.SABR_RERESOLVE
        else -> DownloadResumeRoute.DIRECT
    }
