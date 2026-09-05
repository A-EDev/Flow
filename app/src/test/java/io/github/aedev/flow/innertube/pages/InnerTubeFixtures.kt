package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.response.BrowseResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
internal object InnerTubeFixtures {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun browse(name: String): BrowseResponse {
        val text =
            checkNotNull(InnerTubeFixtures::class.java.classLoader?.getResource("innertube/$name.json")) {
                "missing fixture $name"
            }.readText()
        return json.decodeFromString(BrowseResponse.serializer(), text)
    }
}
