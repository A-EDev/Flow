package io.github.aedev.flow.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

data class AppLanguageOption(
    val tag: String,
    val nativeName: String,
    val localizedName: String
)

object AppLanguageManager {
    const val SYSTEM_DEFAULT = "system"
    private const val PREFS_FILE = "flow_language_prefs"
    private const val PREFS_KEY = "app_language_tag"

    private val supportedLanguageTags = listOf(
        "en",
        "ar",
        "bs",
        "de",
        "es",
        "et",
        "fr",
        "hi",
        "id",
        "it",
        "kab",
        "pl",
        "pt-BR",
        "ru",
        "tr",
        "uk"
    )

    fun loadSelectedLanguageTag(context: Context): String {
        return context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getString(PREFS_KEY, SYSTEM_DEFAULT) ?: SYSTEM_DEFAULT
    }

    fun saveLanguageTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit().putString(PREFS_KEY, tag).apply()
    }

    fun wrapContext(base: Context, selectedTag: String): Context {
        val normalizedTag = normalizeLanguageTag(selectedTag)
        val locale = resolveLocale(base, normalizedTag)
        Locale.setDefault(locale)

        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(LocaleList(locale))
        }

        return base.createConfigurationContext(configuration)
    }

    fun getSupportedLanguages(displayLocale: Locale = Locale.getDefault()): List<AppLanguageOption> {
        return supportedLanguageTags.map { tag ->
            val locale = localeFromTag(tag)
            AppLanguageOption(
                tag = tag,
                nativeName = locale.getDisplayName(locale).replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString()
                },
                localizedName = locale.getDisplayName(displayLocale).replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(displayLocale) else ch.toString()
                }
            )
        }.sortedBy { it.localizedName }
    }

    fun getLanguageLabel(tag: String, displayLocale: Locale = Locale.getDefault()): String {
        val normalizedTag = normalizeLanguageTag(tag)
        if (normalizedTag == SYSTEM_DEFAULT) {
            return ""
        }

        val locale = localeFromTag(normalizedTag)
        return locale.getDisplayName(displayLocale).replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(displayLocale) else ch.toString()
        }
    }

    fun normalizeLanguageTag(rawTag: String?): String {
        val value = rawTag?.trim().orEmpty()
        if (value.isEmpty() || value.equals(SYSTEM_DEFAULT, ignoreCase = true)) {
            return SYSTEM_DEFAULT
        }

        return when (value.lowercase(Locale.ROOT)) {
            "in", "id-id" -> "id"
            "pt-rbr", "pt_br", "pt-br" -> "pt-BR"
            else -> localeFromTag(value).toLanguageTag().takeIf { it.isNotBlank() } ?: value
        }
    }

    fun activityContext(context: Context): android.app.Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is android.app.Activity) {
                return current
            }
            current = current.baseContext
        }
        return null
    }

    private fun resolveLocale(context: Context, selectedTag: String): Locale {
        if (selectedTag == SYSTEM_DEFAULT) {
            val configuration = context.resources.configuration
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                configuration.locales[0] ?: Locale.getDefault()
            } else {
                @Suppress("DEPRECATION")
                configuration.locale ?: Locale.getDefault()
            }
        }

        return localeFromTag(selectedTag)
    }

    private fun localeFromTag(tag: String): Locale {
        val normalizedTag = when (tag) {
            "id" -> "id"
            else -> tag
        }
        return Locale.forLanguageTag(normalizedTag)
    }
}