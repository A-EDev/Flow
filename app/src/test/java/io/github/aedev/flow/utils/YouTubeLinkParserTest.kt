package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YouTubeLinkParserTest {
    @Test
    fun `watch link resolves the video id`() {
        val link = YouTubeLinkParser.parseVideoLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        assertThat(link).isEqualTo(ParsedVideoLink(videoId = "dQw4w9WgXcQ", isShort = false))
    }

    @Test
    fun `watch link with playlist and timestamp still resolves the video`() {
        val link =
            YouTubeLinkParser.parseVideoLink(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabcdefghij&t=90s&index=2",
            )

        assertThat(link?.videoId).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `short share link resolves the video id`() {
        val link = YouTubeLinkParser.parseVideoLink("https://youtu.be/dQw4w9WgXcQ")

        assertThat(link).isEqualTo(ParsedVideoLink(videoId = "dQw4w9WgXcQ", isShort = false))
    }

    @Test
    fun `shorts link is flagged as a short`() {
        val link = YouTubeLinkParser.parseVideoLink("https://www.youtube.com/shorts/dQw4w9WgXcQ")

        assertThat(link).isEqualTo(ParsedVideoLink(videoId = "dQw4w9WgXcQ", isShort = true))
    }

    @Test
    fun `mobile and music hosts resolve the same way`() {
        val mobile = YouTubeLinkParser.parseVideoLink("https://m.youtube.com/watch?v=dQw4w9WgXcQ")
        val music = YouTubeLinkParser.parseVideoLink("https://music.youtube.com/watch?v=dQw4w9WgXcQ")

        assertThat(mobile?.videoId).isEqualTo("dQw4w9WgXcQ")
        assertThat(music?.videoId).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `mobile music watch link resolves video id and timestamp`() {
        val link =
            YouTubeLinkParser.parseVideoLink(
                "https://m.music.youtube.com/watch?v=dQw4w9WgXcQ&t=252",
            )

        assertThat(link).isEqualTo(
            ParsedVideoLink(
                videoId = "dQw4w9WgXcQ",
                isShort = false,
                startPositionMs = 252_000L,
            ),
        )
    }

    @Test
    fun `live and embed links resolve the video id`() {
        val live = YouTubeLinkParser.parseVideoLink("https://www.youtube.com/live/dQw4w9WgXcQ")
        val embed = YouTubeLinkParser.parseVideoLink("https://www.youtube.com/embed/dQw4w9WgXcQ")

        assertThat(live?.videoId).isEqualTo("dQw4w9WgXcQ")
        assertThat(embed?.videoId).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `share text with a link in the middle resolves the video`() {
        val link =
            YouTubeLinkParser.parseVideoLink(
                "Watch this on YouTube: https://youtu.be/dQw4w9WgXcQ copied from another app",
            )

        assertThat(link?.videoId).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `playlist link is not treated as a video`() {
        val link = YouTubeLinkParser.parseVideoLink("https://www.youtube.com/playlist?list=PLabcdefghijklmnopqrstu")

        assertThat(link).isNull()
    }

    @Test
    fun `channel and search links are not treated as videos`() {
        assertThat(YouTubeLinkParser.parseVideoLink("https://www.youtube.com/@someChannel")).isNull()
        assertThat(YouTubeLinkParser.parseVideoLink("https://www.youtube.com/results?search_query=cats")).isNull()
        assertThat(YouTubeLinkParser.parseVideoLink("https://www.youtube.com/feed/subscriptions")).isNull()
    }

    @Test
    fun `other hosts are ignored`() {
        assertThat(YouTubeLinkParser.parseVideoLink("https://vimeo.com/watch?v=dQw4w9WgXcQ")).isNull()
        assertThat(YouTubeLinkParser.parseVideoLink("https://example.com/watch?v=dQw4w9WgXcQ")).isNull()
    }

    @Test
    fun `malformed or partial ids are rejected`() {
        assertThat(YouTubeLinkParser.parseVideoLink("https://youtu.be/short")).isNull()
        assertThat(YouTubeLinkParser.parseVideoLink("https://youtu.be/way_too_long_video_id")).isNull()
        assertThat(YouTubeLinkParser.parseVideoLink("https://www.youtube.com/watch?v=")).isNull()
    }

    @Test
    fun `plain text and empty input resolve to nothing`() {
        assertThat(YouTubeLinkParser.parseVideoLink(null)).isNull()
        assertThat(YouTubeLinkParser.parseVideoLink("")).isNull()
        assertThat(YouTubeLinkParser.parseVideoLink("just some notes I copied earlier")).isNull()
    }

    @Test
    fun `first resolvable link in a multi link text wins`() {
        val link =
            YouTubeLinkParser.parseVideoLink(
                "https://www.youtube.com/@channel and https://youtu.be/dQw4w9WgXcQ",
            )

        assertThat(link?.videoId).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `short link host with extra path still resolves the first segment`() {
        val link = YouTubeLinkParser.parseVideoLink("https://youtu.be/dQw4w9WgXcQ?t=30")

        assertThat(link?.videoId).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `timestamp link carries the start position`() {
        val link = YouTubeLinkParser.parseVideoLink("https://youtu.be/dQw4w9WgXcQ?t=252")

        assertThat(link?.startPositionMs).isEqualTo(252_000L)
    }

    @Test
    fun `watch link timestamp is read past the video id`() {
        val link =
            YouTubeLinkParser.parseVideoLink(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabcdefghij&t=90s",
            )

        assertThat(link?.startPositionMs).isEqualTo(90_000L)
    }

    @Test
    fun `link without a timestamp has no start position`() {
        val link = YouTubeLinkParser.parseVideoLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        assertThat(link?.startPositionMs).isNull()
    }

    @Test
    fun `duration style timestamps are converted to milliseconds`() {
        assertThat(YouTubeLinkParser.parseTimestamp("1h2m3s")).isEqualTo(3_723_000L)
        assertThat(YouTubeLinkParser.parseTimestamp("2m30s")).isEqualTo(150_000L)
        assertThat(YouTubeLinkParser.parseTimestamp("45s")).isEqualTo(45_000L)
        assertThat(YouTubeLinkParser.parseTimestamp("90")).isEqualTo(90_000L)
    }

    @Test
    fun `unparseable or empty timestamps are dropped`() {
        assertThat(YouTubeLinkParser.parseTimestamp("")).isNull()
        assertThat(YouTubeLinkParser.parseTimestamp("0")).isNull()
        assertThat(YouTubeLinkParser.parseTimestamp("0s")).isNull()
        assertThat(YouTubeLinkParser.parseTimestamp("later")).isNull()
        assertThat(YouTubeLinkParser.parseTimestamp("5x")).isNull()
    }

    @Test
    fun `a junk timestamp still opens the video from the beginning`() {
        val link = YouTubeLinkParser.parseVideoLink("https://youtu.be/dQw4w9WgXcQ?t=later")

        assertThat(link?.videoId).isEqualTo("dQw4w9WgXcQ")
        assertThat(link?.startPositionMs).isNull()
    }
}
