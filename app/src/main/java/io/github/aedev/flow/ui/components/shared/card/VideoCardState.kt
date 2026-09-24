package io.github.aedev.flow.ui.components.shared.card

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.VideoCollaborator
import io.github.aedev.flow.ui.components.QuickActionsViewModel
import io.github.aedev.flow.ui.components.layout.navigation.MediaNavigator
import io.github.aedev.flow.ui.components.rememberDeArrowResult
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Card settings that are identical for every card on screen.
 *
 * Each of these used to be collected per card, so a feed showing ten cards ran fifty DataStore
 * collectors and re-mapped the same preference file in each of them on every write. They are
 * collected once at the composition root instead and read through [LocalVideoCardPreferences].
 */
@Immutable
data class VideoCardPreferences(
    val deArrowEnabled: Boolean = false,
    val deArrowBadgeEnabled: Boolean = false,
    val actionsEnabled: Boolean = false,
    val markWatchedEnabled: Boolean = false,
    val upcomingReminderIds: Set<String> = emptySet(),
)

/**
 * Static because these change only when the user edits a setting: reads cost nothing, and the
 * rare write invalidates the subtree wholesale instead of being tracked per reader.
 */
val LocalVideoCardPreferences = staticCompositionLocalOf { VideoCardPreferences() }

/**
 * Watch progress for every video in history, backed by one Room observer.
 *
 * Cards used to open a `getVideoHistory(id)` query each. Handing them the map directly would
 * trade that for the opposite problem — one progress write recomposing every visible card — so
 * lookups go through [rememberWatchProgress], which derives per-id state.
 */
@Stable
class VideoWatchProgressStore internal constructor(
    private val entries: State<Map<String, Float>>,
) {
    internal fun progressFor(videoId: String): Float? = entries.value[videoId]

    internal companion object {
        val Empty = VideoWatchProgressStore(mutableStateOf(emptyMap()))
    }
}

val LocalVideoWatchProgress = staticCompositionLocalOf { VideoWatchProgressStore.Empty }

/**
 * Watch progress for [videoId] as a card renders it, or null when there is nothing to show.
 *
 * `derivedStateOf` is what keeps the shared map from becoming a global invalidation: a card is
 * recomposed only when its own entry changes, not when any video's progress is written.
 */
@Composable
fun rememberWatchProgress(videoId: String): Float? {
    val store = LocalVideoWatchProgress.current
    val progress = remember(store, videoId) { derivedStateOf { store.progressFor(videoId) } }
    return progress.value
}

internal const val WATCHED_PROGRESS_THRESHOLD = 0.90f

/**
 * Whether a card should read as watched. Marking a video watched writes a history entry at its full
 * length, so the progress store is the one answer for every screen.
 */
internal fun isWatchedProgress(watchProgress: Float?): Boolean = (watchProgress ?: 0f) >= WATCHED_PROGRESS_THRESHOLD

/** The feedback a card sends without opening its sheet. */
@Stable
class VideoCardActions(
    val onInterested: (Video) -> Unit,
    val onNotInterested: (Video) -> Unit,
    val onWatched: (Video) -> Unit,
) {
    internal companion object {
        val None = VideoCardActions({}, {}, {})
    }
}

val LocalVideoCardActions = staticCompositionLocalOf { VideoCardActions.None }

/**
 * Installs the shared card state. Must wrap any tree that renders video cards; without it cards
 * fall back to defaults (settings off, no progress bars, actions that do nothing). Installed once at
 * the activity root, so [quickActions] is the activity's instance and no card looks one up itself.
 */
@Composable
fun ProvideVideoCardState(
    quickActions: QuickActionsViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val actions =
        remember(quickActions) {
            VideoCardActions(
                onInterested = quickActions::markAsInteresting,
                onNotInterested = quickActions::markNotInterested,
                onWatched = quickActions::markAsWatched,
            )
        }

    val preferencesFlow =
        remember(context) {
            val preferences = PlayerPreferences(context)
            combine(
                preferences.deArrowEnabled,
                preferences.deArrowBadgeEnabled,
                preferences.videoCardActionsEnabled,
                preferences.videoCardMarkWatchedEnabled,
                preferences.upcomingVideoReminderIds,
            ) { deArrow, deArrowBadge, actions, markWatched, reminders ->
                VideoCardPreferences(deArrow, deArrowBadge, actions, markWatched, reminders)
            }.distinctUntilChanged()
        }
    val preferences by preferencesFlow.collectAsStateWithLifecycle(VideoCardPreferences())

    val progressFlow =
        remember(context) {
            ViewHistory
                .getInstance(context)
                .getAllHistory()
                .map { entries -> entries.toWatchProgressMap() }
                .distinctUntilChanged()
        }
    val progressEntries = progressFlow.collectAsStateWithLifecycle(emptyMap())
    val progressStore = remember(progressEntries) { VideoWatchProgressStore(progressEntries) }

    CompositionLocalProvider(
        LocalVideoCardPreferences provides preferences,
        LocalVideoWatchProgress provides progressStore,
        LocalVideoCardActions provides actions,
        content = content,
    )
}

/**
 * Below 3% a video counts as not started, and at 90% the bar is filled rather than left a sliver
 * short of the end. Mirrors what each card computed for itself before.
 */
internal fun List<VideoHistoryEntry>.toWatchProgressMap(): Map<String, Float> =
    buildMap {
        this@toWatchProgressMap.forEach { entry ->
            val percentage = entry.progressPercentage
            if (entry.duration > 0 && percentage >= 3f) {
                put(entry.videoId, if (percentage >= 90f) 1f else percentage / 100f)
            }
        }
    }

/** Which of a card's sheets is open. Kept apart from [VideoCardState] so a new title or progress value never closes one. */
@Stable
internal class VideoCardSheetState {
    var showQuickActions by mutableStateOf(false)
    var showCollaborators by mutableStateOf(false)
}

/** Everything a card shows about [video], resolved once for every card layout. */
@Stable
internal class VideoCardState(
    val video: Video,
    val title: String,
    val thumbnailUrl: String,
    val showDeArrowBadge: Boolean,
    val channelName: String,
    val collaborators: List<VideoCollaborator>,
    val watchProgress: Float?,
    val showReminderBadge: Boolean,
    val sheets: VideoCardSheetState,
) {
    val avatarUrls: List<String> get() = video.channelAvatarUrls(collaborators)

    val isWatched: Boolean get() = isWatchedProgress(watchProgress)

    /** A collaboration opens the list of its channels; a single channel opens directly. */
    fun openChannel(navigator: MediaNavigator) {
        if (collaborators.size > 1) {
            sheets.showCollaborators = true
        } else {
            navigator.openChannel(video.channelId)
        }
    }
}

@Composable
internal fun rememberVideoCardState(video: Video): VideoCardState {
    val preferences = LocalVideoCardPreferences.current
    val deArrow = rememberDeArrowResult(video.id, preferences.deArrowEnabled)
    val collaborators = rememberCollaboratorItems(video)
    val channelName = rememberCollaboratorChannelDisplayName(video.channelName, collaborators)
    val watchProgress = rememberWatchProgress(video.id)
    val sheets = remember { VideoCardSheetState() }
    return VideoCardState(
        video = video,
        title = deArrow?.title ?: video.title,
        thumbnailUrl = deArrow?.thumbnailUrl ?: video.thumbnailUrl,
        showDeArrowBadge = deArrow != null && preferences.deArrowBadgeEnabled,
        channelName = channelName,
        collaborators = collaborators,
        watchProgress = watchProgress,
        showReminderBadge = video.isUpcoming && video.id in preferences.upcomingReminderIds,
        sheets = sheets,
    )
}
