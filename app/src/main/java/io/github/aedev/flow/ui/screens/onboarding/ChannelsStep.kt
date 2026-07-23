package io.github.aedev.flow.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.aedev.flow.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem

@Composable
internal fun ChannelsStep(
    searchQuery: String,
    searchResults: List<ChannelSearchResult>,
    isSearching: Boolean,
    subscribedInSession: Set<String>,
    onQueryChange: (String) -> Unit,
    onSubscribeToggle: (ChannelSearchResult) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp)
    ) {
        StepHeader(
            title = stringResource(R.string.onboarding_channels_title),
            subtitle = stringResource(R.string.onboarding_channels_subtitle)
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    stringResource(R.string.onboarding_channels_search_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = if (isSearching) {
                { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            if (searchQuery.isBlank()) {
                item {
                    ChannelsPlaceholder(
                        text = stringResource(R.string.onboarding_channels_empty_prompt),
                        strong = false
                    )
                }
            } else if (searchResults.isEmpty() && !isSearching) {
                item {
                    ChannelsPlaceholder(
                        text = stringResource(R.string.onboarding_channels_no_results, searchQuery),
                        strong = true
                    )
                }
            }

            items(searchResults, key = { it.channelId }) { result ->
                ChannelResultRow(
                    result = result,
                    isSubscribed = subscribedInSession.contains(result.channelId),
                    onToggle = { onSubscribeToggle(result) }
                )
            }

            if (subscribedInSession.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.onboarding_channels_added_count, subscribedInSession.size),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun ChannelsPlaceholder(text: String, strong: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (strong) 32.dp else 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!strong) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ChannelResultRow(
    result: ChannelSearchResult,
    isSubscribed: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = result.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.ic_notification_logo),
            error = painterResource(R.drawable.ic_notification_logo)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (result.subscriberCount > 0) {
                Text(
                    text = stringResource(
                        R.string.onboarding_channels_subscribers,
                        formatSubscriberCount(result.subscriberCount)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = isSubscribed,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
            label = "sub_btn_${result.channelId}"
        ) { subscribed ->
            if (subscribed) {
                FilledTonalButton(
                    onClick = onToggle,
                    modifier = Modifier.height(38.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.onboarding_channels_subscribed), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                OutlinedButton(
                    onClick = onToggle,
                    modifier = Modifier.height(38.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.onboarding_channels_subscribe), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

internal suspend fun searchChannels(query: String): List<ChannelSearchResult> =
    withContext(Dispatchers.IO) {
        try {
            val extractor = ServiceList.YouTube.getSearchExtractor(query, listOf("channels"), null)
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<ChannelInfoItem>()
                .take(15)
                .mapNotNull { item ->
                    val channelId = try {
                        val url = item.url
                        when {
                            url.contains("/channel/") ->
                                url.substringAfter("/channel/").substringBefore("/").substringBefore("?")
                            url.contains("/@") ->
                                url.substringAfter("/@").substringBefore("/").substringBefore("?")
                            else ->
                                url.substringAfterLast("/").substringBefore("?")
                        }
                    } catch (e: Exception) { "" }

                    if (channelId.isEmpty() || item.name.isNullOrEmpty()) return@mapNotNull null

                    ChannelSearchResult(
                        channelId = channelId,
                        name = item.name ?: "",
                        thumbnailUrl = item.thumbnails
                            .sortedByDescending { it.height }
                            .firstOrNull()?.url ?: "",
                        subscriberCount = item.subscriberCount
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

private fun formatSubscriberCount(count: Long): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}M"
    count >= 1_000 -> "${count / 1_000}K"
    else -> count.toString()
}
