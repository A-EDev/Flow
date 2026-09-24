package io.github.aedev.flow.data.update

/** Where the download of one release stands, as the update page and its notification show it. */
sealed interface UpdateDownload {
    data object Idle : UpdateDownload

    /** [progress] runs 0..1, or is null while the size is not known yet. */
    data class Running(
        val progress: Float?,
    ) : UpdateDownload

    data object Verifying : UpdateDownload

    data object Ready : UpdateDownload

    data class Failed(
        val reason: UpdateFailure,
    ) : UpdateDownload
}

enum class UpdateFailure { NETWORK, STORAGE, CHECKSUM, PACKAGE, INSTALL }
