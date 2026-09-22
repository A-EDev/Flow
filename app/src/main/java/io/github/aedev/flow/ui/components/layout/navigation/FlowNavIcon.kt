package io.github.aedev.flow.ui.components.layout.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp

/**
 * Where an icon starts when its tab becomes selected; it springs back to rest from here, and the
 * spatial spring's overshoot gives each tab its bounce. Translation is a fraction of the icon size.
 */
@Immutable
internal data class NavIconPose(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val translationY: Float = 0f,
    val pivotX: Float = 0.5f,
    val pivotY: Float = 0.5f,
    val slow: Boolean = false,
)

internal val FlowTab.selectionPose: NavIconPose
    get() =
        when (this) {
            FlowTab.Home -> NavIconPose(scaleX = 0.85f, scaleY = 0.85f, translationY = 0.25f)
            FlowTab.Shorts -> NavIconPose(scaleX = 0.8f, scaleY = 0.8f, rotation = -30f)
            FlowTab.Music -> NavIconPose(rotation = 28f, pivotX = 0.35f, pivotY = 0.85f)
            FlowTab.Subscriptions -> NavIconPose(scaleX = 1.25f, scaleY = 0.7f, pivotY = 1f)
            FlowTab.Library -> NavIconPose(rotation = -14f, translationY = 0.15f, pivotY = 1f)
            FlowTab.Search -> NavIconPose(scaleX = 1.2f, scaleY = 1.2f, rotation = -40f, pivotX = 0.4f, pivotY = 0.4f)
            FlowTab.Explore -> NavIconPose(rotation = -360f, slow = true)
        }

internal val OverflowPose = NavIconPose(scaleX = 0.6f, scaleY = 0.6f)

/**
 * A navigation icon that swaps outlined for filled and plays [pose] when it becomes selected. It
 * animates only on that change, never on first composition, and reads its progress in the draw
 * phase, so a settled bar costs nothing.
 */
@Composable
internal fun AnimatedNavIcon(
    selected: Boolean,
    pose: NavIconPose,
    modifier: Modifier = Modifier,
    icon: @Composable (selected: Boolean) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val progress = remember { Animatable(0f) }
    val lastSelected = remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        val becameSelected = selected && !lastSelected.value
        lastSelected.value = selected
        if (becameSelected) {
            progress.snapTo(1f)
            progress.animateTo(0f, if (pose.slow) motion.slowSpatialSpec() else motion.defaultSpatialSpec())
        }
    }

    Box(
        modifier =
            modifier.graphicsLayer {
                val p = progress.value
                scaleX = lerp(1f, pose.scaleX, p)
                scaleY = lerp(1f, pose.scaleY, p)
                rotationZ = pose.rotation * p
                translationY = pose.translationY * size.height * p
                transformOrigin = TransformOrigin(pose.pivotX, pose.pivotY)
            },
    ) {
        Crossfade(targetState = selected, animationSpec = motion.fastEffectsSpec(), label = "navIcon") { filled ->
            icon(filled)
        }
    }
}

@Composable
internal fun FlowTabIcon(
    tab: FlowTab,
    selected: Boolean,
) {
    AnimatedNavIcon(selected = selected, pose = tab.selectionPose) { filled ->
        Icon(imageVector = tab.icon(filled), contentDescription = null)
    }
}
