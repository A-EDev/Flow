package io.github.aedev.flow.ui.screens.player.state

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.aedev.flow.ui.components.CommentSortFilter
import io.github.aedev.flow.ui.components.videoplayer.subtitle.SubtitleStyle
import org.junit.Test

/**
 * Pins the exact shape of [PlayerScreenState] ahead of its replacement: what every property starts
 * as and precisely which ones each mutator touches. The tables are exhaustive on purpose so the
 * sealed-state successor can be checked row by row against the same expectations.
 */
class PlayerScreenStateTest {
    private val defaults: Map<String, Any?> =
        mapOf(
            "showControls" to true,
            "isTouchLocked" to false,
            "lockOverlayRevealSignal" to 0,
            "isFullscreen" to false,
            "isFullscreenPortrait" to false,
            "isScrubbing" to false,
            "isSeekDragging" to false,
            "seekDragTargetMs" to 0L,
            "seekDragDeltaMs" to 0L,
            "currentPosition" to 0L,
            "bufferedPosition" to 0L,
            "duration" to 0L,
            "showQualitySelector" to false,
            "showAudioTrackSelector" to false,
            "showSubtitleSelector" to false,
            "showSettingsMenu" to false,
            "showDownloadDialog" to false,
            "showPlaybackSpeedSelector" to false,
            "showSubtitleStyleCustomizer" to false,
            "showSleepTimerSheet" to false,
            "showDlnaDialog" to false,
            "showQuickActions" to false,
            "showCommentsSheet" to false,
            "showDescriptionSheet" to false,
            "showChaptersSheet" to false,
            "showPlaylistQueueSheet" to false,
            "showLiveChatSheet" to false,
            "showLiveChatPanel" to true,
            "showLiveChatFullscreen" to false,
            "showCommentsFullscreen" to false,
            "commentSortFilter" to CommentSortFilter.TOP,
            "brightnessLevel" to 0.5f,
            "volumeLevel" to 0.5f,
            "maxVolumeLevel" to 2.0f,
            "showBrightnessOverlay" to false,
            "showVolumeOverlay" to false,
            "showSeekForwardAnimation" to false,
            "seekAccumulation" to 10,
            "lastSeekTime" to 0L,
            "showSeekBackAnimation" to false,
            "subtitlesEnabled" to false,
            "selectedSubtitleUrl" to null,
            "subtitleStyle" to SubtitleStyle(),
            "resizeMode" to 0,
            "zoomScale" to 1f,
            "zoomOffsetX" to 0f,
            "zoomOffsetY" to 0f,
            "showZoomIndicator" to false,
            "zoomIndicatorSequence" to 0,
            "exitDragOffsetY" to 0f,
            "exitDragProgress" to 0f,
            "isSpeedBoostActive" to false,
            "normalSpeed" to 1.0f,
            "showShortsPrompt" to false,
            "hasShownShortsPrompt" to false,
        )

    private val resetForNewVideoTouches =
        setOf(
            "showControls",
            "isScrubbing",
            "isSeekDragging",
            "seekDragTargetMs",
            "seekDragDeltaMs",
            "isFullscreenPortrait",
            "isTouchLocked",
            "lockOverlayRevealSignal",
            "currentPosition",
            "duration",
            "subtitlesEnabled",
            "selectedSubtitleUrl",
            "showBrightnessOverlay",
            "showVolumeOverlay",
            "showSeekBackAnimation",
            "showSeekForwardAnimation",
            "hasShownShortsPrompt",
            "showShortsPrompt",
            "showPlaylistQueueSheet",
            "showDownloadDialog",
            "showQualitySelector",
            "showAudioTrackSelector",
            "showSubtitleSelector",
            "showSettingsMenu",
            "showPlaybackSpeedSelector",
            "showSubtitleStyleCustomizer",
            "showSleepTimerSheet",
            "showDlnaDialog",
            "showQuickActions",
            "showCommentsSheet",
            "showDescriptionSheet",
            "showChaptersSheet",
            "showLiveChatSheet",
            "showLiveChatPanel",
            "showLiveChatFullscreen",
            "showCommentsFullscreen",
            "zoomScale",
            "zoomOffsetX",
            "zoomOffsetY",
            "showZoomIndicator",
            "zoomIndicatorSequence",
            "exitDragOffsetY",
            "exitDragProgress",
        )

    // Pins current behaviour: bufferedPosition, isSpeedBoostActive, lastSeekTime and seekAccumulation
    // survive a video change alongside the deliberately persistent display and gesture preferences.
    private val resetForNewVideoLeaves =
        setOf(
            "isFullscreen",
            "bufferedPosition",
            "commentSortFilter",
            "brightnessLevel",
            "volumeLevel",
            "maxVolumeLevel",
            "seekAccumulation",
            "lastSeekTime",
            "subtitleStyle",
            "resizeMode",
            "isSpeedBoostActive",
            "normalSpeed",
        )

    private val dismissMediaSheetsClears =
        setOf(
            "showCommentsSheet",
            "showDescriptionSheet",
            "showChaptersSheet",
            "showLiveChatSheet",
            "showLiveChatFullscreen",
            "showCommentsFullscreen",
            "showPlaylistQueueSheet",
            "showSettingsMenu",
            "showQualitySelector",
            "showAudioTrackSelector",
            "showSubtitleSelector",
            "showPlaybackSpeedSelector",
            "showSubtitleStyleCustomizer",
        )

    private val dismissMediaSheetsLeavesOpen =
        setOf(
            "showSleepTimerSheet",
            "showDownloadDialog",
            "showDlnaDialog",
            "showQuickActions",
        )

    /** Every snapshot-backed property except the wall-clock timestamp, keyed by name. */
    private fun PlayerScreenState.snapshot(): Map<String, Any?> =
        mapOf(
            "showControls" to showControls,
            "isTouchLocked" to isTouchLocked,
            "lockOverlayRevealSignal" to lockOverlayRevealSignal,
            "isFullscreen" to isFullscreen,
            "isFullscreenPortrait" to isFullscreenPortrait,
            "isScrubbing" to isScrubbing,
            "isSeekDragging" to isSeekDragging,
            "seekDragTargetMs" to seekDragTargetMs,
            "seekDragDeltaMs" to seekDragDeltaMs,
            "currentPosition" to currentPosition,
            "bufferedPosition" to bufferedPosition,
            "duration" to duration,
            "showQualitySelector" to showQualitySelector,
            "showAudioTrackSelector" to showAudioTrackSelector,
            "showSubtitleSelector" to showSubtitleSelector,
            "showSettingsMenu" to showSettingsMenu,
            "showDownloadDialog" to showDownloadDialog,
            "showPlaybackSpeedSelector" to showPlaybackSpeedSelector,
            "showSubtitleStyleCustomizer" to showSubtitleStyleCustomizer,
            "showSleepTimerSheet" to showSleepTimerSheet,
            "showDlnaDialog" to showDlnaDialog,
            "showQuickActions" to showQuickActions,
            "showCommentsSheet" to showCommentsSheet,
            "showDescriptionSheet" to showDescriptionSheet,
            "showChaptersSheet" to showChaptersSheet,
            "showPlaylistQueueSheet" to showPlaylistQueueSheet,
            "showLiveChatSheet" to showLiveChatSheet,
            "showLiveChatPanel" to showLiveChatPanel,
            "showLiveChatFullscreen" to showLiveChatFullscreen,
            "showCommentsFullscreen" to showCommentsFullscreen,
            "commentSortFilter" to commentSortFilter,
            "brightnessLevel" to brightnessLevel,
            "volumeLevel" to volumeLevel,
            "maxVolumeLevel" to maxVolumeLevel,
            "showBrightnessOverlay" to showBrightnessOverlay,
            "showVolumeOverlay" to showVolumeOverlay,
            "showSeekForwardAnimation" to showSeekForwardAnimation,
            "seekAccumulation" to seekAccumulation,
            "lastSeekTime" to lastSeekTime,
            "showSeekBackAnimation" to showSeekBackAnimation,
            "subtitlesEnabled" to subtitlesEnabled,
            "selectedSubtitleUrl" to selectedSubtitleUrl,
            "subtitleStyle" to subtitleStyle,
            "resizeMode" to resizeMode,
            "zoomScale" to zoomScale,
            "zoomOffsetX" to zoomOffsetX,
            "zoomOffsetY" to zoomOffsetY,
            "showZoomIndicator" to showZoomIndicator,
            "zoomIndicatorSequence" to zoomIndicatorSequence,
            "exitDragOffsetY" to exitDragOffsetY,
            "exitDragProgress" to exitDragProgress,
            "isSpeedBoostActive" to isSpeedBoostActive,
            "normalSpeed" to normalSpeed,
            "showShortsPrompt" to showShortsPrompt,
            "hasShownShortsPrompt" to hasShownShortsPrompt,
        )

    /** Moves every property off its default so an untouched field is detectable after a mutator. */
    private fun PlayerScreenState.dirtyEverything() {
        showControls = false
        isTouchLocked = true
        lockOverlayRevealSignal = 3
        isFullscreen = true
        isFullscreenPortrait = true
        lastInteractionTimestamp = 1L
        isScrubbing = true
        isSeekDragging = true
        seekDragTargetMs = 1_234L
        seekDragDeltaMs = -500L
        currentPosition = 90_000L
        bufferedPosition = 120_000L
        duration = 600_000L
        showQualitySelector = true
        showAudioTrackSelector = true
        showSubtitleSelector = true
        showSettingsMenu = true
        showDownloadDialog = true
        showPlaybackSpeedSelector = true
        showSubtitleStyleCustomizer = true
        showSleepTimerSheet = true
        showDlnaDialog = true
        showQuickActions = true
        showCommentsSheet = true
        showDescriptionSheet = true
        showChaptersSheet = true
        showPlaylistQueueSheet = true
        showLiveChatSheet = true
        showLiveChatPanel = false
        showLiveChatFullscreen = true
        showCommentsFullscreen = true
        commentSortFilter = CommentSortFilter.NEWEST
        brightnessLevel = 0.9f
        volumeLevel = 0.1f
        maxVolumeLevel = 1.0f
        showBrightnessOverlay = true
        showVolumeOverlay = true
        showSeekForwardAnimation = true
        seekAccumulation = 30
        lastSeekTime = 77L
        showSeekBackAnimation = true
        subtitlesEnabled = true
        selectedSubtitleUrl = "https://example.invalid/en.vtt"
        subtitleStyle = SubtitleStyle(fontSize = 22f)
        resizeMode = 2
        zoomScale = 2.5f
        zoomOffsetX = 40f
        zoomOffsetY = -40f
        showZoomIndicator = true
        zoomIndicatorSequence = 4
        exitDragOffsetY = 300f
        exitDragProgress = 0.4f
        isSpeedBoostActive = true
        normalSpeed = 1.5f
        showShortsPrompt = true
        hasShownShortsPrompt = true
    }

    @Test
    fun `a fresh state matches the defaults table`() {
        val before = System.currentTimeMillis()
        val state = PlayerScreenState()
        val after = System.currentTimeMillis()

        assertThat(state.snapshot()).containsExactlyEntriesIn(defaults)
        assertThat(state.lastInteractionTimestamp).isAtLeast(before)
        assertThat(state.lastInteractionTimestamp).isAtMost(after)
    }

    @Test
    fun `the dirty fixture moves every property off its default`() {
        val dirty = PlayerScreenState().apply { dirtyEverything() }.snapshot()

        assertThat(dirty.keys).containsExactlyElementsIn(defaults.keys)
        dirty.forEach { (name, value) -> assertWithMessage(name).that(value).isNotEqualTo(defaults[name]) }
    }

    @Test
    fun `the reset tables partition every property`() {
        assertThat(resetForNewVideoTouches + resetForNewVideoLeaves).containsExactlyElementsIn(defaults.keys)
        assertThat(resetForNewVideoTouches intersect resetForNewVideoLeaves).isEmpty()
    }

    @Test
    fun `resetForNewVideo resets exactly the per video fields`() {
        val state = PlayerScreenState().apply { dirtyEverything() }
        val dirty = state.snapshot()
        val before = System.currentTimeMillis()

        state.resetForNewVideo()

        val expected = dirty + defaults.filterKeys { it in resetForNewVideoTouches }
        assertThat(state.snapshot()).containsExactlyEntriesIn(expected)
        assertThat(state.lastInteractionTimestamp).isAtLeast(before)
        resetForNewVideoLeaves.forEach { name ->
            assertWithMessage(name).that(state.snapshot()[name]).isEqualTo(dirty[name])
        }
    }

    @Test
    fun `dismissMediaSheets closes exactly the media sheets and pickers`() {
        val state = PlayerScreenState().apply { dirtyEverything() }
        val dirty = state.snapshot()

        state.dismissMediaSheets()

        val expected = dirty + dismissMediaSheetsClears.associateWith { false }
        assertThat(state.snapshot()).containsExactlyEntriesIn(expected)
        dismissMediaSheetsLeavesOpen.forEach { name ->
            assertWithMessage(name).that(state.snapshot()[name]).isEqualTo(true)
        }
        assertThat(state.lastInteractionTimestamp).isEqualTo(1L)
    }

    @Test
    fun `cycleResizeMode wraps fit fill zoom`() {
        val state = PlayerScreenState()

        val seen = mutableListOf(state.resizeMode)
        repeat(3) {
            state.cycleResizeMode()
            seen += state.resizeMode
        }

        assertThat(seen).containsExactly(0, 1, 2, 0).inOrder()
    }

    @Test
    fun `toggleFullscreen flips the flag and always leaves portrait fullscreen`() {
        val state = PlayerScreenState().apply { isFullscreenPortrait = true }

        state.toggleFullscreen()
        assertThat(state.isFullscreen).isTrue()
        assertThat(state.isFullscreenPortrait).isFalse()

        state.isFullscreenPortrait = true
        state.toggleFullscreen()
        assertThat(state.isFullscreen).isFalse()
        assertThat(state.isFullscreenPortrait).isFalse()
    }

    @Test
    fun `enableSubtitles stores the url and disableSubtitles clears it`() {
        val state = PlayerScreenState()

        state.enableSubtitles("https://example.invalid/en.vtt")
        assertThat(state.subtitlesEnabled).isTrue()
        assertThat(state.selectedSubtitleUrl).isEqualTo("https://example.invalid/en.vtt")

        state.disableSubtitles()
        assertThat(state.subtitlesEnabled).isFalse()
        assertThat(state.selectedSubtitleUrl).isNull()
    }

    @Test
    fun `revealLockOverlay counts up without touching the lock itself`() {
        val state = PlayerScreenState().apply { isTouchLocked = true }

        state.revealLockOverlay()
        state.revealLockOverlay()

        assertThat(state.lockOverlayRevealSignal).isEqualTo(2)
        assertThat(state.isTouchLocked).isTrue()
    }

    @Test
    fun `onInteraction refreshes the timestamp and nothing else`() {
        val state = PlayerScreenState().apply { dirtyEverything() }
        val dirty = state.snapshot()
        val before = System.currentTimeMillis()

        state.onInteraction()

        assertThat(state.lastInteractionTimestamp).isAtLeast(before)
        assertThat(state.snapshot()).containsExactlyEntriesIn(dirty)
    }
}
