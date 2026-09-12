package io.github.aedev.flow.data.repository

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.SponsorBlockCategories
import org.junit.Test

class SponsorBlockRepositoryTest {
    @Test
    fun `skipSegments request includes settings categories the API used to omit`() {
        val url = SponsorBlockRepository.skipSegmentsUrl("dQw4w9wgGcQ")
        val categories = url.queryParameter("categories").orEmpty()
        val actionTypes = url.queryParameter("actionTypes").orEmpty()

        assertThat(url.queryParameter("videoID")).isEqualTo("dQw4w9wgGcQ")
        SponsorBlockCategories.ALL.forEach { category ->
            assertThat(categories).contains(category)
        }
        SponsorBlockCategories.FETCH_ACTION_TYPES.forEach { actionType ->
            assertThat(actionTypes).contains(actionType)
        }
    }

    @Test
    fun `not found is the SponsorBlock no-segments response`() {
        assertThat(sponsorBlockFetchOutcomeForStatus(404)).isEqualTo(SponsorBlockFetchResult.Empty)
        assertThat(sponsorBlockFetchOutcomeForStatus(503)).isEqualTo(SponsorBlockFetchResult.HttpFailure(503))
    }
}
