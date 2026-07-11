package io.github.aedev.flow.data.shorts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShortsFeedOrderingTest {
    @Test
    fun `subscription diversity preserves rank while opening discovery slots`() {
        val ranked = listOf("s1", "s2", "s3", "d1", "d2", "d3")

        val result = diversifySubscriptions(ranked, isSubscribed = { it.startsWith("s") })

        assertThat(result).containsExactly("s1", "d1", "d2", "s2", "d3", "s3").inOrder()
    }

    @Test
    fun `discovery merge never moves the playing item or consumed prefix`() {
        val current = listOf("old1", "playing", "old2", "old3")

        val result = mergeDiscoveryCandidates(current, listOf("new1", "new2"), 1) { it }

        assertThat(result).containsExactly(
            "old1", "playing", "new1", "old2", "new2", "old3"
        ).inOrder()
    }
}
