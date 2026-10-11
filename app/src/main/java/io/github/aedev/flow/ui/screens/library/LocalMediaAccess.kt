package io.github.aedev.flow.ui.screens.library

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** How much of one kind of media Flow may read. */
internal enum class MediaAccess { FULL, PARTIAL, NONE }

/** The permissions the local library asks for together, for videos and music. */
internal fun localMediaPermissions(): Array<String> =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        }

        else -> {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

private fun Context.granted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Videos may be partly visible on Android 14 and later, when the viewer picked only some. */
internal fun Context.videoAccess(): MediaAccess =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_MEDIA_VIDEO) -> MediaAccess.FULL

        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> MediaAccess.FULL

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
        -> MediaAccess.PARTIAL

        else -> MediaAccess.NONE
    }

internal fun Context.musicAccess(): MediaAccess {
    val permission =
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    return if (granted(permission)) MediaAccess.FULL else MediaAccess.NONE
}

/** After a denial, whether the system will still show the prompt, or only app settings can grant it. */
internal fun Activity.canStillAsk(): Boolean = localMediaPermissions().any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }

/** What Downloads asks for to list the files an earlier install saved: videos and songs, never photos. */
internal fun downloadReadPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

/** Flow's page in system settings, the only place left to grant a permission after "Don't allow". */
internal fun Context.openAppPermissionSettings() {
    runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) }
}
