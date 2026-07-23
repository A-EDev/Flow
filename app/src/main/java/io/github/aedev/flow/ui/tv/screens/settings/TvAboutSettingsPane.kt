package io.github.aedev.flow.ui.tv.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.tv.components.TvNavRow
import io.github.aedev.flow.ui.tv.components.TvSectionHeader
import io.github.aedev.flow.ui.tv.focus.ProvideTvColumnPivot

private const val FLOW_WEBSITE_URL = "https://flow.aedev.me"
private const val FLOW_RELEASES_URL = "https://github.com/A-EDev/Flow/releases"
private const val FLOW_GITHUB_URL = "https://github.com/A-EDev/Flow"
private const val FLOW_REDDIT_URL = "https://www.reddit.com/r/Flow_Official/"
private const val FLOW_CREATOR_URL = "https://github.com/A-EDev"
private const val FLOW_DONATION_URL = "https://patreon.com/A_EDev"
private const val FLOW_LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val NEWPIPE_EXTRACTOR_URL = "https://github.com/TeamNewPipe/NewPipeExtractor"

/** TV counterpart of the mobile About screen, adapted for remote focus and scrolling. */
@Composable
fun TvAboutSettingsPane(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    ProvideTvColumnPivot {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "identity") {
                TvAboutIdentity()
            }

            item(key = "app-header") {
                TvAboutSectionHeader(stringResource(R.string.section_app))
            }
            item(key = "changelog") {
                TvNavRow(
                    label = stringResource(R.string.about_changelog),
                    supportingText = stringResource(R.string.whats_new_in_flow),
                    leadingIcon = Icons.Outlined.History,
                    onClick = { context.openUrl(FLOW_RELEASES_URL) },
                )
            }
            item(key = "donate") {
                TvNavRow(
                    label = stringResource(R.string.donate_item_title),
                    supportingText = stringResource(R.string.support_dev_subtitle),
                    leadingIcon = Icons.Outlined.VolunteerActivism,
                    onClick = { context.openUrl(FLOW_DONATION_URL) },
                )
            }

            item(key = "contact-header") {
                TvAboutSectionHeader(stringResource(R.string.section_contact))
            }
            item(key = "website") {
                TvNavRow(
                    label = stringResource(R.string.about_website),
                    value = stringResource(R.string.about_website_address),
                    leadingIcon = Icons.Outlined.Public,
                    onClick = { context.openUrl(FLOW_WEBSITE_URL) },
                )
            }
            item(key = "github") {
                TvNavRow(
                    label = stringResource(R.string.github_label),
                    supportingText = stringResource(R.string.github_subtitle),
                    leadingIcon = Icons.Outlined.Code,
                    onClick = { context.openUrl(FLOW_GITHUB_URL) },
                )
            }
            item(key = "reddit") {
                TvNavRow(
                    label = stringResource(R.string.about_reddit),
                    value = stringResource(R.string.about_reddit_subtitle),
                    leadingIcon = Icons.Outlined.Forum,
                    onClick = { context.openUrl(FLOW_REDDIT_URL) },
                )
            }
            item(key = "creator") {
                TvNavRow(
                    label = stringResource(R.string.about_creator),
                    value = stringResource(R.string.about_creator_name),
                    leadingIcon = Icons.Outlined.Person,
                    onClick = { context.openUrl(FLOW_CREATOR_URL) },
                )
            }

            item(key = "legal-header") {
                TvAboutSectionHeader(stringResource(R.string.section_legal))
            }
            item(key = "license") {
                TvNavRow(
                    label = stringResource(R.string.about_license),
                    value = stringResource(R.string.about_license_name),
                    leadingIcon = Icons.Outlined.Description,
                    onClick = { context.openUrl(FLOW_LICENSE_URL) },
                )
            }
            item(key = "newpipe") {
                TvNavRow(
                    label = stringResource(R.string.newpipe_extractor_title),
                    supportingText = stringResource(R.string.newpipe_extractor_subtitle),
                    leadingIcon = Icons.Outlined.Extension,
                    onClick = { context.openUrl(NEWPIPE_EXTRACTOR_URL) },
                )
            }

            item(key = "device-header") {
                TvAboutSectionHeader(stringResource(R.string.section_device))
            }
            item(key = "device-info") {
                TvNavRow(
                    label = stringResource(R.string.about_device_info),
                    value = "${Build.MANUFACTURER} ${Build.MODEL}",
                    leadingIcon = Icons.Outlined.Tv,
                    onClick = { context.openDeviceInfo() },
                )
            }
        }
    }
}

@Composable
private fun TvAboutIdentity() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_notification_logo),
                contentDescription = stringResource(R.string.app_logo_desc),
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(
                        R.string.v_version_template,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE.toString(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TvAboutSectionHeader(title: String) {
    TvSectionHeader(
        title = title,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

private fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

private fun Context.openDeviceInfo() {
    val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
