package io.github.aedev.flow.ui.screens.player.stage

import android.content.Context
import android.content.res.Configuration
import android.os.Build

private const val LARGE_SCREEN_MIN_WIDTH_DP = 600
private const val ORIENTATION_IGNORED_FROM_SDK = 36

/**
 * Whether fullscreen uses the portrait layout. Where the rotation request is honoured it mirrors the
 * orientation about to be requested, so no landscape frame shows before a phone turns; where
 * Android ignores the request, it follows the window the player actually has.
 */
internal fun usesPortraitFullscreenLayout(
    isFullscreen: Boolean,
    isFullscreenPortrait: Boolean,
    videoAspectRatio: Float,
    orientationRequestIgnored: Boolean,
    windowIsPortrait: Boolean,
): Boolean =
    isFullscreen &&
        if (orientationRequestIgnored) windowIsPortrait else (isFullscreenPortrait || videoAspectRatio < 1f)

/** Android 16 ignores orientation requests on screens 600 dp and wider for apps targeting it. */
internal fun Context.orientationRequestIgnored(configuration: Configuration): Boolean =
    Build.VERSION.SDK_INT >= ORIENTATION_IGNORED_FROM_SDK &&
        applicationInfo.targetSdkVersion >= ORIENTATION_IGNORED_FROM_SDK &&
        configuration.smallestScreenWidthDp >= LARGE_SCREEN_MIN_WIDTH_DP
