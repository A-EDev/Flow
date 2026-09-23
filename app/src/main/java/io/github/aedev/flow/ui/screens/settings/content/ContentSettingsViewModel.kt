package io.github.aedev.flow.ui.screens.settings.content

import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.WatchedThreshold
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import javax.inject.Inject

/** What shows up in the feeds: Home, Subscriptions, Shorts, watched filtering, notes and sharing. */
@HiltViewModel
class ContentSettingsViewModel
    @Inject
    constructor(
        private val preferences: PlayerPreferences,
    ) : SettingsViewModel() {
        val homeFeed = preferences.homeFeedEnabled.asState(true)
        val refreshOnReselect = preferences.refreshHomeOnReselect.asState(true)
        val continueWatching = preferences.continueWatchingEnabled.asState(true)
        val homeShortsShelf = preferences.homeShortsShelfEnabled.asState(true)
        val hideWatchedHome = preferences.hideWatchedVideosFromHome.asState(false)

        val subsVideos = preferences.subscriptionShowVideos.asState(true)
        val subsShorts = preferences.subscriptionShowShorts.asState(true)
        val subsShortsShelf = preferences.shortsShelfEnabled.asState(true)
        val subsLive = preferences.subscriptionShowLive.asState(true)
        val hideWatchedSubs = preferences.hideWatchedVideosFromSubscriptions.asState(false)
        val hideUnplayableSubs = preferences.hideUnplayableVideosFromSubscriptions.asState(false)
        val subsRefreshOnStartup = preferences.subscriptionRefreshOnStartup.asState(false)
        val subsCheckedCount = preferences.subscriptionShowCheckedVideoCount.asState(true)

        val shortsContent = preferences.shortsContentEnabled.asState(true)
        val watchedThreshold = preferences.watchedThreshold.asState(WatchedThreshold.ALMOST_FINISHED)

        val notes = preferences.notesEnabled.asState(true)
        val channelNotes = preferences.channelNotesEnabled.asState(true)
        val videoNotes = preferences.videoNotesEnabled.asState(true)
        val shareWithoutText = preferences.shareWithoutText.asState(false)

        fun setHomeFeed(value: Boolean) = write { preferences.setHomeFeedEnabled(value) }

        fun setRefreshOnReselect(value: Boolean) = write { preferences.setRefreshHomeOnReselect(value) }

        fun setContinueWatching(value: Boolean) = write { preferences.setContinueWatchingEnabled(value) }

        fun setHomeShortsShelf(value: Boolean) = write { preferences.setHomeShortsShelfEnabled(value) }

        fun setHideWatchedHome(value: Boolean) = write { preferences.setHideWatchedVideosFromHome(value) }

        fun setSubsVideos(value: Boolean) = write { preferences.setSubscriptionShowVideos(value) }

        fun setSubsShorts(value: Boolean) = write { preferences.setSubscriptionShowShorts(value) }

        fun setSubsShortsShelf(value: Boolean) = write { preferences.setShortsShelfEnabled(value) }

        fun setSubsLive(value: Boolean) = write { preferences.setSubscriptionShowLive(value) }

        fun setHideWatchedSubs(value: Boolean) = write { preferences.setHideWatchedVideosFromSubscriptions(value) }

        fun setHideUnplayableSubs(value: Boolean) = write { preferences.setHideUnplayableVideosFromSubscriptions(value) }

        fun setSubsRefreshOnStartup(value: Boolean) = write { preferences.setSubscriptionRefreshOnStartup(value) }

        fun setSubsCheckedCount(value: Boolean) = write { preferences.setSubscriptionShowCheckedVideoCount(value) }

        fun setShortsContent(value: Boolean) = write { preferences.setShortsContentEnabled(value) }

        fun setWatchedThreshold(value: WatchedThreshold) = write { preferences.setWatchedThreshold(value) }

        fun setNotes(value: Boolean) = write { preferences.setNotesEnabled(value) }

        fun setChannelNotes(value: Boolean) = write { preferences.setChannelNotesEnabled(value) }

        fun setVideoNotes(value: Boolean) = write { preferences.setVideoNotesEnabled(value) }

        fun setShareWithoutText(value: Boolean) = write { preferences.setShareWithoutText(value) }
    }
