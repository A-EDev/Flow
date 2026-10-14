package io.github.aedev.flow.ui.screens.home

internal const val HOME_PREFETCH_AHEAD_VIDEO_COUNT = 24

/**
 * Prefetch only starts once fewer than this many loaded videos remain below the viewport.
 *
 * The feed used to queue a full prefetch target the moment it first painted, so a cold start
 * spent radio, parsing and ranking work on pages the user had not scrolled toward — and often
 * never would. Roughly two screenfuls of runway is enough to keep scrolling seamless.
 */
internal const val HOME_PREFETCH_TRIGGER_REMAINING_VIDEOS = 8

/**
 * One page per run. The drain loop re-arms itself from the viewport, so a user who keeps
 * scrolling still gets continuous content — just fetched in step with them rather than in a
 * burst of up to three pages.
 */
internal const val HOME_PREFETCH_MAX_PAGES_PER_RUN = 1

/**
 * How many times a run retries after a page that appended nothing.
 *
 * A page comes back empty when every candidate it fetched was already on screen. Each attempt
 * advances the discovery-query cursor and rotates the related seeds, so a retry asks for
 * genuinely different content rather than repeating the same request.
 */
internal const val HOME_PREFETCH_EMPTY_PAGE_RETRIES = 3

internal const val HOME_PREFETCH_EMPTY_PAGE_BACKOFF_MS = 400L

/** Keeps only visible grid keys that map to real feed videos (drops shelf/loader keys). */
internal fun feedImpressionIds(
    visibleKeys: List<String>,
    knownIds: Set<String>,
): List<String> = visibleKeys.filter { it in knownIds }
