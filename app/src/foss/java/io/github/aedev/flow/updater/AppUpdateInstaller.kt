package io.github.aedev.flow.updater

import android.content.Intent
import io.github.aedev.flow.data.update.AppRelease
import io.github.aedev.flow.data.update.UpdateDownload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** Foss builds never install updates themselves; their store does. */
@Singleton
class AppUpdateInstaller
    @Inject
    constructor() {
        val installFailed: StateFlow<Boolean> = MutableStateFlow(false)

        val isAvailable: Boolean = false

        fun download(release: AppRelease) = Unit

        fun cancel() = Unit

        fun state(version: String): Flow<UpdateDownload> = flowOf(UpdateDownload.Idle)

        fun canInstall(): Boolean = false

        fun installPermissionIntent(): Intent = Intent()

        suspend fun install(version: String): Boolean = false

        fun clean(keepVersion: String?) = Unit
    }
