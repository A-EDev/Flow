package io.github.aedev.flow.ui.tv.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aedev.flow.ui.components.shared.FlowSheetPresenter

/**
 * Opens the shared media sheets (quick actions, Save, the music menus) as a [TvSidePanelSurface] in
 * a dialog window, which keeps D-pad focus inside it and lays it over whatever screen opened it.
 */
internal object TvSheetPresenter : FlowSheetPresenter {
    @Composable
    override fun Present(
        onDismiss: () -> Unit,
        onBack: (() -> Unit)?,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = onBack == null, usePlatformDefaultWidth = false),
        ) {
            BackHandler(enabled = onBack != null) { onBack?.invoke() }
            Box(modifier = Modifier.fillMaxSize()) {
                TvSidePanelSurface(
                    title = null,
                    onClose = null,
                    keepsFocus = { true },
                    modifier = Modifier.align(Alignment.CenterEnd),
                    content = content,
                )
            }
        }
    }
}
