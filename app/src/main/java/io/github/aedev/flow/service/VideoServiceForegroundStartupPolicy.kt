package io.github.aedev.flow.service

internal data class VideoServiceForegroundStartupPlan(
    val promoteImmediately: Boolean,
    val stopAfterPromotion: Boolean,
)

internal fun videoServiceForegroundStartupPlan(
    hasMediaSession: Boolean,
): VideoServiceForegroundStartupPlan = VideoServiceForegroundStartupPlan(
    promoteImmediately = true,
    stopAfterPromotion = !hasMediaSession,
)
