package io.github.aedev.flow.ui.components

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.aedev.flow.R

private const val SPLASH_ICON_NAMESPACE = "io.github.aedev.flow"

private data class SplashIconOption(
    val componentSuffix: String,
    val drawableRes: Int,
    /** When true, preview bg uses MaterialTheme dynamic colors (Material You variant). */
    val isDynamic: Boolean = false
)

private val SPLASH_ICONS = listOf(
    SplashIconOption(".IconFlowRed",    R.drawable.ic_flow_logo),
    SplashIconOption(".IconFlowLight",  R.drawable.ic_flow_logo),
    SplashIconOption(".IconAmoled",     R.drawable.splash_icon_amoled),
    SplashIconOption(".IconMonochrome", R.drawable.splash_icon_monochrome),
    SplashIconOption(".IconGhost",      R.drawable.splash_icon_ghost),
    SplashIconOption(".IconDynamic",    R.drawable.ic_launcher_dynamic_foreground, isDynamic = true),
    SplashIconOption(".IconMaterialSky", R.drawable.ic_flow_logo),
    SplashIconOption(".IconMaterialMint", R.drawable.ic_flow_logo)
)

@Composable
fun FlowSplashScreen(
    onAnimationFinished: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val textColor = colorScheme.onBackground
    val loadingTrackColor = colorScheme.onBackground.copy(
        alpha = if (colorScheme.background.luminance() < 0.5f) 0.22f else 0.12f
    )
    val loadingGradient = listOf(
        colorScheme.primary,
        colorScheme.tertiary
    )

    // Detect the currently active app icon
    val activeIcon = remember {
        val pm = context.packageManager
        val pkg = context.packageName
        SPLASH_ICONS.firstOrNull { option ->
            val cn = ComponentName(pkg, "$SPLASH_ICON_NAMESPACE${option.componentSuffix}")
            pm.getComponentEnabledSetting(cn) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: SPLASH_ICONS.first()
    }
    // --- Animation States ---
    val scale = remember { Animatable(0f) }      // For the Logo Pop
    val lineProgress = remember { Animatable(0f) } // For the Red Line
    val alpha = remember { Animatable(1f) }      // For the Screen Fade Out
    
    // --- The Choreography ---
    LaunchedEffect(key1 = true) {
        // 1. Logo Springs In (0ms -> 600ms)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )

        // 2. The Line Grows (Wait 200ms, then grow)
        launch {
            delay(200)
            lineProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
            )
        }

        // 3. Wait for app to be ready, then Fade Out
        delay(1500) // Adjust this based on your actual data loading time
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 500)
        )
        
        // 4. Tell MainActivity to remove the Splash
        onAnimationFinished()
    }

    // --- The UI ---
    // Only render if we are visible
    if (alpha.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .alpha(alpha.value), // Controls the fade out
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 1. The Logo — rendered differently for static vs dynamic icons
                if (activeIcon.isDynamic) {
                    // Material You: use the same padded foreground as Android themed icons.
                    Box(
                        modifier = Modifier
                            .scale(scale.value)
                            .size(90.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = activeIcon.drawableRes),
                            contentDescription = "Flow Logo",
                            colorFilter = ColorFilter.tint(colorScheme.onSecondaryContainer),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Image(
                        painter = painterResource(id = activeIcon.drawableRes),
                        contentDescription = "Flow Logo",
                        modifier = Modifier
                            .scale(scale.value)
                            .size(90.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. The Text (Optional)
                Text(
                    text = "Flow",
                    color = textColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.alpha(scale.value)
                )

                Spacer(modifier = Modifier.height(48.dp))
            }

            // 3. The "Flow" Loading Line
            // Positioned slightly below center
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 180.dp) 
                    .width(180.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(loadingTrackColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(lineProgress.value) // The growing animation
                        .clip(CircleShape)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = loadingGradient
                            )
                        )
                )
            }
        }
    }
}
