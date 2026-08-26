package io.github.aedev.flow.data.video.downloader

/** What a download's notification should be left showing once its coroutine has stopped running. */
enum class SettledNotification {
    /** Terminal success — replace whatever transient phase was on screen with the completion notice. */
    COMPLETE,

    /** Terminal failure, or a pause the user can resume from: the mission's own state is the truth. */
    KEEP_STATE,

    /** Nothing is running and nothing finished, so any notification left behind would be a lie. */
    DISMISS,
}

/**
 * Decides how [FlowDownloadService] settles a download's notification from its `finally` block.
 *
 * That block also runs on cancellation, which is how a finished, already-moved file used to end up
 * sitting under a notification frozen on "Merging audio & video…".
 *
 * [tracked] is false once the mission has left `activeMissions`, which only happens when the user
 * cancelled it — that path takes the notification down itself and must not have one posted back.
 */
fun settledNotificationFor(
    status: MissionStatus,
    tracked: Boolean,
): SettledNotification =
    when {
        !tracked -> SettledNotification.DISMISS
        status == MissionStatus.FINISHED -> SettledNotification.COMPLETE
        status == MissionStatus.FAILED || status == MissionStatus.PAUSED -> SettledNotification.KEEP_STATE
        else -> SettledNotification.DISMISS
    }
