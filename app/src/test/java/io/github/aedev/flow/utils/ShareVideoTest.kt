package io.github.aedev.flow.utils

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * Pins the share paths every video, short and song affordance goes through, so none can drift back
 * to a payload that ignores the "share without text" preference.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ShareVideoTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun payload(intent: Intent): Intent = intent.getParcelableExtra(Intent.EXTRA_INTENT)!!

    @Test
    fun `a watch url has no timestamp until one is asked for`() {
        assertThat(youtubeWatchUrl("abc123")).isEqualTo("https://www.youtube.com/watch?v=abc123")
    }

    @Test
    fun `a watch url carries the requested position in seconds`() {
        assertThat(youtubeWatchUrl("abc123", 95L)).isEqualTo("https://www.youtube.com/watch?v=abc123&t=95s")
        assertThat(youtubeWatchUrl("abc123", 0L)).isEqualTo("https://www.youtube.com/watch?v=abc123&t=0s")
    }

    @Test
    fun `share without text sends the bare link`() {
        val sent = payload(shareVideoIntent(context, "abc123", "A title", linkOnly = true))

        assertThat(sent.getStringExtra(Intent.EXTRA_TEXT))
            .isEqualTo(context.getString(R.string.share_link_only_template, "abc123"))
        assertThat(sent.getStringExtra(Intent.EXTRA_TEXT)).doesNotContain("A title")
    }

    @Test
    fun `share with text introduces the video by name`() {
        val sent = payload(shareVideoIntent(context, "abc123", "A title", linkOnly = false))

        assertThat(sent.getStringExtra(Intent.EXTRA_TEXT))
            .isEqualTo(context.getString(R.string.check_out_video_template, "A title", "abc123"))
        assertThat(sent.getStringExtra(Intent.EXTRA_TEXT)).contains("A title")
    }

    @Test
    fun `the title rides as the subject only when text is shared`() {
        val withText = payload(shareVideoIntent(context, "abc123", "A title", linkOnly = false))
        val linkOnly = payload(shareVideoIntent(context, "abc123", "A title", linkOnly = true))

        listOf(withText, linkOnly).forEach { sent ->
            assertThat(sent.action).isEqualTo(Intent.ACTION_SEND)
            assertThat(sent.type).isEqualTo("text/plain")
        }
        assertThat(withText.getStringExtra(Intent.EXTRA_SUBJECT)).isEqualTo("A title")
        assertThat(linkOnly.hasExtra(Intent.EXTRA_SUBJECT)).isFalse()
    }

    @Test
    fun `a short is shared as a shorts link`() {
        val linkOnly = payload(shareVideoIntent(context, "abc123", "A title", linkOnly = true, isShort = true))
        val withText = payload(shareVideoIntent(context, "abc123", "A title", linkOnly = false, isShort = true))

        assertThat(linkOnly.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo("https://youtube.com/shorts/abc123")
        assertThat(withText.getStringExtra(Intent.EXTRA_TEXT))
            .isEqualTo(context.getString(R.string.check_out_short_template, "A title", "abc123"))
        assertThat(withText.getStringExtra(Intent.EXTRA_TEXT)).contains("https://youtube.com/shorts/abc123")
    }

    @Test
    fun `a song shared without text is the bare youtube music link`() {
        val sent = payload(shareSongIntent(context, "abc123", "A song", "An artist", linkOnly = true))

        assertThat(sent.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo("https://music.youtube.com/watch?v=abc123")
        assertThat(sent.hasExtra(Intent.EXTRA_SUBJECT)).isFalse()
    }

    @Test
    fun `a song shared with text names the song and artist`() {
        val sent = payload(shareSongIntent(context, "abc123", "A song", "An artist", linkOnly = false))

        assertThat(sent.getStringExtra(Intent.EXTRA_TEXT))
            .isEqualTo(context.getString(R.string.share_message_template, "A song", "An artist", "abc123"))
        assertThat(sent.getStringExtra(Intent.EXTRA_TEXT)).contains("https://music.youtube.com/watch?v=abc123")
        assertThat(sent.getStringExtra(Intent.EXTRA_SUBJECT)).isEqualTo("A song")
    }
}
