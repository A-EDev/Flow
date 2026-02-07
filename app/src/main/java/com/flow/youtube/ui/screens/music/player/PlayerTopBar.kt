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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.youtube.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerTopBar(
    onBackClick: () -> Unit,
    onMoreOptionsClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
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
