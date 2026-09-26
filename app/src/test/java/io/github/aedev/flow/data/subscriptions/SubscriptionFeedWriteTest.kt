package io.github.aedev.flow.data.subscriptions

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

/** #1094: what a refresh writes back when some channels could not be read. */
class SubscriptionFeedWriteTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun video(
        id: String,
        channelId: String,
        ageHours: Long = 1L,
        duration: Int = 0,
    ) = Video(
        id = id,
        title = id,
        channelName = channelId,
        channelId = channelId,
        thumbnailUrl = "",
        duration = duration,
        viewCount = 0L,
        uploadDate = "",
        timestamp = now - ageHours * hour,
    )

    private fun write(
        plan: SubscriptionRefreshPlan,
        fresh: List<Video>,
        cached: List<Video>,
        failed: Set<String> = emptySet(),
        subscribed: Set<String> = setOf("UCa", "UCb", "UCc"),
    ) = subscriptionFeedWrite(
        plan = plan,
        freshVideos = fresh,
        cachedSlice = cached,
        failedChannelIds = failed,
        subscribedChannelIds = subscribed,
        now = now,
        windowMs = 60L * 24L * hour,
        maxItems = 1500,
    )

    private val fullPlan = SubscriptionRefreshPlan(listOf("UCa", "UCb"), isFullRefresh = true)

    @Test
    fun `a full refresh keeps the rows of a channel that failed`() {
        val result =
            write(
                plan = fullPlan,
                fresh = listOf(video("a2", "UCa")),
                cached = listOf(video("a1", "UCa", ageHours = 5), video("b1", "UCb", ageHours = 3)),
                failed = setOf("UCb"),
            )

        assertThat(result.rows.map { it.id }).containsExactly("a2", "b1")
    }

    @Test
    fun `a full refresh still drops stale rows of a channel that answered`() {
        val result =
            write(
                plan = fullPlan,
                fresh = listOf(video("a2", "UCa")),
                cached = listOf(video("a1", "UCa", ageHours = 5)),
            )

        assertThat(result.rows.map { it.id }).containsExactly("a2")
    }

    @Test
    fun `a failed channel is not stamped fresh`() {
        val result = write(plan = fullPlan, fresh = emptyList(), cached = emptyList(), failed = setOf("UCb"))

        assertThat(result.fetchedChannelIds).containsExactly("UCa")
    }

    @Test
    fun `an incremental refresh keeps the fetched channels' earlier rows`() {
        val result =
            write(
                plan = SubscriptionRefreshPlan(listOf("UCa"), isFullRefresh = false),
                fresh = listOf(video("a2", "UCa")),
                cached = listOf(video("a1", "UCa", ageHours = 5)),
            )

        assertThat(result.rows.map { it.id }).containsExactly("a2", "a1").inOrder()
        assertThat(result.fetchedChannelIds).containsExactly("UCa")
    }

    @Test
    fun `a channel unsubscribed during the refresh is not written back`() {
        val result =
            write(
                plan = fullPlan,
                fresh = listOf(video("a2", "UCa"), video("b2", "UCb")),
                cached = emptyList(),
                subscribed = setOf("UCa"),
            )

        assertThat(result.rows.map { it.id }).containsExactly("a2")
    }

    @Test
    fun `an enriched duration survives the refresh that re-reads the row from RSS`() {
        val result =
            write(
                plan = fullPlan,
                fresh = listOf(video("a1", "UCa")),
                cached = listOf(video("a1", "UCa", duration = 321)),
            )

        assertThat(result.rows.single().duration).isEqualTo(321)
    }
}
