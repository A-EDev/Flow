package io.github.aedev.flow.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ComponentEnabledSetting
import android.os.Build
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

        /**
         * Enables [suffix] before disabling the rest, so a launcher that reloads mid-switch never sees
         * the app without an entry. From Android 13 the whole switch is one atomic call.
         */
        suspend fun apply(suffix: String) {
            require(suffix in AppIcons.ALL_SUFFIXES) { "Unknown launcher alias $suffix" }
            withContext(Dispatchers.IO) {
                val states =
                    (listOf(suffix) + (AppIcons.ALL_SUFFIXES - suffix)).map { alias ->
                        val state =
                            if (alias == suffix) {
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            } else {
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            }
                        componentFor(alias) to state
                    }
                val packageManager = context.packageManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.setComponentEnabledSettings(
                        states.map { (component, state) -> ComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) },
                    )
                } else {
                    states.forEach { (component, state) ->
                        packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
                    }
                }
            }
            playerPreferences.setSelectedAppIcon(suffix)
        }

        /**
         * Gives the app its default launcher entry back when no known alias is enabled, which happens
         * when an update drops the alias a user had picked: the default stays explicitly disabled.
         */
        suspend fun repair() {
            val hasLauncherEntry =
                withContext(Dispatchers.IO) {
                    val packageManager = context.packageManager
                    AppIcons.ALL_SUFFIXES.any { suffix ->
                        when (packageManager.getComponentEnabledSetting(componentFor(suffix))) {
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> suffix == AppIcons.DEFAULT_SUFFIX
                            else -> false
                        }
                    }
                }
            if (!hasLauncherEntry) apply(AppIcons.DEFAULT_SUFFIX)
        }

        private fun componentFor(suffix: String) = ComponentName(context.packageName, AppIcons.NAMESPACE + suffix)
    }
