package io.github.aedev.flow.ui.tv.input

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TvPlayerKeyMapperTest {
    @Test
    fun `maps dedicated media keys`() {
        assertThat(TvPlayerKeyMapper.map(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)).isEqualTo(TvPlayerAction.TOGGLE_PLAYBACK)
        assertThat(TvPlayerKeyMapper.map(KeyEvent.KEYCODE_MEDIA_PLAY)).isEqualTo(TvPlayerAction.PLAY)
        assertThat(TvPlayerKeyMapper.map(KeyEvent.KEYCODE_MEDIA_PAUSE)).isEqualTo(TvPlayerAction.PAUSE)
        assertThat(TvPlayerKeyMapper.map(KeyEvent.KEYCODE_MEDIA_REWIND)).isEqualTo(TvPlayerAction.SEEK_BACK)
        assertThat(TvPlayerKeyMapper.map(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)).isEqualTo(TvPlayerAction.SEEK_FORWARD)
    }

    @Test
    fun `does not consume dpad navigation keys`() {
        assertThat(TvPlayerKeyMapper.map(KeyEvent.KEYCODE_DPAD_CENTER)).isNull()
        assertThat(TvPlayerKeyMapper.map(KeyEvent.KEYCODE_DPAD_LEFT)).isNull()
        assertThat(TvPlayerKeyMapper.map(KeyEvent.KEYCODE_DPAD_RIGHT)).isNull()
        assertThat(TvPlayerKeyMapper.map(KeyEvent.KEYCODE_DPAD_UP)).isNull()
        assertThat(TvPlayerKeyMapper.map(KeyEvent.KEYCODE_DPAD_DOWN)).isNull()
    }
}
