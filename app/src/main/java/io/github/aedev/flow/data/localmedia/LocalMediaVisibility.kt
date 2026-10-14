package io.github.aedev.flow.data.localmedia

private const val MILLIS_PER_SECOND = 1_000L

// Folders chat apps, recorders and the system keep audio in. WhatsApp and Telegram voice notes are
// often tagged IS_MUSIC, so the MediaStore flags alone let them into a music library.
private val APP_AUDIO_PATH_FRAGMENTS =
    listOf(
        "whatsapp",
        "telegram",
        "signal/",
        "viber",
        "threema",
        "voice note",
        "voicenote",
        "voice recorder",
        "voicerecorder",
        "voicemail",
        "recordings/",
        "recording/",
        "sound recorder",
        "soundrecorder",
        "call recording",
        "callrecord",
        "call_rec",
        "notifications/",
        "ringtones/",
        "alarms/",
    )

/** Why a file is left out of the library, or null when it is shown. */
enum class HiddenReason { FOLDER, APP_AUDIO, TOO_SHORT }

fun LocalMediaItem.hiddenReason(settings: LocalMediaSettings): HiddenReason? {
    if (folderId.isNotEmpty() && folderId in settings.hiddenFolderIds) return HiddenReason.FOLDER
    if (isVideo) return null
    if (settings.hideAppAudio) {
        val folder = path.lowercase()
        if (APP_AUDIO_PATH_FRAGMENTS.any { folder.contains(it) }) return HiddenReason.APP_AUDIO
    }
    val minMs = settings.minAudioSeconds * MILLIS_PER_SECOND
    if (minMs > 0 && durationMs in 1 until minMs) return HiddenReason.TOO_SHORT
    return null
}
