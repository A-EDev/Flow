package io.github.aedev.flow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.layout.FlowNavItemSpec
import io.github.aedev.flow.ui.components.layout.rememberFlowNavItems

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
    val enabledItems =
        rememberFlowNavItems(
            isHomeEnabled = isHomeEnabled,
            isShortsEnabled = isShortsEnabled,
            isMusicEnabled = isMusicEnabled,
            isSearchEnabled = isSearchEnabled,
            isCategoriesEnabled = isCategoriesEnabled,
            navOrder = navOrder,
        )

    val visibleItems: List<FlowNavItemSpec>
    val overflowItems: List<FlowNavItemSpec>
    if (enabledItems.size <= MAX_VISIBLE_NAV_ITEMS) {
        visibleItems = enabledItems
        overflowItems = emptyList()
    } else {
        visibleItems = enabledItems.take(MAX_VISIBLE_NAV_ITEMS - 1)
        overflowItems = enabledItems.drop(MAX_VISIBLE_NAV_ITEMS - 1)
    }

    val isOverflowSelected = overflowItems.any { it.index == selectedIndex }
    var showMoreMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = 3.dp),
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

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "scale",
    )

    val iconTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "iconTint",
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
                    .scale(scale)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true, radius = 28.dp),
                        onClick = onClick,
                    ).padding(horizontal = 12.dp, vertical = 2.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )

            Spacer(modifier = Modifier.height(1.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = iconTint,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
