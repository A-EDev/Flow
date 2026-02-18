package com.flow.youtube.ui.screens.player.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.flow.youtube.R
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.data.repository.SponsorBlockRepository
import kotlinx.coroutines.launch

/** All SponsorBlock submit categories shown in the dialog dropdown. */
private val SB_SUBMIT_CATEGORIES = listOf(
    "sponsor"          to R.string.sb_category_sponsor,
    "intro"            to R.string.sb_category_intro,
    "outro"            to R.string.sb_category_outro,
    "selfpromo"        to R.string.sb_category_selfpromo,
    "interaction"      to R.string.sb_category_interaction,
    "music_offtopic"   to R.string.sb_category_music_offtopic,
    "filler"           to R.string.sb_category_filler,
    "preview"          to R.string.sb_category_preview,
    "exclusive_access" to R.string.sb_category_exclusive_access
)

/**
 * Dialog for submitting a new SponsorBlock segment.
 * Start time is pre-filled with the current player position.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SbSubmitSegmentDialog(
    videoId: String,
    currentPositionMs: Long,
    onDismiss: () -> Unit,
    repository: SponsorBlockRepository = remember { SponsorBlockRepository() }
) {
    val context = LocalContext.current
    val playerPreferences = remember { PlayerPreferences(context) }
    val coroutineScope = rememberCoroutineScope()

    fun msToTimestamp(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    fun timestampToSeconds(ts: String): Float? {
        val parts = ts.trim().split(":").map { it.trim() }
        return when (parts.size) {
            2 -> {
                val m = parts[0].toFloatOrNull() ?: return null
                val s = parts[1].toFloatOrNull() ?: return null
                m * 60 + s
            }
            3 -> {
                val h = parts[0].toFloatOrNull() ?: return null
                val m = parts[1].toFloatOrNull() ?: return null
                val s = parts[2].toFloatOrNull() ?: return null
                h * 3600 + m * 60 + s
            }
            else -> ts.toFloatOrNull()
        }
    }

    var startTime by remember { mutableStateOf(msToTimestamp(currentPositionMs)) }
    var endTime by remember { mutableStateOf("") }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var startError by remember { mutableStateOf(false) }
    var endError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = stringResource(R.string.sb_submit_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Time row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = {
                            startTime = it
                            startError = false
                        },
                        label = { Text(stringResource(R.string.sb_submit_start_time)) },
                        isError = startError,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val tmp = startTime
                        startTime = endTime
                        endTime = tmp
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.SwapVert,
                            contentDescription = stringResource(R.string.sb_submit_swap)
                        )
                    }
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = {
                            endTime = it
                            endError = false
                        },
                        label = { Text(stringResource(R.string.sb_submit_end_time)) },
                        isError = endError,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = stringResource(SB_SUBMIT_CATEGORIES[selectedCategoryIndex].second),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.sb_submit_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        SB_SUBMIT_CATEGORIES.forEachIndexed { idx, (_, labelRes) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge) },
                                onClick = {
                                    selectedCategoryIndex = idx
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Status message
                statusMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("✓"))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val start = timestampToSeconds(startTime)
                            val end = timestampToSeconds(endTime)
                            if (start == null) { startError = true; return@Button }
                            if (end == null || end <= (start)) { endError = true; return@Button }
                            isSubmitting = true
                            statusMessage = null
                            coroutineScope.launch {
                                val userId = playerPreferences.getOrCreateSbUserId()
                                val category = SB_SUBMIT_CATEGORIES[selectedCategoryIndex].first
                                val success = repository.submitSegment(
                                    videoId = videoId,
                                    startTime = start,
                                    endTime = end,
                                    category = category,
                                    userId = userId
                                )
                                isSubmitting = false
                                statusMessage = if (success) {
                                    context.getString(R.string.sb_submit_success)
                                } else {
                                    context.getString(R.string.sb_submit_error)
                                }
                                if (success) onDismiss()
                            }
                        },
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.sb_submit_create))
                        }
                    }
                }
            }
        }
    }
}
