package io.github.aedev.flow.data.video.downloader.work

import java.io.File

/**
 * The working files of one download in app-private staging. Every name is keyed by video id, so two
 * downloads can never write to each other's files, whatever their titles.
 */
internal class DownloadStaging(
    directory: File,
    videoId: String,
) {
    private val stem = File(directory, videoId.replace(UNSAFE, "_")).path

    val videoPart = File("$stem.video.part")
    val audioPart = File("$stem.audio.part")
    val state = File("$stem.state.json")

    fun output(extension: String) = File("$stem.out.$extension")

    /** Everything this download wrote to staging, including a half-made output. */
    fun clear() {
        listOf(videoPart, audioPart, state, File("${state.path}.tmp"), output("mp4"), output("m4a"), output("webm"))
            .forEach { it.delete() }
    }

    /** A half-made output, keeping the fetched parts for a retry. */
    fun clearOutputs() {
        listOf(output("mp4"), output("m4a"), output("webm")).forEach { it.delete() }
    }

    /** The part files only, for a transfer that has to start over rather than resume. */
    fun clearParts() {
        listOf(videoPart, audioPart, state).forEach { it.delete() }
    }

    private companion object {
        val UNSAFE = Regex("[^A-Za-z0-9_-]")
    }
}
