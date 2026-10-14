package io.github.aedev.flow.data.scrobble

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ListenBrainzClientTest {
    private val entry = ScrobbleEntry("Artist", "Song", "Album", 200, 1_700_000_000, fromYouTube = false)

    @Test
    fun `now playing carries no timestamp`() {
        val listen = listensJson("playing_now", listOf(entry))["payload"]!!.jsonArray.single().jsonObject

        assertThat(listen).doesNotContainKey("listened_at")
        assertThat(listen["track_metadata"]!!.jsonObject["artist_name"]!!.jsonPrimitive.content).isEqualTo("Artist")
    }

    @Test
    fun `a finished listen has its time and leaves out the service for device files`() {
        val listen = listensJson("single", listOf(entry))["payload"]!!.jsonArray.single().jsonObject
        val info = listen["track_metadata"]!!.jsonObject["additional_info"]!!.jsonObject

        assertThat(listen["listened_at"]!!.jsonPrimitive.content).isEqualTo("1700000000")
        assertThat(info["duration_ms"]!!.jsonPrimitive.content).isEqualTo("200000")
        assertThat(info).doesNotContainKey("music_service")
    }
}
