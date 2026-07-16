package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WatchedVideoPreferenceMigrationTest {

    @Test
    fun `split preference inherits enabled legacy value when unset`() {
        assertThat(
            resolveMigratedHideWatchedPreference(splitValue = null, legacyValue = true)
        ).isTrue()
    }

    @Test
    fun `split preference inherits disabled legacy value when unset`() {
        assertThat(
            resolveMigratedHideWatchedPreference(splitValue = null, legacyValue = false)
        ).isFalse()
    }

    @Test
    fun `split preference overrides legacy value once changed`() {
        assertThat(
            resolveMigratedHideWatchedPreference(splitValue = false, legacyValue = true)
        ).isFalse()
        assertThat(
            resolveMigratedHideWatchedPreference(splitValue = true, legacyValue = false)
        ).isTrue()
    }

    @Test
    fun `new installs default split preference to disabled`() {
        assertThat(
            resolveMigratedHideWatchedPreference(splitValue = null, legacyValue = null)
        ).isFalse()
    }
}
