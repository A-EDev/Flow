/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 * Test-source-set only — never shipped in the APK.
 */

package io.github.aedev.flow.data.recommendation.eval

import io.github.aedev.flow.data.recommendation.FlowPersona
import io.github.aedev.flow.data.recommendation.NeuroClusters
import io.github.aedev.flow.data.recommendation.NeuroDiscovery
import io.github.aedev.flow.data.recommendation.NeuroScoring
import io.github.aedev.flow.data.recommendation.NeuroStorage
import io.github.aedev.flow.data.recommendation.NeuroTokenizer
import io.github.aedev.flow.data.recommendation.NeuroTopicCatalog
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.data.recommendation.toUserBrain
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Offline diagnostic over a REAL exported brain (Settings → Export Data →
 * engine profile, or Flow Personality → export). Runs the production
 * clustering, scheduling, and discovery code on the user's actual data so
 * cluster shape, rotation state, and repetition defenses can be inspected
 * instead of guessed at.
 */
internal object BrainDiagnostic {
    fun parseBrain(json: String): UserBrain =
        Json { ignoreUnknownKeys = true }
            .decodeFromString<NeuroStorage.SerializableBrain>(json)
            .toUserBrain()

    // ── Machine-readable report (for visualization) ──

    @Serializable
    data class TopicDto(
        val name: String,
        val score: Double,
    )

    @Serializable
    data class ClusterDto(
        val representative: String,
        val mass: Double,
        val massShare: Double,
        val lastServedHoursAgo: Double?,
        val scheduledPosition: Int,
        val topics: List<TopicDto>,
    )

    @Serializable
    data class SessionDto(
        val queries: List<String>,
        val servedClusters: List<String>,
    )

    @Serializable
    data class GateStatsDto(
        val feedHistoryEntries: Int,
        val impressedWithin6h: Int,
        val gatedByRepeatWindow: Int,
        val showCountHistogram: Map<Int, Int>,
    )

    @Serializable
    data class ReportDto(
        val totalInteractions: Int,
        val persona: String,
        val topicCount: Int,
        val clusterCount: Int,
        val effectiveClusterCount: Double,
        val topClusterMassShare: Double,
        val clusters: List<ClusterDto>,
        val sessions: List<SessionDto>,
        val gate: GateStatsDto,
        val topTopics: List<TopicDto>,
        val preferredTopics: List<String>,
        val staleQueriesActive: Int,
        val recentRelatedSeeds: Int,
        val suppressedVideos: Int,
        val suppressedChannels: Int,
        val blockedChannels: Int,
        val blockedTopics: List<String>,
        val watchHistoryEntries: Int,
        val tagAffinityEdges: Int,
        val topicAffinityEdges: Int,
    )

    fun diagnose(
        brain: UserBrain,
        now: Long = System.currentTimeMillis(),
        sessions: Int = 3,
    ): ReportDto {
        val tokenizer = NeuroTokenizer()
        val discovery = NeuroDiscovery(NeuroTopicCatalog.TOPIC_CATEGORIES, tokenizer)
        val persona =
            FlowPersona.entries.find { it.name == brain.lastPersona } ?: FlowPersona.EXPLORER

        // ── Clusters, exactly as production builds them ──
        val clusters =
            NeuroClusters.buildClusters(
                topicScores = brain.globalVector.topics,
                affinities = brain.topicAffinities,
                channelTopicProfiles = brain.channelTopicProfiles,
                categories = NeuroTopicCatalog.TOPIC_CATEGORIES,
                normalizeLemma = tokenizer::normalizeLemma,
                tagAffinities = brain.tagAffinities,
            )
        val scheduled = NeuroClusters.schedule(clusters, brain.clusterRotation, now)
        val totalMass = clusters.sumOf { it.mass }.coerceAtLeast(1e-9)
        val herfindahl = clusters.sumOf { (it.mass / totalMass) * (it.mass / totalMass) }

        val scoreOf = { topic: String -> brain.globalVector.topics[topic] ?: 0.0 }
        val baseScores = HashMap<String, Double>()
        brain.globalVector.topics.forEach { (k, v) ->
            baseScores.merge(NeuroScoring.stripDomainTag(k), v, ::maxOf)
        }

        val clusterDtos =
            scheduled.mapIndexed { index, cluster ->
                val lastServed = brain.clusterRotation[cluster.representative]
                ClusterDto(
                    representative = cluster.representative,
                    mass = cluster.mass,
                    massShare = cluster.mass / totalMass,
                    lastServedHoursAgo = lastServed?.let { (now - it) / 3_600_000.0 },
                    scheduledPosition = index + 1,
                    topics =
                        cluster.topics.map { TopicDto(it, baseScores[it] ?: 0.0) },
                )
            }

        // ── Discovery simulation: what the NEXT refreshes would actually query ──
        var rotation = brain.clusterRotation
        var clock = now
        val sessionDtos = mutableListOf<SessionDto>()
        repeat(sessions) {
            val queries =
                discovery.generateQueries(brain.copy(clusterRotation = rotation), clock) { persona }
            val served = queries.mapNotNull { it.clusterKey }.distinct()
            sessionDtos +=
                SessionDto(
                    queries = queries.map { "[${it.strategy}] ${it.query}" },
                    servedClusters = served,
                )
            if (served.isNotEmpty()) rotation = rotation + served.associateWith { clock }
            clock += 4L * 3_600_000L
        }

        // ── Seen-gate state over the real feed history ──
        val histogram =
            brain.feedHistory.values
                .groupingBy { it.showCount.coerceAtMost(9) }
                .eachCount()
        val within6h =
            brain.feedHistory.values.count { (now - it.lastShown) / 3_600_000.0 < NeuroScoring.SEEN_GATE_SINGLE_SHOW_WINDOW_HOURS }
        val gatedRepeats =
            brain.feedHistory.values.count {
                it.showCount >= NeuroScoring.SEEN_GATE_SHOW_COUNT &&
                    (now - it.lastShown) / 3_600_000.0 < NeuroScoring.SEEN_GATE_WINDOW_HOURS
            }

        val staleCutoff = now - NeuroScoring.STALE_QUERY_EXPIRY_HOURS * 3_600_000L

        return ReportDto(
            totalInteractions = brain.totalInteractions,
            persona = persona.name,
            topicCount = brain.globalVector.topics.size,
            clusterCount = clusters.size,
            effectiveClusterCount = if (herfindahl > 0) 1.0 / herfindahl else 0.0,
            topClusterMassShare = clusters.maxOfOrNull { it.mass / totalMass } ?: 0.0,
            clusters = clusterDtos,
            sessions = sessionDtos,
            gate =
                GateStatsDto(
                    feedHistoryEntries = brain.feedHistory.size,
                    impressedWithin6h = within6h,
                    gatedByRepeatWindow = gatedRepeats,
                    showCountHistogram = histogram,
                ),
            topTopics =
                brain.globalVector.topics.entries
                    .sortedByDescending { it.value }
                    .take(40)
                    .map { TopicDto(it.key, it.value) },
            preferredTopics = brain.preferredTopics.sorted(),
            staleQueriesActive = brain.staleQueries.count { it.value > staleCutoff },
            recentRelatedSeeds = brain.recentRelatedSeeds.size,
            suppressedVideos = brain.suppressedVideoIds.size,
            suppressedChannels = brain.suppressedChannels.size,
            blockedChannels = brain.blockedChannels.size,
            blockedTopics = brain.blockedTopics.sorted(),
            watchHistoryEntries = brain.watchHistoryMap.size,
            tagAffinityEdges = brain.tagAffinities.size,
            topicAffinityEdges = brain.topicAffinities.size,
        )
    }

    fun toJson(report: ReportDto): String = Json { prettyPrint = true }.encodeToString(report)

    fun renderText(r: ReportDto): String =
        buildString {
            appendLine("═══ Brain Diagnostic ═══")
            appendLine("interactions=${r.totalInteractions}  persona=${r.persona}  topics=${r.topicCount}")
            appendLine(
                "clusters=${r.clusterCount}  effectiveClusters=%.1f  topClusterShare=%.0f%%"
                    .format(r.effectiveClusterCount, r.topClusterMassShare * 100),
            )
            appendLine()
            appendLine("CLUSTERS (scheduled order for the NEXT refresh):")
            r.clusters.forEach { c ->
                val served =
                    c.lastServedHoursAgo?.let { "served %.1fh ago".format(it) } ?: "never served"
                appendLine(
                    "  #%d %-18s mass=%.2f (%2.0f%%) [%s]"
                        .format(c.scheduledPosition, c.representative, c.mass, c.massShare * 100, served),
                )
                val members =
                    c.topics.take(12).joinToString(", ") { "%s(%.2f)".format(it.name, it.score) }
                appendLine("      $members${if (c.topics.size > 12) " … +${c.topics.size - 12}" else ""}")
            }
            appendLine()
            appendLine("DISCOVERY SIMULATION (rotation advancing):")
            r.sessions.forEachIndexed { i, s ->
                appendLine("  session ${i + 1}: clusters=${s.servedClusters}")
                s.queries.forEach { appendLine("    $it") }
            }
            appendLine()
            appendLine("SEEN-GATE STATE:")
            appendLine("  feedHistory entries        = ${r.gate.feedHistoryEntries}")
            appendLine("  hidden by 6h single-show   = ${r.gate.impressedWithin6h}")
            appendLine("  hidden by 60h repeat gate  = ${r.gate.gatedByRepeatWindow}")
            appendLine("  showCount histogram        = ${r.gate.showCountHistogram.toSortedMap()}")
            appendLine()
            appendLine("STATE: staleQueries=${r.staleQueriesActive} relatedSeedCooldowns=${r.recentRelatedSeeds}")
            appendLine(
                "       suppressedVideos=${r.suppressedVideos} suppressedChannels=${r.suppressedChannels} " +
                    "blockedChannels=${r.blockedChannels} watchHistory=${r.watchHistoryEntries}",
            )
            appendLine("       affinityEdges=${r.topicAffinityEdges} tagAffinityEdges=${r.tagAffinityEdges}")
            appendLine("       blockedTopics=${r.blockedTopics}")
            appendLine("       preferredTopics=${r.preferredTopics}")
            appendLine()
            appendLine("TOP 40 TOPICS:")
            r.topTopics.forEach { appendLine("  %-28s %.3f".format(it.name, it.score)) }
        }
}
