package io.github.aedev.flow.data.lyrics

class LyricsProviderRegistry(
    private val providerMap: Map<String, LyricsProvider>,
) {
    fun getOrderedProviders(orderString: String): List<LyricsProvider> =
        LyricsProviderOrder.resolve(orderString).mapNotNull { providerMap[it] }

    companion object {
        fun default(): LyricsProviderRegistry {
            val providers =
                listOf(
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
