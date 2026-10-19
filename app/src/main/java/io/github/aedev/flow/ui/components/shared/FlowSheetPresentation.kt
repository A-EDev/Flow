package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Shows a media sheet's content somewhere other than an M3 bottom sheet. A bottom sheet is a touch
 * surface, so Android TV provides one that opens the same content as a side panel.
 */
@Stable
interface FlowSheetPresenter {
    /** With [onBack] set, Back returns to the sheet's previous page instead of closing it. */
    @Composable
    fun Present(
        onDismiss: () -> Unit,
        onBack: (() -> Unit)?,
        content: @Composable ColumnScope.() -> Unit,
    )
}

/** Null on phones and tablets, where sheets stay M3 bottom sheets. */
val LocalFlowSheetPresenter = staticCompositionLocalOf<FlowSheetPresenter?> { null }
