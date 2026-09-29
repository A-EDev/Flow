package io.github.aedev.flow.ui.screens.settings.diagnostics

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private val ChunkPadding = 12.dp

/** A run of log lines in monospace, each coloured by its level from the theme. */
@Composable
internal fun LogChunk(
    lines: List<LogLine>,
    shape: Shape,
) {
    val colors = MaterialTheme.colorScheme
    val palette =
        remember(colors) {
            mapOf(
                LogLevel.ERROR to colors.error,
                LogLevel.WARN to colors.tertiary,
                LogLevel.INFO to colors.onSurface,
                LogLevel.QUIET to colors.onSurfaceVariant,
                LogLevel.HEADING to colors.primary,
            )
        }
    val text =
        remember(lines, palette) {
            buildAnnotatedString {
                lines.forEachIndexed { index, line ->
                    withStyle(SpanStyle(color = palette[line.level] ?: Color.Unspecified)) { append(line.text) }
                    if (index < lines.lastIndex) append('\n')
                }
            }
        }
    Surface(shape = shape, color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(ChunkPadding),
            )
        }
    }
}
