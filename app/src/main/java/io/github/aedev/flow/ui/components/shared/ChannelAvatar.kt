package io.github.aedev.flow.ui.components.shared

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.utils.ThumbnailUrlResolver

private const val AVATAR_TAG = "ChannelAvatarImage"

/**
 * Channel avatar that gracefully degrades on load failure:
 *  1. Tries the original URL (may be high-res, e.g. =s800)
 *  2. On failure, retries with =s88 (low-res) if a size parameter is present
 *  3. On second failure, or no size param, shows the AccountCircle icon
 */
@Composable
fun ChannelAvatarImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    var currentModel by remember(url) {
        val highQualityUrl = ThumbnailUrlResolver.resolveChannelAvatar(url)
        val initial = highQualityUrl.takeIf { it.isNotEmpty() } ?: Icons.Default.AccountCircle
        if (initial is ImageVector) {
            Log.d(AVATAR_TAG, "null/empty url for '$contentDescription', using icon")
        } else {
            Log.d(AVATAR_TAG, "init url='$highQualityUrl' for '$contentDescription'")
        }
        mutableStateOf<Any>(initial)
    }
    var didRetry by remember(url) { mutableStateOf(false) }

    when (val model = currentModel) {
        is ImageVector -> {
            Image(
                imageVector = model,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = ContentScale.Crop,
                colorFilter =
                    androidx.compose.ui.graphics.ColorFilter.tint(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }

        else -> {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = ContentScale.Crop,
                onError = { errorResult ->
                    val errMsg = errorResult.result.throwable?.message ?: "unknown error"
                    if (!didRetry) {
                        didRetry = true
                        val src =
                            currentModel as? String ?: run {
                                Log.e(AVATAR_TAG, "Expected String model but got ${currentModel::class.simpleName}")
                                return@AsyncImage
                            }
                        val lowRes = src.replace(Regex("=s\\d+"), "=s88")
                        if (lowRes != src) {
                            Log.w(AVATAR_TAG, "Failed '$src' ($errMsg) → retrying with '$lowRes'")
                            currentModel = lowRes
                        } else {
                            Log.e(AVATAR_TAG, "Failed '$src' ($errMsg), no size param to replace → icon")
                            currentModel = Icons.Default.AccountCircle
                        }
                    } else {
                        Log.e(AVATAR_TAG, "Retry also failed for '$model' ($errMsg) → icon")
                        currentModel = Icons.Default.AccountCircle
                    }
                },
            )
        }
    }
}

@Composable
fun ChannelAvatarStack(
    urls: List<String>,
    contentDescription: String?,
    avatarSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    ringColor: Color = MaterialTheme.colorScheme.background,
) {
    val avatarUrls = urls.ifEmpty { listOf("") }.take(3)
    val primaryUrl = avatarUrls.first()
    val secondaryUrl = avatarUrls.getOrNull(1)
    val tertiaryUrl = avatarUrls.getOrNull(2)
    val stackedAvatarSize =
        when {
            !tertiaryUrl.isNullOrBlank() -> avatarSize * 0.64f
            !secondaryUrl.isNullOrBlank() -> avatarSize * 0.78f
            else -> avatarSize
        }

    Box(
        modifier =
            modifier
                .size(avatarSize),
    ) {
        if (!tertiaryUrl.isNullOrBlank()) {
            ChannelAvatarImage(
                url = tertiaryUrl,
                contentDescription = contentDescription,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(stackedAvatarSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.5.dp,
                            color = ringColor,
                            shape = CircleShape,
                        ),
            )
        }

        if (!secondaryUrl.isNullOrBlank()) {
            ChannelAvatarImage(
                url = secondaryUrl,
                contentDescription = contentDescription,
                modifier =
                    Modifier
                        .align(if (tertiaryUrl.isNullOrBlank()) Alignment.BottomEnd else Alignment.BottomStart)
                        .size(stackedAvatarSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.5.dp,
                            color = ringColor,
                            shape = CircleShape,
                        ),
            )
        }

        ChannelAvatarImage(
            url = primaryUrl,
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .align(if (tertiaryUrl.isNullOrBlank()) Alignment.TopStart else Alignment.TopCenter)
                    .size(stackedAvatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (!secondaryUrl.isNullOrBlank()) {
                            Modifier
                                .border(
                                    width = 1.5.dp,
                                    color = ringColor,
                                    shape = CircleShape,
                                )
                        } else {
                            Modifier
                        },
                    ),
        )
    }
}
