package io.github.aedev.flow.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.backup.BackupCoordinator
import io.github.aedev.flow.data.backup.BackupOperation
import io.github.aedev.flow.data.backup.ImportKind
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Onboarding's view of the backup coordinator: the same imports Settings offers, one at a time. */
@HiltViewModel
class OnboardingImportViewModel
    @Inject
    constructor(
        private val coordinator: BackupCoordinator,
    ) : ViewModel() {
        val operation: StateFlow<BackupOperation> = coordinator.operation

        fun start(
            kind: ImportKind,
            uri: android.net.Uri,
        ) = kind.start(coordinator, uri)

        fun dismiss() = coordinator.dismiss()
    }
