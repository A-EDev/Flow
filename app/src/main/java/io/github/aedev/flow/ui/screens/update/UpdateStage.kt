package io.github.aedev.flow.ui.screens.update

import io.github.aedev.flow.data.update.UpdateDownload
import io.github.aedev.flow.data.update.UpdateFailure

const val UPDATE_ROUTE = "update"

/** What the update page's action bar offers next. */
sealed interface UpdateStage {
    data object Available : UpdateStage

    data class Downloading(
        val progress: Float?,
    ) : UpdateStage

    data object Verifying : UpdateStage

    data object NeedsPermission : UpdateStage

    data object Ready : UpdateStage

    data object Installing : UpdateStage

    data class Failed(
        val reason: UpdateFailure,
    ) : UpdateStage
}

/**
 * The stage for a download state. A finished download still needs Flow to be allowed to install
 * apps, and an install the system rejected is offered again as a retry.
 */
internal fun updateStage(
    download: UpdateDownload,
    canInstall: Boolean,
    installing: Boolean,
    installFailed: Boolean,
): UpdateStage =
    when (download) {
        UpdateDownload.Idle -> {
            UpdateStage.Available
        }

        is UpdateDownload.Running -> {
            UpdateStage.Downloading(download.progress)
        }

        UpdateDownload.Verifying -> {
            UpdateStage.Verifying
        }

        is UpdateDownload.Failed -> {
            UpdateStage.Failed(download.reason)
        }

        UpdateDownload.Ready -> {
            when {
                !canInstall -> UpdateStage.NeedsPermission
                installFailed -> UpdateStage.Failed(UpdateFailure.INSTALL)
                installing -> UpdateStage.Installing
                else -> UpdateStage.Ready
            }
        }
    }
