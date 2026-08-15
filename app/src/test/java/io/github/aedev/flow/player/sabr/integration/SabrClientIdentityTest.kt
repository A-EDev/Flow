package io.github.aedev.flow.player.sabr.integration

import io.github.aedev.flow.innertube.models.YouTubeClient
import org.junit.Assert.assertEquals
import org.junit.Test

class SabrClientIdentityTest {
    @Test
    fun theStreamerContextClientNameIsTheNumericInnerTubeId() {
        assertEquals(1, SabrClientIdentity.clientNameId(YouTubeClient.WEB))
        assertEquals(2, SabrClientIdentity.clientNameId(YouTubeClient.MWEB))
    }

    @Test
    fun aSessionResolvesBackToTheClientThatOpenedIt() {
        listOf(YouTubeClient.WEB, YouTubeClient.MWEB).forEach { client ->
            val roundTripped = SabrClientIdentity.sabrClientFor(SabrClientIdentity.clientNameId(client))

            assertEquals(client.clientName, roundTripped.clientName)
            assertEquals(client.clientVersion, roundTripped.clientVersion)
        }
    }

    @Test
    fun anUnrecognisedSessionFallsBackToWeb() {
        assertEquals(YouTubeClient.WEB.clientName, SabrClientIdentity.sabrClientFor(0).clientName)
    }

    /**
     * A default-constructed [SabrStreamInfo] still has to describe WEB: it is what every
     * construction site produced before the identity fields existed.
     */
    @Test
    fun streamInfoDefaultsDescribeWeb() {
        val info =
            SabrStreamInfo(
                streamingUrl = "https://example.invalid/videoplayback",
                audioItag = 251,
                audioLmt = 0L,
                videoItag = 299,
                videoLmt = 0L,
                durationMs = 1_000L,
            )

        assertEquals(SabrClientIdentity.WEB_CLIENT_NAME_ID, info.clientNameId)
        assertEquals(YouTubeClient.WEB.clientName, SabrClientIdentity.sabrClientFor(info.clientNameId).clientName)
    }
}
