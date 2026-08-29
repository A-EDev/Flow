/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RelativeUploadDateParserTest {
    private val now = 1_700_000_000_000L
    private val dayMs = 86_400_000L

    @Test
    fun `parses common relative dates`() {
        assertThat(RelativeUploadDateParser.parse("3 days ago", now)).isEqualTo(now - 3 * dayMs)
        assertThat(RelativeUploadDateParser.parse("2 weeks ago", now)).isEqualTo(now - 14 * dayMs)
        assertThat(RelativeUploadDateParser.parse("1 month ago", now)).isEqualTo(now - 30 * dayMs)
        assertThat(RelativeUploadDateParser.parse("4 years ago", now)).isEqualTo(now - 4 * 365 * dayMs)
    }

    @Test
    fun `strips streamed and premiered prefixes`() {
        assertThat(RelativeUploadDateParser.parse("Streamed 2 days ago", now)).isEqualTo(now - 2 * dayMs)
        assertThat(RelativeUploadDateParser.parse("Premiered 5 hours ago", now)).isEqualTo(now - 5 * 3_600_000L)
    }

    @Test
    fun `unknown text returns null - never now`() {
        assertThat(RelativeUploadDateParser.parse(null, now)).isNull()
        assertThat(RelativeUploadDateParser.parse("", now)).isNull()
        assertThat(RelativeUploadDateParser.parse("some random text", now)).isNull()
    }

    @Test
    fun `today and yesterday resolve`() {
        assertThat(RelativeUploadDateParser.parse("today", now)).isEqualTo(now)
        assertThat(RelativeUploadDateParser.parse("yesterday", now)).isEqualTo(now - dayMs)
    }
}
