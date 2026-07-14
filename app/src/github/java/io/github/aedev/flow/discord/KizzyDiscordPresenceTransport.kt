package io.github.aedev.flow.discord

import android.app.Activity
import android.content.Context
import android.content.Intent
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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
    private val imageCache = ConcurrentHashMap<String, String>()
    private var activityReference = WeakReference<Activity>(null)
    private var socket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var readySignal: CompletableDeferred<DiscordLinkResult>? = null
    private var currentToken: String? = null
    private var sequence: Int? = null
    private var generation = 0

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
        val activity = activityReference.get()
            ?: return fail("Open Flow before connecting Discord.")
        val result = DiscordLoginBroker.begin()
            ?: return fail("A Discord connection is already in progress.")

        _connectionState.value = DiscordConnectionState.LINKING
        _lastError.value = null
        activity.startActivity(Intent(activity, DiscordLoginActivity::class.java))

        val token = runCatching {
            withTimeout(LOGIN_TIMEOUT_MS) { result.await().getOrThrow() }
        }.getOrElse { error ->
            return fail(error.message ?: "Discord connection did not complete.")
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
        if (tokens.accessToken.isBlank()) return fail("Discord returned an empty session token.")
        if (applicationId.isBlank()) return fail("Discord application ID is missing from this build.")
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

        return runCatching {
            withTimeout(CONNECTION_TIMEOUT_MS) { signal.await() }
        }.getOrElse {
            fail("Discord Gateway connection timed out.")
        }
    }

    override suspend fun update(payload: DiscordPresencePayload): Boolean {
        if (!ensureConnected()) return false
        val resolvedImage = resolveExternalImage(payload.largeImage)
        return socket?.send(
            KizzyGatewayProtocol.presence(payload, applicationId, resolvedImage),
        ) == true
    }

    override suspend fun clear(): Boolean {
        if (_connectionState.value != DiscordConnectionState.CONNECTED) return true
        return socket?.send(KizzyGatewayProtocol.presence(null, applicationId, null)) == true
    }

    override suspend fun unlink(): Boolean {
        clear()
        closeSocket()
        tokenStore.clear()
        currentToken = null
        _linkedAccountName.value = null
        _lastError.value = null
        _connectionState.value = DiscordConnectionState.DISCONNECTED
        return true
    }

    override fun close() {
        if (_connectionState.value == DiscordConnectionState.CONNECTED) {
            socket?.send(KizzyGatewayProtocol.presence(null, applicationId, null))
        }
        closeSocket()
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
                        ?: "Discord user"
                    _linkedAccountName.value = accountName
                    _connectionState.value = DiscordConnectionState.CONNECTED
                    _lastError.value = null
                    tokenStore.save(tokens)
                    readySignal?.complete(DiscordLinkResult.Success)
                }

                1 -> webSocket.send(KizzyGatewayProtocol.heartbeat(sequence))
                7 -> failFromCallback("Discord requested a reconnect.")
                9 -> failFromCallback("Discord rejected the session. Reconnect your account.")
                10 -> {
                    val interval = payload.optJSONObject("d")?.optLong("heartbeat_interval") ?: 0L
                    startHeartbeat(webSocket, interval, connectionGeneration)
                    webSocket.send(KizzyGatewayProtocol.identify(tokens.accessToken))
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
            if (connectionGeneration != generation) return
            failFromCallback("Discord Gateway connection failed.")
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
        heartbeatJob = scope.launch {
            while (isActive && connectionGeneration == generation) {
                delay(intervalMs)
                webSocket.send(KizzyGatewayProtocol.heartbeat(sequence))
            }
        }
    }

    private suspend fun ensureConnected(): Boolean {
        if (_connectionState.value == DiscordConnectionState.CONNECTED) return true
        val saved = tokenStore.load() ?: return false
        return connect(saved) == DiscordLinkResult.Success
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
    }

    private fun fail(message: String): DiscordLinkResult.Failure {
        _connectionState.value = DiscordConnectionState.ERROR
        _lastError.value = message
        return DiscordLinkResult.Failure(message)
    }

    private fun failFromCallback(message: String) {
        val failure = fail(message)
        readySignal?.complete(failure)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val LOGIN_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5)
        val CONNECTION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20)
    }
}
