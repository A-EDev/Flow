package io.github.aedev.flow.ui.screens.settings.appearance.theme

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.CustomThemeCodec
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import io.github.aedev.flow.ui.theme.CustomTheme
import io.github.aedev.flow.ui.theme.FlowPalettes
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/** What an import or export did, for the page to say once. */
sealed interface CustomThemeMessage {
    data class Imported(
        val count: Int,
    ) : CustomThemeMessage

    data object ImportFailed : CustomThemeMessage

    data object LimitReached : CustomThemeMessage

    data object Exported : CustomThemeMessage

    data object ExportFailed : CustomThemeMessage
}

/** The custom themes list and editor: create, edit, rename, duplicate, delete, import and export. */
@HiltViewModel
class CustomThemesViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dataManager: LocalDataManager,
    ) : SettingsViewModel() {
        val themes: StateFlow<List<CustomTheme>?> = dataManager.customThemes.asState<List<CustomTheme>?>(null)

        /** The custom theme the app shows right now, or null when a built-in palette is showing. */
        val inUseId: StateFlow<String?> =
            combine(dataManager.themeMode, dataManager.activeCustomTheme) { mode, active ->
                active?.id?.takeIf { mode == ThemeMode.CUSTOM }
            }.asState(null)

        private val currentVariant = dataManager.themeVariant.asState(ThemeVariant.DARK)

        private val _message = MutableStateFlow<CustomThemeMessage?>(null)
        val message: StateFlow<CustomThemeMessage?> = _message.asStateFlow()

        fun consumeMessage() {
            _message.value = null
        }

        val canAddMore: Boolean get() = (themes.value?.size ?: 0) < CustomTheme.MAX_COUNT

        /** Creates a theme named [name] from every style of [base] and returns its id, for the editor to open. */
        fun create(
            name: String,
            base: ThemeMode,
        ): String? {
            if (!canAddMore) {
                _message.value = CustomThemeMessage.LimitReached
                return null
            }
            val theme = CustomTheme.from(newId(), name, FlowPalettes.forMode(base))
            write { dataManager.saveCustomTheme(theme) }
            return theme.id
        }

        fun save(theme: CustomTheme) = write { dataManager.saveCustomTheme(theme) }

        fun rename(
            theme: CustomTheme,
            name: String,
        ) = save(theme.copy(name = name.trim().take(CustomTheme.MAX_NAME_LENGTH)))

        fun duplicate(
            theme: CustomTheme,
            name: String,
        ) {
            if (!canAddMore) {
                _message.value = CustomThemeMessage.LimitReached
                return
            }
            save(theme.copy(id = newId(), name = name.take(CustomTheme.MAX_NAME_LENGTH)))
        }

        fun delete(id: String) = write { dataManager.deleteCustomTheme(id) }

        fun use(id: String) = write { dataManager.useCustomTheme(id, currentVariant.value) }

        fun import(uri: Uri) =
            write {
                val decoded =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver
                                .openInputStream(uri)
                                ?.bufferedReader()
                                ?.use { it.readText() }
                        }.onFailure { Log.w(TAG, "Theme file could not be read", it) }
                            .getOrNull()
                            .let(CustomThemeCodec::decode)
                    }
                _message.value =
                    when {
                        decoded.isEmpty() -> CustomThemeMessage.ImportFailed
                        !canAddMore -> CustomThemeMessage.LimitReached
                        else -> CustomThemeMessage.Imported(dataManager.importCustomThemes(decoded, ::newId))
                    }
            }

        /** Writes [exported] to [uri]: one theme as an object, several as a list, as Flow Desktop stores them. */
        fun export(
            uri: Uri,
            exported: List<CustomTheme>,
        ) = write {
            val written =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val text =
                            if (exported.size ==
                                1
                            ) {
                                CustomThemeCodec.encodeOne(exported.single())
                            } else {
                                CustomThemeCodec.encodeList(exported)
                            }
                        context.contentResolver
                            .openOutputStream(uri, "wt")
                            ?.bufferedWriter()
                            ?.use { it.write(text) } != null
                    }.onFailure { Log.w(TAG, "Theme file could not be written", it) }.getOrDefault(false)
                }
            _message.value = if (written) CustomThemeMessage.Exported else CustomThemeMessage.ExportFailed
        }

        fun shareText(theme: CustomTheme): String = CustomThemeCodec.encodeOne(theme)

        private fun newId(): String = CustomTheme.ID_PREFIX + UUID.randomUUID().toString()

        private companion object {
            const val TAG = "CustomThemes"
        }
    }
