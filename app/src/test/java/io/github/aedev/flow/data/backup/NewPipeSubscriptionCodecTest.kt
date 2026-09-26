package io.github.aedev.flow.data.backup

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.ChannelSubscription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test

class NewPipeSubscriptionCodecTest {
    /** The shape NewPipe's importer decodes, with its strict default [Json]: no unknown or missing keys. */
    @Serializable
    private data class NewPipeSubscriptionData(
        @SerialName("app_version") val appVersion: String,
        @SerialName("app_version_int") val appVersionInt: Int,
        val subscriptions: List<NewPipeSubscriptionItem>,
    )

    @Serializable
    private data class NewPipeSubscriptionItem(
        @SerialName("service_id") val serviceId: Int,
        val url: String,
        val name: String,
    )

    private val ucId = "UCuAXFkgsw1L7xaCfnd5JJOw"

    private fun encode(vararg subscriptions: ChannelSubscription) =
        NewPipeSubscriptionCodec.encode(subscriptions.toList(), appVersion = "1.2.3", appVersionInt = 123)

    @Test
    fun `NewPipe's strict model reads Flow's export`() {
        val export =
            encode(
                ChannelSubscription(ucId, "Rick | Astley \"official\"", "https://t"),
                ChannelSubscription("@lofigirl", "Lofi Girl", ""),
            )

        val data = Json.decodeFromString(NewPipeSubscriptionData.serializer(), export.json)

        assertThat(data.appVersion).isEqualTo("1.2.3")
        assertThat(data.appVersionInt).isEqualTo(123)
        assertThat(data.subscriptions)
            .containsExactly(
                NewPipeSubscriptionItem(0, "https://www.youtube.com/channel/$ucId", "Rick | Astley \"official\""),
                NewPipeSubscriptionItem(0, "https://www.youtube.com/@lofigirl", "Lofi Girl"),
            ).inOrder()
    }

    @Test
    fun `ids NewPipe cannot open are left out and counted`() {
        val export = encode(ChannelSubscription(ucId, "Kept", ""), ChannelSubscription("someusername", "Legacy", ""))

        assertThat(export.skipped).isEqualTo(1)
        assertThat(Json.decodeFromString(NewPipeSubscriptionData.serializer(), export.json).subscriptions).hasSize(1)
    }

    @Test
    fun `an empty library still writes a valid export`() {
        val data = Json.decodeFromString(NewPipeSubscriptionData.serializer(), encode().json)

        assertThat(data.subscriptions).isEmpty()
    }

    @Test
    fun `an export reads back as the same channels`() {
        val export = encode(ChannelSubscription(ucId, "A", ""), ChannelSubscription("@handle.with-dots_", "B", ""))

        assertThat(NewPipeSubscriptionCodec.decode(export.json).getOrThrow())
            .containsExactly(
                NewPipeSubscriptionEntry(NewPipeChannelRef.Id(ucId), "A"),
                NewPipeSubscriptionEntry(NewPipeChannelRef.Handle("handle.with-dots_"), "B"),
            ).inOrder()
    }

    @Test
    fun `every channel link form is understood`() {
        val json =
            """
            {"app_version":"0.27.0","app_version_int":1000,"subscriptions":[
              {"service_id":0,"url":"https://www.youtube.com/channel/$ucId","name":"Id"},
              {"service_id":0,"url":"https://m.youtube.com/@Handle?si=x","name":"Handle"},
              {"service_id":0,"url":"https://www.youtube.com/user/OldName","name":"User"},
              {"service_id":0,"url":"https://youtube.com/c/Custom/videos","name":"Custom"},
              {"service_id":0,"url":"https://music.youtube.com/channel/$ucId","name":"Duplicate"},
              {"service_id":1,"url":"https://soundcloud.com/someone","name":"Other service"},
              {"service_id":0,"url":"https://example.com/channel/$ucId","name":"Other host"},
              {"service_id":0,"url":"https://www.youtube.com/watch?v=dQw4w9WgXcQ","name":"Video"}
            ]}
            """.trimIndent()

        assertThat(NewPipeSubscriptionCodec.decode(json).getOrThrow())
            .containsExactly(
                NewPipeSubscriptionEntry(NewPipeChannelRef.Id(ucId), "Id"),
                NewPipeSubscriptionEntry(NewPipeChannelRef.Handle("Handle"), "Handle"),
                NewPipeSubscriptionEntry(NewPipeChannelRef.Legacy("https://www.youtube.com/user/OldName"), "User"),
                NewPipeSubscriptionEntry(NewPipeChannelRef.Legacy("https://www.youtube.com/c/Custom"), "Custom"),
            ).inOrder()
    }

    @Test
    fun `an export with no subscriptions is an empty success`() {
        assertThat(NewPipeSubscriptionCodec.decode("""{"subscriptions":[]}""").getOrThrow()).isEmpty()
    }

    @Test
    fun `a file that is not an export fails instead of importing nothing`() {
        listOf("""{"app_version":"1"}""", "not json", """{"subscriptions":{}}""", "").forEach { json ->
            assertThat(NewPipeSubscriptionCodec.decode(json).isFailure).isTrue()
        }
    }
}
