package io.github.aedev.flow.player.stream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class InFlightRequestCoalescer<K, V>(
    private val scope: CoroutineScope,
) {
    private data class Request<V>(
        val deferred: Deferred<V>,
        var waiterCount: Int = 0,
    )

    private val mutex = Mutex()
    private val requests = mutableMapOf<K, Request<V>>()

    suspend fun run(key: K, request: suspend () -> V): V {
        var created = false
        val activeRequest = mutex.withLock {
            val existing = requests[key]?.takeIf { it.deferred.isActive }
            val selected = existing ?: Request(
                deferred = scope.async(start = CoroutineStart.LAZY) { request() }
            ).also {
                requests[key] = it
                created = true
            }
            selected.waiterCount++
            selected
        }

        if (created) {
            activeRequest.deferred.invokeOnCompletion {
                scope.launch {
                    mutex.withLock {
                        if (requests[key] === activeRequest) {
                            requests.remove(key)
                        }
                    }
                }
            }
            activeRequest.deferred.start()
        }

        return try {
            activeRequest.deferred.await()
        } finally {
            val cancelUnobservedRequest = withContext(NonCancellable) {
                mutex.withLock {
                    if (requests[key] !== activeRequest) return@withLock false
                    activeRequest.waiterCount--
                    if (activeRequest.waiterCount == 0 && activeRequest.deferred.isActive) {
                        requests.remove(key)
                        true
                    } else {
                        false
                    }
                }
            }
            if (cancelUnobservedRequest) {
                activeRequest.deferred.cancel()
            }
        }
    }
}
