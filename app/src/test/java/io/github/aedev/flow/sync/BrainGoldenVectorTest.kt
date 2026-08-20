package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.canonical.BrainCounters
import io.github.aedev.flow.sync.canonical.BrainFlags
import io.github.aedev.flow.sync.canonical.BrainLwwMaps
import io.github.aedev.flow.sync.canonical.BrainPerVideo
import io.github.aedev.flow.sync.canonical.BrainSets
import io.github.aedev.flow.sync.canonical.CanonicalBrain
import io.github.aedev.flow.sync.canonical.CanonicalBrainVectors
import io.github.aedev.flow.sync.canonical.CanonicalChannelStrike
import io.github.aedev.flow.sync.canonical.CanonicalFeedEntry
import io.github.aedev.flow.sync.canonical.CanonicalRejectionSignal
import io.github.aedev.flow.sync.canonical.CanonicalTopicEvidence
import io.github.aedev.flow.sync.canonical.CanonicalVector
import io.github.aedev.flow.sync.canonical.GCounter
import io.github.aedev.flow.sync.canonical.Lww
import io.github.aedev.flow.sync.canonical.OrSet
import io.github.aedev.flow.sync.protocol.SyncSerialization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The brain half of the shared FLOW-SYNC/1 anti-drift gate.
 *
 * [android_decodes_the_desktop_brain_fixture] reads the desktop's own golden fixture
 * (`src-tauri/tests/fixtures/sync/flow_neuro_brain.json`, mirrored verbatim into this module's test
 * resources) and asserts that **every** field lands. That is the test that would have caught the
 * silent-drop bug: Android's canonical brain was a second, incompatible model, so once the
 * G-Counter parse failure was out of the way a "successful" brain sync transferred almost nothing.
 *
 * Keep the two fixture copies byte-identical. If they drift again, the next protocol break will
 * also ship silently.
 */
class BrainGoldenVectorTest {

    private fun fixture(): String =
        checkNotNull(javaClass.getResourceAsStream("/sync/flow_neuro_brain.json")) {
            "missing test resource sync/flow_neuro_brain.json"
        }.use { it.readBytes().toString(Charsets.UTF_8) }

    private val deviceA = "device-aaa"
    private val stamp = "1781000000000:0:device-aaa"
    private val newerStamp = "1781512000000:0:device-aaa"

    @Test
    fun android_decodes_the_desktop_brain_fixture() {
        val brain = SyncSerialization.decodeBrain(listOf(fixture()))
        assertNotNull("the desktop's canonical brain must decode", brain)
        requireNotNull(brain)

        assertEquals(14, brain.schema)
        assertEquals(deviceA, brain.deviceId)
        assertEquals(newerStamp, brain.hlc)

        // vectors, including the nested scalar dims Android used to inline (and therefore drop).
        assertEquals(0.8, brain.vectors.globalVector.topics["minecraft"]!!, 0.0)
        assertEquals(0.5, brain.vectors.globalVector.topics["coding"]!!, 0.0)
        assertEquals(0.4, brain.vectors.globalVector.duration, 0.0)
        assertEquals(0.5, brain.vectors.globalVector.pacing, 0.0) // absent dim falls back to default
        assertEquals(0.6, brain.vectors.timeVectors.getValue("WEEKDAY_EVENING").topics["music"]!!, 0.0)
        assertEquals(0.3, brain.vectors.shortsVector.topics["comedy"]!!, 0.0)
        assertEquals(0.8, brain.vectors.topicAffinities["minecraft"]!!, 0.0)
        assertEquals(0.7, brain.vectors.channelScores["UCabc"]!!, 0.0)
        assertEquals(0.9, brain.vectors.channelTopicProfiles.getValue("UCabc")["coding"]!!, 0.0)

        // counters: the bare {device: count} G-Counter map, nested under `counters`.
        assertEquals(420L, brain.counters.idfTotalDocuments.sum())
        assertEquals(1500L, brain.counters.totalInteractions.sum())
        assertEquals(420L, brain.counters.idfTotalDocuments.perDevice[deviceA])
        assertEquals(42L, brain.idfWordFrequency.getValue("minecraft").sum())
        assertEquals(30L, brain.idfWordFrequency.getValue("coding").sum())

        // perVideo: both maps, not just watchHistoryMap.
        assertEquals(0.8f, brain.perVideo.watchHistoryMap["dQw4w9WgXcQ"])
        assertEquals(0.8f, brain.perVideo.watchSignalProgress["dQw4w9WgXcQ"])

        // OR-Sets.
        assertEquals(setOf("politics"), brain.sets.blockedTopics.members())
        assertTrue(brain.sets.blockedChannels.members().isEmpty())
        assertEquals(setOf("coding"), brain.sets.preferredTopics.members())

        // Every LWW map, value and stamp.
        assertEquals(1781000000000L, brain.lwwMaps.suppressedVideoIds.getValue("badvid01").value)
        assertEquals(stamp, brain.lwwMaps.suppressedVideoIds.getValue("badvid01").hlc)
        assertTrue(brain.lwwMaps.suppressedChannels.isEmpty())
        assertEquals(3, brain.lwwMaps.rejectionPatterns.getValue("politics").value.count)
        val evidence = brain.lwwMaps.topicEvidence.getValue("coding").value
        assertEquals(10, evidence.positiveSignals)
        assertEquals(1, evidence.negativeSignals)
        assertEquals(8, evidence.watchSignals)
        assertEquals(2, evidence.explicitSignals)
        assertEquals(4.5, evidence.positiveScore, 0.0)
        assertEquals(setOf("dQw4w9WgXcQ"), evidence.videoIds)
        assertEquals(setOf("UCabc"), evidence.channelIds)
        assertEquals(2, brain.lwwMaps.feedHistory.getValue("dQw4w9WgXcQ").value.showCount)
        val strike = brain.lwwMaps.channelStrikes.getValue("UCbad").value
        assertEquals(2, strike.count)
        assertEquals(1781100000000L, strike.lastAt)

        assertTrue(brain.flags.hasCompletedOnboarding)
    }

    @Test
    fun re_encoding_the_fixture_loses_nothing() {
        val decoded = requireNotNull(SyncSerialization.decodeBrain(listOf(fixture())))
        val reDecoded = SyncSerialization.decodeBrain(SyncSerialization.encodeBrain(decoded).lines)
        assertEquals("a wire round-trip must be lossless", decoded, reDecoded)
    }

    @Test
    fun canonical_brain_matches_golden_bytes() {
        val brain = CanonicalBrain(
            schema = 13,
            deviceId = "dev-a",
            hlc = "100:0:aaa",
            vectors = CanonicalBrainVectors(
                globalVector = CanonicalVector(
                    topics = mapOf("kotlin" to 0.8),
                    dims = mapOf(CanonicalVector.DIM_DURATION to 0.4),
                ),
                timeVectors = mapOf("WEEKDAY_EVENING" to CanonicalVector(topics = mapOf("music" to 0.6))),
                topicAffinities = mapOf("kotlin" to 0.8),
                channelScores = mapOf("UCabc" to 0.7),
                channelTopicProfiles = mapOf("UCabc" to mapOf("coding" to 0.9)),
            ),
            counters = BrainCounters(
                idfTotalDocuments = GCounter(mapOf("dev-a" to 12L)),
                totalInteractions = GCounter(mapOf("dev-a" to 34L)),
            ),
            idfWordFrequency = mapOf("kotlin" to GCounter(mapOf("dev-a" to 5L))),
            perVideo = BrainPerVideo(
                watchHistoryMap = mapOf("v1" to 0.8f),
                watchSignalProgress = mapOf("v1" to 0.5f),
            ),
            sets = BrainSets(
                blockedTopics = OrSet(adds = mapOf("politics" to "100:0:aaa")),
                blockedChannels = OrSet(
                    adds = mapOf("UCbad" to "90:0:aaa"),
                    removes = mapOf("UCbad" to "110:0:aaa"),
                ),
                preferredTopics = OrSet(adds = mapOf("coding" to "100:0:aaa")),
            ),
            lwwMaps = BrainLwwMaps(
                suppressedVideoIds = mapOf("bad1" to Lww(1781000000000L, "100:0:aaa")),
                rejectionPatterns = mapOf(
                    "politics" to Lww(CanonicalRejectionSignal(3, 1781000000000L), "100:0:aaa"),
                ),
                topicEvidence = mapOf(
                    "coding" to Lww(
                        CanonicalTopicEvidence(
                            positiveSignals = 10,
                            watchSignals = 8,
                            explicitSignals = 2,
                            positiveScore = 4.5,
                            videoIds = setOf("v1"),
                            channelIds = setOf("UCabc"),
                            firstSeenAt = 1,
                            lastSeenAt = 2,
                        ),
                        "100:0:aaa",
                    ),
                ),
                feedHistory = mapOf("v1" to Lww(CanonicalFeedEntry(1781000000000L, 2), "100:0:aaa")),
                channelStrikes = mapOf("UCbad" to Lww(CanonicalChannelStrike(2, 1, 2), "100:0:aaa")),
            ),
            flags = BrainFlags(hasCompletedOnboarding = true),
        )

        val expected = """{"counters":{"idfTotalDocuments":{"dev-a":12},"totalInteractions":{"dev-a":34}},""" +
            """"deviceId":"dev-a","flags":{"hasCompletedOnboarding":true},"hlc":"100:0:aaa",""" +
            """"idfWordFrequency":{"kotlin":{"dev-a":5}},""" +
            """"lwwMaps":{"channelStrikes":{"UCbad":{"hlc":"100:0:aaa",""" +
            """"value":{"count":2,"firstAt":1,"lastAt":2}}},""" +
            """"feedHistory":{"v1":{"hlc":"100:0:aaa","value":{"lastShown":1781000000000,"showCount":2}}},""" +
            """"rejectionPatterns":{"politics":{"hlc":"100:0:aaa",""" +
            """"value":{"count":3,"lastRejectedAt":1781000000000}}},"suppressedChannels":{},""" +
            """"suppressedVideoIds":{"bad1":{"hlc":"100:0:aaa","value":1781000000000}},""" +
            """"topicEvidence":{"coding":{"hlc":"100:0:aaa","value":{"channelIds":["UCabc"],""" +
            """"explicitSignals":2,"firstSeenAt":1,"lastSeenAt":2,"negativeSignals":0,""" +
            """"positiveScore":4.5,"positiveSignals":10,"videoIds":["v1"],"watchSignals":8}}}},""" +
            """"perVideo":{"watchHistoryMap":{"v1":0.8},"watchSignalProgress":{"v1":0.5}},"schema":13,""" +
            """"sets":{"blockedChannels":{"adds":{"UCbad":"90:0:aaa"},"removes":{"UCbad":"110:0:aaa"}},""" +
            """"blockedTopics":{"adds":{"politics":"100:0:aaa"},"removes":{}},""" +
            """"preferredTopics":{"adds":{"coding":"100:0:aaa"},"removes":{}}},""" +
            """"vectors":{"channelScores":{"UCabc":0.7},"channelTopicProfiles":{"UCabc":{"coding":0.9}},""" +
            """"globalVector":{"dims":{"duration":0.4},"topics":{"kotlin":0.8}},""" +
            """"shortsVector":{"dims":{},"topics":{}},""" +
            """"timeVectors":{"WEEKDAY_EVENING":{"dims":{},"topics":{"music":0.6}}},""" +
            """"topicAffinities":{"kotlin":0.8}}}"""

        assertEquals(expected, SyncSerialization.encodeBrain(brain).lines.single())
    }
}
