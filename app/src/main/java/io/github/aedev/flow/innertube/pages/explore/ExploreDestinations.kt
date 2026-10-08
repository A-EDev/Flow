package io.github.aedev.flow.innertube.pages.explore

/**
 * The surfaces that replaced the Trending page.
 *
 * `FEtrending`, `FEexplore` and `FEshorts` are HTTP 400 on every InnerTube client — WEB, MWEB,
 * ANDROID, IOS, TVHTML5 and WEB_REMIX, with and without the legacy tab tokens, at any `gl`
 * (probed 2026-09-18). Do not try to revive them with a client swap or a params token.
 *
 * charts.youtube.com, which served the music and movie charts, answers 429 to every keyless request
 * since 2026-10-08. Music reads YouTube Music's charts instead; movies have no anonymous source.
 *
 * Only the browse ids live here. Every shelf and tab token is read back out of the response that
 * carried it, so a token rotation cannot strand a destination.
 */
enum class ExploreDestination(
    val browseId: String,
    val kind: ExploreSectionKind,
) {
    LIVE("UC4R8DWoMoI7CAwX8_LjQHig", ExploreSectionKind.SHELVES),
    GAMING("UCOpNcN46UbXVtpKMrmU4Abg", ExploreSectionKind.GRID),
    MUSIC(MUSIC_CHARTS_BROWSE_ID, ExploreSectionKind.CHART),
    NEWS("FEnews_destination", ExploreSectionKind.SHELVES),
    SPORTS("UCEgdi0XIXXZ-qJOFPf4JSKw", ExploreSectionKind.SHELVES),
    LEARNING("UCtFRv9O2AHqOZjjynzrv-xg", ExploreSectionKind.SHELVES),
    FASHION("UCrpQ4p1Ql_hG8rKXIKM1MOQ", ExploreSectionKind.SHELVES),
    ;

    /**
     * The Gaming destination's landing page is a game-card carousel rather than videos, so its
     * Trending tab is browsed directly. It is the one token not present in a response Flow reads.
     */
    val params: String?
        get() = if (this == GAMING) GAMING_TRENDING_PARAMS else null
}

/** How a destination's first page is shaped, and therefore how the screen renders it. */
enum class ExploreSectionKind {
    /** A landing page of shelves; each shelf's `moreParams` opens a paginated grid. */
    SHELVES,

    /** A flat grid of videos. */
    GRID,

    /** A ranked, unpaginated chart read from YouTube Music's charts. */
    CHART,
}

internal const val MUSIC_CHARTS_BROWSE_ID = "FEmusic_charts"

private const val GAMING_TRENDING_PARAMS = "Egh0cmVuZGluZw%3D%3D"
