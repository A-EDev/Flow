/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/** Bridges the Hilt-injected music brain into the export/import settings screens. */
@HiltViewModel
class MusicBrainBackupViewModel
    @Inject
    constructor(
        private val musicBrain: MusicBrainEngine,
    ) : ViewModel() {
        suspend fun exportTo(out: OutputStream): Boolean =
            try {
                musicBrain.exportBrainToStream(out)
                true
            } catch (e: Exception) {
                Log.e("MusicBrainBackup", "Export failed", e)
                false
            }

        suspend fun importFrom(input: InputStream): Boolean =
            try {
                musicBrain.importBrainFromStream(input)
                true
            } catch (e: Exception) {
                Log.e("MusicBrainBackup", "Import failed", e)
                false
            }
    }
