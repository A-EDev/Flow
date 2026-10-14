package io.github.aedev.flow.ui.screens.settings.about

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

private val VersionParts = Regex("""\d+""")
private val HeaderLine = Regex("""^(VERSION|DATE|STATUS):\s*(.*)$""")
private val BulletPrefixes = listOf("- ", "* ", "• ")

/** One block of a release: a heading such as "New features" and the changes under it. */
@Immutable
data class ChangelogSection(
    val title: String,
    val items: List<String>,
)

/** A bundled release note, read from `assets/changelog/v<version>.txt`. */
@Immutable
data class ChangelogRelease(
    val version: String,
    val date: LocalDate?,
    val preRelease: Boolean,
    val sections: List<ChangelogSection>,
)

/**
 * The changelog asset [files], newest first, compared as versions: a plain string sort would put
 * 2.10 before 2.9. Anything that is not a `.txt` file is skipped.
 */
internal fun sortedChangelogs(files: List<String>): List<String> =
    files
        .filter { it.endsWith(".txt") }
        .sortedWith { a, b -> compareVersions(versionOf(b), versionOf(a)) }

/** The version a changelog file or tag names, as numbers: "v2.2.5.txt" is [2, 2, 5]. */
internal fun versionOf(name: String): List<Int> = VersionParts.findAll(name.removeSuffix(".txt")).map { it.value.toInt() }.toList()

internal fun compareVersions(
    a: List<Int>,
    b: List<Int>,
): Int {
    for (index in 0 until maxOf(a.size, b.size)) {
        val difference = a.getOrElse(index) { 0 } - b.getOrElse(index) { 0 }
        if (difference != 0) return difference
    }
    return 0
}

/**
 * Reads one release note. Header lines (`VERSION:`, `DATE:`, `STATUS:`) describe the release,
 * bulleted lines are changes, and any other line opens a new section. [fallbackVersion] is used
 * when the file names no version.
 */
internal fun parseChangelog(
    text: String,
    fallbackVersion: String,
): ChangelogRelease {
    var version = fallbackVersion
    var date: LocalDate? = null
    var preRelease = false
    val sections = mutableListOf<ChangelogSection>()
    var title: String? = null
    val items = mutableListOf<String>()

    fun closeSection() {
        if (items.isNotEmpty()) sections += ChangelogSection(title.orEmpty(), items.toList())
        items.clear()
    }

    text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
        val header = HeaderLine.find(line)
        val bullet = BulletPrefixes.firstOrNull { line.startsWith(it) }
        when {
            header != null -> {
                val value = header.groupValues[2].trim()
                when (header.groupValues[1]) {
                    "VERSION" -> version = value.ifEmpty { version }
                    "DATE" -> date = parseDate(value)
                    "STATUS" -> preRelease = value.contains("PRE", ignoreCase = true)
                }
            }

            bullet != null -> {
                items += line.removePrefix(bullet).trim()
            }

            line.equals("FLOW CHANGE LOG", ignoreCase = true) -> {
                Unit
            }

            else -> {
                closeSection()
                title = sectionTitle(line)
            }
        }
    }
    closeSection()
    return ChangelogRelease(version, date, preRelease, sections)
}

private fun parseDate(value: String): LocalDate? =
    try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

private val ShortWords = setOf("OR", "OF", "TO", "IN", "ON", "AT", "BY", "AS", "IS", "IT", "A")
private val KnownAcronyms = setOf("API", "SDK", "CPU", "GPU", "PIP", "RSS", "URL", "HDR", "DNS", "EQ")

/**
 * The files shout their headings ("UI AND STYLE ENHANCEMENTS"); the sheet shows them in sentence
 * case, keeping short all-caps words such as "UI" and "CI" as the acronyms they are.
 */
internal fun sectionTitle(raw: String): String {
    val cleaned = raw.trim().trim('!', ':').trim()
    if (cleaned.any { it.isLowerCase() }) return cleaned
    return cleaned
        .split(' ')
        .mapIndexed { index, word ->
            val acronym =
                word in KnownAcronyms ||
                    (word.length <= 2 && word.all { it.isUpperCase() } && word !in ShortWords) ||
                    (word.any { it.isDigit() } && word.none { it.isLowerCase() })
            when {
                acronym -> word
                index == 0 -> word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
                else -> word.lowercase(Locale.ROOT)
            }
        }.joinToString(" ")
}
