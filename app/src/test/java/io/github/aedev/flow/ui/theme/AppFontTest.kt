package io.github.aedev.flow.ui.theme

import androidx.compose.ui.text.font.FontFamily
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.CustomFontStore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppFontTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `stored ids round trip and anything unknown is the system font`() {
        AppFont.entries.forEach { assertThat(AppFont.fromStorage(it.storageId)).isEqualTo(it) }
        assertThat(AppFont.fromStorage(null)).isEqualTo(AppFont.SYSTEM)
        assertThat(AppFont.fromStorage("inter")).isEqualTo(AppFont.SYSTEM)
    }

    @Test
    fun `only ttf and otf names are fonts`() {
        assertThat(CustomFontStore.fontExtension("Atkinson Hyperlegible.TTF")).isEqualTo("ttf")
        assertThat(CustomFontStore.fontExtension("inter.var.otf")).isEqualTo("otf")
        assertThat(CustomFontStore.fontExtension("font.woff2")).isNull()
        assertThat(CustomFontStore.fontExtension("ttf")).isNull()
        assertThat(CustomFontStore.fontExtension(null)).isNull()
    }

    @Test
    fun `the size cap is ten megabytes and an empty file is refused`() {
        assertThat(CustomFontStore.isWithinSizeLimit(CustomFontStore.MAX_SIZE_BYTES)).isTrue()
        assertThat(CustomFontStore.isWithinSizeLimit(CustomFontStore.MAX_SIZE_BYTES + 1)).isFalse()
        assertThat(CustomFontStore.isWithinSizeLimit(0)).isFalse()
    }

    @Test
    fun `a custom font is used only when its file loads`() {
        val file = temp.newFile("custom.ttf")
        assertThat(appFontFamily(AppFont.CUSTOM, file) { FontFamily.Monospace }).isEqualTo(FontFamily.Monospace)
        assertThat(appFontFamily(AppFont.CUSTOM, file) { null }).isEqualTo(FlowFontFamily)
    }

    @Test
    fun `a custom choice without its file falls back to the system font`() {
        var loaded = false
        val missing = temp.root.resolve("custom.otf")
        assertThat(
            appFontFamily(AppFont.CUSTOM, null) {
                loaded = true
                FontFamily.Monospace
            },
        ).isEqualTo(FlowFontFamily)
        assertThat(
            appFontFamily(AppFont.CUSTOM, missing) {
                loaded = true
                FontFamily.Monospace
            },
        ).isEqualTo(FlowFontFamily)
        assertThat(loaded).isFalse()
    }

    @Test
    fun `built-in choices never touch the custom file`() {
        val file = temp.newFile("custom.ttf")
        assertThat(appFontFamily(AppFont.SYSTEM, file) { error("not loaded") }).isEqualTo(FlowFontFamily)
        assertThat(appFontFamily(AppFont.SERIF, file) { error("not loaded") }).isEqualTo(FontFamily.Serif)
    }
}
