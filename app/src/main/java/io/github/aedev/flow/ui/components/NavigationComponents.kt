package io.github.aedev.flow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion

private data class NavItemSpec(
    val index: Int,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val labelRes: Int,
)

private const val MAX_VISIBLE_NAV_ITEMS = 5

@Composable
fun FloatingBottomNavBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isHomeEnabled: Boolean = true,
    isShortsEnabled: Boolean = true,
    isMusicEnabled: Boolean = true,
    isSearchEnabled: Boolean = false,
    isCategoriesEnabled: Boolean = false,
    navOrder: List<Int> = listOf(0, 1, 2, 3, 4, 5, 6),
) {
    val shortsIcon = ImageVector.vectorResource(id = R.drawable.ic_shorts)

    val enabledItems =
        remember(isHomeEnabled, isShortsEnabled, isMusicEnabled, isSearchEnabled, isCategoriesEnabled, navOrder) {
            val items =
                buildList {
                    if (isHomeEnabled) add(NavItemSpec(0, Icons.Filled.Home, Icons.Outlined.Home, R.string.nav_home))
                    if (isShortsEnabled) add(NavItemSpec(1, shortsIcon, shortsIcon, R.string.nav_shorts))
                    if (isMusicEnabled) add(NavItemSpec(2, Icons.Filled.MusicNote, Icons.Outlined.MusicNote, R.string.nav_music))
                    add(NavItemSpec(3, Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions, R.string.nav_subs))
                    add(NavItemSpec(4, Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary, R.string.nav_library))
                    if (isSearchEnabled) add(NavItemSpec(5, Icons.Filled.Search, Icons.Outlined.Search, R.string.nav_search))
                    if (isCategoriesEnabled) add(NavItemSpec(6, Icons.Filled.Explore, Icons.Outlined.Explore, R.string.nav_explore))
                }
            val order = navOrder.withIndex().associate { it.value to it.index }
            items.sortedBy { order[it.index] ?: Int.MAX_VALUE }
        }

    val visibleItems: List<NavItemSpec>
    val overflowItems: List<NavItemSpec>
    if (enabledItems.size <= MAX_VISIBLE_NAV_ITEMS) {
        visibleItems = enabledItems
        overflowItems = emptyList()
    } else {
        visibleItems = enabledItems.take(MAX_VISIBLE_NAV_ITEMS - 1)
        overflowItems = enabledItems.drop(MAX_VISIBLE_NAV_ITEMS - 1)
    }

    val isOverflowSelected = overflowItems.any { it.index == selectedIndex }
    var showMoreMenu by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visibleItems.forEach { spec ->
                    BottomNavItem(
                        modifier = Modifier.weight(1f),
                        icon = if (selectedIndex == spec.index) spec.filledIcon else spec.outlinedIcon,
                        label = stringResource(spec.labelRes),
                        selected = selectedIndex == spec.index,
                        onClick = { onItemSelected(spec.index) },
                    )
                }

                if (overflowItems.isNotEmpty()) {
                    Box(modifier = Modifier.weight(1f)) {
                        BottomNavItem(
                            modifier = Modifier.fillMaxWidth(),
                            icon = if (isOverflowSelected) Icons.Filled.MoreHoriz else Icons.Outlined.MoreHoriz,
                            label = stringResource(R.string.nav_more),
                            selected = isOverflowSelected,
                            onClick = { showMoreMenu = true },
                        )
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            offset = DpOffset(x = 0.dp, y = (-8).dp),
                        ) {
                            overflowItems.forEach { spec ->
                                val isSelected = selectedIndex == spec.index
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(spec.labelRes),
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color =
                                                if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isSelected) spec.filledIcon else spec.outlinedIcon,
                                            contentDescription = stringResource(spec.labelRes),
                                            tint =
                                                if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onItemSelected(spec.index)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberFlowReduceMotion()
    val iconTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec =
            tween(
                FlowMotion.durationFor(FlowMotion.CONTENT_DURATION_MILLIS, reduceMotion),
                easing = FlowMotion.EnterEasing,
            ),
        label = "iconTint",
    )
    val indicatorColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                androidx.compose.ui.graphics.Color.Transparent
            },
        animationSpec =
            tween(
                FlowMotion.durationFor(FlowMotion.CONTENT_DURATION_MILLIS, reduceMotion),
                easing = FlowMotion.EnterEasing,
            ),
        label = "indicatorColor",
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(indicatorColor)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true, radius = 28.dp),
                        onClick = onClick,
                    ).semantics(mergeDescendants = true) { }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )

            Spacer(modifier = Modifier.height(1.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = iconTint,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
