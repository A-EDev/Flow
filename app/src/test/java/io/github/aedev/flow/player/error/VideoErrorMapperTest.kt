package io.github.aedev.flow.player.error

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import io.github.aedev.flow.player.stream.PlaybackBlock
import io.github.aedev.flow.player.stream.PlaybackBlockedException
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class VideoErrorMapperTest {
    private val context =
        mockk<Context> {
            every { getString(any()) } answers { "string:${firstArg<Int>()}" }
        }

    @Test
    fun `a bot wall names the network instead of an unknown error`() {
        val error = VideoErrorMapper.from(context, PlaybackBlockedException(PlaybackBlock.BOT_WALL))

        assertThat(error.message).isEqualTo("string:${R.string.error_bot_wall}")
        assertThat(error.hint).isEqualTo("string:${R.string.error_bot_wall_hint}")
        assertThat(error.isRetryable).isTrue()
    }

    @Test
    fun `a sign-in wall says an account is needed`() {
        val error = VideoErrorMapper.from(context, PlaybackBlockedException(PlaybackBlock.LOGIN_REQUIRED))

        assertThat(error.message).isEqualTo("string:${R.string.error_login_required}")
        assertThat(error.hint).isEqualTo("string:${R.string.error_login_required_hint}")
    }

    @Test
    fun `a failure with no cause stays generic`() {
        assertThat(VideoErrorMapper.from(context, null).message).isEqualTo("string:${R.string.error_generic}")
    }
}
