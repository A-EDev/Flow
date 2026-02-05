package com.flow.youtube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.ui.theme.Dimensions
import com.flow.youtube.ui.theme.GridItemSize

@Composable
fun currentGridThumbnailHeight(): Dp {
    val context = LocalContext.current
    val preferences = PlayerPreferences(context)
    val gridSizeString by preferences.gridItemSize.collectAsState(initial = "BIG")
    val gridSize = try {
        GridItemSize.valueOf(gridSizeString)
    } catch (e: Exception) {
        GridItemSize.BIG
    }
    return gridSize.thumbnailHeight
}

@Composable
fun ListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: (@Composable RowScope.() -> Unit)? = null,
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isAvailable: Boolean = true
) {
    val backgroundColor = when {
        isActive && isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        isActive -> MaterialTheme.colorScheme.secondaryContainer
        isSelected -> MaterialTheme.colorScheme.inversePrimary.copy(alpha = 0.4f)
        else -> null
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(Dimensions.ListItemHeight)
            .padding(horizontal = 8.dp)
            .then(
                if (backgroundColor != null) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(backgroundColor)
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            modifier = Modifier.padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            thumbnailContent()
            
            if (!isAvailable) {
                Box(
                    modifier = Modifier
                        .size(Dimensions.ListThumbnailSize)
                        .clip(RoundedCornerShape(Dimensions.ThumbnailCornerRadius))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                }
            }
        }
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isActive) MaterialTheme.colorScheme.primary 
                       else MaterialTheme.colorScheme.onBackground
            )
            
            if (subtitle != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    subtitle()
                }
            }
        }
        
        trailingContent()
    }
}

@Composable
fun ListItem(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean = false,
    isActive: Boolean = false
) = ListItem(
    title = title,
    subtitle = {
        badges()
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = trailingContent,
    modifier = modifier,
    isSelected = isSelected,
    isActive = isActive
)

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false
) {
    val gridHeight = currentGridThumbnailHeight()
    
    Column(
        modifier = if (fillMaxWidth) {
            modifier
                .padding(Dimensions.ItemSpacing)
                .fillMaxWidth()
        } else {
            modifier
                .padding(Dimensions.ItemSpacing)
                .width(gridHeight * thumbnailRatio)
        }
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = if (fillMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.height(gridHeight)
            }
                .aspectRatio(thumbnailRatio)
        ) {
            thumbnailContent()
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        title()
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            badges()
            subtitle()
        }
    }
}

@Composable
fun GridItem(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false
) = GridItem(
    modifier = modifier,
    title = {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    },
    subtitle = {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    },
    badges = badges,
    thumbnailContent = thumbnailContent,
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridItem(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    thumbnailHeight: Dp,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .width(thumbnailHeight * aspectRatio)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .height(thumbnailHeight)
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(Dimensions.ThumbnailCornerRadius))
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListItem(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(Dimensions.ListItemHeight)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp)
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(Dimensions.ListThumbnailSize)
                .clip(RoundedCornerShape(Dimensions.ThumbnailCornerRadius))
        )
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) MaterialTheme.colorScheme.primary 
                       else MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
