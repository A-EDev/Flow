package io.github.aedev.flow.player.datasource

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FakeStreamWallTest {
    private val visionos = "https://rr1.googlevideo.com/videoplayback?c=VISIONOS&itag=140&clen=4151075&dur=256.280&expire=9999999999"

    private var switchedOn = true
    private var visitor: String? = "first"
    private val wall = FakeStreamWall(isSwitchedOn = { switchedOn }, currentVisitor = { visitor })

    @Test
    fun `the wall sits at sixty seconds of the file, read from clen and dur`() {
        assertThat(FakeStreamWall.isPastWall(visionos, 971_799L)).isFalse()
        assertThat(FakeStreamWall.isPastWall(visionos, 971_900L)).isTrue()
    }

    @Test
    fun `only the walled app clients are refused`() {
        val tizen = visionos.replace("c=VISIONOS", "c=TVHTML5")

        assertThat(FakeStreamWall.isPastWall(tizen, 3_000_000L)).isFalse()
        assertThat(FakeStreamWall.isPastWall(visionos.replace("c=VISIONOS", "c=ANDROID_VR"), 3_000_000L)).isTrue()
    }

    @Test
    fun `a video shorter than the wall is never refused`() {
        assertThat(FakeStreamWall.isPastWall(visionos.replace("dur=256.280", "dur=45.0"), 4_000_000L)).isFalse()
    }

    @Test
    fun `a fresh visitor is served past the wall`() {
        assertThat(wall.refuses(visionos, 3_000_000L)).isTrue()

        visitor = "second"

        assertThat(wall.refuses(visionos, 3_000_000L)).isFalse()
    }

    @Test
    fun `the walls-everyone switch refuses every client and every visitor past the wall`() {
        val everyone = FakeStreamWall(isSwitchedOn = { false }, currentVisitor = { "any" }, wallsEveryone = { true })
        val tizen = visionos.replace("c=VISIONOS", "c=TVHTML5")

        assertThat(everyone.refuses(tizen, 3_000_000L)).isTrue()
        assertThat(everyone.refuses(tizen, 0L)).isFalse()
    }

    @Test
    fun `switching off and on again walls whoever is current`() {
        wall.refuses(visionos, 0L)
        switchedOn = false
        assertThat(wall.refuses(visionos, 3_000_000L)).isFalse()

        visitor = "second"
        switchedOn = true

        assertThat(wall.refuses(visionos, 3_000_000L)).isTrue()
    }
}
