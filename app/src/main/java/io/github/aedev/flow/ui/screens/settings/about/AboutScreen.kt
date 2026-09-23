package io.github.aedev.flow.ui.screens.settings.about

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.nav
import io.github.aedev.flow.ui.screens.settings.index.AboutIndex

private enum class AboutDialog { CHANGELOG, LICENSE, DEVICE }

private const val WEBSITE_URL = "https://flow.aedev.me"
private const val GITHUB_URL = "https://github.com/A-EDev/flow"
private const val REDDIT_URL = "https://www.reddit.com/r/Flow_Official/"
private const val CREATOR_URL = "https://github.com/A-EDev"
private const val NEWPIPE_URL = "https://github.com/TeamNewPipe/NewPipeExtractor"

private val LogoSize = 72.dp
private val HeaderPadding = 24.dp
private val HeaderSpacing = 8.dp

/** The app's version, where to find and reach the project, and its licences. */
@Composable
internal fun AboutScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
) {
    val uriHandler = LocalUriHandler.current
    var dialog by rememberSaveable { mutableStateOf<AboutDialog?>(null) }
    val open = { url: String -> runCatching { uriHandler.openUri(url) } }

    SettingsPage(
        title = stringResource(R.string.settings_item_about_flow),
        onBack = onBack,
        highlight = highlight,
    ) {
        item("about.header") { AboutHeader() }
        group(key = "about.app", header = R.string.section_app) {
            nav(AboutIndex.changelog, icon = Icons.Outlined.History, showChevron = false, onClick = { dialog = AboutDialog.CHANGELOG })
        }
        group(key = "about.contact", header = R.string.section_contact) {
            nav(AboutIndex.website, icon = Icons.Outlined.Public, showChevron = false, onClick = { open(WEBSITE_URL) })
            nav(AboutIndex.github, iconRes = R.drawable.ic_github, showChevron = false, onClick = { open(GITHUB_URL) })
            nav(AboutIndex.reddit, icon = IconReddit, showChevron = false, onClick = { open(REDDIT_URL) })
            nav(AboutIndex.creator, icon = Icons.Outlined.Person, showChevron = false, onClick = { open(CREATOR_URL) })
        }
        group(key = "about.legal", header = R.string.section_legal) {
            nav(AboutIndex.license, icon = Icons.Outlined.Description, showChevron = false, onClick = { dialog = AboutDialog.LICENSE })
            nav(AboutIndex.newPipe, icon = Icons.Outlined.Extension, showChevron = false, onClick = { open(NEWPIPE_URL) })
        }
        group(key = "about.device", header = R.string.section_device) {
            nav(
                AboutIndex.deviceInfo,
                value = "${Build.MANUFACTURER} ${Build.MODEL}",
                icon = Icons.Outlined.Smartphone,
                showChevron = false,
                onClick = { dialog = AboutDialog.DEVICE },
            )
        }
    }

    when (dialog) {
        AboutDialog.CHANGELOG -> ChangelogDialog(onDismiss = { dialog = null })
        AboutDialog.LICENSE -> LicenseDialog(onDismiss = { dialog = null })
        AboutDialog.DEVICE -> DeviceInfoDialog(onDismiss = { dialog = null })
        null -> Unit
    }
}

@Composable
private fun AboutHeader() {
    val context = LocalContext.current
    val unknown = stringResource(R.string.unknown)
    val version =
        remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }
                .getOrNull()
                ?.let { it.versionName to it.longVersionCode.toString() }
        }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = HeaderPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HeaderSpacing),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_notification_logo),
            contentDescription = null,
            modifier = Modifier.size(LogoSize),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.v_version_template, version?.first ?: unknown, version?.second ?: "0"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
