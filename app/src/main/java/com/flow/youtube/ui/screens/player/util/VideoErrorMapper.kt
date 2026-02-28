package com.flow.youtube.ui.screens.player.util

import android.util.Log
import android.content.Context
import com.flow.youtube.R
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps throwables (from NewPipe Extractor, ExoPlayer, network layer, etc.) to
 * human-readable, actionable error messages shown in the video player.
 *
 * Designed to mirror NewPipe's `ErrorInfo.getMessage()` logic but return plain
 * strings suitable for the Flow player's snackbar / error panel.
 */
object VideoErrorMapper {

    private const val TAG = "VideoErrorMapper"

    /**
     * Structured error result carrying a short display message and an optional
     * hint that gives the user more context or a suggested action.
     */
    data class VideoError(
        /** Short primary message — always present, suitable for a snackbar. */
        val message: String,
        /**
         * Optional secondary hint shown below the primary message in the error
         * panel (e.g. "Try a VPN or check your region settings.").
         */
        val hint: String? = null,
        /** Whether it is worth retrying (show a Retry button). */
        val isRetryable: Boolean = true,
        /** Whether this can be "fixed" by the user (vs. a server-side restriction). */
        val isUserActionable: Boolean = false
    )

    /**
     * Converts any [Throwable] thrown during stream resolution or playback to a
     * [VideoError] with a professional, non-technical message.
     *
     * @param context     Android context to resolve string resources.
     * @param throwable   The exception to map.
     * @param videoId     Optional video ID — used only for log output.
     */
    fun from(context: Context, throwable: Throwable?, videoId: String? = null): VideoError {
        Log.w(TAG, "Mapping error for video=$videoId: ${throwable?.javaClass?.simpleName}: ${throwable?.message}")

        return when {

            // ── Content restriction exceptions (NewPipe Extractor) ────────────

            throwable is AgeRestrictedContentException ->
                VideoError(
                    message = context.getString(R.string.error_age_restricted),
                    hint = context.getString(R.string.error_age_restricted_hint),
                    isRetryable = false,
                    isUserActionable = false
                )

            throwable is PrivateContentException ->
                VideoError(
                    message = context.getString(R.string.error_private_video),
                    hint = context.getString(R.string.error_private_video_hint),
                    isRetryable = false,
                    isUserActionable = false
                )

            throwable is GeographicRestrictionException ||
            throwable is UnsupportedContentInCountryException ->
                VideoError(
                    message = context.getString(R.string.error_geo_restricted),
                    hint = context.getString(R.string.error_geo_restricted_hint),
                    isRetryable = false,
                    isUserActionable = true
                )

            throwable is PaidContentException ->
                VideoError(
                    message = context.getString(R.string.error_paid_content),
                    hint = context.getString(R.string.error_paid_content_hint),
                    isRetryable = false,
                    isUserActionable = false
                )

            throwable is YoutubeMusicPremiumContentException ->
                VideoError(
                    message = context.getString(R.string.error_yt_music_premium),
                    hint = context.getString(R.string.error_yt_music_premium_hint),
                    isRetryable = false,
                    isUserActionable = false
                )

            throwable is AccountTerminatedException -> {
                val reason = throwable.message?.takeIf { it.isNotBlank() }
                VideoError(
                    message = context.getString(R.string.error_channel_terminated),
                    hint = if (reason != null) reason else context.getString(R.string.error_channel_terminated_hint),
                    isRetryable = false,
                    isUserActionable = false
                )
            }

            throwable is ReCaptchaException ->
                VideoError(
                    message = context.getString(R.string.error_captcha),
                    hint = context.getString(R.string.error_captcha_hint),
                    isRetryable = true,
                    isUserActionable = true
                )

            // Generic "content not available" must come AFTER the specific sub-classes above
            throwable is ContentNotAvailableException ->
                VideoError(
                    message = context.getString(R.string.error_not_available),
                    hint = context.getString(R.string.error_not_available_hint),
                    isRetryable = false,
                    isUserActionable = false
                )

            throwable is ContentNotSupportedException ->
                VideoError(
                    message = context.getString(R.string.error_not_supported),
                    hint = context.getString(R.string.error_not_supported_hint),
                    isRetryable = false,
                    isUserActionable = false
                )

            // ── Network / connectivity errors ─────────────────────────────────

            throwable is UnknownHostException ->
                VideoError(
                    message = context.getString(R.string.error_no_internet),
                    hint = context.getString(R.string.error_no_internet_hint),
                    isRetryable = true,
                    isUserActionable = true
                )

            throwable is SocketTimeoutException ->
                VideoError(
                    message = context.getString(R.string.error_timeout),
                    hint = context.getString(R.string.error_timeout_hint),
                    isRetryable = true,
                    isUserActionable = true
                )

            throwable is kotlinx.coroutines.TimeoutCancellationException ->
                VideoError(
                    message = context.getString(R.string.error_timeout), // Use common connection reset title
                    hint = context.getString(R.string.error_timeout_hint),
                    isRetryable = true,
                    isUserActionable = true
                )

            throwable != null && isNetworkRelated(throwable) ->
                VideoError(
                    message = context.getString(R.string.error_network),
                    hint = context.getString(R.string.error_network_hint),
                    isRetryable = true,
                    isUserActionable = true
                )

            // ── ExoPlayer / media source errors ──────────────────────────────

            throwable != null && isExoHttpForbidden(throwable) ->
                VideoError(
                    message = context.getString(R.string.error_http_forbidden),
                    hint = context.getString(R.string.error_http_forbidden_hint),
                    isRetryable = true,
                    isUserActionable = false
                )

            throwable != null && isExoHttpError(throwable) -> {
                val code = getExoHttpCode(throwable) ?: ""
                VideoError(
                    message = context.getString(R.string.error_http_general, code.toString()),
                    hint = context.getString(R.string.error_http_general_hint, code.toString()),
                    isRetryable = true,
                    isUserActionable = false
                )
            }

            throwable != null && isExoSourceError(throwable) ->
                VideoError(
                    message = context.getString(R.string.error_stream_failed),
                    hint = context.getString(R.string.error_stream_failed_hint),
                    isRetryable = true,
                    isUserActionable = false
                )

            throwable != null && isExoRendererError(throwable) ->
                VideoError(
                    message = context.getString(R.string.error_codec_issue),
                    hint = context.getString(R.string.error_codec_issue_hint),
                    isRetryable = true,
                    isUserActionable = true
                )

            throwable != null && isExoError(throwable) ->
                VideoError(
                    message = context.getString(R.string.error_playback_failed),
                    hint = context.getString(R.string.error_playback_failed_hint),
                    isRetryable = true,
                    isUserActionable = false
                )

            // ── NewPipe parsing error (extraction failed, not network) ────────

            throwable is ExtractionException ->
                VideoError(
                    message = context.getString(R.string.error_extraction_failed),
                    hint = context.getString(R.string.error_extraction_failed_hint),
                    isRetryable = true,
                    isUserActionable = false
                )

            // ── Fallback ─────────────────────────────────────────────────────

            throwable != null ->
                VideoError(
                    message = context.getString(R.string.error_generic),
                    hint = buildFallbackHint(throwable),
                    isRetryable = true,
                    isUserActionable = false
                )

            else ->
                VideoError(
                    message = context.getString(R.string.error_generic),
                    hint = context.getString(R.string.error_generic_hint),
                    isRetryable = true,
                    isUserActionable = false
                )
        }
    }

    /**
     * Convenience overload for the timeout-only path where no exception is available.
     */
    fun fromTimeout(context: Context): VideoError = VideoError(
        message = context.getString(R.string.error_timeout),
        hint = context.getString(R.string.error_timeout_hint),
        isRetryable = true,
        isUserActionable = true
    )

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun isNetworkRelated(t: Throwable): Boolean {
        return t is java.io.IOException ||
            t.cause?.let { isNetworkRelated(it) } == true
    }

    /** Returns true when [t] is an ExoPlaybackException with an HTTP 403 cause. */
    private fun isExoHttpForbidden(t: Throwable): Boolean {
        return try {
            if (t.javaClass.simpleName != "ExoPlaybackException") return false
            val cause = t.cause ?: return false
            if (cause.javaClass.simpleName != "InvalidResponseCodeException") return false
            val codeField = cause.javaClass.getField("responseCode")
            (codeField.get(cause) as? Int) == 403
        } catch (_: Exception) { false }
    }

    /** Returns true when [t] is an ExoPlaybackException with any non-2xx HTTP cause. */
    private fun isExoHttpError(t: Throwable): Boolean {
        return try {
            if (t.javaClass.simpleName != "ExoPlaybackException") return false
            val cause = t.cause ?: return false
            cause.javaClass.simpleName == "InvalidResponseCodeException"
        } catch (_: Exception) { false }
    }

    private fun getExoHttpCode(t: Throwable): Int? {
        return try {
            val cause = t.cause ?: return null
            val field = cause.javaClass.getField("responseCode")
            field.get(cause) as? Int
        } catch (_: Exception) { null }
    }

    private fun isExoSourceError(t: Throwable): Boolean {
        return try {
            if (t.javaClass.simpleName != "ExoPlaybackException") return false
            val typeField = t.javaClass.getField("type")
            (typeField.get(t) as? Int) == 0 // TYPE_SOURCE = 0
        } catch (_: Exception) { false }
    }

    private fun isExoRendererError(t: Throwable): Boolean {
        return try {
            if (t.javaClass.simpleName != "ExoPlaybackException") return false
            val typeField = t.javaClass.getField("type")
            (typeField.get(t) as? Int) == 1 // TYPE_RENDERER = 1
        } catch (_: Exception) { false }
    }

    private fun isExoError(t: Throwable): Boolean =
        t.javaClass.simpleName == "ExoPlaybackException"

    private fun buildFallbackHint(t: Throwable): String {
        // Provide a minimal technical hint without leaking a raw stack trace.
        val type = t.javaClass.simpleName
        val msg  = t.message?.take(120)?.let { if (it.isNotBlank()) it else null }
        return when {
            msg != null -> "$type: $msg"
            else        -> "Error type: $type. Please try again or restart the app."
        }
    }
}
