package io.github.aedev.flow.data.sponsordetection

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

class SponsorTokenizerTest {
    @Test
    fun `Kotlin tokenizer matches every exported Hugging Face golden`() {
        val directory = modelAssetsDirectory()
        val tokenizer = SponsorTokenizer.fromJson(directory.resolve("tokenizer.json").readText())
        val cases =
            Json
                .parseToJsonElement(directory.resolve("tokenizer_goldens.json").readText())
                .jsonObject
                .getValue("cases")
                .jsonArray

        cases.forEach { element ->
            val golden = element.jsonObject
            val normalized = normalizeSponsorCue(golden.getValue("input").jsonPrimitive.content)
            val encoded = tokenizer.encode(normalized)
            val expectedIds = golden.getValue("input_ids").jsonArray.map { it.jsonPrimitive.content.toLong() }
            val expectedOffsets =
                golden.getValue("offset_mapping").jsonArray.map { offset ->
                    val values = offset.jsonArray
                    values[0].jsonPrimitive.content.toInt() to values[1].jsonPrimitive.content.toInt()
                }

            assertThat(normalized).isEqualTo(golden.getValue("normalized").jsonPrimitive.content)
            assertThat(encoded.map { it.id }).containsExactlyElementsIn(expectedIds).inOrder()
            assertThat(encoded.map { it.startCodePoint to it.endCodePoint }).containsExactlyElementsIn(expectedOffsets).inOrder()
            assertThat(encoded.first().id).isEqualTo(50_281)
            assertThat(encoded.last().id).isEqualTo(50_282)
            assertThat(encoded.drop(1).dropLast(1).map { it.id }).isEqualTo(tokenizer.encodeContent(normalized).map { it.id }.toList())
        }
    }

    @Test
    fun `long transcript offsets stay monotonic without recounting prefixes`() {
        val tokenizer = SponsorTokenizer.fromJson(modelAssetsDirectory().resolve("tokenizer.json").readText())
        val text = (0 until 4_000).joinToString(" ") { "podcast$it" }
        val encoded = tokenizer.encode(text)
        val offsets = encoded.filter { it.endCodePoint > it.startCodePoint }

        assertThat(offsets.size).isGreaterThan(4_000)
        offsets.zipWithNext().forEach { (previous, next) ->
            assertThat(next.startCodePoint).isAtLeast(previous.endCodePoint)
        }
        assertThat(offsets.last().endCodePoint).isEqualTo(text.codePointCount(0, text.length))
    }

    private fun modelAssetsDirectory(): File {
        val relative = "ml/sponsor_detection/artifacts/android/ettin_17m_sponsor_v1/android"
        return generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .map { it.resolve(relative) }
            .firstOrNull { it.resolve("tokenizer.json").isFile }
            ?: error("Could not locate exported sponsor model assets")
    }
}
