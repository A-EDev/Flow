package io.github.aedev.flow.data.sponsordetection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer
import java.util.LinkedHashMap

internal data class SponsorToken(
    val id: Long,
    val startCodePoint: Int,
    val endCodePoint: Int,
)

internal class SponsorTokenizer private constructor(
    private val vocabulary: Map<String, Long>,
    private val mergeRanks: Map<String, Int>,
) {
    private val byteSymbols = byteSymbols()
    private val bpeCache =
        object : LinkedHashMap<String, List<String>>(BPE_CACHE_CAPACITY, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean = size > BPE_CACHE_CAPACITY
        }

    fun encode(text: String): List<SponsorToken> {
        val tokens = mutableListOf(SponsorToken(CLS_ID, 0, 0))
        encodeContent(text).forEach { tokens += it }
        tokens += SponsorToken(SEP_ID, 0, 0)
        return tokens
    }

    /**
     * Content tokens only (no CLS/SEP). Uses a running UTF-16/code-point cursor so
     * long transcripts stay O(n) instead of recounting prefixes for every word.
     */
    fun encodeContent(text: String): Sequence<SponsorToken> =
        sequence {
            val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
            val matcher = PRE_TOKEN_PATTERN.matcher(normalized)
            var utf16Cursor = 0
            var codePointCursor = 0
            while (matcher.find()) {
                if (matcher.start() > utf16Cursor) {
                    codePointCursor += normalized.codePointCount(utf16Cursor, matcher.start())
                    utf16Cursor = matcher.start()
                }
                val match = matcher.group()
                val symbols = byteLevelSymbols(match, codePointCursor)
                val cacheKey = symbols.joinToString(separator = "") { it.text }
                val pieces =
                    synchronized(bpeCache) {
                        bpeCache[cacheKey] ?: bpe(symbols.map { it.text }).also { bpeCache[cacheKey] = it }
                    }
                var symbolIndex = 0
                for (piece in pieces) {
                    var consumedCharacters = 0
                    val start = symbols[symbolIndex].startCodePoint
                    while (consumedCharacters < piece.length) {
                        consumedCharacters += symbols[symbolIndex].text.length
                        symbolIndex++
                    }
                    check(consumedCharacters == piece.length)
                    yield(
                        SponsorToken(
                            checkNotNull(vocabulary[piece]) { "Tokenizer vocabulary is missing '$piece'" },
                            start,
                            symbols[symbolIndex - 1].endCodePoint,
                        ),
                    )
                }
                codePointCursor += match.codePointCount(0, match.length)
                utf16Cursor = matcher.end()
            }
        }

    private fun byteLevelSymbols(
        text: String,
        globalStart: Int,
    ): List<ByteSymbol> {
        val result = ArrayList<ByteSymbol>(text.length)
        var utf16Index = 0
        var codePointIndex = globalStart
        while (utf16Index < text.length) {
            val codePoint = text.codePointAt(utf16Index)
            if (codePoint <= 0x7f) {
                result += ByteSymbol(byteSymbols[codePoint], codePointIndex, codePointIndex + 1)
            } else {
                val bytes = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)
                bytes.forEach { byte ->
                    result +=
                        ByteSymbol(
                            text = byteSymbols[byte.toInt() and 0xff],
                            startCodePoint = codePointIndex,
                            endCodePoint = codePointIndex + 1,
                        )
                }
            }
            utf16Index += Character.charCount(codePoint)
            codePointIndex++
        }
        return result
    }

    private fun bpe(initial: List<String>): List<String> {
        if (initial.size < 2) return initial
        var symbols = initial
        while (true) {
            var bestPair: String? = null
            var bestRank = Int.MAX_VALUE
            for (index in 0 until symbols.lastIndex) {
                val pair = pairKey(symbols[index], symbols[index + 1])
                val rank = mergeRanks[pair] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    bestPair = pair
                }
            }
            val selected = bestPair ?: return symbols
            val merged = ArrayList<String>(symbols.size)
            var index = 0
            while (index < symbols.size) {
                if (index < symbols.lastIndex && pairKey(symbols[index], symbols[index + 1]) == selected) {
                    merged += symbols[index] + symbols[index + 1]
                    index += 2
                } else {
                    merged += symbols[index]
                    index++
                }
            }
            symbols = merged
        }
    }

    private data class ByteSymbol(
        val text: String,
        val startCodePoint: Int,
        val endCodePoint: Int,
    )

    companion object {
        private const val CLS_ID = 50281L
        private const val SEP_ID = 50282L
        private const val BPE_CACHE_CAPACITY = 2_048
        private const val PAIR_SEPARATOR = '\u0000'
        private val PRE_TOKEN_PATTERN =
            Regex(
                "'(?:s|t|re|ve|m|ll|d)| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+",
            ).toPattern()

        fun fromJson(source: String): SponsorTokenizer {
            val model =
                Json
                    .parseToJsonElement(source)
                    .jsonObject
                    .getValue("model")
                    .jsonObject
            val vocabulary =
                model
                    .getValue("vocab")
                    .jsonObject
                    .mapValues { (_, value) -> value.jsonPrimitive.content.toLong() }
            val mergeRanks =
                model
                    .getValue("merges")
                    .jsonArray
                    .mapIndexed { rank, value ->
                        val pair = value.jsonArray
                        pairKey(pair[0].jsonPrimitive.content, pair[1].jsonPrimitive.content) to rank
                    }.toMap()
            return SponsorTokenizer(vocabulary, mergeRanks)
        }

        private fun pairKey(
            left: String,
            right: String,
        ): String = "$left$PAIR_SEPARATOR$right"

        private fun byteSymbols(): Array<String> {
            val retained =
                (33..126).toMutableList().apply {
                    addAll(161..172)
                    addAll(174..255)
                }
            val codePoints = retained.toMutableList()
            var extra = 0
            for (byte in 0..255) {
                if (byte !in retained) {
                    retained += byte
                    codePoints += 256 + extra
                    extra++
                }
            }
            return Array(256) { byte ->
                val position = retained.indexOf(byte)
                String(Character.toChars(codePoints[position]))
            }
        }
    }
}
