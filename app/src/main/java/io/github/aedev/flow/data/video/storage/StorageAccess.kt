package io.github.aedev.flow.data.video.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/**
 * Which shared files Flow may read. Files an earlier install or another app saved are visible only
 * with All files access, or with the media permissions for their kind.
 */
data class StorageAccess(
    val allFiles: Boolean,
    val video: Boolean,
    val audio: Boolean,
) {
    /** Whether every video and song an earlier install downloaded can be found. */
    val seesAllDownloads: Boolean get() = allFiles || (video && audio)

    companion object {
        fun read(context: Context): StorageAccess {
            val tiramisu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val legacyRead = !tiramisu && context.granted(Manifest.permission.READ_EXTERNAL_STORAGE)
            return StorageAccess(
                allFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager(),
                video = legacyRead || (tiramisu && context.granted(Manifest.permission.READ_MEDIA_VIDEO)),
                audio = legacyRead || (tiramisu && context.granted(Manifest.permission.READ_MEDIA_AUDIO)),
            )
        }

        private fun Context.granted(permission: String) =
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}
