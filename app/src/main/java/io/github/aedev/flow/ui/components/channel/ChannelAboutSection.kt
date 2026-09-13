package io.github.aedev.flow.ui.components.channel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.pages.channel.ChannelHeader
import io.github.aedev.flow.ui.theme.extendedColors

@Composable
internal fun ChannelAboutSection(header: ChannelHeader) {
    val uriHandler = LocalUriHandler.current
    val rows =
        remember(header) {
            listOfNotNull(
                header.handle?.let { R.string.channel_about_handle to it },
                header.subscriberCountText?.let { R.string.subscribers to it },
                header.videoCountText?.let { R.string.channel_about_videos to it },
                header.viewCountText?.let { R.string.views to it },
                header.joinedDateText?.let { R.string.channel_about_joined to it },
                header.countryText?.let { R.string.channel_about_country to it },
            )
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!header.description.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChannelAboutHeading(stringResource(R.string.about))
                Text(
                    text = header.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (rows.isNotEmpty()) {
            if (!header.description.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChannelAboutHeading(stringResource(R.string.stats))
                rows.forEach { (labelRes, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extendedColors.textSecondary,
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        if (header.links.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChannelAboutHeading(stringResource(R.string.channel_about_links))
                header.links.forEach { link ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { uriHandler.openUri(link.url) },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!link.iconUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = link.iconUrl,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = link.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelAboutHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.extendedColors.textSecondary,
    )
}
