/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation.eval

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.NeuroMaintenance
import io.github.aedev.flow.data.recommendation.NeuroScoring
import io.github.aedev.flow.data.recommendation.NeuroTokenizer
import io.github.aedev.flow.data.recommendation.UserBrain
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs [BrainDiagnostic] against a real exported brain when one is present.
 *
 * Usage: export the engine profile on the device (Settings → Export Data →
 * engine profile, or Flow Personality → export), copy the JSON to
 * `diagnostics/brain.json` at the repo root (or pass -Dflow.brainPath=...),
 * then run this test. Reports land in app/build/reports/neuro-benchmark/
 * as brain-diagnostic.txt and brain-diagnostic.json.
 *
 * Skips silently when no brain file is present, so CI is unaffected.
 */
class BrainDiagnosticTest {
    private fun locateBrainFile(): File? {
        System.getProperty("flow.brainPath")?.let { override ->
            val f = File(override)
            if (f.isFile) return f
        }
        return listOf(
            File("../diagnostics/brain.json"),
            File("diagnostics/brain.json"),
        ).firstOrNull { it.isFile }
    }

    @Test
    fun `diagnose exported brain`() {
        val brainFile = locateBrainFile()
        assumeTrue("No diagnostics/brain.json present — skipping", brainFile != null)

        val brain = BrainDiagnostic.parseBrain(brainFile!!.readText())
        val report = BrainDiagnostic.diagnose(brain)
        val text = BrainDiagnostic.renderText(report)

        println(text)
        val out = File("build/reports/neuro-benchmark").apply { mkdirs() }
        File(out, "brain-diagnostic.txt").writeText(text)
        File(out, "brain-diagnostic.json").writeText(BrainDiagnostic.toJson(report))

        assertThat(report.topicCount).isGreaterThan(0)
    }

    @Test
    fun `the exported brain survives every migration`() {
        val brainFile = locateBrainFile()
        assumeTrue("No diagnostics/brain.json present — skipping", brainFile != null)
        val tokenizer = NeuroTokenizer()
        val before = BrainDiagnostic.parseBrain(brainFile!!.readText())
        val after = NeuroMaintenance.runIfNeeded(before, tokenizer)

        fun pairs(brain: UserBrain) =
            brain.globalVector.topics.keys
                .count { ' ' in NeuroScoring.stripDomainTag(it) }

        fun signals(brain: UserBrain) = brain.topicEvidence.values.sumOf { it.positiveSignals }
        val report =
            "schema ${before.schemaVersion} -> ${after.schemaVersion}\n" +
                "topics ${before.globalVector.topics.size} -> ${after.globalVector.topics.size}, pairs ${pairs(
                    before,
                )} -> ${pairs(after)}\n" +
                "evidence ${before.topicEvidence.size} -> ${after.topicEvidence.size}, signals ${signals(before)} -> ${signals(after)}\n" +
                "affinities ${before.topicAffinities.size} -> ${after.topicAffinities.size}\n" +
                "top: ${after.globalVector.topics.entries.sortedByDescending { it.value }.take(
                    20,
                ).joinToString { "${it.key} %.3f".format(it.value) }}\n"
        println(report)
        File("build/reports/neuro-benchmark").apply { mkdirs() }.resolve("brain-migration.txt").writeText(report)

        assertThat(after.schemaVersion).isEqualTo(NeuroMaintenance.TARGET_SCHEMA_VERSION)
        assertThat(after.globalVector.topics).isNotEmpty()
        assertThat(NeuroMaintenance.runIfNeeded(after, tokenizer)).isSameInstanceAs(after)
    }

    @Test
    fun `diagnostic runs on a synthetic brain`() {
        // Keeps the diagnostic itself regression-tested even when no export exists.
        val universe = NeuroBenchmark.multiInterestUniverse()
        val report = BrainDiagnostic.diagnose(NeuroBenchmark.brainFor(universe), now = NeuroEval.FIXED_NOW)
        assertThat(report.clusterCount).isEqualTo(universe.groups.size)
        assertThat(report.sessions).isNotEmpty()
        assertThat(report.clusters.first().scheduledPosition).isEqualTo(1)
    }
}
