package io.github.aedev.flow.sync.merge

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aedev.flow.data.local.safePreferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.sync.canonical.CanonicalBrain
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.brainCrdtDataStore by safePreferencesDataStore(name = "sync_brain_crdt")

/**
 * Per-device G-Counter state for the brain's additive counters (idf docs/interactions/word-freq),
 * persisted alongside the brain. This is what makes the G-Counter sums idempotent: each device
 * only ever increments its OWN sub-count (via [attributeLocal] delta-attribution), so re-syncing
 * the same peer re-maxes its sub-count to the same value — no double-counting.
 */
@Serializable
data class BrainCrdtState(
    val idfDocs: Map<String, Long> = emptyMap(),
    val interactions: Map<String, Long> = emptyMap(),
    val idfWords: Map<String, Map<String, Long>> = emptyMap(),
    val lastIdfDocsScalar: Long = 0,
    val lastInteractionsScalar: Long = 0,
    val lastIdfWordScalars: Map<String, Long> = emptyMap(),
) {
    companion object {
        /**
         * Fold the local brain's growth since the last update into [myDevice]'s sub-counts.
         * Because sync is the only cross-device path, any change between syncs is local activity.
         */
        fun attributeLocal(
            state: BrainCrdtState,
            myDevice: String,
            idfDocsScalar: Long,
            interactionsScalar: Long,
            idfWordCounts: Map<String, Long>,
        ): BrainCrdtState {
            val docDelta = (idfDocsScalar - state.lastIdfDocsScalar).coerceAtLeast(0)
            val newIdfDocs = state.idfDocs + (myDevice to ((state.idfDocs[myDevice] ?: 0L) + docDelta))
            val intDelta = (interactionsScalar - state.lastInteractionsScalar).coerceAtLeast(0)
            val newInteractions = state.interactions + (myDevice to ((state.interactions[myDevice] ?: 0L) + intDelta))

            val newIdfWords = HashMap(state.idfWords)
            val newLastWords = HashMap(state.lastIdfWordScalars)
            for ((word, count) in idfWordCounts) {
                val delta = (count - (state.lastIdfWordScalars[word] ?: 0L)).coerceAtLeast(0)
                val perDev = HashMap(state.idfWords[word] ?: emptyMap())
                perDev[myDevice] = (perDev[myDevice] ?: 0L) + delta
                newIdfWords[word] = perDev
                newLastWords[word] = count
            }
            return state.copy(
                idfDocs = newIdfDocs,
                interactions = newInteractions,
                idfWords = newIdfWords,
                lastIdfDocsScalar = idfDocsScalar,
                lastInteractionsScalar = interactionsScalar,
                lastIdfWordScalars = newLastWords,
            )
        }

        /** After merging with a peer, adopt the merged per-device maps + reset the scalar baselines. */
        fun afterMerge(state: BrainCrdtState, merged: CanonicalBrain): BrainCrdtState = state.copy(
            idfDocs = merged.idfTotalDocuments.perDevice,
            interactions = merged.totalInteractions.perDevice,
            idfWords = merged.idfWordFrequency.mapValues { it.value.perDevice },
            lastIdfDocsScalar = merged.idfTotalDocuments.sum(),
            lastInteractionsScalar = merged.totalInteractions.sum(),
            lastIdfWordScalars = merged.idfWordFrequency.mapValues { it.value.sum() },
        )
    }
}

@Singleton
class BrainCrdtStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.brainCrdtDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("state")

    suspend fun load(): BrainCrdtState {
        val raw = store.data.first()[key] ?: return BrainCrdtState()
        return runCatching { json.decodeFromString(BrainCrdtState.serializer(), raw) }
            .getOrDefault(BrainCrdtState())
    }

    suspend fun save(state: BrainCrdtState) {
        store.edit { it[key] = json.encodeToString(BrainCrdtState.serializer(), state) }
    }
}
