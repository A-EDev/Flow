package io.github.aedev.flow.sync.merge

import io.github.aedev.flow.sync.canonical.CanonicalBrain
import io.github.aedev.flow.sync.canonical.CanonicalBrainVectors
import io.github.aedev.flow.sync.canonical.CanonicalVector
import io.github.aedev.flow.sync.identity.Hlc
import kotlinx.serialization.Serializable
import java.util.TreeMap
import java.util.TreeSet

/** One device's learned vectors as it last sent them, weighted by its experience (idf documents). */
@Serializable
data class DeviceVectors(
    val hlc: String = "",
    val weight: Long = 0,
    val vectors: CanonicalBrainVectors = CanonicalBrainVectors(),
)

/**
 * The effective learned vectors: an experience-weighted mean over every known device's latest
 * snapshot. This is the desktop's `MergedFlowNeuroBrain::effective_vectors` rule for rule, down to
 * summing in device-id order, so both platforms derive the same numbers from the same snapshots.
 *
 * A pairwise merge cannot do this: blending a blend with a third device is not the blend of all
 * three, so the per-device snapshots are kept and the mean is recomputed from scratch each time.
 */
object BrainVectorBlend {
    /** The desktop's weight for a snapshot: the sender's own idf count, else the whole counter. */
    fun weightOf(snapshot: CanonicalBrain): Long {
        val own = snapshot.counters.idfTotalDocuments.perDevice[snapshot.deviceId] ?: 0L
        return if (own > 0) own else snapshot.counters.idfTotalDocuments.sum()
    }

    /** Keep [incoming] for [deviceId] unless an equal or newer snapshot from that device is held. */
    fun upsert(
        devices: Map<String, DeviceVectors>,
        deviceId: String,
        incoming: DeviceVectors,
    ): Map<String, DeviceVectors> {
        val existing = devices[deviceId]
        return if (existing != null && Hlc.compareEncoded(existing.hlc, incoming.hlc) >= 0) {
            devices
        } else {
            devices + (deviceId to incoming)
        }
    }

    fun effective(devices: Map<String, DeviceVectors>): CanonicalBrainVectors {
        val ordered = devices.toSortedMap().values.toList()
        val timeBuckets = ordered.flatMapTo(TreeSet()) { it.vectors.timeVectors.keys }
        val channels = ordered.flatMapTo(TreeSet()) { it.vectors.channelTopicProfiles.keys }
        val shorts = ordered.mapNotNull { d -> d.vectors.shortsVector?.let { d.weight to it } }
        return CanonicalBrainVectors(
            globalVector = blendVectors(ordered.map { it.weight to it.vectors.globalVector }),
            timeVectors =
                timeBuckets.associateWith { bucket ->
                    blendVectors(ordered.mapNotNull { d -> d.vectors.timeVectors[bucket]?.let { d.weight to it } })
                },
            shortsVector = if (shorts.isEmpty()) null else blendVectors(shorts),
            topicAffinities = blendMaps(ordered.map { it.weight to it.vectors.topicAffinities }),
            channelScores = blendMaps(ordered.map { it.weight to it.vectors.channelScores }),
            channelTopicProfiles =
                channels.associateWith { channel ->
                    blendMaps(ordered.mapNotNull { d -> d.vectors.channelTopicProfiles[channel]?.let { d.weight to it } })
                },
        )
    }

    /** Weighted mean; a key a device lacks counts as 0 for it, and every weight is floored at 1. */
    private fun blendMaps(contributions: List<Pair<Long, Map<String, Double>>>): Map<String, Double> {
        val numerator = TreeMap<String, Double>()
        var denominator = 0.0
        for ((weight, map) in contributions) {
            val w = weight.coerceAtLeast(1).toDouble()
            denominator += w
            for ((key, value) in map) numerator[key] = (numerator[key] ?: 0.0) + value * w
        }
        if (denominator == 0.0) denominator = 1.0
        return numerator.mapValuesTo(LinkedHashMap()) { it.value / denominator }
    }

    private fun blendVectors(contributions: List<Pair<Long, CanonicalVector>>): CanonicalVector =
        CanonicalVector(
            topics = blendMaps(contributions.map { it.first to it.second.topics }),
            dims = blendMaps(contributions.map { it.first to it.second.dims }),
        )
}
