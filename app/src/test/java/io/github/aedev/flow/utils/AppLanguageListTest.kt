package io.github.aedev.flow.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppLanguageListTest {
    private val tags = AppLanguageManager.getSupportedLanguages(Locale.ENGLISH).map { it.tag }

    @Test
    fun `the picker lists every shipped translation (#1300)`() {
        listOf("en", "zh-TW", "zh-CN", "yue-Hant", "pt-BR", "pt", "id", "vi", "ko", "ja").forEach { tag ->
            assertTrue("$tag missing from $tags", tag in tags)
        }
        assertFalse("in" in tags)
    }

    @Test
    fun `every listed tag is a well-formed language tag`() {
        tags.forEach { tag -> assertEquals(tag, Locale.forLanguageTag(tag).toLanguageTag()) }
    }

    @Test
    fun `Traditional Chinese is named in its own script`() {
        val option = AppLanguageManager.getSupportedLanguages(Locale.ENGLISH).first { it.tag == "zh-TW" }
        assertEquals("中文 (台灣)", option.nativeName)
        assertEquals("Chinese (Taiwan)", option.localizedName)
    }
}
