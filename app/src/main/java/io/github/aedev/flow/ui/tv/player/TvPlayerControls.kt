package io.github.aedev.flow.ui.tv.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.shared.ChannelAvatarImage
import io.github.aedev.flow.ui.components.shared.FocusLiftDefaults
import io.github.aedev.flow.ui.components.shared.MediaSeekBar
import io.github.aedev.flow.ui.components.shared.focusLift
import io.github.aedev.flow.ui.components.shared.videoMetadataLine
import io.github.aedev.flow.ui.components.videoplayer.controls.PlayerControlActions
import io.github.aedev.flow.ui.components.videoplayer.controls.PlayerPillIconButton
import io.github.aedev.flow.ui.components.videoplayer.controls.PlayerTransportControls
import io.github.aedev.flow.ui.components.videoplayer.controls.ScrimEdge
import io.github.aedev.flow.ui.components.videoplayer.controls.playerEdgeScrim
import io.github.aedev.flow.ui.theme.PlayerScrimAffordance
import io.github.aedev.flow.ui.theme.PlayerScrimContent
import io.github.aedev.flow.ui.theme.PlayerScrimContentSecondary
import io.github.aedev.flow.ui.theme.PlayerScrimEdgeGradient
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
import io.github.aedev.flow.utils.formatDuration
import org.schabi.newpipe.extractor.stream.StreamSegment

private val AvatarSize = 40.dp
private val UtilityButtonSize = 48.dp
private val UtilityIconSize = 24.dp
private val TransportWidth = 200.dp
private val ScrimLeadIn = 120.dp

/** The panels and toggles the TV control row opens; the play actions come in [PlayerControlActions]. */
internal class TvPlayerUtilityActions(
    val onCaptions: () -> Unit,
    val onSave: () -> Unit,
    val onComments: () -> Unit,
    val onLiveChat: () -> Unit,
    val onInfo: () -> Unit,
    val onSettings: () -> Unit,
)

/**
 * TV's player controls, built from the phone player's parts: its edge scrim behind everything, the
 * title block, the current chapter, the phone's seek bar and transport, and its round icon buttons
 * for the panels. The focused utility names itself under the row, so each stays a single icon.
 */
@Composable
internal fun BoxScope.TvPlayerControls(
    visible: Boolean,
    video: Video,
    isPlaying: Boolean,
    hasEnded: Boolean,
    isBuffering: Boolean,
    isLive: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    subtitlesAvailable: Boolean,
    subtitlesOn: Boolean,
    positionMs: () -> Long,
    durationMs: () -> Long,
    scrubTargetMs: Long?,
    bufferedFraction: Float,
    chapters: List<StreamSegment>,
    sponsorSegments: List<SponsorBlockSegment>,
    sponsorColors: Map<String, Color>,
    playActions: PlayerControlActions,
    utilityActions: TvPlayerUtilityActions,
    seekBarFocusRequester: FocusRequester,
    playPauseFocusRequester: FocusRequester,
    onSeekBarFocusChanged: (Boolean) -> Unit,
    upNextShown: Boolean,
    upNext: @Composable () -> Unit,
) {
    val dimens = LocalTvDimens.current
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 4 },
        exit = fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { it / 4 },
    ) {
        // The phone player's edge scrim (D4: the one gradient the redesign allows, in the player). It starts
        // well above the title so the text sits on enough of it over a bright frame.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .playerEdgeScrim(ScrimEdge.Bottom, PlayerScrimEdgeGradient)
                    .padding(
                        start = dimens.overscanHorizontal,
                        end = dimens.overscanHorizontal,
                        top = ScrimLeadIn,
                        bottom = dimens.overscanVertical,
                    ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvPlayerTitle(video = video, isLive = isLive)
            val displayedPositionMs = { scrubTargetMs ?: positionMs() }
            val chapterTitle by remember(chapters) {
                derivedStateOf {
                    val positionSeconds = displayedPositionMs() / 1_000L
                    chapters.lastOrNull { it.startTimeSeconds <= positionSeconds }?.title
                }
            }
            chapterTitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = PlayerScrimContentSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!isLive) {
                MediaSeekBar(
                    value = {
                        val duration = durationMs()
                        if (duration > 0) (displayedPositionMs().toFloat() / duration).coerceIn(0f, 1f) else 0f
                    },
                    onValueChange = {},
                    chapters = chapters,
                    sponsorSegments = sponsorSegments,
                    sponsorColors = sponsorColors,
                    duration = durationMs(),
                    bufferedValue = bufferedFraction,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(seekBarFocusRequester)
                            .onFocusChanged { onSeekBarFocusChanged(it.isFocused) },
                )
                TvPlayerTimes(positionMs = displayedPositionMs, durationMs = durationMs, scrubbing = scrubTargetMs != null)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerTransportControls(
                    isPlaying = isPlaying,
                    hasEnded = hasEnded,
                    showBufferingSpinner = isBuffering,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    showSkipButtons = true,
                    showFrameStep = false,
                    actions = playActions,
                    modifier = Modifier.width(TransportWidth),
                    playPauseModifier = Modifier.focusRequester(playPauseFocusRequester),
                )
                Spacer(Modifier.weight(1f))
                TvPlayerUtilities(
                    isLive = isLive,
                    subtitlesAvailable = subtitlesAvailable,
                    subtitlesOn = subtitlesOn,
                    actions = utilityActions,
                )
            }
            // The row opens on Down from the controls, so it is composed only once someone asks for it.
            if (upNextShown) {
                upNext()
            } else {
                Text(
                    text = stringResource(R.string.up_next),
                    style = MaterialTheme.typography.labelLarge,
                    color = PlayerScrimContentSecondary,
                )
            }
        }
    }
}

@Composable
private fun TvPlayerTitle(
    video: Video,
    isLive: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatarImage(
            url = video.channelThumbnailUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .size(AvatarSize)
                    .clip(CircleShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleLarge,
                color = PlayerScrimContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    if (isLive) {
                        listOf(video.channelName, stringResource(R.string.status_live)).filter(String::isNotBlank).joinToString(" • ")
                    } else {
                        videoMetadataLine(video = video, isUpcoming = false, includeChannel = true)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = PlayerScrimContentSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Elapsed and total time, recomposed once a second at most. */
@Composable
private fun TvPlayerTimes(
    positionMs: () -> Long,
    durationMs: () -> Long,
    scrubbing: Boolean,
) {
    val positionSeconds by remember { derivedStateOf { (positionMs() / 1_000L).toInt() } }
    val durationSeconds by remember { derivedStateOf { (durationMs() / 1_000L).toInt() } }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = formatDuration(positionSeconds.coerceAtLeast(0)),
            style = MaterialTheme.typography.labelLarge,
            color = if (scrubbing) MaterialTheme.colorScheme.primary else PlayerScrimContent,
        )
        Text(
            text = formatDuration(durationSeconds.coerceAtLeast(0)),
            style = MaterialTheme.typography.labelLarge,
            color = PlayerScrimContentSecondary,
        )
    }
}

@Composable
private fun TvPlayerUtilities(
    isLive: Boolean,
    subtitlesAvailable: Boolean,
    subtitlesOn: Boolean,
    actions: TvPlayerUtilityActions,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (subtitlesAvailable) {
            TvPlayerUtility(
                icon = Icons.Outlined.ClosedCaption,
                label = stringResource(R.string.filter_subtitles),
                onClick = actions.onCaptions,
                active = subtitlesOn,
            )
        }
        TvPlayerUtility(Icons.AutoMirrored.Outlined.PlaylistAdd, stringResource(R.string.save), actions.onSave)
        if (isLive) {
            TvPlayerUtility(Icons.Outlined.Forum, stringResource(R.string.live_chat), actions.onLiveChat)
        } else {
            TvPlayerUtility(Icons.AutoMirrored.Outlined.Comment, stringResource(R.string.comments), actions.onComments)
        }
        TvPlayerUtility(Icons.Outlined.Info, stringResource(R.string.description), actions.onInfo)
        TvPlayerUtility(Icons.Outlined.Settings, stringResource(R.string.player_settings), actions.onSettings)
    }
}

/** One of the phone player's round buttons; on focus it lifts and its name appears beneath it. */
@Composable
private fun TvPlayerUtility(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PlayerPillIconButton(
            onClick = onClick,
            icon = icon,
            contentDescription = label,
            buttonSize = UtilityButtonSize,
            iconSize = UtilityIconSize,
            interactionSource = interactionSource,
            containerColor = if (active) MaterialTheme.colorScheme.primary else PlayerScrimAffordance,
            contentColor = if (active) MaterialTheme.colorScheme.onPrimary else PlayerScrimContent,
            modifier = Modifier.focusLift(interactionSource, FocusLiftDefaults.SMALL_SCALE),
        )
        Text(
            text = if (focused) label else "",
            style = MaterialTheme.typography.labelMedium,
            color = PlayerScrimContent,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
