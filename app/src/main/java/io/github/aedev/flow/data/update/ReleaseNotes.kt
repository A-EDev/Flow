package io.github.aedev.flow.data.update

private const val REPO_URL = "https://github.com/A-EDev/Flow"
private const val GITHUB_URL = "https://github.com"
private const val RELEASE_DATE_LABEL = "**release date"

/** Release notes split the way the update page shows them: an intro, then titled sections of items. */
data class ReleaseNotes(
    val intro: List<NoteText>,
    val sections: List<NoteSection>,
)

data class NoteSection(
    val title: String,
    val items: List<NoteText>,
)

data class NoteText(
    val spans: List<NoteSpan>,
)

sealed interface NoteSpan {
    data class Plain(
        val text: String,
    ) : NoteSpan

    data class Strong(
        val text: String,
    ) : NoteSpan

    data class Link(
        val text: String,
        val url: String,
    ) : NoteSpan
}

private val InlinePattern =
    Regex(
        """\*\*(.+?)\*\*""" +
            """|\[([^\]]+)]\((https?://[^)\s]+)\)""" +
            """|(?<![\w/&])#(\d+)\b""" +
            """|(?<![\w/])@([A-Za-z0-9][A-Za-z0-9-]{0,38})""" +
            """|(https?://[^\s)]+)""",
    )
private val BulletPattern = Regex("""^[-*+]\s+""")
private val HeadingPattern = Regex("""^(#{1,6})\s+""")
private val RulePattern = Regex("""^([-*_])\1{2,}$""")

/**
 * Reads the Markdown Flow's GitHub releases are written in: the `#` title and the release date line
 * are dropped (the page shows both), `##` headings open sections, bullets and paragraphs become
 * items, and bold text, links, `#123` issues and `@user` mentions keep their meaning.
 */
fun parseReleaseNotes(markdown: String): ReleaseNotes {
    val intro = mutableListOf<NoteText>()
    val sections = mutableListOf<NoteSection>()
    var title: String? = null
    var items = mutableListOf<NoteText>()
    val paragraph = StringBuilder()

    fun target() = if (title == null) intro else items

    fun flushParagraph() {
        if (paragraph.isNotBlank()) target() += parseInline(paragraph.toString().trim())
        paragraph.clear()
    }

    fun closeSection() {
        flushParagraph()
        title?.let { if (items.isNotEmpty()) sections += NoteSection(it, items) }
        items = mutableListOf()
    }

    markdown.replace("\r\n", "\n").lineSequence().map { it.trim() }.forEach { line ->
        val heading = HeadingPattern.find(line)
        when {
            line.isEmpty() || RulePattern.matches(line) -> {
                flushParagraph()
            }

            line.lowercase().startsWith(RELEASE_DATE_LABEL) -> {
                flushParagraph()
            }

            heading != null && heading.groupValues[1].length == 1 -> {
                flushParagraph()
            }

            heading != null -> {
                closeSection()
                title = plainText(line.removeRange(heading.range))
            }

            BulletPattern.containsMatchIn(line) -> {
                flushParagraph()
                target() += parseInline(line.replaceFirst(BulletPattern, ""))
            }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
            }
        }
    }
    closeSection()
    return ReleaseNotes(intro, sections)
}

internal fun parseInline(text: String): NoteText {
    val spans = mutableListOf<NoteSpan>()
    var cursor = 0
    InlinePattern.findAll(text).forEach { match ->
        if (match.range.first > cursor) spans += NoteSpan.Plain(text.substring(cursor, match.range.first))
        val (bold, label, url, issue, user, bare) = match.destructured
        spans +=
            when {
                bold.isNotEmpty() -> NoteSpan.Strong(bold)
                label.isNotEmpty() -> NoteSpan.Link(label, url)
                issue.isNotEmpty() -> NoteSpan.Link("#$issue", "$REPO_URL/issues/$issue")
                user.isNotEmpty() -> NoteSpan.Link("@$user", "$GITHUB_URL/$user")
                else -> NoteSpan.Link(bare, bare)
            }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) spans += NoteSpan.Plain(text.substring(cursor))
    return NoteText(spans)
}

private fun plainText(markdown: String): String =
    parseInline(markdown).spans.joinToString("") { span ->
        when (span) {
            is NoteSpan.Plain -> span.text
            is NoteSpan.Strong -> span.text
            is NoteSpan.Link -> span.text
        }
    }
