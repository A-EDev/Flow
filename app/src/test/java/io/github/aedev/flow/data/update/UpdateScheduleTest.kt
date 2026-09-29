package io.github.aedev.flow.data.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateScheduleTest {
    private val now = 1_000_000_000L

    @Test
    fun `a check inside the cooldown is not due, one after it is`() {
        assertThat(UpdateSchedule.isDue(now - UpdateSchedule.COOLDOWN_MS + 1, now)).isFalse()
        assertThat(UpdateSchedule.isDue(now - UpdateSchedule.COOLDOWN_MS, now)).isTrue()
    }

    @Test
    fun `a clock moved backwards makes the check due`() {
        assertThat(UpdateSchedule.isDue(now + 1, now)).isTrue()
    }

    @Test
    fun `a skipped or already shown version is not announced again`() {
        assertThat(UpdateSchedule.mayAnnounce("2.3.0", skippedVersion = "2.3.0", announcedVersion = null)).isFalse()
        assertThat(UpdateSchedule.mayAnnounce("2.3.0", skippedVersion = null, announcedVersion = "2.3.0")).isFalse()
        assertThat(UpdateSchedule.mayAnnounce("2.3.1", skippedVersion = "2.3.0", announcedVersion = "2.3.0")).isTrue()
    }
}
