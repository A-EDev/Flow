package io.github.aedev.flow.utils

import androidx.tracing.Trace
import java.util.concurrent.atomic.AtomicInteger

private val cookies = AtomicInteger()

/**
 * Runs [block] inside an async trace section named [section], which the startup and Home benchmarks
 * read (#1301). Async because a coroutine may resume on another thread; not inline, so long callers
 * stay small for the compiler.
 */
suspend fun <T> traced(
    section: String,
    block: suspend () -> T,
): T {
    val cookie = cookies.incrementAndGet()
    Trace.beginAsyncSection(section, cookie)
    try {
        return block()
    } finally {
        Trace.endAsyncSection(section, cookie)
    }
}
