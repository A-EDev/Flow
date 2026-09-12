package io.github.aedev.flow.data.sponsordetection

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

interface SponsorTrainingSink {
    val stats: StateFlow<SponsorJournalStats>

    suspend fun recordEvaluation(event: SponsorEvaluationEvent): Boolean

    suspend fun recordFeedback(event: SponsorFeedbackEvent): Boolean

    suspend fun refreshStats()

    suspend fun exportTo(output: OutputStream)

    suspend fun clear()
}

class SponsorFeedbackJournal(
    context: Context,
) : SponsorTrainingSink {
    private val store =
        SponsorFeedbackFileStore(
            journal = File(context.noBackupFilesDir, JOURNAL_FILE_NAME),
            dedupeIndex = File(context.noBackupFilesDir, DEDUPE_FILE_NAME),
        )

    override val stats: StateFlow<SponsorJournalStats> = store.stats

    override suspend fun recordEvaluation(event: SponsorEvaluationEvent): Boolean = store.recordEvaluation(event)

    override suspend fun recordFeedback(event: SponsorFeedbackEvent): Boolean = store.recordFeedback(event)

    override suspend fun refreshStats() = store.refreshStats()

    override suspend fun exportTo(output: OutputStream) = store.exportTo(output)

    override suspend fun clear() = store.clear()

    private companion object {
        const val JOURNAL_FILE_NAME = "sponsor_training_v1.jsonl"
        const val DEDUPE_FILE_NAME = "sponsor_training_v1.keys"
    }
}

internal class SponsorFeedbackFileStore(
    private val journal: File,
    private val dedupeIndex: File = File(journal.parentFile, "${journal.name}.keys"),
    private val maxBytes: Long = 100L * 1024L * 1024L,
    private val json: Json = Json { encodeDefaults = true },
) {
    private val mutex = Mutex()
    private val _stats = MutableStateFlow(SponsorJournalStats())
    val stats: StateFlow<SponsorJournalStats> = _stats.asStateFlow()

    suspend fun recordEvaluation(event: SponsorEvaluationEvent): Boolean =
        append(
            line = json.encodeToString(event),
            dedupeKey = event.dedupeKey,
        )

    suspend fun recordFeedback(event: SponsorFeedbackEvent): Boolean = append(json.encodeToString(event))

    private suspend fun append(
        line: String,
        dedupeKey: String? = null,
    ): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                journal.parentFile?.mkdirs()
                if (dedupeKey != null && containsDedupeKey(dedupeKey)) {
                    updateStatsLocked()
                    return@withLock false
                }
                val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
                if (journal.length() + bytes.size > maxBytes) {
                    updateStatsLocked(forceFull = true)
                    return@withLock false
                }
                FileOutputStream(journal, true).use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                if (dedupeKey != null) {
                    FileOutputStream(dedupeIndex, true).use { output ->
                        output.write((dedupeKey + "\n").toByteArray(Charsets.UTF_8))
                        output.fd.sync()
                    }
                }
                updateStatsLocked()
                true
            }
        }

    suspend fun refreshStats() =
        withContext(Dispatchers.IO) {
            mutex.withLock { updateStatsLocked() }
        }

    suspend fun exportTo(output: OutputStream) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (journal.isFile) journal.inputStream().use { it.copyTo(output) }
                output.flush()
            }
        }

    suspend fun clear() =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                listOf(journal, dedupeIndex).forEach { file ->
                    if (file.exists() && !file.delete()) {
                        throw IllegalStateException("Could not delete ${file.name}")
                    }
                }
                _stats.value = SponsorJournalStats()
            }
        }

    private fun containsDedupeKey(key: String): Boolean = dedupeIndex.isFile && dedupeIndex.useLines { lines -> lines.any { it == key } }

    private fun updateStatsLocked(forceFull: Boolean = false) {
        var evaluations = 0
        var feedback = 0
        if (journal.isFile) {
            journal.forEachLine { line ->
                when {
                    "\"event_type\":\"evaluation\"" in line -> evaluations++
                    "\"event_type\":\"feedback\"" in line -> feedback++
                }
            }
        }
        val size = journal.length()
        _stats.value =
            SponsorJournalStats(
                evaluationCount = evaluations,
                feedbackCount = feedback,
                sizeBytes = size,
                isFull = forceFull || size >= maxBytes,
            )
    }
}
