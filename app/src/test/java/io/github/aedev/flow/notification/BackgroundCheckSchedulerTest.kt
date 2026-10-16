package io.github.aedev.flow.notification

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BackgroundCheckSchedulerTest {
    @Test
    fun `the subscription check runs only while new video notifications can be posted`() {
        assertThat(BackgroundCheckScheduler.subscriptionCheckWanted(notificationsEnabled = true, newVideosEnabled = true)).isTrue()
        assertThat(BackgroundCheckScheduler.subscriptionCheckWanted(notificationsEnabled = true, newVideosEnabled = false)).isFalse()
        assertThat(BackgroundCheckScheduler.subscriptionCheckWanted(notificationsEnabled = false, newVideosEnabled = true)).isFalse()
    }

    @Test
    fun `the update check runs only while update notifications can be posted`() {
        assertThat(BackgroundCheckScheduler.updateCheckWanted(notificationsEnabled = true, updatesEnabled = true)).isTrue()
        assertThat(BackgroundCheckScheduler.updateCheckWanted(notificationsEnabled = true, updatesEnabled = false)).isFalse()
        assertThat(BackgroundCheckScheduler.updateCheckWanted(notificationsEnabled = false, updatesEnabled = true)).isFalse()
    }
}
