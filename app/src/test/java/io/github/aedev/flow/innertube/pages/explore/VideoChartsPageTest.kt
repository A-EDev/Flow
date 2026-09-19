package io.github.aedev.flow.innertube.pages.explore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VideoChartsPageTest {
    private fun chart(
        name: String,
        chartType: String = "TRENDING_VIDEOS",
        country: String = "US",
    ) = ExploreFixture(name).toVideoChartsPage(chartType, country)

    @Test
    fun `reads the trending videos chart`() {
        val page = chart(ExploreFixture.CHARTS_TRENDING_VIDEOS)

        assertThat(page.chartType).isEqualTo("TRENDING_VIDEOS")
        assertThat(page.country).isEqualTo("US")
        assertThat(page.entries).isNotEmpty()
    }

    @Test
    fun `duration arrives as whole seconds, not a length label`() {
        val first = chart(ExploreFixture.CHARTS_TRENDING_VIDEOS).entries.first()

        assertThat(first.duration).isGreaterThan(0)
    }

    @Test
    fun `the chart rank ships as a badge`() {
        val entries = chart(ExploreFixture.CHARTS_TRENDING_VIDEOS).entries

        assertThat(entries.first().badges).containsExactly("#1")
        assertThat(entries[1].badges).containsExactly("#2")
    }

    @Test
    fun `no view count is invented from a chart that carries none`() {
        assertThat(chart(ExploreFixture.CHARTS_TRENDING_VIDEOS).entries.all { it.viewCount == 0L }).isTrue()
    }

    @Test
    fun `the release date becomes the upload date and a timestamp`() {
        val first = chart(ExploreFixture.CHARTS_TRENDING_VIDEOS).entries.first()

        assertThat(first.uploadDate).matches("""\d{4}-\d{2}-\d{2}""")
        assertThat(first.timestamp).isGreaterThan(0L)
    }

    @Test
    fun `the channel is carried through`() {
        val first = chart(ExploreFixture.CHARTS_TRENDING_VIDEOS).entries.first()

        assertThat(first.channelName).isNotEmpty()
        assertThat(first.channelId).startsWith("UC")
        assertThat(first.thumbnailUrl).contains(first.id)
    }

    @Test
    fun `the movies chart reads the same way`() {
        val page = chart(ExploreFixture.CHARTS_TRENDING_MOVIES, chartType = "TRENDING_MOVIES")

        assertThat(page.entries).isNotEmpty()
        assertThat(page.entries.first().title).isNotEmpty()
    }

    @Test
    fun `an unsupported country's error body yields no entries instead of throwing`() {
        val page = chart(ExploreFixture.CHARTS_UNSUPPORTED_COUNTRY, country = "ZZ")

        assertThat(page.entries).isEmpty()
    }

    @Test
    fun `an unsupported region falls back rather than issuing a request that fails`() {
        assertThat(chartsCountryOrFallback("ZZ")).isEqualTo("US")
        assertThat(chartsCountryOrFallback("zz")).isEqualTo("US")
        assertThat(chartsCountryOrFallback("gb")).isEqualTo("GB")
        assertThat(chartsCountryOrFallback("IN")).isEqualTo("IN")
    }
}
