package io.github.aedev.flow.data.sponsordetection

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SponsorModelReadinessTest {
    @Test
    fun `readiness changes only when enablement or installation changes`() =
        runTest {
            val enabled = MutableStateFlow(false)
            val online = MutableStateFlow(false)
            val model = MutableStateFlow<SponsorModelState>(SponsorModelState.NotInstalled)
            val values = mutableListOf<SponsorPlaybackSettings>()
            val collection =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    sponsorPlaybackSettings(online, enabled, model).toList(values)
                }

            enabled.value = true
            model.value = SponsorModelState.Downloading(20, 100)
            model.value = SponsorModelState.Installed(100)
            model.value = SponsorModelState.Installed(101)
            online.value = true

            assertThat(values.map { Triple(it.onlineEnabled, it.onDevice.enabled, it.onDevice.active) })
                .containsExactly(
                    Triple(false, false, false),
                    Triple(false, true, false),
                    Triple(false, true, true),
                    Triple(true, true, true),
                ).inOrder()
            assertThat(values.last().handlerEnabled).isTrue()
            collection.cancel()
        }

    @Test
    fun `model readiness retries only the current skipped unavailable VOD`() {
        val readiness = SponsorModelReadiness(enabled = true, installed = true)
        val skipped =
            SponsorDetectionUiState(
                videoId = "video",
                status = SponsorDetectionStatus.SKIPPED,
                errorMessage = ON_DEVICE_UNAVAILABLE_MESSAGE,
            )

        assertThat(shouldRetrySkippedSponsorEvaluation("video", "video", false, readiness, skipped)).isTrue()
        assertThat(shouldRetrySkippedSponsorEvaluation("old", "video", false, readiness, skipped)).isFalse()
        assertThat(shouldRetrySkippedSponsorEvaluation("video", "video", true, readiness, skipped)).isFalse()
        assertThat(
            shouldRetrySkippedSponsorEvaluation(
                "video",
                "video",
                false,
                readiness,
                skipped.copy(errorMessage = "No usable English captions"),
            ),
        ).isFalse()
    }
}
