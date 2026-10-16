package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.local.HomeFeedCacheRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube translates titles into the request language, so both Home caches hold the old language's
 * titles after a switch and would keep showing them until they expire (#1277).
 */
@Singleton
internal class HomeFeedLanguageReset
    @Inject
    constructor(
        private val storedFeed: HomeFeedCacheRepository,
    ) {
        @Volatile private var lastLanguage: String? = null

        suspend fun onContentLanguage(hl: String) {
            val previous = lastLanguage
            lastLanguage = hl
            if (!isLanguageSwitch(previous, hl)) return
            HomeFeedCache.clear()
            storedFeed.clearAll()
        }
    }

/** The first language of a process is where the caches came from, not a switch. */
internal fun isLanguageSwitch(
    previous: String?,
    current: String,
): Boolean = previous != null && previous != current
