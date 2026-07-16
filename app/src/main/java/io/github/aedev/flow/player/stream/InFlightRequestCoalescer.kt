package io.github.aedev.flow.player.stream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InFlightRequestCoalescer<K, V>(
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val requests = mutableMapOf<K, Deferred<V>>()

    suspend fun run(key: K, request: suspend () -> V): V {
        var created = false
        val deferred = mutex.withLock {
            requests[key]?.takeIf { it.isActive } ?: scope.async(start = CoroutineStart.LAZY) {
                request()
            }.also {
                requests[key] = it
                created = true
            }
        }

        if (created) {
            deferred.invokeOnCompletion {
                scope.launch {
                    mutex.withLock {
                        if (requests[key] === deferred) {
                            requests.remove(key)
                        }
                    }
                }
            }
            deferred.start()
        }

        return deferred.await()
    }
}
