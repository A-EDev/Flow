package io.github.aedev.flow.ui.components.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.theme.extendedColors

@Composable
internal fun ChannelTabRow(
    selectedIndex: Int,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit,
) {
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 0.dp,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        indicator = { tabPositions ->
            TabRowDefaults.Indicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                height = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        divider = {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                thickness = 0.5.dp,
            )
        },
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                modifier = Modifier.height(44.dp),
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = MaterialTheme.extendedColors.textSecondary,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selectedIndex == index) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
