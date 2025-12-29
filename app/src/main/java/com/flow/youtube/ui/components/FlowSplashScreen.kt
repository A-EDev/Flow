package com.flow.youtube.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import com.flow.youtube.R

@Composable
fun FlowSplashScreen(
    onAnimationFinished: () -> Unit
) {
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
                .background(Color(0xFF0F0F0F)) // Deep Dark Background
                .alpha(alpha.value), // Controls the fade out
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 1. The Logo Container
                Box(
                    modifier = Modifier
                        .scale(scale.value) // Applies the spring animation
                        .size(90.dp)
                        .background(
                            // Optional: Subtle Gradient Background for the Logo
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF222222), Color(0xFF0F0F0F))
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // YOUR LOGO HERE (Using the new ic_flow_logo)
                    Icon(
                        painter = painterResource(id = R.drawable.ic_flow_logo),
                        contentDescription = "Flow Logo",
                        tint = Color.Unspecified, // Keep SVG colors
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. The Text (Optional)
                Text(
                    text = "Flow",
                    color = Color.White,
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
                    .background(Color(0xFF333333)) // Dark Track
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(lineProgress.value) // The growing animation
                        .clip(CircleShape)
                        .background(
                            // UNIQUE TOUCH: A gradient line instead of flat red
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFF0000), // Red
                                    Color(0xFFFF8A80)  // Lighter Red/Pink tip
                                )
                            )
                        )
                )
            }
        }
    }
}
