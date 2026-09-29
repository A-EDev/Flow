package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class YouTubeTakeoutLikesParserTest {
    private val channelId = "UC${"b".repeat(22)}"

    private fun record(
        title: String,
        videoId: String,
        time: String,
        header: String = "YouTube",
        extra: String = "",
    ) = """
        {"header":"$header","title":"$title","titleUrl":"https://www.youtube.com/watch?v=$videoId",
         "subtitles":[{"name":"Some Channel","url":"https://www.youtube.com/channel/$channelId"}],
         "time":"$time","products":["YouTube"]$extra}
        """.trimIndent()

    private fun parse(
        vararg records: String,
        limit: Int = MAX_IMPORTED_LIKES,
    ) = readMyActivityLikes(records.joinToString(",", "[", "]").byteInputStream(), limit)

    @Test
    fun `likes are kept and other activity is ignored`() {
        val result =
            parse(
                record("Liked Guitar basics", "aaaaaaaaaaa", "2026-03-01T10:00:00.000Z"),
                record("Watched Guitar basics", "aaaaaaaaaaa", "2026-03-01T09:00:00.000Z"),
                record("Watched Cooking", "bbbbbbbbbbb", "2026-02-01T10:00:00Z"),
            )

        assertThat(result.likes)
            .containsExactly(
                TakeoutLike(
                    videoId = "aaaaaaaaaaa",
                    title = "Guitar basics",
                    channelName = "Some Channel",
                    channelId = channelId,
                    likedAt = Instant.parse("2026-03-01T10:00:00Z").toEpochMilli(),
                    isMusic = false,
                ),
            )
    }

    @Test
    fun `the latest like or dislike of a video decides`() {
        val result =
            parse(
                record("Disliked Changed my mind", "aaaaaaaaaaa", "2026-03-02T00:00:00Z"),
                record("Liked Changed my mind", "aaaaaaaaaaa", "2026-03-01T00:00:00Z"),
                record("Liked Still liked", "bbbbbbbbbbb", "2026-03-03T00:00:00Z"),
                record("Disliked Still liked", "bbbbbbbbbbb", "2026-01-01T00:00:00Z"),
            )

        assertThat(result.likes.map { it.videoId }).containsExactly("bbbbbbbbbbb")
    }

    @Test
    fun `ads and entries without a video are skipped`() {
        val result =
            parse(
                record("Liked Sponsored", "aaaaaaaaaaa", "2026-03-01T00:00:00Z", extra = ""","details":[{"name":"From Google Ads"}]"""),
                """{"header":"YouTube","title":"Liked a video that has been removed","time":"2026-03-01T00:00:00Z"}""",
            )

        assertThat(result.likes).isEmpty()
    }

    @Test
    fun `YouTube Music likes are marked as music`() {
        val result = parse(record("Liked A song", "aaaaaaaaaaa", "2026-03-01T00:00:00Z", header = "YouTube Music"))

        assertThat(result.likes.single().isMusic).isTrue()
    }

    @Test
    fun `only the newest likes are kept past the limit`() {
        val result =
            parse(
                record("Liked Old", "aaaaaaaaaaa", "2020-01-01T00:00:00Z"),
                record("Liked Newest", "bbbbbbbbbbb", "2026-01-01T00:00:00Z"),
                record("Liked Middle", "ccccccccccc", "2023-01-01T00:00:00Z"),
                limit = 2,
            )

        assertThat(result.likes.map { it.title }).containsExactly("Newest", "Middle").inOrder()
        assertThat(result.totalFound).isEqualTo(3)
    }

    @Test
    fun `a non-English export yields no likes`() {
        val result = parse(record("Mag ich: Gitarre", "aaaaaaaaaaa", "2026-03-01T00:00:00Z"))

        assertThat(result.likes).isEmpty()
    }

    @Test
    fun `a file that is not an activity array is rejected`() {
        assertThrows(SerializationException::class.java) {
            readMyActivityLikes("""{"subscriptions":[]}""".byteInputStream())
        }
    }

    @Test
    fun `my activity entry is the YouTube json of the export`() {
        assertThat(isMyActivityYouTubeEntry("Takeout/My Activity/YouTube/MyActivity.json")).isTrue()
        assertThat(isMyActivityYouTubeEntry("Takeout\\My Activity\\YouTube\\MyActivity.JSON")).isTrue()

        assertThat(isMyActivityYouTubeEntry("Takeout/My Activity/YouTube/MyActivity.html")).isFalse()
        assertThat(isMyActivityYouTubeEntry("Takeout/My Activity/Search/MyActivity.json")).isFalse()
        assertThat(isMyActivityYouTubeEntry("Takeout/YouTube and YouTube Music/history/watch-history.json")).isFalse()
        assertThat(isMyActivityYouTubeEntry("YouTube/MyActivity.json")).isFalse()
        assertThat(isMyActivityYouTubeEntry("Takeout/../YouTube/MyActivity.json")).isFalse()
    }
}
