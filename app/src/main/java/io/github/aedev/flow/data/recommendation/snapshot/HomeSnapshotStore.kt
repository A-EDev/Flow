/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation.snapshot

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last few Home snapshots, kept outside backups and only while recording is on. They leave
 * the device only when the viewer exports one.
 */
@Singleton
class HomeSnapshotStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private companion object {
            const val TAG = "HomeSnapshotStore"
            const val DIRECTORY = "home_snapshots"
            const val KEEP = 3
            val json = Json { encodeDefaults = true }
        }

        private val directory: File get() = File(context.noBackupFilesDir, "neuro/$DIRECTORY")

        private fun files(): List<File> =
            directory
                .listFiles { file -> file.name.endsWith(".json.gz") }
                .orEmpty()
                .sortedByDescending { it.name }

        internal suspend fun save(snapshot: HomeSnapshot) =
            withContext(Dispatchers.IO) {
                runCatching {
                    directory.mkdirs()
                    File(directory, "home_${snapshot.createdAt}.json.gz").outputStream().use { out ->
                        GZIPOutputStream(out).use { it.write(json.encodeToString(snapshot).toByteArray()) }
                    }
                    files().drop(KEEP).forEach(File::delete)
                }.onFailure { Log.e(TAG, "Failed to save a Home snapshot", it) }
            }

        suspend fun count(): Int = withContext(Dispatchers.IO) { files().size }

        /** Writes the newest snapshot, still gzipped, to [output]; false when there is none or the write fails. */
        suspend fun exportLatest(output: OutputStream): Boolean =
            withContext(Dispatchers.IO) {
                val latest = files().firstOrNull() ?: return@withContext false
                runCatching { latest.inputStream().use { it.copyTo(output) } }
                    .onFailure { Log.e(TAG, "Failed to export a Home snapshot", it) }
                    .isSuccess
            }

        suspend fun clear() =
            withContext(Dispatchers.IO) {
                directory.deleteRecursively()
            }
    }
