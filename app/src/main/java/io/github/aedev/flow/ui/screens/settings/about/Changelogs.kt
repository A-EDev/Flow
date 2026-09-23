package io.github.aedev.flow.ui.screens.settings.about

private val VersionParts = Regex("""\d+""")

/**
 * The newest changelog among asset [files] named like "v2.10.0.txt", compared as versions: a plain
 * string sort would put 2.10 before 2.9.
 */
internal fun latestChangelog(files: List<String>): String? =
    files
        .filter { it.endsWith(".txt") }
        .maxWithOrNull { a, b -> compareVersions(versionOf(a), versionOf(b)) }

private fun versionOf(file: String): List<Int> = VersionParts.findAll(file.removeSuffix(".txt")).map { it.value.toInt() }.toList()

private fun compareVersions(
    a: List<Int>,
    b: List<Int>,
): Int {
    for (index in 0 until maxOf(a.size, b.size)) {
        val difference = a.getOrElse(index) { 0 } - b.getOrElse(index) { 0 }
        if (difference != 0) return difference
    }
    return 0
}
