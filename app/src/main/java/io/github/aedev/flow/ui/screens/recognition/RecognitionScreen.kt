package io.github.aedev.flow.ui.screens.recognition

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.recognition.RecognitionResult
import io.github.aedev.flow.data.recognition.RecognitionStatus
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion

@Composable
fun RecognitionScreen(
    onBackClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onPlay: (RecognitionResult) -> Unit,
    onSearch: (RecognitionResult) -> Unit,
    autoStart: Boolean = false,
    viewModel: RecognitionViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val reduceMotion = rememberFlowReduceMotion()

    var hasPermission by remember { mutableStateOf(viewModel.hasRecordPermission()) }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            hasPermission = granted
            if (granted) viewModel.startRecognition()
        }

    fun start() {
        if (hasPermission) {
            viewModel.startRecognition()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.clearResultOnEnter()
        if (autoStart && status is RecognitionStatus.Ready) start()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        RecognitionHeader(
            title = stringResource(R.string.recognize_music),
            onBackClick = onBackClick,
        ) {
            IconButton(onClick = onHistoryClick) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = stringResource(R.string.recognition_history),
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val enterDuration = FlowMotion.durationFor(FlowMotion.ENTER_DURATION_MILLIS, reduceMotion)
            val exitDuration = FlowMotion.durationFor(FlowMotion.EXIT_DURATION_MILLIS, reduceMotion)

            AnimatedContent(
                targetState = status,
                transitionSpec = {
                    val enter =
                        fadeIn(
                            tween(
                                durationMillis = enterDuration,
                                easing = FlowMotion.EnterEasing,
                            ),
                        ) +
                            scaleIn(
                                animationSpec =
                                    tween(
                                        durationMillis = enterDuration,
                                        easing = FlowMotion.EnterEasing,
                                    ),
                                initialScale = 0.98f,
                            )
                    val exit =
                        fadeOut(
                            tween(
                                durationMillis = exitDuration,
                                easing = FlowMotion.ExitEasing,
                            ),
                        ) +
                            scaleOut(
                                animationSpec =
                                    tween(
                                        durationMillis = exitDuration,
                                        easing = FlowMotion.ExitEasing,
                                    ),
                                targetScale = 0.98f,
                            )
                    enter togetherWith exit
                },
                label = "recognitionStatus",
            ) { state ->
                when (state) {
                    is RecognitionStatus.Ready -> {
                        ReadyState(onStart = ::start)
                    }

                    is RecognitionStatus.Listening -> {
                        ListeningState(
                            reduceMotion = reduceMotion,
                            onCancel = viewModel::cancel,
                        )
                    }

                    is RecognitionStatus.Processing -> {
                        ProcessingState(reduceMotion = reduceMotion)
                    }

                    is RecognitionStatus.Success -> {
                        SuccessState(
                            result = state.result,
                            onPlay = onPlay,
                            onSearch = onSearch,
                            onTryAgain = ::start,
                            onClose = viewModel::cancel,
                            onSave = viewModel::saveToHistory,
                        )
                    }

                    is RecognitionStatus.NoMatch -> {
                        MessageState(
                            icon = Icons.Filled.Close,
                            title = stringResource(R.string.no_match_found),
                            message = state.message,
                            onTryAgain = ::start,
                        )
                    }

                    is RecognitionStatus.Error -> {
                        MessageState(
                            icon = Icons.Filled.ErrorOutline,
                            title = stringResource(R.string.recognition_error),
                            message = state.message,
                            onTryAgain = ::start,
                        )
                    }
                }
            }
        }
    }
}

/** Keeps recognition navigation consistent without adding another scaffold or inset layer. */
@Composable
internal fun RecognitionHeader(
    title: String,
    onBackClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
        )
        actions()
    }
}

@Composable
private fun ReadyState(onStart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Surface(
            onClick = onStart,
            modifier = Modifier.size(200.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.tap_to_recognize),
                    modifier = Modifier.size(72.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.tap_to_recognize),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ListeningState(
    reduceMotion: Boolean,
    onCancel: () -> Unit,
) {
    val pulseScale =
        if (reduceMotion) {
            null
        } else {
            val transition = rememberInfiniteTransition(label = "recognitionPulse")
            transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.06f,
                animationSpec =
                    infiniteRepeatable(
                        animation =
                            tween(
                                durationMillis = FlowMotion.EMPHASIZED_DURATION_MILLIS * 3,
                                easing = LinearEasing,
                            ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "pulseScale",
            )
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            modifier = Modifier.size(260.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .size(208.dp)
                        .graphicsLayer {
                            val scale = pulseScale?.value ?: 1f
                            scaleX = scale
                            scaleY = scale
                        },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {}
            Surface(
                modifier = Modifier.size(176.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {}
            Surface(
                onClick = onCancel,
                modifier = Modifier.size(152.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.cancel),
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.listening),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun ProcessingState(reduceMotion: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (reduceMotion) {
                CircularProgressIndicator(
                    progress = { 0.66f },
                    modifier = Modifier.size(144.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(144.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(R.string.processing),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SuccessState(
    result: RecognitionResult,
    onPlay: (RecognitionResult) -> Unit,
    onSearch: (RecognitionResult) -> Unit,
    onTryAgain: () -> Unit,
    onClose: () -> Unit,
    onSave: (RecognitionResult) -> Unit,
) {
    LaunchedEffect(result) { onSave(result) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Card(
            modifier =
                Modifier
                    .size(200.dp)
                    .aspectRatio(1f),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = result.coverArtHqUrl ?: result.coverArtUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Text(
            text = result.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = result.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        result.album?.takeIf { it.isNotBlank() }?.let { album ->
            Text(
                text = album,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(8.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (!result.youtubeVideoId.isNullOrBlank()) {
                Button(
                    onClick = { onPlay(result) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.play))
                }
            }
            FilledTonalButton(
                onClick = { onSearch(result) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.search_in_flow))
            }
            OutlinedButton(
                onClick = onTryAgain,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.try_again))
            }
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun MessageState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    onTryAgain: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(120.dp)
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Button(onClick = onTryAgain) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.try_again))
        }
    }
}
