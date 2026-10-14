package io.github.aedev.flow.ui.screens.settings.content

import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.aedev.flow.R

/**
 * Whether YouTube links open in Flow. Flow cannot prove it owns youtube.com, so from Android 12 a
 * link reaches it only for the addresses the user turned on under the system's "Open by default".
 */
internal sealed interface LinkHandling {
    /** Before Android 12 there is no per-address choice; the system asks which app to use. */
    data object SystemAsks : LinkHandling

    data object Off : LinkHandling

    data object All : LinkHandling

    data class Some(
        val enabled: Int,
        val total: Int,
    ) : LinkHandling
}

internal fun linkHandling(
    perAddressChoice: Boolean,
    allowed: Boolean,
    enabledAddresses: Int,
    totalAddresses: Int,
): LinkHandling =
    when {
        !perAddressChoice -> LinkHandling.SystemAsks
        !allowed || enabledAddresses == 0 -> LinkHandling.Off
        enabledAddresses >= totalAddresses -> LinkHandling.All
        else -> LinkHandling.Some(enabledAddresses, totalAddresses)
    }

/** Read again whenever the page resumes, so it reflects what the user just changed in the system screen. */
@Composable
internal fun rememberLinkHandlingLabel(): String {
    val context = LocalContext.current
    var handling by remember { mutableStateOf(readLinkHandling(context)) }
    LifecycleResumeEffect(context) {
        handling = readLinkHandling(context)
        onPauseOrDispose { }
    }
    return when (val current = handling) {
        LinkHandling.SystemAsks -> stringResource(R.string.content_settings_open_links_system_asks)
        LinkHandling.Off -> stringResource(R.string.content_settings_open_links_off)
        LinkHandling.All -> stringResource(R.string.content_settings_open_links_all)
        is LinkHandling.Some -> stringResource(R.string.content_settings_open_links_some, current.enabled, current.total)
    }
}

/** Opens the system screen where the user picks which links Flow opens. */
internal fun openLinkSettings(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val openByDefault =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, packageUri)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        }
    runCatching { context.startActivity(openByDefault) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)) } }
}

private fun readLinkHandling(context: Context): LinkHandling {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return LinkHandling.SystemAsks
    val state =
        runCatching {
            context
                .getSystemService(DomainVerificationManager::class.java)
                ?.getDomainVerificationUserState(context.packageName)
        }.getOrNull() ?: return LinkHandling.Off
    val addresses = state.hostToStateMap.values
    return linkHandling(
        perAddressChoice = true,
        allowed = state.isLinkHandlingAllowed,
        enabledAddresses = addresses.count { it != DomainVerificationUserState.DOMAIN_STATE_NONE },
        totalAddresses = addresses.size,
    )
}
