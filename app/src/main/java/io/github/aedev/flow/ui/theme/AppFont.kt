package io.github.aedev.flow.ui.theme

import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/** The font the app draws its text in. Stored by [storageId], so entries can be renamed freely. */
enum class AppFont(
    val storageId: String,
) {
    SYSTEM("system"),
    CONDENSED("condensed"),
    SERIF("serif"),
    CUSTOM("custom"),
    ;

    companion object {
        fun fromStorage(id: String?): AppFont = entries.firstOrNull { it.storageId == id } ?: SYSTEM
    }
}

private val CondensedWeights =
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)

/**
 * The family for [font]. A custom font comes from [customFile] through [loadCustom], which must only
 * return a family for a file it could actually read; anything missing or unreadable is the system font.
 */
fun appFontFamily(
    font: AppFont,
    customFile: File?,
    loadCustom: (File) -> FontFamily?,
): FontFamily =
    when (font) {
        AppFont.SYSTEM -> FlowFontFamily
        AppFont.CONDENSED -> FontFamily(CondensedWeights.map { Font(DeviceFontFamilyName("sans-serif-condensed"), it) })
        AppFont.SERIF -> FontFamily.Serif
        AppFont.CUSTOM -> customFile?.takeIf { it.isFile }?.let(loadCustom) ?: FlowFontFamily
    }
