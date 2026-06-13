package io.github.aedev.flow.ui.screens.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LocalMediaItem(
    val id: Long,
    val contentUri: String,
    val title: String,
    val subtitle: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val isVideo: Boolean,
    val artworkUri: String? = null
)

data class LocalMediaUiState(
    val videos: List<LocalMediaItem> = emptyList(),
    val music: List<LocalMediaItem> = emptyList(),
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
    val permissionDenied: Boolean = false
)

@HiltViewModel
class LocalMediaViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalMediaUiState())
    val uiState: StateFlow<LocalMediaUiState> = _uiState.asStateFlow()

    private val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

    fun scan() {
        if (_uiState.value.isScanning) return
        _uiState.update { it.copy(isScanning = true, permissionDenied = false) }
        viewModelScope.launch {
            val videos = withContext(PerformanceDispatcher.diskIO) { queryVideos() }
            val music = withContext(PerformanceDispatcher.diskIO) { queryMusic() }
            _uiState.update {
                it.copy(videos = videos, music = music, isScanning = false, hasScanned = true)
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.update { it.copy(permissionDenied = true, isScanning = false, hasScanned = true) }
    }

    private fun queryVideos(): List<LocalMediaItem> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        val result = ArrayList<LocalMediaItem>()
        try {
            appContext.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val size = cursor.getLong(sizeCol)
                    if (size <= 0L) continue
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(nameCol)?.substringBeforeLast('.')
                        ?: continue
                    val uri = ContentUris.withAppendedId(collection, id)
                    result += LocalMediaItem(
                        id = id,
                        contentUri = uri.toString(),
                        title = title,
                        subtitle = cursor.getString(bucketCol)?.takeIf { it.isNotBlank() } ?: "",
                        durationMs = cursor.getLong(durCol),
                        sizeBytes = size,
                        isVideo = true
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryVideos failed", e)
        }
        return result
    }

    private fun queryMusic(): List<LocalMediaItem> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA
        )
        val selectionParts = mutableListOf(
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0",
            "${MediaStore.Audio.Media.IS_ALARM} = 0",
            "${MediaStore.Audio.Media.IS_RINGTONE} = 0",
            "${MediaStore.Audio.Media.IS_PODCAST} = 0"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            selectionParts += "${MediaStore.Audio.Media.IS_RECORDING} = 0"
        }
        val result = ArrayList<LocalMediaItem>()
        try {
            appContext.contentResolver.query(
                collection,
                projection,
                selectionParts.joinToString(" AND "),
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val size = cursor.getLong(sizeCol)
                    if (size <= 0L) continue
                    val durationMs = cursor.getLong(durCol)
                    if (durationMs in 1 until MIN_MUSIC_DURATION_MS) continue
                    val path = (cursor.getString(dataCol) ?: "").lowercase()
                    if (MUSIC_PATH_DENYLIST.any { path.contains(it) }) continue
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(nameCol)?.substringBeforeLast('.')
                        ?: continue
                    val artist = cursor.getString(artistCol)
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: ""
                    val albumId = cursor.getLong(albumIdCol)
                    val artwork = if (albumId > 0) {
                        ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
                    } else null
                    val uri = ContentUris.withAppendedId(collection, id)
                    result += LocalMediaItem(
                        id = id,
                        contentUri = uri.toString(),
                        title = title,
                        subtitle = artist,
                        durationMs = durationMs,
                        sizeBytes = size,
                        isVideo = false,
                        artworkUri = artwork
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryMusic failed", e)
        }
        return result
    }

    companion object {
        private const val TAG = "LocalMediaViewModel"
        private const val MIN_MUSIC_DURATION_MS = 60_000L

        private val MUSIC_PATH_DENYLIST = listOf(
            "whatsapp", "telegram", "/signal", "viber", "/threema",
            "voice note", "voicenote", "voice recorder", "voicerecorder", "voicemail",
            "/recordings/", "/recording/", "sound recorder", "soundrecorder",
            "call recording", "callrecord", "/call_rec",
            "/notifications/", "/ringtones/", "/alarms/", "/ui/"
        )

        fun localMediaId(item: LocalMediaItem): String = "local_${item.id}"
    }
}
