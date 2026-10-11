package io.github.aedev.flow.sync

import android.content.Context
import io.github.aedev.flow.R
import io.github.aedev.flow.sync.protocol.SyncPeerException
import io.github.aedev.flow.sync.protocol.SyncProtocol
import io.github.aedev.flow.sync.protocol.SyncProtocolException
import java.io.IOException

/** Turns a failed session into a message that says what went wrong and what to try. */
internal object SyncErrorMessages {
    fun describe(
        context: Context,
        error: Throwable,
    ): String =
        when (error) {
            is SyncPeerException -> peer(context, error)

            is SyncProtocolException -> context.getString(local(error.code))

            is IOException -> context.getString(R.string.sync_error_unreachable)

            // Pre-session checks (bad code, both sides sending, no network) already throw localized text.
            else -> error.message ?: context.getString(R.string.sync_failed_title)
        }

    private fun local(code: String): Int =
        when (code) {
            SyncProtocol.CODE_TIMEOUT -> R.string.sync_error_timeout
            SyncProtocol.CODE_PEER_CLOSED -> R.string.sync_error_peer_closed
            SyncProtocol.CODE_HASH_MISMATCH -> R.string.sync_error_corrupted
            SyncProtocol.CODE_APPLY_FAILED -> R.string.sync_error_apply_failed
            SyncProtocol.CODE_UNSUPPORTED_VERSION -> R.string.sync_error_qr_wrong_version
            SyncProtocol.CODE_SAS_REJECTED -> R.string.sync_error_code_not_confirmed
            SyncProtocol.CODE_DECLINED, SyncProtocol.CODE_PEER_DECLINED -> R.string.sync_error_declined
            else -> R.string.sync_error_protocol
        }

    private fun peer(
        context: Context,
        error: SyncPeerException,
    ): String =
        when (error.code) {
            SyncProtocol.CODE_SAS_REJECTED -> context.getString(R.string.sync_error_code_rejected)
            SyncProtocol.CODE_APPLY_FAILED -> context.getString(R.string.sync_error_peer_apply_failed)
            SyncProtocol.CODE_TIMEOUT -> context.getString(R.string.sync_error_timeout)
            SyncProtocol.CODE_HASH_MISMATCH -> context.getString(R.string.sync_error_corrupted)
            SyncProtocol.CODE_UNSUPPORTED_VERSION -> context.getString(R.string.sync_error_qr_wrong_version)
            SyncProtocol.CODE_PROTOCOL -> context.getString(R.string.sync_error_protocol)
            else -> context.getString(R.string.sync_error_peer, error.detail)
        }
}
