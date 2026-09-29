package io.github.aedev.flow.ui.screens.settings.region

import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.CONTENT_LANGUAGE_FOLLOW_APP
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import io.github.aedev.flow.utils.AppLanguageManager
import org.schabi.newpipe.extractor.ServiceList
import java.util.Locale
import javax.inject.Inject

/** A language as a picker lists it: its own name first, then its name in the UI language. */
data class LanguageOption(
    val tag: String,
    val nativeName: String,
    val localizedName: String?,
)

@HiltViewModel
class LanguageRegionViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: PlayerPreferences,
    ) : SettingsViewModel() {
        val appLanguage = preferences.appLanguage.asState(AppLanguageManager.SYSTEM_DEFAULT)
        val contentLanguage = preferences.contentLanguage.asState(CONTENT_LANGUAGE_FOLLOW_APP)
        val contentCountry = preferences.trendingRegion.asState(DEFAULT_COUNTRY)

        /** The languages YouTube localizes content into, i.e. every value its `hl` accepts. */
        val contentLanguages: List<LanguageOption> by lazy {
            val displayLocale = Locale.getDefault()
            ServiceList.YouTube.supportedLocalizations
                .map { localization ->
                    val locale = Locale.forLanguageTag(localization.localizationCode)
                    val native = locale.getDisplayName(locale)
                    LanguageOption(
                        tag = localization.localizationCode,
                        nativeName = native,
                        localizedName = locale.getDisplayName(displayLocale).takeIf { it != native },
                    )
                }.sortedBy { it.nativeName.lowercase(displayLocale) }
        }

        /**
         * Saves the app language and returns whether the activity must be recreated to apply it.
         * The recreate itself belongs to the screen, which owns the activity.
         */
        fun setAppLanguage(tag: String) =
            write {
                preferences.setAppLanguage(tag)
                AppLanguageManager.saveLanguageTag(context, tag)
            }

        fun setContentLanguage(tag: String) = write { preferences.setContentLanguage(tag) }

        fun setContentCountry(code: String) = write { preferences.setTrendingRegion(code) }

        private companion object {
            const val DEFAULT_COUNTRY = "US"
        }
    }
