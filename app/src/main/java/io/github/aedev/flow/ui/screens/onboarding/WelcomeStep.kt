@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowPopIn

internal val HeroStageSize = 300.dp
private val SatelliteIconSize = 24.dp
private val BrandLogoWidth = 26.dp
private val StageTopPadding = 24.dp
private val TextSpacing = 10.dp
private val BrandSpacing = 8.dp

private class Satellite(
    val shape: RoundedPolygon,
    val icon: ImageVector,
    val size: Dp,
    val offset: DpOffset,
)

private val Satellites =
    listOf(
        Satellite(MaterialShapes.Cookie6Sided, Icons.Outlined.MusicNote, 64.dp, DpOffset((-112).dp, (-96).dp)),
        Satellite(MaterialShapes.Clover4Leaf, Icons.Outlined.SmartDisplay, 56.dp, DpOffset(116.dp, (-104).dp)),
        Satellite(MaterialShapes.Circle, Icons.Outlined.SlowMotionVideo, 60.dp, DpOffset(118.dp, 96.dp)),
    )

@Composable
internal fun WelcomeStep(
    hero: HeroSlot,
    contentPadding: PaddingValues,
) {
    BookendLayout(
        contentPadding = contentPadding,
        stage = {
            hero(HeroLarge)
            Satellites.forEachIndexed { index, satellite ->
                FlowPopIn(index + 1, Modifier.offset(satellite.offset.x, satellite.offset.y)) {
                    Surface(
                        shape = satellite.shape.toShape(),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(satellite.size),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(satellite.icon, contentDescription = null, modifier = Modifier.size(SatelliteIconSize))
                        }
                    }
                }
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(BrandSpacing), verticalAlignment = Alignment.CenterVertically) {
            FlowLogo(Modifier.width(BrandLogoWidth))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The Welcome and Ready layout: a stage for the large hero at the top and the text at the bottom,
 * within thumb reach. It scrolls instead of overlapping when the window is short.
 */
@Composable
internal fun BookendLayout(
    contentPadding: PaddingValues,
    stage: @Composable () -> Unit,
    text: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(contentPadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = StageTopPadding),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(HeroStageSize), contentAlignment = Alignment.Center) { stage() }
            }
            Column(verticalArrangement = Arrangement.spacedBy(TextSpacing)) { text() }
        }
    }
}
