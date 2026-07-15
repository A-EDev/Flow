package io.github.aedev.flow.innertube.models.response

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelVideosResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reads total video count from channel header metadata`() {
        val response = json.decodeFromString<ChannelVideosResponse>(
            """
            {
              "header": {
                "pageHeaderRenderer": {
                  "content": {
                    "pageHeaderViewModel": {
                      "metadata": {
                        "contentMetadataViewModel": {
                          "metadataRows": [
                            { "metadataParts": [{ "text": { "content": "@channel" } }] },
                            {
                              "metadataParts": [
                                { "text": { "content": "2M subscribers" } },
                                { "text": { "content": "1.8K videos" } }
                              ]
                            }
                          ]
                        }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("1.8K videos", response.channelVideoCountText())
    }
}
