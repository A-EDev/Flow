package io.github.aedev.flow.data.local

import java.io.BufferedReader

internal sealed interface CsvRecordResult {
    data class Record(
        val fields: List<String>,
    ) : CsvRecordResult

    data object End : CsvRecordResult

    data object Malformed : CsvRecordResult
}

private class TakeoutCsvLimitExceededException : IllegalArgumentException("invalid_format")

// Bound decompressed CSV input selected outside the app before it can grow parser allocations.
private const val MAX_CSV_CHARACTERS = 16 * 1_024 * 1_024
private const val MAX_IGNORED_CSV_CHARACTERS = 64 * 1_024 * 1_024
private const val MAX_ARCHIVE_CSV_CHARACTERS = 256 * 1_024 * 1_024
private const val MAX_CSV_RECORD_CHARACTERS = 64 * 1_024
private const val MAX_CSV_RECORDS = 100_000
private const val MAX_CSV_FIELDS = 64
private const val MAX_ARCHIVE_CSV_ENTRIES = 10_000
private const val MAX_IMPORTED_ROWS = 500_000
private const val MAX_IMPORTED_CHARACTERS = 16 * 1_024 * 1_024
private const val NO_PENDING_CHARACTER = -2

internal class YouTubeTakeoutCsvBudget {
    private var charactersRead = 0L
    private var entriesRead = 0
    private var rowsAccepted = 0L
    private var charactersAccepted = 0L

    fun startEntry() {
        if (++entriesRead > MAX_ARCHIVE_CSV_ENTRIES) throw TakeoutCsvLimitExceededException()
    }

    fun recordCharacter() {
        if (++charactersRead > MAX_ARCHIVE_CSV_CHARACTERS) throw TakeoutCsvLimitExceededException()
    }

    fun acceptContent(
        rows: Int,
        characters: Int,
    ) {
        if (rowsAccepted + rows > MAX_IMPORTED_ROWS) throw TakeoutCsvLimitExceededException()
        if (charactersAccepted + characters > MAX_IMPORTED_CHARACTERS) throw TakeoutCsvLimitExceededException()
        rowsAccepted += rows.toLong()
        charactersAccepted += characters.toLong()
    }
}

internal class TakeoutCsvReader(
    private val reader: BufferedReader,
    private val budget: YouTubeTakeoutCsvBudget,
) {
    private var charactersRead = 0
    private var recordsRead = 0
    private var pendingCharacter = NO_PENDING_CHARACTER
    private var pendingRows = 0
    private var pendingContentCharacters = 0

    fun nextNonBlankRecord(): CsvRecordResult {
        while (true) {
            when (val result = readRecord()) {
                is CsvRecordResult.Record -> if (result.fields.any(String::isNotBlank)) return result
                else -> return result
            }
        }
    }

    fun rejectTargetAsUnsupported(): YouTubeTakeoutCsvContent.Unsupported = rejectAsUnsupported(MAX_CSV_CHARACTERS)

    fun rejectIgnoredAsUnsupported(): YouTubeTakeoutCsvContent.Unsupported = rejectAsUnsupported(MAX_IGNORED_CSV_CHARACTERS)

    fun stageContent(characters: Int) {
        pendingRows++
        pendingContentCharacters += characters
    }

    fun commitContent() = budget.acceptContent(pendingRows, pendingContentCharacters)

    private fun rejectAsUnsupported(characterLimit: Int): YouTubeTakeoutCsvContent.Unsupported {
        while (readCharacter(characterLimit) != -1) {}
        return YouTubeTakeoutCsvContent.Unsupported
    }

    private fun readRecord(): CsvRecordResult {
        if (recordsRead >= MAX_CSV_RECORDS) {
            if (readCharacter() == -1) return CsvRecordResult.End
            throw TakeoutCsvLimitExceededException()
        }

        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var recordCharacters = 0
        var inQuotes = false
        var closedQuote = false
        var hasInput = false

        while (true) {
            val value = readCharacter()
            if (value == -1) {
                if (!hasInput) return CsvRecordResult.End
                if (inQuotes) return CsvRecordResult.Malformed
                fields += field.toString()
                recordsRead++
                return CsvRecordResult.Record(fields)
            }

            hasInput = true
            recordCharacters++
            if (recordCharacters > MAX_CSV_RECORD_CHARACTERS) throw TakeoutCsvLimitExceededException()

            val character = value.toChar()
            when {
                inQuotes && character == '"' -> {
                    val next = readCharacter()
                    when (next) {
                        '"'.code -> {
                            field.append('"')
                        }

                        else -> {
                            if (next != -1) pendingCharacter = next
                            inQuotes = false
                            closedQuote = true
                        }
                    }
                }

                inQuotes -> {
                    field.append(character)
                }

                closedQuote && character == ',' -> {
                    if (fields.size >= MAX_CSV_FIELDS - 1) throw TakeoutCsvLimitExceededException()
                    fields += field.toString()
                    field.clear()
                    closedQuote = false
                }

                closedQuote && (character == ' ' || character == '\t') -> {}

                closedQuote && (character == '\r' || character == '\n') -> {
                    consumeLineFeedAfterCarriageReturn(character)
                    fields += field.toString()
                    recordsRead++
                    return CsvRecordResult.Record(fields)
                }

                closedQuote -> {
                    return CsvRecordResult.Malformed
                }

                character == '"' && field.isEmpty() -> {
                    inQuotes = true
                }

                character == '"' -> {
                    return CsvRecordResult.Malformed
                }

                character == ',' -> {
                    if (fields.size >= MAX_CSV_FIELDS - 1) throw TakeoutCsvLimitExceededException()
                    fields += field.toString()
                    field.clear()
                }

                character == '\r' || character == '\n' -> {
                    consumeLineFeedAfterCarriageReturn(character)
                    fields += field.toString()
                    recordsRead++
                    return CsvRecordResult.Record(fields)
                }

                else -> {
                    field.append(character)
                }
            }
        }
    }

    private fun readCharacter(characterLimit: Int = MAX_CSV_CHARACTERS): Int {
        if (pendingCharacter != NO_PENDING_CHARACTER) {
            return pendingCharacter.also { pendingCharacter = NO_PENDING_CHARACTER }
        }
        while (true) {
            val value = reader.read()
            if (value == -1) return value
            budget.recordCharacter()
            if (++charactersRead > characterLimit) throw TakeoutCsvLimitExceededException()
            if (charactersRead != 1 || value != '\uFEFF'.code) return value
        }
    }

    private fun consumeLineFeedAfterCarriageReturn(character: Char) {
        if (character != '\r') return
        val next = readCharacter()
        if (next != '\n'.code && next != -1) pendingCharacter = next
    }
}
