package io.github.aedev.flow.ui

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class MediaNavigationTest {
    @Test
    fun routesEncodeTheIdAndRejectBlankOnes() {
        assertEquals("artist/UCabc", musicArtistRoute(" UCabc "))
        assertEquals("musicPlaylist/MPREb_x%2Fy", musicCollectionRoute("MPREb_x/y"))
        assertNull(musicArtistRoute(" "))
        assertNull(musicCollectionRoute(""))
    }
}
