package io.github.aedev.flow.ui.components.categories

import androidx.annotation.StringRes
import io.github.aedev.flow.R
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode

/** What a failed Explore load says. Exception text, which carries the request URL and body, never reaches the screen. */
@StringRes
internal fun categoryErrorRes(error: Throwable): Int =
    if (error.isRateLimitOrOutage()) R.string.categories_rate_limited else R.string.error_failed_to_load_videos

/** A refusal or outage on YouTube's side: nothing the viewer can fix except waiting. */
internal fun Throwable.isRateLimitOrOutage(): Boolean {
    val status = (this as? ResponseException)?.response?.status ?: return false
    return status == HttpStatusCode.TooManyRequests || status.value >= HttpStatusCode.InternalServerError.value
}
