package io.github.aedev.flow.data.local

/**
 * The stored form of a subscription:
 * `id|name|thumbnail|subscribedAt|lastVideoId|lastCheckTime|notify|isMusic|lastFeedFetchAt`.
 *
 * Text fields escape `|` as `%7C`, and `%` as `%25` only where it would otherwise read as an escape,
 * so a record without either is byte-identical to what older versions wrote. An older version
 * reading an escaped record still gets all nine fields and simply shows `%7C` in the name. The
 * thumbnail is a URL, where `%7C` already means `|`, so it is written that way and read verbatim.
 *
 * Older versions wrote text unescaped and had fewer trailing fields, so [decode] also recovers
 * those rows by anchoring the id at the front and the typed fields at the back.
 */
internal object SubscriptionRecordCodec {
    private const val SEPARATOR = '|'
    private const val ESCAPED_SEPARATOR = "%7C"
    private const val ESCAPED_PERCENT = "%25"

    private enum class Field { LONG, BOOLEAN, VIDEO_ID }

    /** Every layout ever written, newest first, as the fields that follow name and thumbnail. */
    private val LegacyTails =
        listOf(
            listOf(Field.LONG, Field.VIDEO_ID, Field.LONG, Field.BOOLEAN, Field.BOOLEAN, Field.LONG),
            listOf(Field.LONG, Field.VIDEO_ID, Field.LONG, Field.BOOLEAN, Field.BOOLEAN),
            listOf(Field.LONG, Field.VIDEO_ID, Field.LONG, Field.BOOLEAN),
            listOf(Field.LONG, Field.VIDEO_ID, Field.LONG),
            listOf(Field.LONG),
        )

    private val VideoIdPattern = Regex("[A-Za-z0-9_-]{11}")

    fun encode(channel: ChannelSubscription): String =
        listOf(
            escape(channel.channelId),
            escape(channel.channelName),
            channel.channelThumbnail.replace(SEPARATOR.toString(), ESCAPED_SEPARATOR),
            channel.subscribedAt.toString(),
            escape(channel.lastVideoId.orEmpty()),
            channel.lastCheckTime.toString(),
            channel.isNotificationEnabled.toString(),
            channel.isMusic.toString(),
            channel.lastFeedFetchAt.toString(),
        ).joinToString(SEPARATOR.toString())

    fun decode(data: String): ChannelSubscription? {
        val parts = data.split(SEPARATOR)
        if (parts.firstOrNull().isNullOrBlank()) return null
        LegacyTails.firstOrNull { tail -> parts.size == tail.size + 3 && fits(parts, tail, strict = false) }?.let {
            return build(parts, it)
        }
        return LegacyTails.firstOrNull { tail -> parts.size > tail.size + 3 && fits(parts, tail, strict = true) }?.let {
            build(parts, it)
        }
    }

    /** [strict] demands exact types, since a recovered row could otherwise anchor on a thumbnail. */
    private fun fits(
        parts: List<String>,
        tail: List<Field>,
        strict: Boolean,
    ): Boolean {
        val offset = parts.size - tail.size
        return tail.withIndex().all { (index, field) ->
            val value = parts[offset + index]
            when (field) {
                Field.LONG -> value.toLongOrNull() != null || (!strict && index > 0 && value.isEmpty())
                Field.BOOLEAN -> value == "true" || value == "false" || (!strict && value.isEmpty())
                Field.VIDEO_ID -> !strict || value.isEmpty() || VideoIdPattern.matches(value)
            }
        }
    }

    private fun build(
        parts: List<String>,
        tail: List<Field>,
    ): ChannelSubscription {
        val offset = parts.size - tail.size
        val (name, thumbnail) = splitNameAndThumbnail(parts.subList(1, offset))

        fun at(index: Int) = parts.getOrNull(offset + index).orEmpty()
        return ChannelSubscription(
            channelId = unescape(parts[0]),
            channelName = unescape(name),
            channelThumbnail = thumbnail,
            subscribedAt = at(0).toLong(),
            lastVideoId = unescape(at(1)).ifEmpty { null },
            lastCheckTime = at(2).toLongOrNull() ?: 0L,
            isNotificationEnabled = at(3).toBoolean(),
            isMusic = at(4).toBoolean(),
            lastFeedFetchAt = at(5).toLongOrNull() ?: 0L,
        )
    }

    /** An unescaped name can hold any number of `|`; the thumbnail, a URL or empty, is the last piece. */
    private fun splitNameAndThumbnail(middle: List<String>): Pair<String, String> {
        if (middle.size <= 2) return middle.getOrElse(0) { "" } to middle.getOrElse(1) { "" }
        val urlStart = (1 until middle.size).firstOrNull { middle[it].isUrl() } ?: middle.lastIndex
        return middle.subList(0, urlStart).joinToString(SEPARATOR.toString()) to
            middle.subList(urlStart, middle.size).joinToString(SEPARATOR.toString())
    }

    private fun String.isUrl() = startsWith("https://") || startsWith("http://") || startsWith("//")

    internal fun escape(text: String): String {
        if (text.none { it == SEPARATOR || it == '%' }) return text
        return buildString(text.length + 8) {
            text.forEachIndexed { index, char ->
                when {
                    char == SEPARATOR -> append(ESCAPED_SEPARATOR)
                    char == '%' && text.startsWithEscapeAt(index) -> append(ESCAPED_PERCENT)
                    else -> append(char)
                }
            }
        }
    }

    internal fun unescape(text: String): String {
        if ('%' !in text) return text
        return buildString(text.length) {
            var index = 0
            while (index < text.length) {
                when {
                    text.startsWith(ESCAPED_SEPARATOR, index) -> append(SEPARATOR).also { index += 3 }
                    text.startsWith(ESCAPED_PERCENT, index) -> append('%').also { index += 3 }
                    else -> append(text[index++])
                }
            }
        }
    }

    private fun String.startsWithEscapeAt(index: Int) = startsWith(ESCAPED_SEPARATOR, index) || startsWith(ESCAPED_PERCENT, index)
}
