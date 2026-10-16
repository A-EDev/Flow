package io.github.aedev.flow.ui

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.SharedLinkAction
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class IncomingLinksTest {
    private val video = "dQw4w9WgXcQ"
    private val watchLink = "https://www.youtube.com/watch?v=$video"

    @Test
    fun `a shared video is marked shared and a tapped one is not`() {
        val links = IncomingLinks()

        links.openLink(watchLink, isShared = true, isRestoring = false)
        assertThat(links.videoId.value).isEqualTo(video)
        assertThat(links.isShared.value).isTrue()

        links.consumeVideo()
        links.openLink(watchLink, isShared = false, isRestoring = false)
        assertThat(links.isShared.value).isFalse()
    }

    @Test
    fun `a recreated activity re-requests a tapped video but not a shared one`() {
        val tapped = IncomingLinks().apply { openLink(watchLink, isShared = false, isRestoring = true) }
        val shared = IncomingLinks().apply { openLink(watchLink, isShared = true, isRestoring = true) }

        assertThat(tapped.videoId.value).isEqualTo(video)
        assertThat(shared.videoId.value).isNull()
    }

    @Test
    fun `a shared short opens as a short and never asks`() {
        val links = IncomingLinks()

        links.openLink("https://youtube.com/shorts/$video", isShared = true, isRestoring = false)

        assertThat(links.isShort.value).isTrue()
        assertThat(links.isShared.value).isFalse()
    }

    @Test
    fun `a link with no page is reported and opens nothing`() {
        val links = IncomingLinks()

        assertThat(links.openLink("https://www.youtube.com/results?search_query=x", isShared = true, isRestoring = false)).isFalse()
        assertThat(links.videoId.value).isNull()
    }

    @Test
    fun `consuming a video clears its flags`() {
        val links = IncomingLinks().apply { openVideo(video, isShared = true) }

        links.consumeVideo()

        assertThat(links.videoId.value).isNull()
        assertThat(links.isShared.value).isFalse()
    }

    @Test
    fun `the setting decides whether a shared video asks first`() {
        assertThat(sharedLinkAsks(SharedLinkAction.PLAY, mediaLoaded = true)).isFalse()
        assertThat(sharedLinkAsks(SharedLinkAction.ASK_WHILE_PLAYING, mediaLoaded = true)).isTrue()
        assertThat(sharedLinkAsks(SharedLinkAction.ASK_WHILE_PLAYING, mediaLoaded = false)).isFalse()
        assertThat(sharedLinkAsks(SharedLinkAction.ALWAYS_ASK, mediaLoaded = false)).isTrue()
    }
}
