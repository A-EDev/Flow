package io.github.aedev.flow.player.error

internal data class StreamFailureContext(
    val reason: String,
    val httpCode: Int? = null,
    val url: String? = null,
    val videoHeight: Int? = null,
    val videoCodec: String? = null,
    val videoItag: String? = null,
    val videoMimeType: String? = null,
    val audioItag: String? = null,
    val audioMimeType: String? = null
) {
    val variantKey: String = listOfNotNull(
        videoHeight?.let { "${it}p" },
        videoCodec?.takeIf { it.isNotBlank() },
        videoItag?.takeIf { it.isNotBlank() }?.let { "v=$it" },
        audioItag?.takeIf { it.isNotBlank() }?.let { "a=$it" }
    ).joinToString("|").ifBlank { url.orEmpty() }

    fun toLogString(): String = buildList {
        add("reason=$reason")
        httpCode?.let { add("http=$it") }
        videoHeight?.let { add("video=${it}p") }
        videoCodec?.takeIf { it.isNotBlank() }?.let { add("codec=$it") }
        videoItag?.takeIf { it.isNotBlank() }?.let { add("videoItag=$it") }
        videoMimeType?.takeIf { it.isNotBlank() }?.let { add("videoMime=$it") }
        audioItag?.takeIf { it.isNotBlank() }?.let { add("audioItag=$it") }
        audioMimeType?.takeIf { it.isNotBlank() }?.let { add("audioMime=$it") }
    }.joinToString(" ")
}

internal class StreamExpiryRetryLimiter(
    private val maxConsecutiveFailures: Int,
    private val debounceMs: Long,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    sealed class Decision {
        data class Retry(val attempt: Int, val limit: Int) : Decision()
        data class GiveUp(val attempts: Int, val limit: Int) : Decision()
        object Debounced : Decision()
        object AlreadyAbandoned : Decision()
    }

    private var consecutiveFailureCount = 0
    private var lastVariantKey: String? = null
    private var lastTriggerMs = 0L
    private var abandonedVariantKey: String? = null

    fun record(context: StreamFailureContext): Decision {
        val variantKey = context.variantKey
        if (abandonedVariantKey != null && abandonedVariantKey == variantKey) {
            return Decision.AlreadyAbandoned
        }

        val now = clockMs()
        if (now - lastTriggerMs < debounceMs) {
            return Decision.Debounced
        }
        lastTriggerMs = now

        consecutiveFailureCount = if (variantKey == lastVariantKey) {
            consecutiveFailureCount + 1
        } else {
            lastVariantKey = variantKey
            1
        }

        return if (consecutiveFailureCount > maxConsecutiveFailures) {
            abandonedVariantKey = variantKey
            Decision.GiveUp(consecutiveFailureCount, maxConsecutiveFailures)
        } else {
            Decision.Retry(consecutiveFailureCount, maxConsecutiveFailures)
        }
    }

    fun reset() {
        consecutiveFailureCount = 0
        lastVariantKey = null
        lastTriggerMs = 0L
        abandonedVariantKey = null
    }

    fun hasGivenUp(): Boolean = abandonedVariantKey != null
}
