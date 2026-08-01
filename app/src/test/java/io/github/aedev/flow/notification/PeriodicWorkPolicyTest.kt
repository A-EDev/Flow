package io.github.aedev.flow.notification

import androidx.work.ExistingPeriodicWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodicWorkPolicyTest {
    @Test
    fun `startup scheduling keeps existing periodic work`() {
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, periodicWorkPolicy(reschedule = false))
    }

    @Test
    fun `explicit settings change updates existing periodic work`() {
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, periodicWorkPolicy(reschedule = true))
    }
}
