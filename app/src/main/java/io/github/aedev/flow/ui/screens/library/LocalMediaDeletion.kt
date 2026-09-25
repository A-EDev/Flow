package io.github.aedev.flow.ui.screens.library

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** What asking to delete files led to. */
internal sealed interface LocalDeleteOutcome {
    /** The system asks the viewer first; the result comes back to the screen's launcher. */
    data class NeedsConsent(
        val request: IntentSender,
    ) : LocalDeleteOutcome

    data class Deleted(
        val count: Int,
    ) : LocalDeleteOutcome

    data object Failed : LocalDeleteOutcome
}

/** Whether a delete moves files to the system trash, where they stay restorable for 30 days. */
internal val deleteMovesToTrash: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

/**
 * Deletes [uris] the way this Android version allows: one trash request for the whole selection on
 * Android 11 and later, the consent prompt from a [RecoverableSecurityException] on Android 10, and
 * a direct delete before that.
 */
internal fun ContentResolver.requestDelete(uris: List<Uri>): LocalDeleteOutcome {
    if (uris.isEmpty()) return LocalDeleteOutcome.Failed
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return LocalDeleteOutcome.NeedsConsent(MediaStore.createTrashRequest(this, uris, true).intentSender)
    }
    var deleted = 0
    for (uri in uris) {
        try {
            deleted += delete(uri, null, null)
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                return LocalDeleteOutcome.NeedsConsent(e.userAction.actionIntent.intentSender)
            }
            return LocalDeleteOutcome.Failed
        }
    }
    return if (deleted > 0) LocalDeleteOutcome.Deleted(deleted) else LocalDeleteOutcome.Failed
}
