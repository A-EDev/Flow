package io.github.aedev.flow.discord

import kotlinx.coroutines.CompletableDeferred

internal object DiscordLoginBroker {
    private var pending: CompletableDeferred<Result<String>>? = null

    @Synchronized
    fun begin(): CompletableDeferred<Result<String>>? {
        if (pending?.isActive == true) return null
        return CompletableDeferred<Result<String>>().also { pending = it }
    }

    @Synchronized
    fun complete(token: String) {
        pending?.complete(Result.success(token))
        pending = null
    }

    @Synchronized
    fun fail(message: String) {
        pending?.complete(Result.failure(IllegalStateException(message)))
        pending = null
    }
}
