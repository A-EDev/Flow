package io.github.aedev.flow.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

internal object FlowNavTransitions {
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
            slideInHorizontally(
                initialOffsetX = { (it * 0.06f).toInt() },
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
            )
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing))
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing))
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) +
            slideOutHorizontally(
                targetOffsetX = { (it * 0.06f).toInt() },
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
            )
    }
}
