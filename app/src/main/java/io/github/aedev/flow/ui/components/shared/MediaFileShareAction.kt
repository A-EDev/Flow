package io.github.aedev.flow.ui.components.shared

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.aedev.flow.R

/**
 * Shares files on the device by their `content://` URIs, granting the receiving app read access,
 * so a video or song can be sent the way a gallery or file manager would send it.
 */
fun Context.shareMediaFiles(
    uris: List<Uri>,
    mimeType: String,
) {
    if (uris.isEmpty()) return
    val send =
        if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }.apply {
            type = mimeType
            clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    val chooser = Intent.createChooser(send, getString(R.string.share))
    if (this !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(chooser) }
}
