package io.github.aedev.flow.sync.apply

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.data.recommendation.music.MusicBrainStorage
import io.github.aedev.flow.sync.canonical.BrainCounters
import io.github.aedev.flow.sync.canonical.CanonicalBrain
import io.github.aedev.flow.sync.canonical.CanonicalMusicBrain
import io.github.aedev.flow.sync.canonical.GCounter
import io.github.aedev.flow.sync.identity.Hlc
import io.github.aedev.flow.sync.mapping.BrainMapper
import io.github.aedev.flow.sync.mapping.MusicBrainMapper
import io.github.aedev.flow.sync.merge.BrainCrdtState
import io.github.aedev.flow.sync.merge.BrainCrdtStore
import io.github.aedev.flow.sync.merge.BrainMerger
import io.github.aedev.flow.sync.merge.BrainVectorBlend
import io.github.aedev.flow.sync.merge.CounterOwnership
import io.github.aedev.flow.sync.merge.DeviceVectors
import io.github.aedev.flow.sync.merge.MusicBrainCrdtState
import io.github.aedev.flow.sync.merge.MusicBrainCrdtStore
import io.github.aedev.flow.sync.merge.MusicBrainMerger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two stateful brain collections. Both keep a CRDT sidecar next to the engine's own file, and
 * both credit local counter growth to this device's HLC node id ([myNode]) on send and receive
 * alike: earlier builds credited the full device id on receive, so that entry stays frozen and is
 * still treated as this device's own.
 */
@Singleton
class BrainSyncAccess
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val brainCrdtStore: BrainCrdtStore,
        private val musicBrainCrdtStore: MusicBrainCrdtStore,
        private val musicBrainEngine: MusicBrainEngine,
    ) {
        private val neuroEngine: FlowNeuroEngine by lazy { FlowNeuroEngine.getInstance(context) }

        // --- FlowNeuro ---

        /** This device's snapshot, carrying the shared CRDT state, then one vector snapshot per peer. */
        suspend fun exportBrain(
            myNode: String,
            hlc: String,
        ): List<CanonicalBrain> {
            val local = parseLocalBrain(exportLocalBrainJson())
            val attributed = attributeLocalEdits(brainCrdtStore.load(), myNode, local, hlc)
            val own = BrainMapper.toCanonical(local, myNode, hlc, attributed)
            val devices = withOwnVectors(attributed.deviceVectors, own)
            brainCrdtStore.save(attributed.copy(deviceVectors = devices))
            val peers =
                devices
                    .filterKeys { it != myNode }
                    .map { (deviceId, dv) ->
                        CanonicalBrain(
                            schema = own.schema,
                            deviceId = deviceId,
                            hlc = dv.hlc,
                            vectors = dv.vectors,
                            counters = BrainCounters(idfTotalDocuments = GCounter(mapOf(deviceId to dv.weight.coerceAtLeast(1)))),
                        )
                    }
            return (peers + own).sortedBy { it.deviceId }
        }

        /** CRDT-merge every snapshot the peer sent into the local brain, then persist + reload the engine. */
        suspend fun mergeBrain(
            remotes: List<CanonicalBrain>,
            senderId: String,
            myNode: String,
            myDeviceId: String,
            hlc: String,
        ) {
            if (remotes.isEmpty()) return
            val localJson = exportLocalBrainJson()
            val local = parseLocalBrain(localJson)
            val attributed = attributeLocalEdits(brainCrdtStore.load(), myNode, local, hlc)
            val own = BrainMapper.toCanonical(local, myNode, hlc, attributed)
            val incoming = remotes.map(BrainMapper::normalizeIncoming)

            val counters =
                CounterOwnership.brain(
                    merged = incoming.fold(own, BrainMerger::merge),
                    local = own,
                    remotes = incoming,
                    own = setOf(myNode, myDeviceId),
                    sender = setOf(senderId, Hlc.nodeFromDeviceId(senderId)),
                )
            var devices = withOwnVectors(attributed.deviceVectors, own)
            for (snapshot in incoming) {
                devices =
                    BrainVectorBlend.upsert(
                        devices,
                        snapshot.deviceId,
                        DeviceVectors(snapshot.hlc, BrainVectorBlend.weightOf(snapshot), snapshot.vectors),
                    )
            }
            val merged = counters.copy(vectors = BrainVectorBlend.effective(devices))
            val mergedBrain = BrainMapper.writeBack(merged, local)
            neuroEngine.importBrainFromStream(ByteArrayInputStream(BrainMapper.serializeOver(localJson, mergedBrain)))
            brainCrdtStore.save(BrainCrdtState.afterMerge(attributed, merged, devices))
        }

        private fun withOwnVectors(
            devices: Map<String, DeviceVectors>,
            own: CanonicalBrain,
        ): Map<String, DeviceVectors> = devices + (own.deviceId to DeviceVectors(own.hlc, BrainVectorBlend.weightOf(own), own.vectors))

        private suspend fun exportLocalBrainJson(): ByteArray {
            var exported = false
            val bytes =
                ByteArrayOutputStream().use { bos ->
                    exported = neuroEngine.exportBrainToStream(bos)
                    bos.toByteArray()
                }
            if (!exported) throw IllegalStateException("could not read the local FlowNeuro brain")
            return bytes
        }

        private fun parseLocalBrain(bytes: ByteArray): BrainMapper.SBrain =
            runCatching { BrainMapper.parse(bytes) }
                .getOrElse { throw IllegalStateException("the local FlowNeuro brain could not be parsed", it) }

        /**
         * Fold everything that changed locally since the last sync into the sidecar: counter growth
         * becomes this device's G-Counter sub-count, and blocklist/preference edits become OR-Set add
         * or remove stamps. Both are diffs against the last-synced state, so re-running with no local
         * activity is a no-op.
         */
        private fun attributeLocalEdits(
            state: BrainCrdtState,
            myNode: String,
            brain: BrainMapper.SBrain,
            hlc: String,
        ): BrainCrdtState {
            val withCounters =
                BrainCrdtState.attributeLocal(
                    state = state,
                    myDevice = myNode,
                    idfDocsScalar = brain.idfTotalDocuments.toLong(),
                    interactionsScalar = brain.interactions.toLong(),
                    idfWordCounts = brain.idfWordFrequency.mapValues { it.value.toLong() },
                )
            return BrainCrdtState.attributeSets(
                state = withCounters,
                blockedTopics = brain.blockedTopics,
                blockedChannels = brain.blockedChannels,
                preferredTopics = brain.preferredTopics,
                hlc = hlc,
            )
        }

        // --- music brain (the music twin of the neuro path; no learned vectors to blend) ---

        suspend fun exportMusicBrain(
            myNode: String,
            hlc: String,
        ): CanonicalMusicBrain {
            val local = exportLocalMusicBrain()
            val sidecar = attributeLocalMusic(musicBrainCrdtStore.load(), myNode, local, hlc)
            musicBrainCrdtStore.save(sidecar)
            return MusicBrainMapper.toCanonical(local, myNode, hlc, sidecar)
        }

        suspend fun mergeMusicBrain(
            remote: CanonicalMusicBrain,
            senderId: String,
            myNode: String,
            myDeviceId: String,
            hlc: String,
        ) {
            val local = exportLocalMusicBrain()
            val sidecar = attributeLocalMusic(musicBrainCrdtStore.load(), myNode, local, hlc)
            val own = MusicBrainMapper.toCanonical(local, myNode, hlc, sidecar)
            val merged =
                CounterOwnership.music(
                    merged = MusicBrainMerger.merge(own, remote),
                    local = own,
                    remote = remote,
                    own = setOf(myNode, myDeviceId),
                    sender = setOf(senderId, Hlc.nodeFromDeviceId(senderId)),
                )
            val mergedBrain = MusicBrainMapper.writeBack(merged, local)
            musicBrainEngine.importBrainFromStream(ByteArrayInputStream(MusicBrainMapper.serialize(mergedBrain)))
            musicBrainCrdtStore.save(MusicBrainCrdtState.afterMerge(merged))
        }

        private suspend fun exportLocalMusicBrain(): MusicBrainStorage.SerializableMusicBrain {
            val bytes =
                ByteArrayOutputStream().use { bos ->
                    musicBrainEngine.exportBrainToStream(bos)
                    bos.toByteArray()
                }
            return runCatching { MusicBrainMapper.parse(bytes) }
                .getOrElse { throw IllegalStateException("the local music brain could not be parsed", it) }
        }

        private fun attributeLocalMusic(
            state: MusicBrainCrdtState,
            myNode: String,
            brain: MusicBrainStorage.SerializableMusicBrain,
            hlc: String,
        ): MusicBrainCrdtState =
            MusicBrainCrdtState.attributeLocal(
                state = state,
                myDevice = myNode,
                totalPlaysScalar = brain.totalPlays.toLong(),
                artistPlayScalars = brain.artistAffinity.mapValues { it.value.plays.toLong() },
                artistScores = brain.artistAffinity.mapValues { it.value.score },
                seenArtists = brain.seenArtists.toSet(),
                blockedArtists = brain.blockedArtists.toSet(),
                dislikedArtists = brain.dislikedArtists,
                appetite = brain.discoveryAppetite,
                hlc = hlc,
            )
    }
