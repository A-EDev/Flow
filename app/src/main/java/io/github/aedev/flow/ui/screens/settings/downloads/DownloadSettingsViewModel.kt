package io.github.aedev.flow.ui.screens.settings.downloads

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.DownloadDialogStyle
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.VideoCodec
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Where downloads go: video or music. */
enum class DownloadTarget { VIDEO, MUSIC }

/** Free and total bytes on the volume a download location lives on. */
@Immutable
data class StorageStats(
    val freeBytes: Long,
    val totalBytes: Long,
) {
    val usedFraction: Float get() = if (totalBytes > 0) (totalBytes - freeBytes).toFloat() / totalBytes else 0f
}

@HiltViewModel
class DownloadSettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: PlayerPreferences,
    ) : SettingsViewModel() {
        val videoLocation = preferences.downloadLocation.asState(null)
        val musicLocation = preferences.musicDownloadLocation.asState(null)
        val quickQuality = preferences.defaultDownloadQuality.asState(VideoQuality.Q_720P)
        val codec = preferences.defaultDownloadCodec.asState(VideoCodec.AUTO)
        val menuStyle = preferences.downloadDialogStyle.asState(DownloadDialogStyle.FULL)
        val wifiOnly = preferences.downloadOverWifiOnly.asState(false)
        val threads = preferences.downloadThreads.asState(DEFAULT_THREADS)
        val cacheSizeMb = preferences.mediaCacheSizeMb.asState(DEFAULT_CACHE_MB)

        /** Space on the video location's volume, measured off the main thread whenever a location changes. */
        val storage =
            preferences.downloadLocation
                .map { video ->
                    statsFor(video ?: defaultPath(DownloadTarget.VIDEO))
                }.flowOn(Dispatchers.IO)
                .asState(null)

        fun defaultPath(target: DownloadTarget): String =
            runCatching {
                val directory = if (target == DownloadTarget.MUSIC) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
                File(Environment.getExternalStoragePublicDirectory(directory), APP_FOLDER).absolutePath
            }.getOrElse { internalPath() }

        fun downloadsPath(): String? =
            runCatching {
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    APP_FOLDER,
                ).absolutePath
            }.getOrNull()

        fun internalPath(): String = File(context.filesDir, INTERNAL_FOLDER).absolutePath

        /** Saves [path] for [target], creating the folder first; null goes back to the default. */
        fun setLocation(
            target: DownloadTarget,
            path: String?,
        ) = write {
            if (path != null) withContext(Dispatchers.IO) { runCatching { File(path).mkdirs() } }
            if (target == DownloadTarget.MUSIC) preferences.setMusicDownloadLocation(path) else preferences.setDownloadLocation(path)
        }

        fun setQuickQuality(value: VideoQuality) = write { preferences.setDefaultDownloadQuality(value) }

        fun setCodec(value: VideoCodec) = write { preferences.setDefaultDownloadCodec(value) }

        fun setMenuStyle(value: DownloadDialogStyle) = write { preferences.setDownloadDialogStyle(value) }

        fun setWifiOnly(value: Boolean) = write { preferences.setDownloadOverWifiOnly(value) }

        fun setThreads(value: Int) = write { preferences.setDownloadThreads(value) }

        fun setCacheSize(value: Int) = write { preferences.setMediaCacheSizeMb(value) }

        private fun statsFor(path: String): StorageStats? =
            runCatching {
                val directory = File(path).apply { if (!exists()) mkdirs() }
                val stat = StatFs(directory.path)
                StorageStats(freeBytes = stat.availableBytes, totalBytes = stat.totalBytes)
            }.getOrNull()

        companion object {
            const val DEFAULT_THREADS = 3
            const val DEFAULT_CACHE_MB = 500
            private const val APP_FOLDER = "Flow"
            private const val INTERNAL_FOLDER = "downloads"
        }
    }
