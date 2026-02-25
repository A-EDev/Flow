package com.flow.youtube.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter

/**
 * Simple in-process event bus that notifies screens when the user taps on
 * the bottom-nav item that is already selected (i.e., they want to scroll
 * back to the top / refresh the current page).
 *
 * Usage:
 *   - Emit:   TabScrollEventBus.emitScrollToTop("home")
 *   - Listen: TabScrollEventBus.scrollToTopEvents.filter { it == "home" }.collect { … }
 */
object TabScrollEventBus {
    private val _scrollToTopEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val scrollToTopEvents: SharedFlow<String> = _scrollToTopEvents.asSharedFlow()

    /**
     * Signal the screen identified by [route] to scroll back to the top.
     */
    fun emitScrollToTop(route: String) {
        _scrollToTopEvents.tryEmit(route)
    }
}
