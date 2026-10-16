package io.github.aedev.flow.service

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaButtonStartTest {
    @Test
    fun `a media button start with nothing loaded is declined`() {
        assertThat(shouldDeclineMediaButtonStart(Intent.ACTION_MEDIA_BUTTON, hasMediaItem = false)).isTrue()
    }

    @Test
    fun `a media button start with something loaded gets the session`() {
        assertThat(shouldDeclineMediaButtonStart(Intent.ACTION_MEDIA_BUTTON, hasMediaItem = true)).isFalse()
    }

    @Test
    fun `an ordinary controller always gets the session`() {
        assertThat(shouldDeclineMediaButtonStart(null, hasMediaItem = false)).isFalse()
    }
}
