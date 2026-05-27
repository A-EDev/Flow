package io.github.aedev.flow.data.lyrics

class LyricsProviderRegistry(
    private val providerMap: Map<String, LyricsProvider>
) {
    val providerNames: List<String> = providerMap.keys.toList()

    fun getProviderByName(name: String): LyricsProvider? = providerMap[name]

    fun deserializeProviderOrder(orderString: String): List<String> {
        if (orderString.isBlank()) return getDefaultProviderOrder()
        return orderString.split(",").map { it.trim() }.filter { it in providerNames }
    }

    fun serializeProviderOrder(providers: List<String>): String {
        return providers.filter { it in providerNames }.joinToString(",")
    }

    fun getDefaultProviderOrder(): List<String> = listOf(
        "BetterLyrics",
        "LyricsPlus",
        "SimpMusic",
        "KuGou",
        "Paxsenix",
        "LrcLib",
        "YouTubeSubtitle",
        "YouTube",
    ).filter { it in providerNames }

    fun getOrderedProviders(orderString: String): List<LyricsProvider> {
        val order = deserializeProviderOrder(orderString)
        val ordered = order.mapNotNull { getProviderByName(it) }
        val missing = providerMap.values.filter { p -> ordered.none { it.name == p.name } }
        return ordered + missing
    }

    companion object {
        fun default(): LyricsProviderRegistry {
            val providers = listOf(
                BetterLyricsProvider(),
                LyricsPlusProvider(),
                SimpMusicLyricsProvider(),
                KuGouLyricsProvider(),
                PaxsenixLyricsProvider(),
                LrcLibLyricsProvider(),
                YouTubeSubtitleLyricsProvider(),
                YouTubeLyricsProvider(),
            )
            return LyricsProviderRegistry(providers.associateBy { it.name })
        }
    }
}
