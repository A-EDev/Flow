/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 * Test-source-set only — never shipped in the APK.
 */

package io.github.aedev.flow.data.recommendation.eval

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * Real video titles, read from the InnerTube responses already committed under src/test/resources,
 * so text metrics also run on titles nobody wrote for a benchmark.
 */
internal object RealTitleCorpus {
    private const val MIN_LENGTH = 8
    private const val MAX_LENGTH = 140

    // Local captures that git ignores, so every checkout and CI read the same titles.
    private val ignoredFolders = setOf("explore", "shorts")

    val titles: List<String> by lazy { load(File("src/test/resources")) }

    fun load(root: File): List<String> {
        val byVideo = LinkedHashMap<String, String>()
        root
            .walkTopDown()
            .onEnter { it == root || it.name !in ignoredFolders || it.parentFile != root }
            .filter { it.isFile && it.extension == "json" }
            .sortedBy { it.path }
            .forEach { file ->
                runCatching { Json.parseToJsonElement(file.readText()) }.getOrNull()?.let { collect(it, byVideo) }
            }
        return byVideo.values.toList()
    }

    private fun collect(
        element: JsonElement,
        byVideo: MutableMap<String, String>,
    ) {
        when (element) {
            is JsonArray -> {
                element.forEach { collect(it, byVideo) }
            }

            is JsonObject -> {
                val videoId = element.string("videoId") ?: element.string("contentId")?.takeIf { element.isVideoLockup() }
                if (videoId != null && videoId !in byVideo) {
                    titleOf(element)?.takeIf { it.length in MIN_LENGTH..MAX_LENGTH }?.let { byVideo[videoId] = it.replace('\n', ' ') }
                }
                element.values.forEach { collect(it, byVideo) }
            }

            else -> {}
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.isVideoLockup(): Boolean = string("contentType")?.endsWith("VIDEO") == true

    private fun titleOf(item: JsonObject): String? =
        text(item["title"])
            ?: text(item["headline"])
            ?: text(((item["metadata"] as? JsonObject)?.get("lockupMetadataViewModel") as? JsonObject)?.get("title"))

    private fun text(element: JsonElement?): String? =
        when (element) {
            is JsonPrimitive -> {
                element.takeIf { it.isString }?.content
            }

            is JsonObject -> {
                element.string("simpleText")
                    ?: (element["runs"] as? JsonArray)?.joinToString("") { (it as? JsonObject)?.string("text").orEmpty() }
                    ?: element.string("content")
            }

            else -> {
                null
            }
        }
}
