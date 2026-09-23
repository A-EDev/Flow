package io.github.aedev.flow.data.stats

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * One ledger file: months keyed "yyyy-MM", plus [knownKeys] for ledgers that need to remember what
 * they had already seen before they started counting (the channels a user watched before the video
 * ledger shipped). The field names match the music ledger's original single-file format, so that
 * file reads back unchanged as a cold file.
 */
@Serializable
data class StoredLedger<M>(
    val schemaVersion: Int = 1,
    val months: Map<String, M> = emptyMap(),
    val knownKeys: List<String> = emptyList(),
)

/** What a ledger store holds after loading both files: every month, the hot one winning. */
class LoadedLedger<M>(
    val months: Map<String, M>,
    val knownKeys: Set<String>,
)

/**
 * Keeps a monthly ledger in two files so every save stays small. The hot file holds only the
 * current month and is rewritten on each debounced save; the cold file holds closed months and is
 * rewritten only when the set of closed months changes (a month rolled over, or old months were
 * pruned). Instances must be process singletons: DataStore allows one instance per file.
 */
class MonthlyLedgerStore<M>(
    private val appContext: Context,
    private val tag: String,
    hotFileName: String,
    coldFileName: String,
    monthSerializer: KSerializer<M>,
) {
    private val fileSerializer = StoredLedger.serializer(monthSerializer)
    private val hot: DataStore<StoredLedger<M>> = dataStoreFor(hotFileName)
    private val cold: DataStore<StoredLedger<M>> = dataStoreFor(coldFileName)
    private var coldKeys: Set<String> = emptySet()
    private var coldKnown: Set<String> = emptySet()

    suspend fun load(): LoadedLedger<M> =
        withContext(Dispatchers.IO) {
            val closed = readOrEmpty(cold)
            val current = readOrEmpty(hot)
            coldKeys = closed.months.keys
            coldKnown = closed.knownKeys.toSet()
            LoadedLedger(months = closed.months + current.months, knownKeys = coldKnown)
        }

    /**
     * Writes [current] as the hot file and, when the closed months differ from what the cold file
     * holds, [closed] as the cold file. [closed] is only invoked when a cold write is needed, so
     * callers can defer the conversion of every closed month until it is actually due.
     */
    suspend fun save(
        currentKey: String,
        current: M?,
        closedKeys: Set<String>,
        knownKeys: Set<String>,
        closed: () -> Map<String, M>,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            hot.updateData { StoredLedger(months = current?.let { mapOf(currentKey to it) }.orEmpty()) }
            if (closedKeys != coldKeys || knownKeys != coldKnown) {
                val months = closed()
                cold.updateData { StoredLedger(months = months, knownKeys = knownKeys.sorted()) }
                coldKeys = months.keys
                coldKnown = knownKeys
            }
        }.onFailure { Log.e(tag, "Failed to persist ledger", it) }
    }

    /** Replaces both files, for a restore. */
    suspend fun replace(
        months: Map<String, M>,
        knownKeys: Set<String>,
        currentKey: String,
    ) = withContext(Dispatchers.IO) {
        val closed = months - currentKey
        cold.updateData { StoredLedger(months = closed, knownKeys = knownKeys.sorted()) }
        hot.updateData { StoredLedger(months = months.filterKeys { it == currentKey }) }
        coldKeys = closed.keys
        coldKnown = knownKeys
    }

    private suspend fun readOrEmpty(store: DataStore<StoredLedger<M>>): StoredLedger<M> =
        runCatching { store.data.first() }
            .onFailure { Log.e(tag, "Failed to load ledger", it) }
            .getOrDefault(StoredLedger())

    private fun dataStoreFor(fileName: String): DataStore<StoredLedger<M>> =
        DataStoreFactory.create(
            serializer = LedgerFileSerializer(fileSerializer, tag),
            corruptionHandler = ReplaceFileCorruptionHandler { StoredLedger() },
            produceFile = { appContext.dataStoreFile(fileName) },
        )
}

private class LedgerFileSerializer<M>(
    private val serializer: KSerializer<StoredLedger<M>>,
    private val tag: String,
) : Serializer<StoredLedger<M>> {
    override val defaultValue: StoredLedger<M> = StoredLedger()

    override suspend fun readFrom(input: InputStream): StoredLedger<M> =
        try {
            val text = input.readBytes().decodeToString()
            if (text.isBlank()) defaultValue else LedgerJson.decodeFromString(serializer, text)
        } catch (e: SerializationException) {
            throw CorruptionException("Corrupted ledger", e)
        } catch (e: IllegalArgumentException) {
            Log.w(tag, "Unreadable ledger, starting empty: ${e.message}")
            defaultValue
        }

    override suspend fun writeTo(
        t: StoredLedger<M>,
        output: OutputStream,
    ) {
        output.write(LedgerJson.encodeToString(serializer, t).encodeToByteArray())
    }
}

internal val LedgerJson = Json { ignoreUnknownKeys = true }
