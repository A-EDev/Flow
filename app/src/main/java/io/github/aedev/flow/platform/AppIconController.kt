package io.github.aedev.flow.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.util.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Switches the launcher icon by enabling exactly one of the manifest's activity aliases. The alias
 * list is [AppIcons.ALL_SUFFIXES] and nothing else, because a picker that drifts from the manifest
 * can leave no launcher alias enabled after a restore.
 */
@Singleton
class AppIconController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playerPreferences: PlayerPreferences,
    ) {
        /** The alias the launcher currently shows. Reads the package manager, so it runs off the main thread. */
        suspend fun activeSuffix(): String =
            withContext(Dispatchers.IO) {
                val packageManager = context.packageManager
                AppIcons.ALL_SUFFIXES.firstOrNull { suffix ->
                    packageManager.getComponentEnabledSetting(componentFor(suffix)) ==
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } ?: AppIcons.DEFAULT_SUFFIX
            }

        suspend fun apply(suffix: String) {
            require(suffix in AppIcons.ALL_SUFFIXES) { "Unknown launcher alias $suffix" }
            withContext(Dispatchers.IO) {
                val packageManager = context.packageManager
                AppIcons.ALL_SUFFIXES.forEach { alias ->
                    val state =
                        if (alias == suffix) {
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        } else {
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        }
                    packageManager.setComponentEnabledSetting(componentFor(alias), state, PackageManager.DONT_KILL_APP)
                }
            }
            playerPreferences.setSelectedAppIcon(suffix)
        }

        private fun componentFor(suffix: String) = ComponentName(context.packageName, AppIcons.NAMESPACE + suffix)
    }
