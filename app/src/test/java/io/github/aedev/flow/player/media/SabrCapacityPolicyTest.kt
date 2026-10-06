package io.github.aedev.flow.player.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SabrCapacityPolicyTest {
    @Test
    fun `re-extracting the same failed format does not retry its capacity failure`() {
        val policy = SabrCapacityPolicy()
        policy.reject("video", 137)
        repeat(3) { assertThat(policy.allows("video", 137)).isFalse() }
    }

    @Test
    fun `a different quality remains available after a capacity failure`() {
        val policy = SabrCapacityPolicy()
        policy.reject("video", 137)
        assertThat(policy.allows("video", 136)).isTrue()
        policy.reject("video", 136)
        assertThat(policy.allows("video", 137)).isFalse()
        assertThat(policy.allows("video", 136)).isFalse()
    }

    @Test
    fun `another video starts with a fresh capacity policy`() {
        val policy = SabrCapacityPolicy()
        policy.reject("video", 137)
        assertThat(policy.allows("other", 137)).isTrue()
        assertThat(policy.allows("video", 137)).isTrue()
    }
}
