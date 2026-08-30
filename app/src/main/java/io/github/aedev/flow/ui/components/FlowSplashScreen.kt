package io.github.aedev.flow.ui.components

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPLASH_ICON_NAMESPACE = "io.github.aedev.flow"
private const val SPLASH_VISIBLE_MILLIS = 900L
private const val SPLASH_LINE_DELAY_MILLIS = 80L

private data class SplashIconOption(
    val componentSuffix: String,
    val drawableRes: Int,
    val isDynamic: Boolean = false,
)

private val SPLASH_ICONS =
    listOf(
        SplashIconOption(".IconFlowRed", R.drawable.ic_flow_logo),
        SplashIconOption(".IconFlowLight", R.drawable.ic_flow_logo),
        SplashIconOption(".IconFlowPlay", R.drawable.ic_fg_flow_play),
        SplashIconOption(".IconAmoled", R.drawable.splash_icon_amoled),
        SplashIconOption(".IconMonochrome", R.drawable.splash_icon_monochrome),
        SplashIconOption(".IconGhost", R.drawable.splash_icon_ghost),
        SplashIconOption(".IconDynamic", R.drawable.ic_launcher_dynamic_foreground, isDynamic = true),
        SplashIconOption(".IconMaterialSky", R.drawable.ic_flow_logo),
        SplashIconOption(".IconMaterialMint", R.drawable.ic_flow_logo),
    )

@Composable
fun FlowSplashScreen(onAnimationFinished: () -> Unit) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val reduceMotion = rememberFlowReduceMotion()
    val latestOnAnimationFinished by rememberUpdatedState(onAnimationFinished)
    val loadingTrackColor =
        colorScheme.onBackground.copy(
            alpha = if (colorScheme.background.luminance() < 0.5f) 0.22f else 0.12f,
        )

    val activeIcon =
        remember(context) {
            val packageManager = context.packageManager
            val packageName = context.packageName
            SPLASH_ICONS.firstOrNull { option ->
                val componentName = ComponentName(packageName, "$SPLASH_ICON_NAMESPACE${option.componentSuffix}")
                packageManager.getComponentEnabledSetting(componentName) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } ?: SPLASH_ICONS.first()
        }

    val scale = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val lineProgress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            alpha.snapTo(0f)
            latestOnAnimationFinished()
            return@LaunchedEffect
        }

        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = FlowMotion.EMPHASIZED_DURATION_MILLIS,
                        easing = FlowMotion.EnterEasing,
                    ),
            )
        }
        launch {
            delay(SPLASH_LINE_DELAY_MILLIS)
            lineProgress.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = FlowMotion.EMPHASIZED_DURATION_MILLIS,
                        easing = FlowMotion.EnterEasing,
                    ),
            )
        }

        delay(SPLASH_VISIBLE_MILLIS)
        alpha.animateTo(
            targetValue = 0f,
            animationSpec =
                tween(
                    durationMillis = FlowMotion.EXIT_DURATION_MILLIS,
                    easing = FlowMotion.ExitEasing,
                ),
        )
        latestOnAnimationFinished()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .graphicsLayer { this.alpha = alpha.value },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (activeIcon.isDynamic) {
                Box(
                    modifier =
                        Modifier
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                            }.size(90.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = activeIcon.drawableRes),
                        contentDescription = stringResource(R.string.ui_flow_logo),
                        colorFilter = ColorFilter.tint(colorScheme.onSecondaryContainer),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Image(
                    painter = painterResource(id = activeIcon.drawableRes),
                    contentDescription = stringResource(R.string.ui_flow_logo),
                    modifier =
                        Modifier
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                            }.size(90.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.app_name),
                color = colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.graphicsLayer { this.alpha = scale.value },
            )

            Spacer(modifier = Modifier.height(48.dp))
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(top = 180.dp)
                    .width(180.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(loadingTrackColor),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            scaleX = lineProgress.value
                        }.background(colorScheme.primary),
            )
        }
    }
}
