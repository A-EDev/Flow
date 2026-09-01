package io.github.aedev.flow.data.local

import java.io.BufferedReader
import java.net.URI
import java.text.Normalizer
import java.time.OffsetDateTime
import java.util.Locale

internal data class YouTubeTakeoutSubscription(
    val channelId: String,
    val channelName: String,
)

internal sealed interface YouTubeTakeoutCsvContent {
    data class Subscriptions(
        val rows: List<YouTubeTakeoutSubscription>,
    ) : YouTubeTakeoutCsvContent

    data class PlaylistVideos(
        val videoIds: List<String>,
    ) : YouTubeTakeoutCsvContent

    data class PlaylistMetadata(
        val titles: List<String>,
    ) : YouTubeTakeoutCsvContent

    data object Unsupported : YouTubeTakeoutCsvContent
}

private val youtubeChannelIdPattern = Regex("UC[A-Za-z0-9_-]{22}")
private val youtubePlaylistIdPattern = Regex("PL[A-Za-z0-9_-]{16,}")
private val youtubeVideoIdPattern = Regex("[A-Za-z0-9_-]{11}")
private const val MAX_IMPORTED_NAME_CHARACTERS = 1_000
private const val MAX_TAKEOUT_ENTRY_NAME_CHARACTERS = 4_096
private const val MAX_TAKEOUT_LEAF_NAME_CHARACTERS = 512
private const val MAX_TAKEOUT_PLAYLISTS = 2_000

internal fun isYouTubeTakeoutCsvEntry(entryName: String): Boolean {
    if (entryName.length > MAX_TAKEOUT_ENTRY_NAME_CHARACTERS) return false
    val normalized = entryName.replace('\\', '/')
    val segments = normalized.split('/')
    val product = segments.getOrNull(1).orEmpty()
    val isYouTubeProduct =
        product.equals("YouTube", ignoreCase = true) ||
            (
                product.startsWith("YouTube ", ignoreCase = true) &&
                    product.endsWith("YouTube Music", ignoreCase = true)
            )
    return segments.size >= 3 &&
        segments.first().equals("Takeout", ignoreCase = true) &&
        isYouTubeProduct &&
        segments.none { it.isEmpty() || it == "." || it == ".." } &&
        segments.last().length <= MAX_TAKEOUT_LEAF_NAME_CHARACTERS &&
        segments.last().endsWith(".csv", ignoreCase = true)
}

internal fun String.takeoutParentPath(): String = replace('\\', '/').substringBeforeLast('/', "")

internal fun validateYouTubeTakeoutPlaylistCount(
    videoFileCount: Int,
    metadataTitleCount: Int,
) {
    if (videoFileCount > MAX_TAKEOUT_PLAYLISTS || metadataTitleCount > MAX_TAKEOUT_PLAYLISTS) {
        throw IllegalArgumentException("invalid_format")
    }
}

internal fun readYouTubeTakeoutCsv(
    reader: BufferedReader,
    budget: YouTubeTakeoutCsvBudget = YouTubeTakeoutCsvBudget(),
): YouTubeTakeoutCsvContent {
    val csvReader = TakeoutCsvReader(reader, budget)
    val header =
        csvReader.nextNonBlankRecord() as? CsvRecordResult.Record
            ?: return csvReader.rejectIgnoredAsUnsupported()
    val firstData =
        csvReader.nextNonBlankRecord() as? CsvRecordResult.Record
            ?: return csvReader.rejectIgnoredAsUnsupported()

    val firstSubscription = firstData.fields.toSubscription()
    val firstPlaylistVideo = firstData.fields.toPlaylistVideoId()
    val firstPlaylistTitle = firstData.fields.toPlaylistTitle()

    return when {
        header.fields.size >= 3 && header.fields.toSubscription() == null && firstSubscription != null -> {
            csvReader.readRows(
                first = firstSubscription,
                weight = { row -> row.channelId.length + row.channelName.length },
                parse = { fields -> fields.toSubscription() },
                build = { rows -> YouTubeTakeoutCsvContent.Subscriptions(rows) },
            )
        }

        header.fields.size == 2 && header.fields.toPlaylistVideoId() == null && firstPlaylistVideo != null -> {
            csvReader.readRows(
                first = firstPlaylistVideo,
                weight = String::length,
                parse = { fields -> fields.toPlaylistVideoId() },
                build = { videoIds -> YouTubeTakeoutCsvContent.PlaylistVideos(videoIds) },
            )
        }

        header.fields.size >= 11 && header.fields.toPlaylistTitle() == null && firstPlaylistTitle != null -> {
            csvReader.readRows(
                first = firstPlaylistTitle,
                weight = String::length,
                parse = { fields -> fields.toPlaylistTitle() },
                build = { titles -> YouTubeTakeoutCsvContent.PlaylistMetadata(titles) },
            )
        }

        else -> {
            csvReader.rejectIgnoredAsUnsupported()
        }
    }
}

internal fun resolveYouTubeTakeoutPlaylistNames(
    filenames: Collection<String>,
    metadataTitles: Collection<String>,
    fallbackPlaylistName: String,
): Map<String, String> {
    validateYouTubeTakeoutPlaylistCount(filenames.size, metadataTitles.size)
    val remainingFilenames = filenames.toMutableList()
    val remainingTitles = metadataTitles.filter { it.isNotBlank() }.toMutableList()
    val resolved = linkedMapOf<String, String>()
    val normalizedFilenames =
        filenames.associateWith { filename ->
            filename.takeoutLeafStem().withoutEnglishVideosSuffix().normalizedTakeoutName()
        }

    val titleCandidates =
        remainingTitles
            .groupBy(String::normalizedTakeoutName)
            .mapNotNull { (normalizedTitle, titles) ->
                if (normalizedTitle.isEmpty()) return@mapNotNull null
                val matchCount = normalizedFilenames.values.count { filename -> filename.contains(normalizedTitle) }
                Triple(titles, normalizedTitle, matchCount).takeIf { matchCount > 0 }
            }.sortedWith(
                compareBy<Triple<List<String>, String, Int>> { (_, _, matchCount) -> matchCount }
                    .thenByDescending { (_, normalizedTitle) -> normalizedTitle.length },
            )

    titleCandidates.forEach { (titles, normalizedTitle) ->
        val matchingFilenames =
            remainingFilenames.filter { filename ->
                normalizedFilenames.getValue(filename).contains(normalizedTitle)
            }
        val hasOneExactTitle = titles.all { title -> title == titles.first() }
        if (hasOneExactTitle && matchingFilenames.size == titles.size) {
            matchingFilenames.zip(titles).forEach { (filename, title) ->
                resolved[filename] = title
            }
            remainingFilenames.removeAll(matchingFilenames.toSet())
            titles.forEach { title -> remainingTitles.remove(title) }
        }
    }

    if (remainingFilenames.size == 1 && remainingTitles.size == 1) {
        resolved[remainingFilenames.removeAt(0)] = remainingTitles.removeAt(0)
    }

    remainingFilenames.forEach { filename ->
        resolved[filename] = filename.fallbackTakeoutPlaylistName(fallbackPlaylistName)
    }
    return resolved
}

private fun <T> TakeoutCsvReader.readRows(
    first: T,
    weight: (T) -> Int,
    parse: (List<String>) -> T?,
    build: (List<T>) -> YouTubeTakeoutCsvContent,
): YouTubeTakeoutCsvContent {
    stageContent(weight(first))
    val rows = mutableListOf(first)
    while (true) {
        when (val result = nextNonBlankRecord()) {
            CsvRecordResult.End -> {
                commitContent()
                return build(rows)
            }

            CsvRecordResult.Malformed -> {
                return rejectTargetAsUnsupported()
            }

            is CsvRecordResult.Record -> {
                parse(result.fields)?.let { row ->
                    stageContent(weight(row))
                    rows += row
                }
            }
        }
    }
}

private fun List<String>.toSubscription(): YouTubeTakeoutSubscription? {
    if (size < 3) return null
    val channelId = this[0].trim().trimStart('\uFEFF')
    if (!youtubeChannelIdPattern.matches(channelId)) return null

    val channelUri = runCatching { URI(this[1].trim()) }.getOrNull() ?: return null
    val scheme = channelUri.scheme?.lowercase(Locale.ROOT)
    val host = channelUri.host?.lowercase(Locale.ROOT)
    if (scheme != "http" && scheme != "https") return null
    if (host != "youtube.com" && host != "www.youtube.com") return null
    if (channelUri.userInfo != null || channelUri.port != -1 || channelUri.query != null || channelUri.fragment != null) return null
    val expectedPath = "/channel/$channelId"
    if (channelUri.path != expectedPath && channelUri.path != "$expectedPath/") return null

    val channelName = this[2].validatedTakeoutName() ?: return null
    return YouTubeTakeoutSubscription(channelId, channelName)
}

private fun List<String>.toPlaylistVideoId(): String? {
    if (size != 2) return null
    val videoId = this[0].trim().trimStart('\uFEFF')
    if (!youtubeVideoIdPattern.matches(videoId)) return null
    val timestamp = this[1].trim()
    if (timestamp.isNotEmpty() && runCatching { OffsetDateTime.parse(timestamp) }.isFailure) return null
    return videoId
}

private fun List<String>.toPlaylistTitle(): String? {
    if (size < 11) return null
    val playlistId = this[0].trim().trimStart('\uFEFF')
    if (!youtubePlaylistIdPattern.matches(playlistId)) return null
    return this[10].validatedTakeoutName()
}

private fun String.validatedTakeoutName(): String? =
    trim().takeIf { value ->
        value.isNotEmpty() &&
            value.length <= MAX_IMPORTED_NAME_CHARACTERS &&
            value.none { character ->
                character.isISOControl() || character == '\u2028' || character == '\u2029'
            }
    }

private fun String.normalizedTakeoutName(): String =
    Normalizer
        .normalize(this, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .filter(Char::isTakeoutNameCharacter)

private fun Char.isTakeoutNameCharacter(): Boolean =
    isLetterOrDigit() ||
        Character.isSurrogate(this) ||
        when (Character.getType(this)) {
            Character.MATH_SYMBOL.toInt(),
            Character.CURRENCY_SYMBOL.toInt(),
            Character.MODIFIER_SYMBOL.toInt(),
            Character.OTHER_SYMBOL.toInt(),
            -> true

            else -> false
        }

private fun String.takeoutLeafStem(): String {
    val leaf = replace('\\', '/').substringAfterLast('/')
    return leaf.substringBeforeLast('.', leaf)
}

private fun String.withoutEnglishVideosSuffix(): String = if (endsWith("-videos", ignoreCase = true)) dropLast(7) else this

private fun String.fallbackTakeoutPlaylistName(fallbackPlaylistName: String): String {
    val fallback =
        takeoutLeafStem()
            .withoutEnglishVideosSuffix()
            .trim { it.isWhitespace() || it == '-' || it == '_' }
    return fallback.validatedTakeoutName() ?: fallbackPlaylistName
}
