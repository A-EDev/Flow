package io.github.aedev.flow.utils.potoken

/**
 * This is based and ported from Metrolist,
 * see https://github.com/MetrolistGroup/Metrolist for the original code and license.
 */
class PoTokenException(message: String) : Exception(message)

// to be thrown if the WebView provided by the system is broken
class BadWebViewException(message: String) : Exception(message)

fun buildExceptionForJsError(error: String): Exception {
    return if (error.contains("SyntaxError"))
        BadWebViewException(error)
    else
        PoTokenException(error)
}
