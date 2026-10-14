package io.github.aedev.flow.ui.screens.update

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.update.UpdateDownload
import io.github.aedev.flow.data.update.UpdateFailure
import org.junit.Test

class UpdateStageTest {
    @Test
    fun `nothing downloaded offers the download`() {
        assertThat(updateStage(UpdateDownload.Idle, canInstall = true, installing = false, installFailed = false))
            .isEqualTo(UpdateStage.Available)
    }

    @Test
    fun `a finished download asks for the install permission before offering install`() {
        assertThat(updateStage(UpdateDownload.Ready, canInstall = false, installing = false, installFailed = false))
            .isEqualTo(UpdateStage.NeedsPermission)
        assertThat(updateStage(UpdateDownload.Ready, canInstall = true, installing = false, installFailed = false))
            .isEqualTo(UpdateStage.Ready)
    }

    @Test
    fun `a rejected install is offered again as a failure`() {
        assertThat(updateStage(UpdateDownload.Ready, canInstall = true, installing = true, installFailed = true))
            .isEqualTo(UpdateStage.Failed(UpdateFailure.INSTALL))
    }

    @Test
    fun `a failed download keeps its reason`() {
        assertThat(updateStage(UpdateDownload.Failed(UpdateFailure.CHECKSUM), canInstall = true, installing = false, installFailed = false))
            .isEqualTo(UpdateStage.Failed(UpdateFailure.CHECKSUM))
    }
}
