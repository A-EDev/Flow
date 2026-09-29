package io.github.aedev.flow.ui.components.donation

import java.util.concurrent.TimeUnit

/** What the donation prompt should do on this launch. */
enum class DonationDecision {
    /** First time the schedule runs: remember today and wait out the grace period. */
    START_GRACE,
    WAIT,
    SHOW,
}

/** Three days to settle in, then at most one ask every 75 days, and never again once declined for good. */
object DonationSchedule {
    val GRACE_MS: Long = TimeUnit.DAYS.toMillis(3)
    val INTERVAL_MS: Long = TimeUnit.DAYS.toMillis(75)

    fun decide(
        nowMs: Long,
        firstLaunchMs: Long,
        lastShownMs: Long,
        disabled: Boolean,
    ): DonationDecision =
        when {
            disabled -> DonationDecision.WAIT
            firstLaunchMs == 0L -> DonationDecision.START_GRACE
            nowMs - firstLaunchMs < GRACE_MS -> DonationDecision.WAIT
            lastShownMs != 0L && nowMs - lastShownMs < INTERVAL_MS -> DonationDecision.WAIT
            else -> DonationDecision.SHOW
        }
}
