package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class YouTubeTakeoutActivityParserTest {
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

    private fun search(
        title: String,
        url: String,
        time: String,
        header: String = "YouTube",
    ) = """{"header":"$header","title":"$title","titleUrl":"$url","time":"$time","products":["YouTube"]}"""

    private fun activity(vararg records: String) = readTakeoutActivity(records.joinToString(",", "[", "]").byteInputStream())

    @Test
    fun `searches are read from the link, in any language`() {
        val result =
            activity(
                search("Hai cercato lofi beats", "https://www.youtube.com/results?search_query=lofi+beats", "2026-03-01T10:00:00Z"),
                search(
                    "Searched for jazz",
                    "https://music.youtube.com/search?q=late%20night%20jazz",
                    "2026-03-02T10:00:00Z",
                    "YouTube Music",
                ),
            )

        assertThat(result.searches)
            .containsExactly(
                TakeoutSearch("lofi beats", Instant.parse("2026-03-01T10:00:00Z").toEpochMilli(), isMusic = false),
                TakeoutSearch("late night jazz", Instant.parse("2026-03-02T10:00:00Z").toEpochMilli(), isMusic = true),
            ).inOrder()
        assertThat(result.likes.likes).isEmpty()
    }

    @Test
    fun `watches keep their own time and their music flag`() {
        val result =
            activity(
                record("Watched Cooking", "bbbbbbbbbbb", "2026-02-01T10:00:00Z"),
                record("Watched Song", "ccccccccccc", "2026-02-02T10:00:00Z", header = "YouTube Music"),
            )

        assertThat(result.watches.map { Triple(it.videoId, it.title, it.isMusic) })
            .containsExactly(Triple("bbbbbbbbbbb", "Cooking", false), Triple("ccccccccccc", "Song", true))
            .inOrder()
        assertThat(result.watches.first().watchedAt).isEqualTo(Instant.parse("2026-02-01T10:00:00Z").toEpochMilli())
    }

    @Test
    fun `a search link needs its query and a YouTube host`() {
        assertThat(takeoutSearchOf("https://www.youtube.com/results?search_query=")).isNull()
        assertThat(takeoutSearchOf("https://example.com/results?search_query=x")).isNull()
        assertThat(takeoutSearchOf("https://www.youtube.com/watch?v=aaaaaaaaaaa")).isNull()
        assertThat(takeoutSearchOf("https://m.youtube.com/results?sp=EgIQAQ&search_query=caf%C3%A9")).isEqualTo("café" to false)
    }
}
