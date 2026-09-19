package io.github.aedev.flow.ui.components.shorts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.videoplayer.controls.PlayerPillIconButton
import io.github.aedev.flow.ui.theme.PlayerScrimContent

private val TopBarHorizontalPadding = 16.dp
private val BackButtonSize = 40.dp
private val BackIconSize = 24.dp

/**
 * The strip over the top of the reel: a back pill when the queue was opened from somewhere, the
 * tab's own title when it is the Shorts tab. Deliberately not a [androidx.compose.material3.TopAppBar]:
 * it rests on video, so it takes the player's scrim tokens rather than a surface colour.
 */
@Composable
internal fun ShortsTopBar(
    visible: Boolean,
    showBackButton: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = TopBarHorizontalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBackButton) {
            PlayerPillIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.btn_back),
                buttonSize = BackButtonSize,
                iconSize = BackIconSize,
            )
        } else {
            Text(
                text = stringResource(R.string.shorts),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PlayerScrimContent,
            )
        }
    }
}
