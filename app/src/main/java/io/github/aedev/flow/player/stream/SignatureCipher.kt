package io.github.aedev.flow.player.stream

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A format's `signatureCipher`: the stream URL without its signature, the scrambled signature, and
 * the query parameter the solved signature goes in. Free of `android.net.Uri` so it runs in a plain
 * unit test.
 */
internal data class SignatureCipher(
    val url: String,
    val signature: String,
    val parameter: String,
) {
    fun signedUrl(solvedSignature: String): String {
        val separator = if ('?' in url) '&' else '?'
        return "$url$separator$parameter=${URLEncoder.encode(solvedSignature, Charsets.UTF_8.name())}"
    }

    companion object {
        private const val DEFAULT_PARAMETER = "signature"

        fun parse(cipher: String?): SignatureCipher? {
            if (cipher.isNullOrBlank()) return null
            val fields =
                cipher
                    .split('&')
                    .mapNotNull { pair ->
                        val separator = pair.indexOf('=')
                        if (separator <= 0) return@mapNotNull null
                        pair.substring(0, separator) to decode(pair.substring(separator + 1))
                    }.toMap()
            val url = fields["url"]?.takeIf { it.isNotBlank() } ?: return null
            val signature = fields["s"]?.takeIf { it.isNotBlank() } ?: return null
            return SignatureCipher(url, signature, fields["sp"]?.takeIf { it.isNotBlank() } ?: DEFAULT_PARAMETER)
        }

        private fun decode(raw: String): String =
            try {
                URLDecoder.decode(raw, Charsets.UTF_8.name())
            } catch (e: IllegalArgumentException) {
                raw
            }
    }
}
