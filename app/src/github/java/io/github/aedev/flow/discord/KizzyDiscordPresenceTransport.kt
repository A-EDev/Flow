package io.github.aedev.flow.discord

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import io.github.aedev.flow.R
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/**
 * GitHub-flavor Discord Gateway adapter inspired by Kizzy's Android RPC approach.
 * This uses a Discord user session and is not an official Discord integration.
 */
class KizzyDiscordPresenceTransport(
    private val context: Context,
    private val client: OkHttpClient,
    private val tokenStore: DiscordTokenStore,
    private val applicationId: String,
) : DiscordPresenceTransport {
    override val isAvailable: Boolean = applicationId.isNotBlank()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionMutex = Mutex()
    private val accountLinkMutex = Mutex()
    private val reconnectBackoff = DiscordReconnectBackoff()
    private val heartbeatTracker = DiscordHeartbeatTracker()
    private val imageCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(MAX_IMAGE_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
                size > MAX_IMAGE_CACHE_ENTRIES
        },
    )
    private var activityReference = WeakReference<Activity>(null)
    private var socket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var readySignal: CompletableDeferred<DiscordLinkResult>? = null
    private var currentToken: String? = null
    @Volatile private var sequence: Int? = null
    @Volatile private var generation = 0

    private val _connectionState = MutableStateFlow(DiscordConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<DiscordConnectionState> = _connectionState.asStateFlow()

    private val _linkedAccountName = MutableStateFlow<String?>(null)
    override val linkedAccountName: StateFlow<String?> = _linkedAccountName.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override fun attachActivity(activity: Activity?) {
        activityReference = WeakReference(activity)
    }

    override suspend fun link(): DiscordLinkResult {
        if (!accountLinkMutex.tryLock()) {
            return fail(R.string.discord_error_connection_in_progress)
        }

        return try {
            linkAccount()
        } finally {
            accountLinkMutex.unlock()
        }
    }

    private suspend fun linkAccount(): DiscordLinkResult {
        val activity = activityReference.get()
            ?: return fail(R.string.discord_error_open_flow)
        val result = DiscordLoginBroker.begin()
            ?: return fail(R.string.discord_error_connection_in_progress)

        _connectionState.value = DiscordConnectionState.LINKING
        _lastError.value = null
        activity.startActivity(Intent(activity, DiscordLoginActivity::class.java))

        val token = try {
            withTimeout(LOGIN_TIMEOUT_MS) { result.await().getOrThrow() }
        } catch (error: TimeoutCancellationException) {
            DiscordLoginBroker.cancel()
            return fail(R.string.discord_error_login_timeout)
        } catch (error: CancellationException) {
            DiscordLoginBroker.cancel()
            throw error
        } catch (error: Throwable) {
            return fail(error.message ?: context.getString(R.string.discord_error_connection_incomplete))
        }

        return connect(
            DiscordAuthTokens(
                accessToken = token,
                refreshToken = "",
                expiresAtEpochSeconds = Long.MAX_VALUE,
            ),
        )
    }

    override suspend fun connect(tokens: DiscordAuthTokens): DiscordLinkResult {
        val result = connectionMutex.withLock {
            connectLocked(tokens)
        }
        if (result == DiscordLinkResult.Success) reconnectBackoff.reset()
        return result
    }

    private suspend fun connectLocked(tokens: DiscordAuthTokens): DiscordLinkResult {
        if (tokens.accessToken.isBlank()) return fail(R.string.discord_error_empty_token)
        if (applicationId.isBlank()) return fail(R.string.discord_error_missing_application_id)
        if (
            _connectionState.value == DiscordConnectionState.CONNECTED &&
            currentToken == tokens.accessToken
        ) {
            return DiscordLinkResult.Success
        }

        closeSocket()
        val connectionGeneration = ++generation
        currentToken = tokens.accessToken
        sequence = null
        _connectionState.value = DiscordConnectionState.CONNECTING
        _lastError.value = null
        val signal = CompletableDeferred<DiscordLinkResult>()
        readySignal = signal

        val request = Request.Builder().url(KizzyGatewayProtocol.GATEWAY_URL).build()
        socket = client.newWebSocket(request, gatewayListener(connectionGeneration, tokens))

        return try {
            withTimeout(CONNECTION_TIMEOUT_MS) { signal.await() }
        } catch (error: TimeoutCancellationException) {
            closeSocket()
            fail(R.string.discord_error_gateway_timeout)
        } catch (error: CancellationException) {
            closeSocket()
            throw error
        }
    }

    override suspend fun update(payload: DiscordPresencePayload): Boolean {
        if (!ensureConnected()) return false
        val resolvedImage = resolveExternalImage(payload.largeImage)
        return socket?.send(
            KizzyGatewayProtocol.presence(
                payload = payload,
                applicationId = applicationId,
                resolvedImage = resolvedImage,
                activityName = context.getString(R.string.app_name),
            ),
        ) == true
    }

    override suspend fun clear(): Boolean {
        if (_connectionState.value != DiscordConnectionState.CONNECTED) return true
        return socket?.send(
            KizzyGatewayProtocol.presence(
                payload = null,
                applicationId = applicationId,
                resolvedImage = null,
                activityName = context.getString(R.string.app_name),
            ),
        ) == true
    }

    override suspend fun disconnect(): Boolean {
        // Opening Discord's login activity stops MainActivity. Presence lifecycle teardown must
        // not cancel the account-link handshake while the gateway is waiting for READY.
        if (!accountLinkMutex.tryLock()) return true
        return try {
            disconnectSocket()
        } finally {
            accountLinkMutex.unlock()
        }
    }

    override suspend fun unlink(): Boolean {
        DiscordLoginBroker.cancel()
        disconnectSocket()
        tokenStore.clear()
        currentToken = null
        imageCache.clear()
        _linkedAccountName.value = null
        _lastError.value = null
        _connectionState.value = DiscordConnectionState.DISCONNECTED
        return true
    }

    private suspend fun disconnectSocket(): Boolean {
        val cleared = clear()
        closeSocket()
        currentToken = null
        imageCache.clear()
        if (_connectionState.value != DiscordConnectionState.UNAVAILABLE) {
            _connectionState.value = DiscordConnectionState.DISCONNECTED
        }
        return cleared
    }

    override fun close() {
        if (_connectionState.value == DiscordConnectionState.CONNECTED) {
            socket?.send(
                KizzyGatewayProtocol.presence(
                    payload = null,
                    applicationId = applicationId,
                    resolvedImage = null,
                    activityName = context.getString(R.string.app_name),
                ),
            )
        }
        closeSocket()
        DiscordLoginBroker.cancel()
        currentToken = null
        imageCache.clear()
        scope.cancel()
    }

    private fun gatewayListener(
        connectionGeneration: Int,
        tokens: DiscordAuthTokens,
    ) = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (connectionGeneration != generation) return
            val payload = runCatching { JSONObject(text) }.getOrNull() ?: return
            if (payload.has("s") && !payload.isNull("s")) sequence = payload.optInt("s")

            when (payload.optInt("op", -1)) {
                0 -> if (payload.optString("t") == "READY") {
                    val user = payload.optJSONObject("d")?.optJSONObject("user")
                    val accountName = user?.optString("global_name")
                        ?.takeIf(String::isNotBlank)
                        ?: user?.optString("username")?.takeIf(String::isNotBlank)
                        ?: context.getString(R.string.discord_account_fallback)
                    _linkedAccountName.value = accountName
                    _connectionState.value = DiscordConnectionState.CONNECTED
                    _lastError.value = null
                    tokenStore.save(tokens)
                    readySignal?.complete(DiscordLinkResult.Success)
                }

                1 -> {
                    heartbeatTracker.markRequestedHeartbeatSent()
                    if (!webSocket.send(KizzyGatewayProtocol.heartbeat(sequence))) {
                        failFromCallback(context.getString(R.string.discord_error_heartbeat_failed))
                    }
                }
                7 -> failFromCallback(context.getString(R.string.discord_error_gateway_reconnect))
                9 -> failFromCallback(context.getString(R.string.discord_error_session_rejected))
                10 -> {
                    val interval = payload.optJSONObject("d")?.optLong("heartbeat_interval") ?: 0L
                    startHeartbeat(webSocket, interval, connectionGeneration)
                    webSocket.send(KizzyGatewayProtocol.identify(tokens.accessToken))
                }

                11 -> heartbeatTracker.acknowledge()
            }
        }

        override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
            if (connectionGeneration != generation) return
            failFromCallback(context.getString(R.string.discord_error_gateway_failed))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (connectionGeneration != generation) return
            heartbeatJob?.cancel()
            if (_connectionState.value == DiscordConnectionState.CONNECTED) {
                _connectionState.value = DiscordConnectionState.DISCONNECTED
            }
        }
    }

    private fun startHeartbeat(webSocket: WebSocket, intervalMs: Long, connectionGeneration: Int) {
        heartbeatJob?.cancel()
        if (intervalMs <= 0) return
        heartbeatTracker.reset()
        heartbeatJob = scope.launch {
            delay(Random.nextLong(intervalMs.coerceAtLeast(1L)))
            while (isActive && connectionGeneration == generation) {
                if (!heartbeatTracker.markPeriodicHeartbeatSent()) {
                    failFromCallback(context.getString(R.string.discord_error_heartbeat_unacknowledged))
                    return@launch
                }
                if (!webSocket.send(KizzyGatewayProtocol.heartbeat(sequence))) {
                    failFromCallback(context.getString(R.string.discord_error_heartbeat_failed))
                    return@launch
                }
                delay(intervalMs)
            }
        }
    }

    private suspend fun ensureConnected(): Boolean {
        if (_connectionState.value == DiscordConnectionState.CONNECTED) return true
        if (!reconnectBackoff.canAttempt()) return false
        val saved = tokenStore.load() ?: return false
        val connected = connect(saved) == DiscordLinkResult.Success
        if (!connected) reconnectBackoff.recordFailure()
        return connected
    }

    private suspend fun resolveExternalImage(imageUrl: String): String? {
        if (!imageUrl.startsWith("https://")) return null
        imageCache[imageUrl]?.let { return it }
        val token = currentToken ?: return null

        return withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("urls", JSONArray().put(imageUrl))
                    .toString()
                    .toRequestBody(JSON_MEDIA_TYPE)
                val request = Request.Builder()
                    .url("https://discord.com/api/v9/applications/$applicationId/external-assets")
                    .header("Authorization", token)
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val path = JSONArray(response.body?.string().orEmpty())
                        .optJSONObject(0)
                        ?.optString("external_asset_path")
                        ?.takeIf(String::isNotBlank)
                    path?.let { "mp:$it" }
                }
            }.getOrNull()?.also { imageCache[imageUrl] = it }
        }
    }

    private fun closeSocket() {
        generation++
        heartbeatJob?.cancel()
        heartbeatJob = null
        readySignal?.cancel()
        readySignal = null
        socket?.close(1000, "Flow Discord presence closed")
        socket = null
        sequence = null
        heartbeatTracker.reset()
        currentToken = null
    }

    private fun fail(message: String): DiscordLinkResult.Failure {
        _connectionState.value = DiscordConnectionState.ERROR
        _lastError.value = message
        return DiscordLinkResult.Failure(message)
    }

    private fun failFromCallback(message: String) {
        val failure = fail(message)
        readySignal?.complete(failure)
        closeSocket()
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val LOGIN_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5)
        val CONNECTION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20)
        const val MAX_IMAGE_CACHE_ENTRIES = 64
    }

    private fun fail(@StringRes messageRes: Int): DiscordLinkResult.Failure =
        fail(context.getString(messageRes))
}
