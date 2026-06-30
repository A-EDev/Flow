package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.protocol.ApplyStats
import io.github.aedev.flow.sync.protocol.TransferSummary

/** UI-facing state of a sync session, emitted by [SyncManager] as a single StateFlow. */
sealed interface SyncState {
    data object Idle : SyncState

    /** Generating the key / starting the server (host) or parsing the QR (client). */
    data object Preparing : SyncState

    /** Host is showing the QR; waiting for a peer to scan + connect. */
    data class ShowingQr(
        val qrText: String,
        val sas: String,
        val expiresAtEpochSeconds: Long,
        /** True when this device will send (peer receives); false when this device will receive. */
        val sending: Boolean,
    ) : SyncState

    /** Client connecting to the host's socket. */
    data object Connecting : SyncState

    /** Both devices show these 6 digits; the user confirms they match. */
    data class AwaitingSas(val sas: String) : SyncState

    /** Receiver is asked to approve merging what the peer offers. */
    data class AwaitingConsent(val summary: TransferSummary) : SyncState

    /** A collection is transferring. */
    data class Transferring(val collection: String, val done: Int, val total: Int) : SyncState

    /** Merge finished — per-collection stats. */
    data class Done(val peerName: String, val stats: Map<String, ApplyStats>) : SyncState

    data class Failed(val message: String) : SyncState
}
