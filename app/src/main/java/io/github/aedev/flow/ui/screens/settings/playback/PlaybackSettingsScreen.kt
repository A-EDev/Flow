package io.github.aedev.flow.ui.screens.settings.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.SettingsTarget

/** The pickers Player & playback opens over itself. */
internal enum class PlaybackDialog {
    AUTOPLAY_COUNTDOWN,
    LONG_PRESS_SPEED,
    DOUBLE_TAP_SEEK,
    AUDIO_LANGUAGE,
    SUBTITLE_LANGUAGE,
    SHORTS_MODE,
    LYRICS,
}

/**
 * What the players do: autoplay, speeds, gestures, the buttons on the player, captions, the watch
 * page, the Shorts player and music. How the players look is Appearance › Player appearance.
 */
@Composable
internal fun PlaybackSettingsScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    onNavigate: (SettingsTarget) -> Unit,
    viewModel: PlaybackSettingsViewModel = hiltViewModel(),
) {
    val loopAll by viewModel.loopAll.collectAsStateWithLifecycle()
    val customSpeeds by viewModel.customSpeeds.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val shortsContent by viewModel.shortsContent.collectAsStateWithLifecycle()
    val disableShortsPlayer by viewModel.disableShortsPlayer.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<PlaybackDialog?>(null) }

    SettingsPage(
        title = stringResource(R.string.settings_playback_title),
        onBack = onBack,
        highlight = highlight,
    ) {
        playbackSections(
            viewModel = viewModel,
            state = PlaybackStructure(loopAll, customSpeeds, comments, shortsContent, disableShortsPlayer),
            openDialog = { dialog = it },
            onNavigate = onNavigate,
        )
    }

    dialog?.let { open ->
        PlaybackDialogs(dialog = open, viewModel = viewModel, onDismiss = { dialog = null })
    }
}

/** The values that decide which rows exist or are enabled; every other row reads its own state. */
internal data class PlaybackStructure(
    val loopAll: Boolean,
    val customSpeeds: Boolean,
    val comments: Boolean,
    val shortsContent: Boolean,
    val disableShortsPlayer: Boolean,
)
