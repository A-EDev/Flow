package io.github.aedev.flow.ui.components.layout.topbar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shared look of every non-player top bar.
 *
 * The values here are not Material defaults on purpose: Flow paints screens on
 * `colorScheme.background` rather than `surface`, and [WindowInsets] owns no status-bar padding
 * because the root `Scaffold` in `FlowApp` already consumes `WindowInsets.systemBars`. Letting a
 * `TopAppBar` apply its own insets would double the status-bar gap on every screen.
 */
object FlowTopBarDefaults {
    /** Status bar padding is applied once by the root scaffold, never by an individual bar. */
    val WindowInsets: WindowInsets = WindowInsets(0.dp)

    /**
     * Internal because [TopAppBarColors] is an experimental Material type: exposing it would force
     * every calling screen to opt in, which is exactly what this component exists to avoid.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    internal fun colors(): TopAppBarColors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

    val titleStyle: TextStyle
        @Composable get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)

    val subtitleStyle: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall
}
