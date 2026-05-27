package io.github.aedev.flow.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MusicCollectionActionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val description: String = subtitle,
    val isAlbum: Boolean = false
) {
    val shareUrl: String
        get() = if (isAlbum) {
            "https://music.youtube.com/browse/$id"
        } else {
            "https://music.youtube.com/playlist?list=$id"
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicCollectionQuickActionsSheet(
    item: MusicCollectionActionItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    viewModel: MusicCollectionActionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            FlowMenuGroup(
                items = listOf(
                    FlowMenuItemData(
                        icon = { Icon(Icons.Outlined.BookmarkBorder, null) },
                        title = { Text(stringResource(R.string.add_to_library)) },
                        onClick = {
                            viewModel.saveToLibrary(item)
                            onDismiss()
                        }
                    ),
                    FlowMenuItemData(
                        icon = { Icon(Icons.Outlined.Share, null) },
                        title = { Text(stringResource(R.string.share)) },
                        onClick = {
                            context.shareCollection(item)
                            onDismiss()
                        }
                    ),
                    FlowMenuItemData(
                        icon = { Icon(Icons.Outlined.OpenInNew, null) },
                        title = { Text(stringResource(R.string.open)) },
                        onClick = {
                            onOpen()
                            onDismiss()
                        }
                    )
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@HiltViewModel
class MusicCollectionActionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {
    fun saveToLibrary(item: MusicCollectionActionItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                playlistRepository.saveExternalMusicPlaylist(
                    id = item.id,
                    name = item.title,
                    description = item.description,
                    thumbnailUrl = item.thumbnailUrl.orEmpty()
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.saved_to_library), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.failed_to_save_to_library), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

private fun Context.shareCollection(item: MusicCollectionActionItem) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, item.title)
        putExtra(Intent.EXTRA_TEXT, item.shareUrl)
    }
    startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
}
