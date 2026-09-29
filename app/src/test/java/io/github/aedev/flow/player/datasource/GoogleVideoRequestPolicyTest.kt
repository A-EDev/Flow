package io.github.aedev.flow.player.datasource

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.innertube.models.YouTubeClient
import org.junit.Test

class GoogleVideoRequestPolicyTest {
    @Test
    fun `the user agent matches the client that minted the url`() {
        assertThat(GoogleVideoRequestPolicy.userAgent("VISIONOS", "fallback")).isEqualTo(YouTubeClient.VISIONOS.userAgent)
        assertThat(GoogleVideoRequestPolicy.userAgent("android_vr", "fallback")).isEqualTo(YouTubeClient.ANDROID_VR_1_61_48.userAgent)
        assertThat(GoogleVideoRequestPolicy.userAgent("MWEB", "fallback")).isEqualTo(YouTubeClient.USER_AGENT_MWEB)
        assertThat(GoogleVideoRequestPolicy.userAgent(null, "fallback")).isEqualTo("fallback")
    }

    @Test
    fun `browser headers go only on urls a web client minted`() {
        assertThat(GoogleVideoRequestPolicy.headers("VISIONOS")).doesNotContainKey("Origin")
        assertThat(GoogleVideoRequestPolicy.headers("ANDROID_VR")).doesNotContainKey("Referer")
        assertThat(GoogleVideoRequestPolicy.headers("WEB")).containsEntry("Origin", "https://www.youtube.com")
        assertThat(GoogleVideoRequestPolicy.headers(null)).containsKey("Sec-Fetch-Mode")
        assertThat(GoogleVideoRequestPolicy.headers("VISIONOS")).containsEntry("Accept-Encoding", "identity")
    }
}
