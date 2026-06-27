package io.github.aedev.flow.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.player.SleepTimerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SleepTimerSheet(
    onDismiss: () -> Unit,
    expandedHeight: Dp? = null,
    enableVerticalDismiss: Boolean = true,
    asBottomSheet: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isActive = SleepTimerManager.isActive
    val pauseAtEndOfMedia = SleepTimerManager.pauseAtEndOfMedia
    val triggerTimeMs = SleepTimerManager.triggerTimeMs

    var remainingMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isActive, triggerTimeMs) {
        if (isActive && triggerTimeMs > 0L) {
            while (true) {
                remainingMs = (triggerTimeMs - System.currentTimeMillis()).coerceAtLeast(0L)
                if (remainingMs == 0L) break
                delay(500L)
            }
        } else {
            remainingMs = 0L
        }
    }

    var sliderValue by remember { mutableFloatStateOf(30f) }
    var customInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf(false) }
    var closeApp by remember { mutableStateOf(SleepTimerManager.closeAppOnExpiry) }

    val content: @Composable (Modifier) -> Unit = { contentModifier ->
        SleepTimerSheetContent(
            isActive = isActive,
            pauseAtEndOfMedia = pauseAtEndOfMedia,
            remainingMs = remainingMs,
            sliderValue = sliderValue,
            onSliderChange = { value ->
                sliderValue = value
                if (customInput.isEmpty() || customInput.toIntOrNull() != null) {
                    customInput = value.roundToInt().toString()
                    inputError = false
                }
            },
            customInput = customInput,
            onCustomInputChange = { text ->
                customInput = text
                inputError = false
                val parsed = text.toIntOrNull()
                if (parsed != null && parsed in 1..1440) {
                    sliderValue = parsed.toFloat().coerceIn(5f, 120f)
                }
            },
            inputError = inputError,
            onEndOfMedia = {
                SleepTimerManager.startEndOfMedia(closeApp)
                onDismiss()
            },
            onCancel = onDismiss,
            onStart = {
                val minutes = customInput.toIntOrNull()
                when {
                    customInput.isNotEmpty() && (minutes == null || minutes < 1 || minutes > 1440) -> {
                        inputError = true
                    }
                    customInput.isNotEmpty() && minutes != null -> {
                        SleepTimerManager.start(minutes, closeApp)
                        onDismiss()
                    }
                    else -> {
                        SleepTimerManager.start(sliderValue.roundToInt(), closeApp)
                        onDismiss()
                    }
                }
            },
            closeAppOnExpiry = closeApp,
            onCloseAppToggle = { closeApp = it },
            onReset = {
                SleepTimerManager.start(sliderValue.roundToInt(), closeApp)
            },
            onCancelTimer = {
                SleepTimerManager.cancel()
                onDismiss()
            },
            modifier = contentModifier
        )
    }

    if (asBottomSheet) {
        FlowSleepTimerBottomSheet(
            onDismiss = onDismiss,
            expandedHeight = expandedHeight,
            enableVerticalDismiss = enableVerticalDismiss,
            modifier = modifier,
            content = content
        )
    } else {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SleepTimerHeader(
                    onDismiss = onDismiss,
                    showDragHandle = false,
                    modifier = Modifier.fillMaxWidth()
                )
                content(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                )
            }
        }
    }
}

@Composable
private fun FlowSleepTimerBottomSheet(
    onDismiss: () -> Unit,
    expandedHeight: Dp?,
    enableVerticalDismiss: Boolean,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val sheetExpandedHeight = expandedHeight ?: (configuration.screenHeightDp.dp * 0.75f)
    val expandedHeightPx = with(density) { sheetExpandedHeight.toPx() }
    val dismissThresholdPx = expandedHeightPx * 0.55f
    val sheetHeightPx = remember { Animatable(0f) }
    var isAnimatingOut by remember { mutableStateOf(false) }

    fun animateToExpanded() {
        if (!enableVerticalDismiss) {
            coroutineScope.launch { sheetHeightPx.snapTo(expandedHeightPx) }
            return
        }
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = expandedHeightPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    fun animateToDismiss() {
        if (isAnimatingOut) return
        if (!enableVerticalDismiss) {
            latestOnDismiss()
            return
        }
        isAnimatingOut = true
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            latestOnDismiss()
        }
    }

    LaunchedEffect(expandedHeightPx) {
        isAnimatingOut = false
        sheetHeightPx.updateBounds(lowerBound = 0f, upperBound = expandedHeightPx)
        if (!enableVerticalDismiss) {
            sheetHeightPx.snapTo(expandedHeightPx)
            return@LaunchedEffect
        }
        if (sheetHeightPx.value == 0f) {
            sheetHeightPx.snapTo(0f)
        }
        sheetHeightPx.animateTo(
            targetValue = expandedHeightPx,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    BackHandler(onBack = ::animateToDismiss)

    val headerDragModifier = if (enableVerticalDismiss) Modifier.pointerInput(expandedHeightPx, dismissThresholdPx, isAnimatingOut) {
        val velocityTracker = VelocityTracker()
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                if (isAnimatingOut) return@detectVerticalDragGestures
                velocityTracker.addPointerInputChange(change)
                coroutineScope.launch {
                    val nextValue = (sheetHeightPx.value - dragAmount).coerceIn(0f, expandedHeightPx)
                    sheetHeightPx.snapTo(nextValue)
                }
            },
            onDragCancel = {
                velocityTracker.resetTracking()
                if (!isAnimatingOut) animateToExpanded()
            },
            onDragEnd = {
                val velocityY = velocityTracker.calculateVelocity().y
                velocityTracker.resetTracking()
                when {
                    velocityY > 1200f || sheetHeightPx.value < dismissThresholdPx -> animateToDismiss()
                    else -> animateToExpanded()
                }
            }
        )
    } else Modifier

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { sheetHeightPx.value.toDp() }),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                SleepTimerHeader(
                    onDismiss = ::animateToDismiss,
                    showDragHandle = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(headerDragModifier)
                )
                content(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerHeader(
    onDismiss: () -> Unit,
    showDragHandle: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (showDragHandle) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                BottomSheetDefaults.DragHandle()
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = if (showDragHandle) 4.dp else 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Bedtime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
    }
}

@Composable
private fun SleepTimerSheetContent(
    isActive: Boolean,
    pauseAtEndOfMedia: Boolean,
    remainingMs: Long,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    customInput: String,
    onCustomInputChange: (String) -> Unit,
    inputError: Boolean,
    onEndOfMedia: () -> Unit,
    onCancel: () -> Unit,
    onStart: () -> Unit,
    closeAppOnExpiry: Boolean,
    onCloseAppToggle: (Boolean) -> Unit,
    onReset: () -> Unit,
    onCancelTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AnimatedContent(targetState = isActive, label = "sleepTimerContent") { active ->
            if (active) {
                ActiveTimerContent(
                    pauseAtEndOfMedia = pauseAtEndOfMedia,
                    remainingMs = remainingMs,
                    closeAppOnExpiry = closeAppOnExpiry,
                    onReset = onReset,
                    onCancel = onCancelTimer
                )
            } else {
                InactiveTimerContent(
                    sliderValue = sliderValue,
                    onSliderChange = onSliderChange,
                    customInput = customInput,
                    onCustomInputChange = onCustomInputChange,
                    inputError = inputError,
                    onEndOfMedia = onEndOfMedia,
                    onCancel = onCancel,
                    onStart = onStart,
                    closeAppOnExpiry = closeAppOnExpiry,
                    onCloseAppToggle = onCloseAppToggle
                )
            }
        }
    }
}

@Composable
private fun ActiveTimerContent(
    pauseAtEndOfMedia: Boolean,
    remainingMs: Long,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    closeAppOnExpiry: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.sleep_timer_stops_in),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (pauseAtEndOfMedia) {
                        stringResource(R.string.sleep_timer_end_of_media)
                    } else {
                        formatCountdown(remainingMs)
                    },
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (closeAppOnExpiry) {
            Text(
                text = stringResource(R.string.sleep_timer_will_close_app),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                enabled = !pauseAtEndOfMedia
            ) {
                Text(stringResource(R.string.sleep_timer_reset))
            }
            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.sleep_timer_cancel_timer))
            }
        }
    }
}

@Composable
private fun InactiveTimerContent(
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    customInput: String,
    onCustomInputChange: (String) -> Unit,
    inputError: Boolean,
    onEndOfMedia: () -> Unit,
    onCancel: () -> Unit,
    onStart: () -> Unit,
    closeAppOnExpiry: Boolean,
    onCloseAppToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer_duration),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.sleep_timer_minutes, sliderValue.roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = sliderValue,
                    onValueChange = onSliderChange,
                    valueRange = 5f..120f,
                    steps = 22,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer_min_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.sleep_timer_max_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        OutlinedTextField(
            value = customInput,
            onValueChange = onCustomInputChange,
            label = { Text(stringResource(R.string.sleep_timer_custom_label)) },
            supportingText = if (inputError) {
                { Text(stringResource(R.string.sleep_timer_input_error)) }
            } else null,
            isError = inputError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingIcon = {
                Text(
                    text = stringResource(R.string.sleep_timer_unit),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedButton(
            onClick = onEndOfMedia,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.Bedtime,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sleep_timer_end_of_song))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sleep_timer_close_app),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.sleep_timer_close_app_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = closeAppOnExpiry,
                    onCheckedChange = onCloseAppToggle
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = onStart,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.sleep_timer_start))
            }
        }
    }
}

private fun formatCountdown(ms: Long): String {
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
