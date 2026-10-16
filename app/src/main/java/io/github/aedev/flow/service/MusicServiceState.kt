package io.github.aedev.flow.service

/**
 * Whether the music service is alive in this process, for instance after the system media controls
 * resumed it. Kept apart from [Media3MusicService] so reading it never loads the service class.
 */
internal object MusicServiceState {
    @Volatile
    var isRunning: Boolean = false
}
