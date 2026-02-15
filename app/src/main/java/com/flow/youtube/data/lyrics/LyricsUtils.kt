package com.flow.youtube.data.lyrics

/**
 * Utility functions for parsing lyrics in various formats.
 * Supports standard LRC, rich sync (word-by-word), and plain text.
 */
object LyricsUtils {
    
    private val LINE_REGEX = "((\\[\\d{1,2}:\\d{2}[.:]\\d{2,3}\\] ?)+)(.+)".toRegex()
    private val TIME_REGEX = "\\[(\\d{1,2}):(\\d{2})[.:](\\d{2,3})\\]".toRegex()
    
    private val RICH_SYNC_LINE_REGEX = "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](.+)".toRegex()
    private val RICH_SYNC_WORD_REGEX = "<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>\\s*([^<]+)".toRegex()

    /**
     * Parse lyrics string into a list of LyricsEntry objects.
     * Auto-detects rich sync vs standard LRC format.
     */
    fun parseLyrics(lyrics: String): List<LyricsEntry> {
        val unescaped = lyrics
            .trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")

        val lines = unescaped.lines()
            .filter { it.isNotBlank() && !it.trim().startsWith("[offset:") }

        val isRichSync = lines.any { line ->
            RICH_SYNC_LINE_REGEX.matches(line.trim()) &&
            RICH_SYNC_WORD_REGEX.containsMatchIn(line)
        }

        return if (isRichSync) {
            parseRichSyncLyrics(lines)
        } else {
            parseStandardLyrics(lines)
        }
    }

    /**
     * Parse rich sync lyrics: [MM:SS.mm]<MM:SS.mm> word <MM:SS.mm> word ...
     */
    private fun parseRichSyncLyrics(lines: List<String>): List<LyricsEntry> {
        val result = mutableListOf<LyricsEntry>()

        lines.forEachIndexed { index, line ->
            val matchResult = RICH_SYNC_LINE_REGEX.matchEntire(line.trim())
            if (matchResult != null) {
                val minutes = matchResult.groupValues[1].toLongOrNull() ?: 0L
                val seconds = matchResult.groupValues[2].toLongOrNull() ?: 0L
                val fraction = matchResult.groupValues[3].toLongOrNull() ?: 0L

                val millisPart = if (matchResult.groupValues[3].length == 3) fraction else fraction * 10
                val lineTimeMs = minutes * 60000 + seconds * 1000 + millisPart

                val content = matchResult.groupValues[4].trimStart()

                val wordTimings = parseRichSyncWords(content, index, lines)

                val plainText = content.replace(Regex("<\\d{1,2}:\\d{2}\\.\\d{2,3}>\\s*"), "").trim()

                if (plainText.isNotBlank()) {
                    result.add(LyricsEntry(lineTimeMs, plainText, wordTimings))
                }
            }
        }

        return result.sorted()
    }

    /**
     * Parse word timestamps from rich sync content.
     */
    private fun parseRichSyncWords(content: String, currentIndex: Int, allLines: List<String>): List<WordTimestamp>? {
        val wordMatches = RICH_SYNC_WORD_REGEX.findAll(content).toList()
        if (wordMatches.isEmpty()) return null

        val wordTimings = mutableListOf<WordTimestamp>()

        wordMatches.forEachIndexed { index, match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fraction = match.groupValues[3].toLongOrNull() ?: 0L

            val millisPart = if (match.groupValues[3].length == 3) fraction else fraction * 10
            val startTimeMs = minutes * 60000 + seconds * 1000 + millisPart

            val wordText = match.groupValues[4].trim()

            val endTimeMs = if (index < wordMatches.size - 1) {
                val next = wordMatches[index + 1]
                val nMin = next.groupValues[1].toLongOrNull() ?: 0L
                val nSec = next.groupValues[2].toLongOrNull() ?: 0L
                val nFrac = next.groupValues[3].toLongOrNull() ?: 0L
                val nMillisPart = if (next.groupValues[3].length == 3) nFrac else nFrac * 10
                nMin * 60000 + nSec * 1000 + nMillisPart
            } else {
                getNextLineStartTimeMs(currentIndex, allLines) ?: (startTimeMs + 500)
            }

            if (wordText.isNotBlank()) {
                wordTimings.add(WordTimestamp(wordText, startTimeMs, endTimeMs))
            }
        }

        return wordTimings.takeIf { it.isNotEmpty() }
    }

    private fun getNextLineStartTimeMs(currentIndex: Int, allLines: List<String>): Long? {
        if (currentIndex + 1 >= allLines.size) return null
        val nextLine = allLines[currentIndex + 1].trim()
        val match = RICH_SYNC_LINE_REGEX.matchEntire(nextLine) ?: return null
        val min = match.groupValues[1].toLongOrNull() ?: return null
        val sec = match.groupValues[2].toLongOrNull() ?: return null
        val frac = match.groupValues[3].toLongOrNull() ?: 0L
        val millisPart = if (match.groupValues[3].length == 3) frac else frac * 10
        return min * 60000 + sec * 1000 + millisPart
    }


    /**
     * Parse standard LRC format: [MM:SS.mm] text
     * Also supports Metrolist's word-data format where word timings are on the next line:
     * [MM:SS.mm] text
     * <word1:startTime:endTime|word2:startTime:endTime|...>
     */
    private fun parseStandardLyrics(lines: List<String>): List<LyricsEntry> {
        val result = mutableListOf<LyricsEntry>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            
            if (line.startsWith("<") && line.endsWith(">")) {
                i++
                continue
            }

            val timeMatchResults = TIME_REGEX.findAll(line).toList()
            if (timeMatchResults.isNotEmpty()) {
                val content = line.replace(TIME_REGEX, "").trim()
                if (content.isNotEmpty()) {
                    val wordTimestamps = if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1].trim()
                        if (nextLine.startsWith("<") && nextLine.endsWith(">")) {
                            parseMetrolistWordTimestamps(nextLine.removeSurrounding("<", ">"))
                        } else null
                    } else null

                    timeMatchResults.forEach { match ->
                        val min = match.groupValues[1].toLong()
                        val sec = match.groupValues[2].toLong()
                        val msStr = match.groupValues[3]
                        val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                        val time = min * 60000 + sec * 1000 + ms
                        result.add(LyricsEntry(time, content, wordTimestamps))
                    }
                }
            }
            i++
        }

        return result.sorted()
    }

    /**
     * Parses Metrolist's word data format: word:startTime:endTime|word:startTime:endTime|...
     * Times are in seconds (Double).
     */
    private fun parseMetrolistWordTimestamps(data: String): List<WordTimestamp>? {
        if (data.isBlank()) return null
        return try {
            data.split("|").mapNotNull { wordData ->
                val parts = wordData.split(":")
                if (parts.size == 3) {
                    WordTimestamp(
                        text = parts[0],
                        startTime = (parts[1].toDouble() * 1000).toLong(),
                        endTime = (parts[2].toDouble() * 1000).toLong()
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Find the index of the current lyrics line based on playback position.
     */
    fun findCurrentLineIndex(lines: List<LyricsEntry>, position: Long): Int {
        for (index in lines.indices) {
            if (lines[index].time >= position + 300L) {
                return index - 1
            }
        }
        return lines.lastIndex
    }
}
