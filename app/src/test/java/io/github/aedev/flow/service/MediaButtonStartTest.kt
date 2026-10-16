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

    @Test
    fun `a play press with a saved queue gets the session so resumption can start it`() {
        assertThat(shouldDeclineMediaButtonStart(Intent.ACTION_MEDIA_BUTTON, hasMediaItem = false, resumesOnPlay = true)).isFalse()
    }

    @Test
    fun `only the keys that begin playback can resume`() {
        assertThat(isPlayKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY)).isTrue()
        assertThat(isPlayKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)).isTrue()
        assertThat(isPlayKey(android.view.KeyEvent.KEYCODE_HEADSETHOOK)).isTrue()
        assertThat(isPlayKey(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)).isFalse()
        assertThat(isPlayKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)).isFalse()
        assertThat(isPlayKey(null)).isFalse()
    }
}
