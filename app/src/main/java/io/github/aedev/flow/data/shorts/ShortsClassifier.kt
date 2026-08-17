package io.github.aedev.flow.data.shorts

import org.schabi.newpipe.extractor.stream.StreamInfoItem

object ShortsClassifier {
    private const val SHORTS_PATH = "/shorts/"

    /** Reel markers on an extractor item, plus its URL. */
    fun isReel(item: StreamInfoItem): Boolean = item.isShortFormContent || isReelUrl(item.url)

    /** True for a canonical Shorts watch URL. Safe on null/blank. */
    fun isReelUrl(url: String?): Boolean = url?.contains(SHORTS_PATH, ignoreCase = true) == true
}
