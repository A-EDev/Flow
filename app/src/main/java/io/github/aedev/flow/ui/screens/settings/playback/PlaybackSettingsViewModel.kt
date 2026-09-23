package io.github.aedev.flow.ui.screens.settings.playback

import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.PlayerOverlayPreferences
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.PlayerRelatedCardStyle
import io.github.aedev.flow.data.lyrics.LyricsProviderRegistry
import io.github.aedev.flow.player.stream.CaptionTrackResolver
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** A lyrics provider as the provider sheet lists it: in the user's order, with its switch. */
data class LyricsProviderState(
    val name: String,
    val enabled: Boolean,
)

/** Player & playback and its Buffer sub-page. */
@HiltViewModel
class PlaybackSettingsViewModel
    @Inject
    constructor(
        private val preferences: PlayerPreferences,
    ) : SettingsViewModel() {
        private val overlay = PlayerOverlayPreferences()
        private val lyricsRegistry = LyricsProviderRegistry.default()

        val backgroundPlay = preferences.backgroundPlayEnabled.asState(false)
        val autoplay = preferences.autoplayEnabled.asState(true)
        val queueAutoplay = preferences.queueAutoplayEnabled.asState(true)
        val autoplayCountdown = preferences.autoplayCountdownSeconds.asState(0)
        val loopAll = preferences.videoLoopEnabled.asState(false)
        val skipSilence = preferences.skipSilenceEnabled.asState(false)
        val playDuringCalls = preferences.playDuringCalls.asState(false)
        val rememberSpeed = preferences.rememberPlaybackSpeed.asState(false)

        val customSpeeds = preferences.customSpeedsEnabled.asState(false)
        val customSpeedPresets =
            preferences.customSpeedPresets
                .map(::parseSpeedPresets)
                .asState(emptyList())
        val speedSlider = preferences.speedSliderEnabled.asState(false)
        val longPressSpeed = preferences.longPressPlaybackSpeed.asState(DEFAULT_LONG_PRESS_SPEED)

        val doubleTapSeek = preferences.doubleTapSeekSeconds.asState(DEFAULT_DOUBLE_TAP_SEEK)
        val brightnessGesture = preferences.brightnessSwipeGesturesEnabled.asState(true)
        val rememberBrightness = preferences.rememberBrightnessEnabled.asState(false)
        val volumeGesture = preferences.volumeSwipeGesturesEnabled.asState(true)
        val volumeBoost = preferences.allowVolumeBoost.asState(false)
        val seekGesture = preferences.seekSwipeGesturesEnabled.asState(true)
        val haptics = preferences.playerHapticsEnabled.asState(true)
        val controlsWhileLoading = preferences.showControlsWhileLoading.asState(overlay.showControlsWhileLoading)

        val castButton = preferences.overlayCastEnabled.asState(overlay.castEnabled)
        val captionsButton = preferences.overlayCcEnabled.asState(overlay.captionsEnabled)
        val pipButton = preferences.overlayPipEnabled.asState(overlay.pipEnabled)
        val autoplayButton = preferences.overlayAutoplayEnabled.asState(overlay.autoplayEnabled)
        val sleepTimerButton = preferences.overlaySleepTimerEnabled.asState(overlay.sleepTimerEnabled)
        val lockButton = preferences.overlayLockModeEnabled.asState(false)
        val speedIndicator = preferences.overlaySpeedIndicatorEnabled.asState(overlay.speedIndicatorEnabled)
        val commentsButton = preferences.overlayCommentsEnabled.asState(overlay.commentsEnabled)

        val autoPip = preferences.autoPipEnabled.asState(false)
        val continueWatchingMiniPlayer = preferences.miniPlayerContinueWatchingEnabled.asState(true)
        val restoreMusicMiniPlayer = preferences.showRestoredMusicMiniPlayer.asState(true)

        val audioLanguage = preferences.preferredAudioLanguage.asState(ORIGINAL_AUDIO)
        val subtitleLanguage = preferences.preferredSubtitleLanguage.asState(CaptionTrackResolver.NO_PREFERRED_LANGUAGE)
        val autoCaptions = preferences.autoEnableSubtitles.asState(false)

        val relatedVideos = preferences.showRelatedVideos.asState(true)
        val relatedCardStyle = preferences.playerRelatedCardStyle.asState(PlayerRelatedCardStyle.FULL_WIDTH)
        val titleLines = preferences.videoTitleMaxLines.asState(1)
        val comments = preferences.commentsEnabled.asState(true)
        val commentsPreview = preferences.commentsPreviewEnabled.asState(true)

        val shortsContent = preferences.shortsContentEnabled.asState(true)
        val disableShortsPlayer = preferences.disableShortsPlayer.asState(false)
        val shortsPlayerPrompt = preferences.showShortsPlayerPrompt.asState(true)
        val shortsPlaybackMode = preferences.shortsPlaybackMode.asState(ShortsPlaybackModes.LOOP)
        val shortsAutoScrollSeconds = preferences.shortsAutoScrollSeconds.asState(DEFAULT_SHORTS_AUTO_SCROLL)
        val shortsBackgroundPlay = preferences.shortsBackgroundPlay.asState(false)
        val shortsPip = preferences.shortsPipEnabled.asState(false)
        val shortsContinueIntoFeed = preferences.shortsQueueContinuesIntoFeed.asState(true)

        val endlessRadio = preferences.musicEndlessRadioEnabled.asState(true)
        val lyricsProviders =
            combine(preferences.lyricsProviderOrder, preferences.allLyricsProviderEnabledStates()) { order, enabled ->
                lyricsRegistry.getOrderedProviders(order).map { LyricsProviderState(it.name, enabled[it.name] ?: true) }
            }.asState(lyricsRegistry.getOrderedProviders("").map { LyricsProviderState(it.name, true) })

        fun setBackgroundPlay(value: Boolean) = write { preferences.setBackgroundPlayEnabled(value) }

        fun setAutoplay(value: Boolean) = write { preferences.setAutoplayEnabled(value && !loopAll.value) }

        fun setQueueAutoplay(value: Boolean) = write { preferences.setQueueAutoplayEnabled(value && !loopAll.value) }

        fun setAutoplayCountdown(value: Int) = write { preferences.setAutoplayCountdownSeconds(value) }

        fun setLoopAll(value: Boolean) = write { preferences.setVideoLoopEnabled(value) }

        fun setSkipSilence(value: Boolean) = write { preferences.setSkipSilenceEnabled(value) }

        fun setPlayDuringCalls(value: Boolean) = write { preferences.setPlayDuringCalls(value) }

        fun setRememberSpeed(value: Boolean) = write { preferences.setRememberPlaybackSpeed(value) }

        fun setCustomSpeeds(value: Boolean) = write { preferences.setCustomSpeedsEnabled(value) }

        fun addSpeedPreset(speed: Float) =
            write { preferences.setCustomSpeedPresets(serializeSpeedPresets(customSpeedPresets.value + speed)) }

        fun removeSpeedPreset(speed: Float) =
            write {
                preferences.setCustomSpeedPresets(
                    serializeSpeedPresets(customSpeedPresets.value - speed),
                )
            }

        fun setSpeedSlider(value: Boolean) = write { preferences.setSpeedSliderEnabled(value) }

        fun setLongPressSpeed(value: Float) = write { preferences.setLongPressPlaybackSpeed(value) }

        fun setDoubleTapSeek(value: Int) = write { preferences.setDoubleTapSeekSeconds(value) }

        fun setBrightnessGesture(value: Boolean) = write { preferences.setBrightnessSwipeGesturesEnabled(value) }

        fun setRememberBrightness(value: Boolean) = write { preferences.setRememberBrightnessEnabled(value) }

        fun setVolumeGesture(value: Boolean) = write { preferences.setVolumeSwipeGesturesEnabled(value) }

        fun setVolumeBoost(value: Boolean) = write { preferences.setAllowVolumeBoost(value) }

        fun setSeekGesture(value: Boolean) = write { preferences.setSeekSwipeGesturesEnabled(value) }

        fun setHaptics(value: Boolean) = write { preferences.setPlayerHapticsEnabled(value) }

        fun setControlsWhileLoading(value: Boolean) = write { preferences.setShowControlsWhileLoading(value) }

        fun setCastButton(value: Boolean) = write { preferences.setOverlayCastEnabled(value) }

        fun setCaptionsButton(value: Boolean) = write { preferences.setOverlayCcEnabled(value) }

        fun setPipButton(value: Boolean) =
            write {
                preferences.setOverlayPipEnabled(value)
                preferences.setManualPipButtonEnabled(value)
            }

        fun setAutoplayButton(value: Boolean) = write { preferences.setOverlayAutoplayEnabled(value) }

        fun setSleepTimerButton(value: Boolean) = write { preferences.setOverlaySleepTimerEnabled(value) }

        fun setLockButton(value: Boolean) = write { preferences.setOverlayLockModeEnabled(value) }

        fun setSpeedIndicator(value: Boolean) = write { preferences.setOverlaySpeedIndicatorEnabled(value) }

        fun setCommentsButton(value: Boolean) = write { preferences.setOverlayCommentsEnabled(value) }

        fun setAutoPip(value: Boolean) = write { preferences.setAutoPipEnabled(value) }

        fun setContinueWatchingMiniPlayer(value: Boolean) = write { preferences.setMiniPlayerContinueWatchingEnabled(value) }

        fun setRestoreMusicMiniPlayer(value: Boolean) = write { preferences.setShowRestoredMusicMiniPlayer(value) }

        fun setAudioLanguage(value: String) = write { preferences.setPreferredAudioLanguage(value) }

        fun setSubtitleLanguage(value: String) = write { preferences.setPreferredSubtitleLanguage(value) }

        fun setAutoCaptions(value: Boolean) = write { preferences.setAutoEnableSubtitles(value) }

        fun setRelatedVideos(value: Boolean) = write { preferences.setShowRelatedVideos(value) }

        fun setRelatedCardStyle(value: PlayerRelatedCardStyle) = write { preferences.setPlayerRelatedCardStyle(value) }

        fun setTitleLines(value: Int) = write { preferences.setVideoTitleMaxLines(value) }

        fun setComments(value: Boolean) = write { preferences.setCommentsEnabled(value) }

        fun setCommentsPreview(value: Boolean) = write { preferences.setCommentsPreviewEnabled(value) }

        fun setDisableShortsPlayer(value: Boolean) = write { preferences.setDisableShortsPlayer(value) }

        fun setShortsPlayerPrompt(value: Boolean) = write { preferences.setShowShortsPlayerPrompt(value) }

        fun setShortsPlaybackMode(value: String) = write { preferences.setShortsPlaybackMode(value) }

        fun setShortsAutoScrollSeconds(value: Int) = write { preferences.setShortsAutoScrollSeconds(value) }

        fun setShortsBackgroundPlay(value: Boolean) = write { preferences.setShortsBackgroundPlay(value) }

        fun setShortsPip(value: Boolean) = write { preferences.setShortsPipEnabled(value) }

        fun setShortsContinueIntoFeed(value: Boolean) = write { preferences.setShortsQueueContinuesIntoFeed(value) }

        fun setEndlessRadio(value: Boolean) = write { preferences.setMusicEndlessRadioEnabled(value) }

        fun setLyricsProviderEnabled(
            name: String,
            enabled: Boolean,
        ) = write { preferences.setLyricsProviderEnabled(name, enabled) }

        fun setLyricsProviderOrder(names: List<String>) =
            write { preferences.setLyricsProviderOrder(lyricsRegistry.serializeProviderOrder(names)) }

        companion object {
            const val ORIGINAL_AUDIO = "original"
            const val DEFAULT_LONG_PRESS_SPEED = 2.0f
            const val DEFAULT_DOUBLE_TAP_SEEK = 10
            const val DEFAULT_SHORTS_AUTO_SCROLL = 10
        }
    }

/** The stored values of the Shorts playback mode preference. */
object ShortsPlaybackModes {
    const val LOOP = "loop"
    const val AUTO_NEXT = "auto_next"
    const val AUTO_INTERVAL = "auto_interval"
}
