package io.github.aedev.flow.ui.components.shared

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

object MediaKindSelectorDefaults {
    val HorizontalPadding: Dp = 16.dp
    val VerticalPadding: Dp = 8.dp
}

enum class MediaKind(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Videos(R.string.tab_videos, Icons.Outlined.VideoLibrary),
    Music(R.string.tab_music, Icons.Outlined.MusicNote),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MediaKindSelector(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
) {
    SingleChoiceSegmentedButtonRow(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MediaKindSelectorDefaults.HorizontalPadding,
                    vertical = MediaKindSelectorDefaults.VerticalPadding,
                ),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {
                    if (icon != null) {
                        Icon(
                            imageVector = icon(option),
                            contentDescription = null,
                        )
                    } else {
                        SegmentedButtonDefaults.Icon(active = option == selected)
                    }
                },
            ) {
                Text(
                    text = label(option),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
