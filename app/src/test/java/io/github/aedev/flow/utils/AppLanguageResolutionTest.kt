package io.github.aedev.flow.utils

import io.github.aedev.flow.utils.AppLanguageManager.LanguageResolution
import io.github.aedev.flow.utils.AppLanguageManager.SYSTEM_DEFAULT
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageResolutionTest {
    @Test
    fun `a language picked in the app before Android kept one is handed over`() {
        assertEquals(
            LanguageResolution(tag = "de", pushToSystem = true),
            AppLanguageManager.resolveAgainstSystem(stored = "de", system = SYSTEM_DEFAULT, handedOver = false),
        )
    }

    @Test
    fun `a language picked in Android's settings wins over the app's old choice`() {
        assertEquals(
            LanguageResolution(tag = "zh-TW", pushToSystem = false),
            AppLanguageManager.resolveAgainstSystem(stored = "de", system = "zh-TW", handedOver = false),
        )
    }

    @Test
    fun `resetting the language in Android's settings is kept once handed over`() {
        assertEquals(
            LanguageResolution(tag = SYSTEM_DEFAULT, pushToSystem = false),
            AppLanguageManager.resolveAgainstSystem(stored = "de", system = SYSTEM_DEFAULT, handedOver = true),
        )
    }

    @Test
    fun `following the system needs nothing handed over`() {
        assertEquals(
            LanguageResolution(tag = SYSTEM_DEFAULT, pushToSystem = false),
            AppLanguageManager.resolveAgainstSystem(stored = SYSTEM_DEFAULT, system = SYSTEM_DEFAULT, handedOver = false),
        )
    }
}
