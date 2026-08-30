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

/** Shared styling for non-player top bars. */
object FlowTopBarDefaults {
    val WindowInsets: WindowInsets = WindowInsets(0.dp)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    internal fun colors(): TopAppBarColors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        )

    val titleStyle: TextStyle
        @Composable
        get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

    val subtitleStyle: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall
}
