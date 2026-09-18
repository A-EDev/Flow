package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.GestureOverlayStyle
import io.github.aedev.flow.ui.components.shared.rememberFlowSheetState
import io.github.aedev.flow.ui.components.videoplayer.overlay.GestureLevelHudPreview
import io.github.aedev.flow.ui.theme.PlayerGround

private val GestureOverlayStyles =
    listOf(
        GestureOverlayStyle.CIRCULAR,
        GestureOverlayStyle.VERTICAL,
        GestureOverlayStyle.HORIZONTAL,
        GestureOverlayStyle.MINIMAL,
    )

/** Tall enough for the standing bar once scaled, and the same for every option so they compare. */
private val PreviewHeight: Dp = 184.dp

internal fun gestureOverlayStyleLabelRes(style: GestureOverlayStyle): Int =
    when (style) {
        GestureOverlayStyle.CIRCULAR -> R.string.gesture_overlay_style_circular
        GestureOverlayStyle.VERTICAL -> R.string.gesture_overlay_style_vertical
        GestureOverlayStyle.HORIZONTAL -> R.string.gesture_overlay_style_horizontal
        GestureOverlayStyle.MINIMAL -> R.string.gesture_overlay_style_minimal
    }

/**
 * The picker for how volume and brightness read out mid-swipe.
 *
 * A sheet rather than the four-way toggle group it replaces: four labels never fitted the row, so
 * two of them read as "Circu…" and "Mini…", and a name alone does not answer what the thing looks
 * like anyway. Each option shows the read-out itself over a stand-in for the video.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GestureOverlayStyleSheet(
    selected: GestureOverlayStyle,
    onSelected: (GestureOverlayStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.player_appearance_gesture_overlay_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
            )
            Text(
                text = stringResource(R.string.player_appearance_gesture_overlay_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )

            LazyColumn {
                items(GestureOverlayStyles) { style ->
                    GestureOverlayStyleRow(
                        style = style,
                        isSelected = style == selected,
                        onClick = { onSelected(style) },
                    )
                    if (style != GestureOverlayStyles.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureOverlayStyleRow(
    style: GestureOverlayStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(gestureOverlayStyleLabelRes(style)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        GestureLevelHudPreview(
            style = style,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(PreviewHeight)
                    .clip(MaterialTheme.shapes.large)
                    .background(PlayerGround),
        )
    }
}
