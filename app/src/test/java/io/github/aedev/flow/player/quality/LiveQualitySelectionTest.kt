package io.github.aedev.flow.player.quality

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.stream.VideoCodecUtils
import org.junit.Test

class LiveQualitySelectionTest {
    private val pick = LiveQualityPick(videoId = "live-1", height = 144)

    @Test
    fun `a manual pick survives a re-prepare of the same stream`() {
        assertThat(LiveQualitySelection.retainedPick("live-1", pick)).isEqualTo(pick)
        assertThat(LiveQualitySelection.targetHeight("live-1", pick, defaultHeight = 720)).isEqualTo(144)
    }

    @Test
    fun `a manual pick is dropped for a different stream`() {
        assertThat(LiveQualitySelection.retainedPick("live-2", pick)).isNull()
        assertThat(LiveQualitySelection.targetHeight("live-2", pick, defaultHeight = 720)).isEqualTo(720)
    }

    @Test
    fun `choosing auto by hand overrides the default quality on a re-prepare`() {
        val auto = LiveQualityPick(videoId = "live-1", height = 0)

        assertThat(LiveQualitySelection.targetHeight("live-1", auto, defaultHeight = 480)).isEqualTo(0)
    }

    @Test
    fun `with no pick the default quality applies`() {
        assertThat(LiveQualitySelection.targetHeight("live-1", null, defaultHeight = 480)).isEqualTo(480)
    }

    @Test
    fun `the target snaps to the highest offered class at or under it`() {
        val offered = listOf(1080, 720, 480, 360, 240, 144)

        assertThat(LiveQualitySelection.snapToOffered(500, offered)).isEqualTo(480)
        assertThat(LiveQualitySelection.snapToOffered(144, offered)).isEqualTo(144)
        assertThat(LiveQualitySelection.snapToOffered(100, offered)).isEqualTo(144)
        assertThat(LiveQualitySelection.snapToOffered(480, emptyList())).isNull()
    }

    @Test
    fun `a landscape class bounds the height`() {
        assertThat(LiveQualitySelection.maxVideoSize(144, portrait = false)).isEqualTo(Int.MAX_VALUE to 144)
    }

    @Test
    fun `a portrait class bounds the width, the frame's short side`() {
        assertThat(LiveQualitySelection.maxVideoSize(720, portrait = true)).isEqualTo(720 to Int.MAX_VALUE)
    }

    @Test
    fun `a portrait frame's class is its short side`() {
        assertThat(VideoCodecUtils.qualityClass(720, 1280)).isEqualTo(720)
        assertThat(VideoCodecUtils.qualityClass(1920, 1080)).isEqualTo(1080)
        assertThat(VideoCodecUtils.qualityClass(0, 480)).isEqualTo(480)
    }
}
