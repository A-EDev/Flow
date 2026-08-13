package io.github.aedev.flow.ui.screens.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerOverlayPreferences
import io.github.aedev.flow.ui.theme.PlayerScrim
import io.github.aedev.flow.ui.theme.PlayerScrimAffordance
import io.github.aedev.flow.ui.theme.PlayerScrimContent

/** Alpha the top gradient reaches at the very top of the player. */
private const val TOP_GRADIENT_ALPHA = 0.38f

/**
 * The row of actions along the top of the player: minimise, title, and the configurable action
 * cluster on the right.
 *
 * Which of the right-hand actions exist at all is driven by [preferences], so this takes the whole
 * snapshot rather than a boolean per button.
 */
@Composable
internal fun PlayerTopBar(
    preferences: PlayerOverlayPreferences,
    isFullscreen: Boolean,
    videoTitle: String?,
    speedIndicatorLabel: String,
    resizeMode: Int,
    resizeModeLabels: List<String>,
    isPipSupported: Boolean,
    sbSubmitEnabled: Boolean,
    isCasting: Boolean,
    isSubtitlesEnabled: Boolean,
    isAutoplayOn: Boolean,
    isLooping: Boolean,
    isSleepTimerActive: Boolean,
    lockModeEnabled: Boolean,
    isLiveChatAvailable: Boolean,
    topPadding: Dp,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    rowMinHeight: Dp,
    pillHeight: Dp,
    actionButtonSize: Dp,
    actionIconSize: Dp,
    actionSpacing: Dp,
    actions: PlayerControlActions,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(PlayerScrim.copy(alpha = TOP_GRADIENT_ALPHA), Color.Transparent),
                        ),
                ).padding(top = topPadding),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = rowMinHeight)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(actionSpacing),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(actionButtonSize)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = actions.onBack,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.btn_minimize),
                        tint = PlayerScrimContent,
                        modifier = Modifier.size(actionIconSize),
                    )
                }

                if (isFullscreen && preferences.fullscreenTitleEnabled && !videoTitle.isNullOrBlank()) {
                    Text(
                        text = videoTitle,
                        color = PlayerScrimContent,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                    )
                }

                if (isPipSupported && preferences.pipEnabled) {
                    TopBarIconButton(
                        onClick = actions.onPipClick,
                        buttonSize = actionButtonSize,
                        iconSize = actionIconSize,
                        icon = Icons.Rounded.PictureInPicture,
                        contentDescription = stringResource(R.string.pip_mode),
                    )
                }

                if (sbSubmitEnabled) {
                    IconButton(
                        onClick = actions.onSbSubmitClick,
                        modifier = Modifier.size(actionButtonSize),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_upload_segment),
                            contentDescription = stringResource(R.string.sb_submit_dialog_title),
                            tint = PlayerScrimContent,
                            modifier = Modifier.size(actionIconSize),
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(actionSpacing),
            ) {
                if (preferences.speedIndicatorEnabled) {
                    Surface(
                        color = PlayerScrimAffordance,
                        shape = RoundedCornerShape(14.dp),
                        modifier =
                            Modifier
                                .height(pillHeight)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = actions.onSpeedClick),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        ) {
                            Text(
                                text = speedIndicatorLabel,
                                color = PlayerScrimContent,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }

                if (isFullscreen) {
                    TopBarIconButton(
                        onClick = actions.onResizeClick,
                        buttonSize = actionButtonSize,
                        iconSize = actionIconSize,
                        icon =
                            when (resizeMode) {
                                0 -> Icons.Rounded.AspectRatio
                                1 -> Icons.Rounded.Fullscreen
                                else -> Icons.Rounded.ZoomIn
                            },
                        contentDescription = stringResource(R.string.resize_to, resizeModeLabels[resizeMode]),
                    )
                }

                if (preferences.castEnabled) {
                    TopBarIconButton(
                        onClick = actions.onCastClick,
                        buttonSize = actionButtonSize,
                        iconSize = actionIconSize,
                        icon = if (isCasting) Icons.Rounded.Cast else Icons.Outlined.Cast,
                        contentDescription = stringResource(R.string.cast_to_tv),
                        tint = if (isCasting) accentColor else PlayerScrimContent,
                    )
                }

                if (preferences.captionsEnabled) {
                    TopBarIconButton(
                        onClick = actions.onSubtitleClick,
                        buttonSize = actionButtonSize,
                        iconSize = actionIconSize,
                        icon =
                            if (isSubtitlesEnabled) {
                                Icons.Rounded.ClosedCaption
                            } else {
                                Icons.Outlined.ClosedCaption
                            },
                        contentDescription = stringResource(R.string.captions),
                        tint = if (isSubtitlesEnabled) accentColor else PlayerScrimContent,
                    )
                }

                if (preferences.autoplayEnabled) {
                    IconButton(
                        onClick = { if (!isLooping) actions.onAutoplayToggle(!isAutoplayOn) },
                        enabled = !isLooping,
                        modifier = Modifier.size(actionButtonSize),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SlowMotionVideo,
                            contentDescription = stringResource(R.string.autoplay),
                            tint =
                                when {
                                    isLooping -> PlayerScrimContent.copy(alpha = 0.35f)
                                    isAutoplayOn -> accentColor
                                    else -> PlayerScrimContent.copy(alpha = 0.7f)
                                },
                            modifier = Modifier.size(actionIconSize),
                        )
                    }
                }

                if (preferences.sleepTimerEnabled) {
                    TopBarIconButton(
                        onClick = actions.onSleepTimerClick,
                        buttonSize = actionButtonSize,
                        iconSize = actionIconSize,
                        icon = Icons.Rounded.Bedtime,
                        contentDescription = stringResource(R.string.sleep_timer),
                        tint = if (isSleepTimerActive) accentColor else PlayerScrimContent,
                    )
                }

                if (lockModeEnabled) {
                    TopBarIconButton(
                        onClick = actions.onTouchLockToggle,
                        buttonSize = actionButtonSize,
                        iconSize = actionIconSize,
                        icon = Icons.Rounded.Lock,
                        contentDescription = stringResource(R.string.player_lock_controls),
                    )
                }

                if (isLiveChatAvailable && isFullscreen) {
                    TopBarIconButton(
                        onClick = actions.onLiveChatClick,
                        buttonSize = actionButtonSize,
                        iconSize = actionIconSize,
                        icon = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = stringResource(R.string.live_chat),
                    )
                }

                TopBarIconButton(
                    onClick = actions.onSettingsClick,
                    buttonSize = actionButtonSize,
                    iconSize = actionIconSize,
                    icon = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings),
                )
            }
        }
    }
}

@Composable
private fun TopBarIconButton(
    onClick: () -> Unit,
    buttonSize: Dp,
    iconSize: Dp,
    icon: ImageVector,
    contentDescription: String,
    tint: Color = PlayerScrimContent,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(buttonSize),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}
