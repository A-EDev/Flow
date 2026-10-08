package io.github.aedev.flow.utils.potoken

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VisitorIdentityAgeTest {
    private val savedAt = 1_000_000L

    @Test
    fun `a visitor younger than a week is reused`() {
        assertThat(VisitorIdentityAge.isReusable("CgtB", savedAt, savedAt + VisitorIdentityAge.MAX_AGE_MS - 1)).isTrue()
    }

    @Test
    fun `a week old visitor is replaced`() {
        assertThat(VisitorIdentityAge.isReusable("CgtB", savedAt, savedAt + VisitorIdentityAge.MAX_AGE_MS)).isFalse()
    }

    @Test
    fun `a visitor with no save time or no value is replaced`() {
        assertThat(VisitorIdentityAge.isReusable("CgtB", 0L, savedAt)).isFalse()
        assertThat(VisitorIdentityAge.isReusable("", savedAt, savedAt)).isFalse()
        assertThat(VisitorIdentityAge.isReusable(null, savedAt, savedAt)).isFalse()
    }

    @Test
    fun `a save time in the future, after a clock change, is not trusted`() {
        assertThat(VisitorIdentityAge.isReusable("CgtB", savedAt, savedAt - 1)).isFalse()
    }
}
