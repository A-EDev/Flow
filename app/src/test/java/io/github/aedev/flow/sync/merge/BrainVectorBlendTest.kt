package io.github.aedev.flow.sync.merge

import io.github.aedev.flow.sync.canonical.BrainCounters
import io.github.aedev.flow.sync.canonical.CanonicalBrain
import io.github.aedev.flow.sync.canonical.CanonicalBrainVectors
import io.github.aedev.flow.sync.canonical.CanonicalVector
import io.github.aedev.flow.sync.canonical.GCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The vector blend must give the numbers the desktop's `effective_vectors` gives. The scenario is
 * the desktop's own `fnb` fixture from `tests/sync_merge.rs` (d1 at seed 1 then 3, d2 at seed 2);
 * the expected values are its weighted mean evaluated in the desktop's operation order.
 */
class BrainVectorBlendTest {
    private fun snapshot(
        device: String,
        seed: Long,
        extraGlobal: Map<String, Double> = emptyMap(),
        shorts: CanonicalVector? = null,
    ) = CanonicalBrain(
        deviceId = device,
        hlc = "${seed * 100}:0:$device",
        vectors =
            CanonicalBrainVectors(
                globalVector = CanonicalVector(topics = mapOf("coding" to seed * 0.1) + extraGlobal),
                topicAffinities = mapOf("coding" to seed * 0.2),
                shortsVector = shorts,
            ),
        counters = BrainCounters(idfTotalDocuments = GCounter(mapOf(device to seed * 10))),
    )

    private fun fold(snapshots: List<CanonicalBrain>): Map<String, DeviceVectors> =
        snapshots.fold(emptyMap()) { devices, s ->
            BrainVectorBlend.upsert(devices, s.deviceId, DeviceVectors(s.hlc, BrainVectorBlend.weightOf(s), s.vectors))
        }

    @Test
    fun blend_matches_the_desktop_scenario() {
        val s0 = snapshot("d1", 1)
        val s1 = snapshot("d2", 2, extraGlobal = mapOf("music" to 0.7))
        val s2 = snapshot("d1", 3)

        val devices = fold(listOf(s0, s1, s2))
        assertEquals("the newer d1 snapshot wins its slot", 30L, devices.getValue("d1").weight)

        val effective = BrainVectorBlend.effective(devices)
        assertEquals(0.26, effective.globalVector.topics.getValue("coding"), 0.0)
        assertEquals("a key one device lacks counts as 0 for it", 0.28, effective.globalVector.topics.getValue("music"), 0.0)
        assertEquals(0.52, effective.topicAffinities.getValue("coding"), 0.0)
    }

    @Test
    fun blend_ignores_snapshot_order_and_duplicates() {
        val s0 = snapshot("d1", 1)
        val s1 = snapshot("d2", 2)
        val s2 = snapshot("d1", 3)

        val forward = BrainVectorBlend.effective(fold(listOf(s0, s1, s2)))
        assertEquals(forward, BrainVectorBlend.effective(fold(listOf(s2, s1, s0))))
        assertEquals(forward, BrainVectorBlend.effective(fold(listOf(s1, s0, s2, s0, s1))))
    }

    @Test
    fun a_device_without_a_shorts_vector_is_left_out_of_that_mean() {
        val desktop = snapshot("desk", 2)
        val phone = snapshot("phone", 3, shorts = CanonicalVector(topics = mapOf("comedy" to 0.5)))

        val shorts = BrainVectorBlend.effective(fold(listOf(desktop, phone))).shortsVector
        assertEquals("not averaged with the desktop's absent vector", 0.5, shorts!!.topics.getValue("comedy"), 0.0)
        assertNull(BrainVectorBlend.effective(fold(listOf(desktop))).shortsVector)
    }

    @Test
    fun weight_is_the_senders_own_count_else_the_whole_counter() {
        val own = CanonicalBrain(deviceId = "a", counters = BrainCounters(idfTotalDocuments = GCounter(mapOf("a" to 7L, "b" to 100L))))
        val relayed = CanonicalBrain(deviceId = "c", counters = BrainCounters(idfTotalDocuments = GCounter(mapOf("a" to 7L, "b" to 3L))))
        assertEquals(7L, BrainVectorBlend.weightOf(own))
        assertEquals(10L, BrainVectorBlend.weightOf(relayed))
    }

    @Test
    fun a_weightless_device_still_counts_once() {
        val devices =
            mapOf(
                "a" to DeviceVectors("1:0:a", 0, CanonicalBrainVectors(topicAffinities = mapOf("x" to 1.0))),
                "b" to DeviceVectors("1:0:b", 0, CanonicalBrainVectors(topicAffinities = mapOf("x" to 0.0))),
            )
        assertEquals(0.5, BrainVectorBlend.effective(devices).topicAffinities.getValue("x"), 0.0)
    }
}
