package io.github.aedev.flow

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Formats every translated string with arguments of the types the English string declares, as
 * `getString(id, …)` does at runtime. A broken placeholder in one locale (a bare `%` in German
 * crashed Notification settings, #981) otherwise only surfaces on devices set to that language.
 */
class TranslationFormatStringsTest {
    private val resDir = File("src/main/res")

    @Test
    fun `every translation formats with the english arguments`() {
        assertThat(resDir.isDirectory).isTrue()
        val english = formatArguments(load(File(resDir, "values/strings.xml")))
        val failures =
            resDir
                .listFiles { dir -> dir.name.startsWith("values-") }
                .orEmpty()
                .mapNotNull { dir -> File(dir, "strings.xml").takeIf(File::isFile) }
                .flatMap { file ->
                    load(file).mapNotNull { (key, text) ->
                        val args = english[key] ?: english[key.substringBefore('#') + "#other"] ?: return@mapNotNull null
                        runCatching { String.format(Locale.ROOT, text, *args) }
                            .exceptionOrNull()
                            ?.let { "${file.parentFile.name} $key: \"$text\" (${it::class.simpleName})" }
                    }
                }
        assertThat(failures).isEmpty()
    }

    /** Strings and plural items that are formatted, keyed by name (`name#quantity` for plurals). */
    private fun load(file: File): Map<String, String> {
        val root =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(file)
                .documentElement
        val entries = mutableMapOf<String, String>()
        root.elements().forEach { element ->
            when (element.tagName) {
                "string" -> {
                    if (element.getAttribute("formatted") != "false") entries[element.getAttribute("name")] = element.textContent
                }

                "plurals" -> {
                    element.elements().forEach { item ->
                        entries["${element.getAttribute("name")}#${item.getAttribute("quantity")}"] = item.textContent
                    }
                }
            }
        }
        return entries
    }

    private fun Element.elements(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    /** Sample arguments matching each English string's placeholders; strings without any are left out. */
    private fun formatArguments(strings: Map<String, String>): Map<String, Array<Any>> =
        strings
            .mapValues { (_, text) ->
                var implicit = 0
                val byIndex =
                    Placeholder
                        .findAll(text)
                        .map { match ->
                            val index = match.groupValues[1].toIntOrNull() ?: ++implicit
                            index to sampleFor(match.groupValues[2].single())
                        }.toMap()
                Array(byIndex.keys.maxOrNull() ?: 0) { byIndex[it + 1] ?: "x" }
            }.filterValues { it.isNotEmpty() }

    private fun sampleFor(conversion: Char): Any =
        when (conversion) {
            'd' -> 1
            'f' -> 1.0
            else -> "x"
        }

    private companion object {
        /** The placeholders the English strings use; a literal `90% played` is not one. */
        val Placeholder = Regex("""%(?:(\d+)\$)?(?:\.\d+)?([sdf])""")
    }
}
