package io.github.aedev.flow.innertube.pages.explore

/**
 * The surfaces that replaced the Trending page.
 *
 * `FEtrending`, `FEexplore` and `FEshorts` are HTTP 400 on every InnerTube client — WEB, MWEB,
 * ANDROID, IOS, TVHTML5 and WEB_REMIX, with and without the legacy tab tokens, at any `gl`
 * (probed 2026-09-18). Do not try to revive them with a client swap or a params token.
 *
 * Only the browse ids live here. Every shelf and tab token is read back out of the response that
 * carried it, so a token rotation cannot strand a destination.
 */
enum class ExploreDestination(
    val browseId: String,
    val kind: ExploreSectionKind,
    val chartType: String? = null,
) {
    LIVE("UC4R8DWoMoI7CAwX8_LjQHig", ExploreSectionKind.SHELVES),
    GAMING("UCOpNcN46UbXVtpKMrmU4Abg", ExploreSectionKind.GRID),
    MUSIC(CHARTS_BROWSE_ID, ExploreSectionKind.CHART, chartType = "TRENDING_VIDEOS"),
    MOVIES(CHARTS_BROWSE_ID, ExploreSectionKind.CHART, chartType = "TRENDING_MOVIES"),
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

    /** A ranked, unpaginated chart from the analytics host. */
    CHART,
}

internal const val CHARTS_BROWSE_ID = "FEmusic_analytics_charts_home"

private const val GAMING_TRENDING_PARAMS = "Egh0cmVuZGluZw%3D%3D"

/**
 * The countries YouTube Charts serves. An unsupported code is an HTTP 400, not an empty chart, so
 * the region is validated before the request rather than after it.
 */
val CHARTS_SUPPORTED_COUNTRIES: Set<String> =
    (
        "AE AR AT AU BE BO BR CA CH CL CO CR CZ DE DK DO EC EE EG ES FI FR GB GT HN HU ID IE " +
            "IL IN IS IT JP KE KR LU MX NG NI NL NO NZ PA PE PL PT PY RO RS RU SA SE SV TR TZ " +
            "UA UG US UY ZA ZW"
    ).split(" ").toSet()

/** Falls back to the chart's widest catalogue rather than issuing a request that 400s. */
fun chartsCountryOrFallback(region: String): String = region.uppercase().takeIf { it in CHARTS_SUPPORTED_COUNTRIES } ?: "US"
