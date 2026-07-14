package io.github.aedev.flow.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VideoServiceForegroundStartupPolicyTest {
    @Test
    fun `a service with a media session still promotes immediately`() {
        val plan = videoServiceForegroundStartupPlan(hasMediaSession = true)

        assertThat(plan.promoteImmediately).isTrue()
        assertThat(plan.stopAfterPromotion).isFalse()
    }

    @Test
    fun `a service without a media session promotes before stopping`() {
        val plan = videoServiceForegroundStartupPlan(hasMediaSession = false)

        assertThat(plan.promoteImmediately).isTrue()
        assertThat(plan.stopAfterPromotion).isTrue()
    }
}
