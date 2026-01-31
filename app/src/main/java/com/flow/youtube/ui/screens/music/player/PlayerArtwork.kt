package com.flow.youtube.ui.screens.music.player

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

@Composable
fun PlayerArtwork(
    thumbnailUrl: String?,
    isVideoMode: Boolean,
    isLoading: Boolean,
    player: Player?,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier
) {
     Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        if (totalDrag > 100) {
                            onSkipPrevious()
                        } else if (totalDrag < -100) {
                            onSkipNext()
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        totalDrag += dragAmount
                        change.consume()
                    }
                )
            }
    ) {
        if (isVideoMode) {
             AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = thumbnailUrl?.replace("w120-h120", "w1000-h1000"),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}
