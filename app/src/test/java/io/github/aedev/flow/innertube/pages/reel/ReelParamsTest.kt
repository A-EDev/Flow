package io.github.aedev.flow.innertube.pages.reel

import org.junit.Assert.assertEquals
import org.junit.Test

class ReelParamsTest {
    @Test
    fun `seed token is the protobuf id, base64url without padding`() {
        assertEquals("CgtwSldxdlZuRFQwQQ", ReelParams.seedSequenceParams("pJWqvVnDT0A"))
    }

    @Test
    fun `initial sequence token is what youtube's own player sends first`() {
        assertEquals("CA8%3D", ReelParams.INITIAL_SEQUENCE)
    }

    @Test
    fun `search shorts filter is the shorts content type`() {
        assertEquals("EgIQCQ%3D%3D", ReelParams.SEARCH_SHORTS_FILTER)
    }
}
