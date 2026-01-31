package com.flow.youtube.ui.screens.music.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flow.youtube.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerTopBar(
    isVideoMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onMoreOptionsClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (!isVideoMode) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                        onClick = { onModeChange(false) },
                        modifier = Modifier.height(28.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                stringResource(R.string.song),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isVideoMode) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                        onClick = { onModeChange(true) },
                        modifier = Modifier.height(28.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                stringResource(R.string.video),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.close),
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
        },
        actions = {
            IconButton(onClick = onMoreOptionsClick) {
                Icon(
                    Icons.Outlined.MoreVert, 
                    stringResource(R.string.more_options),
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent
        ),
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}
