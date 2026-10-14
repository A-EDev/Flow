package io.github.aedev.flow.ui.startup

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LaunchPromptsTest {
    @Test
    fun `the donation prompt waits for the update check and stands down when the update page opened`() =
        runTest {
            val prompts = LaunchPrompts()
            val allowed = async { prompts.donationMayShow() }
            runCurrent()
            assertThat(allowed.isCompleted).isFalse()

            prompts.updateCheckFinished(shownUpdate = true)

            assertThat(allowed.await()).isFalse()
        }

    @Test
    fun `no update found lets the donation prompt show`() =
        runTest {
            val prompts = LaunchPrompts()
            prompts.updateCheckFinished(shownUpdate = false)

            assertThat(prompts.donationMayShow()).isTrue()
        }

    @Test
    fun `a check that never finishes does not block the donation prompt forever`() =
        runTest {
            val prompts = LaunchPrompts()
            val allowed = async { prompts.donationMayShow(waitMs = 1_000) }
            advanceTimeBy(1_001)

            assertThat(allowed.await()).isTrue()
        }
}
