package io.github.aedev.flow.utils

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.core.app.LocaleManagerCompat
import io.github.aedev.flow.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class AppLanguageOption(
    val tag: String,
    val nativeName: String,
    val localizedName: String,
)

object AppLanguageManager {
    const val SYSTEM_DEFAULT = "system"
    private const val PREFS_FILE = "flow_language_prefs"
    private const val PREFS_KEY = "app_language_tag"
    private const val HANDED_OVER_KEY = "app_language_handed_to_system"

    private val supportedLanguageTags = BuildConfig.APP_LANGUAGE_TAGS.split(',')

    private val applied = MutableStateFlow<String?>(null)

    /**
     * The language the UI is shown in, once resolved at start or saved. YouTube's `hl` follows this,
     * not the preference that mirrors it: a restored or synced preference never reached the UI.
     */
    val appliedLanguage: StateFlow<String?> = applied.asStateFlow()

    /**
     * The device's language. [Locale.getDefault] and even `Resources.getSystem()` carry the app's
     * language instead, still the old one right after choosing "System default".
     */
    fun deviceLocale(context: Context): Locale = LocaleManagerCompat.getSystemLocales(context)[0] ?: Locale.getDefault()

    /** Android 13+ keeps the per-app language itself, shared with Settings › App languages. */
    val systemStoresLanguage: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun loadSelectedLanguageTag(context: Context): String =
        context
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getString(PREFS_KEY, SYSTEM_DEFAULT) ?: SYSTEM_DEFAULT

    fun saveLanguageTag(
        context: Context,
        tag: String,
    ) {
        context
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_KEY, tag)
            .apply()
        applied.value = normalizeLanguageTag(tag)
    }

    /** Saves [tag] and, on Android 13+, hands it to the system, which then restarts the activities itself. */
    fun applyLanguageTag(
        context: Context,
        tag: String,
    ) {
        saveLanguageTag(context, tag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setSystemLanguageTag(context, tag)
    }

    /**
     * The language to start with. On Android 13+ a choice made in Settings › App languages wins and is
     * saved here; a choice saved before the system kept one is handed over to it once.
     */
    fun resolveLanguageTag(context: Context): String {
        val stored = loadSelectedLanguageTag(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return stored.also { applied.value = normalizeLanguageTag(it) }
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val resolution =
            resolveAgainstSystem(
                stored = normalizeLanguageTag(stored),
                system = systemLanguageTag(context),
                handedOver = prefs.getBoolean(HANDED_OVER_KEY, false),
            )
        if (resolution.pushToSystem) setSystemLanguageTag(context, resolution.tag)
        if (!prefs.getBoolean(HANDED_OVER_KEY, false)) prefs.edit().putBoolean(HANDED_OVER_KEY, true).apply()
        if (resolution.tag != stored) saveLanguageTag(context, resolution.tag)
        applied.value = resolution.tag
        return resolution.tag
    }

    internal data class LanguageResolution(
        val tag: String,
        val pushToSystem: Boolean,
    )

    internal fun resolveAgainstSystem(
        stored: String,
        system: String,
        handedOver: Boolean,
    ): LanguageResolution =
        if (handedOver || system != SYSTEM_DEFAULT || stored == SYSTEM_DEFAULT) {
            LanguageResolution(tag = system, pushToSystem = false)
        } else {
            LanguageResolution(tag = stored, pushToSystem = true)
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun systemLanguageTag(context: Context): String {
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
        if (locales == null || locales.isEmpty) return SYSTEM_DEFAULT
        return normalizeLanguageTag(locales[0].toLanguageTag())
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setSystemLanguageTag(
        context: Context,
        tag: String,
    ) {
        val normalized = normalizeLanguageTag(tag)
        context.getSystemService(LocaleManager::class.java)?.applicationLocales =
            if (normalized == SYSTEM_DEFAULT) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(normalized)
    }

    /**
     * [base] with the chosen language applied, or [base] itself when following the system.
     *
     * The override carries the locale and nothing else. Copying the whole current configuration
     * froze every field at launch, the night-mode bit included, so the app never followed a system
     * switch between light and dark (#972).
     */
    fun wrapContext(
        base: Context,
        selectedTag: String,
    ): Context {
        val normalizedTag = normalizeLanguageTag(selectedTag)
        val locale = resolveLocale(base, normalizedTag)
        Locale.setDefault(locale)
        if (normalizedTag == SYSTEM_DEFAULT) return base

        val configuration = Configuration()
        configuration.setLocales(LocaleList(locale))
        return base.createConfigurationContext(configuration)
    }

    fun getSupportedLanguages(displayLocale: Locale = Locale.getDefault()): List<AppLanguageOption> =
        supportedLanguageTags
            .map { tag ->
                val locale = localeFromTag(tag)
                AppLanguageOption(
                    tag = tag,
                    nativeName =
                        locale.getDisplayName(locale).replaceFirstChar { ch ->
                            if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString()
                        },
                    localizedName =
                        locale.getDisplayName(displayLocale).replaceFirstChar { ch ->
                            if (ch.isLowerCase()) ch.titlecase(displayLocale) else ch.toString()
                        },
                )
            }.sortedBy { it.localizedName }

    fun getLanguageLabel(
        tag: String,
        displayLocale: Locale = Locale.getDefault(),
    ): String {
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

    private fun resolveLocale(
        context: Context,
        selectedTag: String,
    ): Locale {
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
        val normalizedTag =
            when (tag) {
                "id" -> "id"
                else -> tag
            }
        return Locale.forLanguageTag(normalizedTag)
    }
}
