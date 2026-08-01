package io.github.aedev.flow.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CommentAvatarPolicyTest {
    @Test
    fun `embedded comment avatar remains preferred`() {
        val result =
            selectCommentAuthorThumbnail(
                embeddedAvatar = "https://yt3.ggpht.com/embedded=s32",
                resolvedChannelAvatar = "https://yt3.ggpht.com/channel=s32",
            )

        assertThat(result).isEqualTo("https://yt3.ggpht.com/embedded=s176")
    }

    @Test
    fun `resolved channel avatar fills missing embedded avatar`() {
        val result =
            selectCommentAuthorThumbnail(
                embeddedAvatar = "",
                resolvedChannelAvatar = "https://yt3.ggpht.com/channel=s32",
            )

        assertThat(result).isEqualTo("https://yt3.ggpht.com/channel=s176")
    }

    @Test
    fun `missing avatar sources remain empty for the UI fallback`() {
        val result = selectCommentAuthorThumbnail(null, null)

        assertThat(result).isEmpty()
    }
}
