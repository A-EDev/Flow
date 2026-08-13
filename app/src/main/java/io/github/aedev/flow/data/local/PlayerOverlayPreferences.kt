package io.github.aedev.flow.data.local

data class PlayerOverlayPreferences(
    val castEnabled: Boolean = true,
    val captionsEnabled: Boolean = false,
    val pipEnabled: Boolean = false,
    val autoplayEnabled: Boolean = false,
    val sleepTimerEnabled: Boolean = true,
    val speedIndicatorEnabled: Boolean = false,
    val commentsEnabled: Boolean = true,
    val fullscreenTitleEnabled: Boolean = false,
    val showControlsWhileLoading: Boolean = false,
    val fullscreenSeekbarHorizontalPaddingDp: Int =
        resolveSeekbarHorizontalPaddingDp(
            mode = SeekbarPaddingMode.DEFAULT,
            customPaddingDp = DEFAULT_FULLSCREEN_SEEKBAR_PADDING_DP,
            defaultPaddingDp = DEFAULT_FULLSCREEN_SEEKBAR_PADDING_DP,
            maxPaddingDp = MAX_FULLSCREEN_SEEKBAR_PADDING_DP,
        ),
    val portraitSeekbarHorizontalPaddingDp: Int =
        resolveSeekbarHorizontalPaddingDp(
            mode = resolvePortraitSeekbarPaddingMode(null),
            customPaddingDp = DEFAULT_PORTRAIT_SEEKBAR_PADDING_DP,
            defaultPaddingDp = DEFAULT_PORTRAIT_SEEKBAR_PADDING_DP,
            maxPaddingDp = MAX_PORTRAIT_SEEKBAR_PADDING_DP,
        ),
    /** Per-category ARGB overrides from SponsorBlock settings; absent categories use the defaults. */
    val sponsorCategoryColors: Map<String, Int> = emptyMap(),
)
