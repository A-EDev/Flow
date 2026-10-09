package io.github.aedev.flow.ui.screens.library

/** How Library shows Downloads while shelf previews are on. */
internal enum class LibraryDownloadsPresentation {
    /** Finished downloads are still loading, so the shelf keeps its placeholder. */
    Loading,

    /** At least one finished download, shown as the shelf. */
    Shelf,

    /**
     * Nothing has finished. The shelf would otherwise vanish, which also hides downloads that are
     * still queued or running. A row keeps that page reachable.
     */
    Row,
}

internal fun libraryDownloadsPresentation(finishedCount: Int?): LibraryDownloadsPresentation =
    when {
        finishedCount == null -> LibraryDownloadsPresentation.Loading
        finishedCount == 0 -> LibraryDownloadsPresentation.Row
        else -> LibraryDownloadsPresentation.Shelf
    }

/**
 * An in-progress download is library content even though it is not a finished file yet. Waiting on
 * either count avoids flashing the empty state before Room emits.
 */
internal fun libraryLooksEmpty(
    countsKnown: Boolean,
    countsEmpty: Boolean,
    activeDownloads: Int?,
): Boolean = countsKnown && activeDownloads != null && countsEmpty && activeDownloads == 0
