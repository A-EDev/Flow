package io.github.aedev.flow.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** For the receiver the system constructs itself, which Hilt cannot inject. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppIconEntryPoint {
    fun appIconController(): AppIconController
}

/** Runs after every update of this app, before it is next opened, and restores a launcher entry if none is left. */
class LauncherAliasRepairReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val controller = EntryPointAccessors.fromApplication(context, AppIconEntryPoint::class.java).appIconController()
        val pending = goAsync()
        scope.launch {
            try {
                controller.repair()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
