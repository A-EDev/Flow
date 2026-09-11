package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

private val MenuWidth = 200.dp
private val MenuLabelHorizontalPadding = 16.dp
private val MenuLabelVerticalPadding = 8.dp

/**
 * How much room the control takes.
 *
 * [Compact] is the extra-small Material 3 size, for a row that already carries an avatar, a channel
 * name and a subscriber count beside it.
 */
enum class FlowSubscribeButtonSize {
    Default,
    Compact,
}

/**
 * The one subscribe control, shared by the channel page, the player, the subscription manager and
 * search.
 *
 * Tapping while subscribed opens the menu rather than unsubscribing outright, so the destructive
 * action always needs a second, deliberate tap.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlowSubscribeButton(
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit,
    modifier: Modifier = Modifier,
    isNotificationsEnabled: Boolean = false,
    onUnsubscribeClick: () -> Unit = {},
    onNotificationChange: ((Boolean) -> Unit)? = null,
    areShortsExcluded: Boolean? = null,
    onShortsExcludeChange: (Boolean) -> Unit = {},
    onManageGroups: (() -> Unit)? = null,
    size: FlowSubscribeButtonSize = FlowSubscribeButtonSize.Default,
) {
    val haptics = LocalHapticFeedback.current
    var menuExpanded by remember { mutableStateOf(false) }
    val compact = size == FlowSubscribeButtonSize.Compact
    val containerHeight =
        if (compact) ButtonDefaults.ExtraSmallContainerHeight else ButtonDefaults.MinHeight
    val leadingIconSize =
        if (compact) ButtonDefaults.ExtraSmallIconSize else SplitButtonDefaults.LeadingIconSize
    val trailingIconSize =
        if (compact) SplitButtonDefaults.ExtraSmallTrailingButtonIconSize else SplitButtonDefaults.TrailingIconSize

    Box(modifier = modifier) {
        if (isSubscribed) {
            SplitButtonLayout(
                leadingButton = {
                    SplitButtonDefaults.TonalLeadingButton(
                        onClick = { onNotificationChange?.invoke(!isNotificationsEnabled) },
                        contentPadding =
                            if (compact) {
                                SplitButtonDefaults.ExtraSmallLeadingButtonContentPadding
                            } else {
                                SplitButtonDefaults.SmallLeadingButtonContentPadding
                            },
                        modifier = Modifier.heightIn(min = containerHeight),
                    ) {
                        Icon(
                            imageVector =
                                if (isNotificationsEnabled) {
                                    Icons.Rounded.NotificationsActive
                                } else {
                                    Icons.Rounded.NotificationsOff
                                },
                            contentDescription = null,
                            modifier = Modifier.size(leadingIconSize),
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = stringResource(R.string.subscribed))
                    }
                },
                trailingButton = {
                    SplitButtonDefaults.TonalTrailingButton(
                        checked = menuExpanded,
                        onCheckedChange = { menuExpanded = it },
                        contentPadding =
                            if (compact) {
                                SplitButtonDefaults.ExtraSmallTrailingButtonContentPadding
                            } else {
                                SplitButtonDefaults.SmallTrailingButtonContentPadding
                            },
                        modifier = Modifier.heightIn(min = containerHeight),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.subscribed),
                            modifier = Modifier.size(trailingIconSize),
                        )
                    }
                },
            )
        } else {
            ToggleButton(
                checked = false,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSubscribeClick()
                },
                shapes = ToggleButtonShapes(CircleShape, CircleShape, CircleShape),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                contentPadding =
                    if (compact) ButtonDefaults.ExtraSmallContentPadding else ToggleButtonDefaults.ContentPadding,
                modifier = Modifier.heightIn(min = containerHeight),
            ) {
                Text(text = stringResource(R.string.subscribe))
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.width(MenuWidth),
        ) {
            if (onNotificationChange != null) {
                Text(
                    text = stringResource(R.string.notifications),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.padding(
                            horizontal = MenuLabelHorizontalPadding,
                            vertical = MenuLabelVerticalPadding,
                        ),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.on)) },
                    leadingIcon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                    onClick = {
                        onNotificationChange(true)
                        menuExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.off)) },
                    leadingIcon = { Icon(Icons.Rounded.NotificationsOff, contentDescription = null) },
                    onClick = {
                        onNotificationChange(false)
                        menuExpanded = false
                    },
                )
                HorizontalDivider()
            }

            if (areShortsExcluded != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (areShortsExcluded) R.string.show_channel_shorts else R.string.hide_channel_shorts,
                            ),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (areShortsExcluded) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        onShortsExcludeChange(!areShortsExcluded)
                        menuExpanded = false
                    },
                )
                HorizontalDivider()
            }

            if (onManageGroups != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.channel_add_to_group)) },
                    leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                    onClick = {
                        onManageGroups()
                        menuExpanded = false
                    },
                )
                HorizontalDivider()
            }

            DropdownMenuItem(
                text = { Text(stringResource(R.string.unsubscribe)) },
                leadingIcon = { Icon(Icons.Rounded.PersonRemove, contentDescription = null) },
                onClick = {
                    onUnsubscribeClick()
                    menuExpanded = false
                },
            )
        }
    }
}
