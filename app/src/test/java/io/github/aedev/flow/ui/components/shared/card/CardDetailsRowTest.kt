package io.github.aedev.flow.ui.components.shared.card

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Widths in dp at density 1: a 48 dp avatar target, a 48 dp menu, 8 dp gaps and an 80 dp title floor. */
class CardDetailsRowTest {
    private fun showsAvatar(rowWidth: Int) =
        showsAvatarBesideTitle(rowWidth, avatarWidth = 48, actionsWidth = 48, gap = 8, minTitleWidth = 80)

    @Test
    fun `a single card across a phone keeps its avatar`() {
        assertThat(showsAvatar(395)).isTrue()
    }

    @Test
    fun `two or three cards across a phone leave the avatar out`() {
        assertThat(showsAvatar(173)).isFalse()
        assertThat(showsAvatar(105)).isFalse()
    }

    @Test
    fun `tablet grid cards keep their avatar`() {
        assertThat(showsAvatar(240)).isTrue()
    }

    @Test
    fun `the avatar shows from the first width that leaves the title its floor`() {
        assertThat(showsAvatar(191)).isFalse()
        assertThat(showsAvatar(192)).isTrue()
    }

    @Test
    fun `a card without an avatar never asks for one`() {
        assertThat(showsAvatarBesideTitle(400, avatarWidth = 0, actionsWidth = 48, gap = 12, minTitleWidth = 80)).isFalse()
    }
}
