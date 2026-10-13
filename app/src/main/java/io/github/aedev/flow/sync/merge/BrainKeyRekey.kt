package io.github.aedev.flow.sync.merge

import io.github.aedev.flow.data.recommendation.NeuroScoring
import io.github.aedev.flow.sync.canonical.CanonicalBrain
import io.github.aedev.flow.sync.canonical.CanonicalBrainVectors
import io.github.aedev.flow.sync.canonical.CanonicalVector
import io.github.aedev.flow.sync.canonical.GCounter
import io.github.aedev.flow.sync.canonical.Lww
import io.github.aedev.flow.sync.identity.Hlc

/**
 * Topic keys read again by this device's key spec (#907). A peer on an older spec, or the sidecar
 * from before it, holds other spellings of the same keys ("guitars" for "guitar"). Spellings that
 * meet merge as their field merges on the wire: vectors and affinities by max, document-frequency
 * sub-counts by sum within one device, registers by the newer stamp. Keys already read stay as they
 * are, so this runs on every snapshot.
 */
internal object BrainKeyRekey {
    fun canonical(
        brain: CanonicalBrain,
        keysFor: (String) -> List<String>,
    ): CanonicalBrain =
        brain.copy(
            vectors = vectors(brain.vectors, keysFor),
            idfWordFrequency = counters(brain.idfWordFrequency, keysFor),
            lwwMaps =
                brain.lwwMaps.copy(
                    rejectionPatterns = registers(brain.lwwMaps.rejectionPatterns, keysFor),
                    topicEvidence = registers(brain.lwwMaps.topicEvidence, keysFor),
                ),
        )

    /** The sidecar's per-device counts and vectors, read again the way [canonical] reads a snapshot. */
    fun state(
        state: BrainCrdtState,
        keysFor: (String) -> List<String>,
    ): BrainCrdtState =
        state.copy(
            idfWords = counters(state.idfWords.mapValues { GCounter(it.value) }, keysFor).mapValues { it.value.perDevice },
            lastIdfWordScalars = merged(state.lastIdfWordScalars, keysFor, Long::plus),
            deviceVectors = state.deviceVectors.mapValues { (_, device) -> device.copy(vectors = vectors(device.vectors, keysFor)) },
            syncedLww =
                state.syncedLww.copy(
                    rejectionPatterns = registers(state.syncedLww.rejectionPatterns, keysFor),
                    topicEvidence = registers(state.syncedLww.topicEvidence, keysFor),
                ),
        )

    private fun vectors(
        vectors: CanonicalBrainVectors,
        keysFor: (String) -> List<String>,
    ): CanonicalBrainVectors {
        fun CanonicalVector.read() = copy(topics = merged(topics, keysFor, ::maxOf))
        return vectors.copy(
            globalVector = vectors.globalVector.read(),
            timeVectors = vectors.timeVectors.mapValues { it.value.read() },
            shortsVector = vectors.shortsVector?.read(),
            topicAffinities = edges(vectors.topicAffinities, keysFor),
            channelTopicProfiles = vectors.channelTopicProfiles.mapValues { merged(it.value, keysFor, ::maxOf) },
        )
    }

    private fun <V : Any> merged(
        map: Map<String, V>,
        keysFor: (String) -> List<String>,
        merge: (V, V) -> V,
    ): Map<String, V> {
        val out = LinkedHashMap<String, V>(map.size)
        map.forEach { (key, value) -> keysFor(key).forEach { out.merge(it, value, merge) } }
        return out
    }

    // Within one device, two spellings were two counts of one word; across devices, G-Counters max.
    private fun counters(
        counters: Map<String, GCounter>,
        keysFor: (String) -> List<String>,
    ): Map<String, GCounter> =
        merged(counters, keysFor) { a, b ->
            GCounter((a.perDevice.keys + b.perDevice.keys).associateWith { (a.perDevice[it] ?: 0L) + (b.perDevice[it] ?: 0L) })
        }

    private fun <T> registers(
        registers: Map<String, Lww<T>>,
        keysFor: (String) -> List<String>,
    ): Map<String, Lww<T>> = merged(registers, keysFor) { a, b -> if (Hlc.compareEncoded(b.hlc, a.hlc) > 0) b else a }

    private fun edges(
        edges: Map<String, Double>,
        keysFor: (String) -> List<String>,
    ): Map<String, Double> {
        val out = LinkedHashMap<String, Double>(edges.size)
        edges.forEach { (key, weight) ->
            val ends = key.split('|')
            if (ends.size != 2) return@forEach
            val left = keysFor(ends[0]).singleOrNull() ?: return@forEach
            val right = keysFor(ends[1]).singleOrNull() ?: return@forEach
            if (left != right) out.merge(NeuroScoring.makeAffinityKey(left, right), weight, ::maxOf)
        }
        return out
    }
}
