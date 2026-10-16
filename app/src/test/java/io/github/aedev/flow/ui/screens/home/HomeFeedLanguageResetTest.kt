package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeFeedLanguageResetTest {
    @Test
    fun `the language a process starts with keeps the caches`() {
        assertThat(isLanguageSwitch(previous = null, current = "ru")).isFalse()
    }

    @Test
    fun `the same language again keeps the caches`() {
        assertThat(isLanguageSwitch(previous = "ru", current = "ru")).isFalse()
    }

    @Test
    fun `a new language drops the caches (#1277)`() {
        assertThat(isLanguageSwitch(previous = "en", current = "ru")).isTrue()
    }
}
