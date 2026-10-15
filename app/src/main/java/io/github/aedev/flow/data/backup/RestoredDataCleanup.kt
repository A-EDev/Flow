package io.github.aedev.flow.data.backup

import java.io.File

/**
 * Files that belong to one install and must not come back from a backup: the recommendation engine
 * (#907) and the sync identity and state. Backups made before the current rules still carry them.
 */
internal object RestoredDataCleanup {
    private val FILES = listOf("user_neuro_brain.json")
    private val DATASTORE_FILES =
        listOf(
            "flow_neuro_brain_v7.json",
            "flow_neuro_brain_v8.json",
            "flow_neuro_brain_v9.json",
            "flow_neuro_brain_v10.json",
            "flow_neuro_content_v1.json",
            "flow_neuro_categories_v1.json",
            "flow_neuro_cowatch_v1.json",
            "flow_neuro_neighbours_v1.json",
            "flow_music_brain_v1.json",
            "sync_identity.preferences_pb",
            "sync_brain_crdt.preferences_pb",
            "sync_music_brain_crdt.preferences_pb",
        )
    private const val SYNC_LEDGER_PREFIX = "sync_ledger_"

    /** Deletes those files under [filesDir] and returns the names it removed. */
    fun deleteInstallOnlyFiles(filesDir: File): List<String> {
        val datastore = File(filesDir, "datastore")
        val ledgers = datastore.listFiles { file -> file.name.startsWith(SYNC_LEDGER_PREFIX) }.orEmpty().toList()
        return (FILES.map { File(filesDir, it) } + DATASTORE_FILES.map { File(datastore, it) } + ledgers)
            .filter { it.isFile && it.delete() }
            .map { it.name }
    }
}
