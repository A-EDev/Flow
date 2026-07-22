package io.github.aedev.flow.discord

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

class DiscordLoginActivity : ComponentActivity() {
    private var completed = false
    private var cleanupStarted = false
    private lateinit var loginWebView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        loginWebView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    if (!request.isForMainFrame) return false
                    return handleNavigation(view, request.url.toString())
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    return handleNavigation(view, url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    requestTokenWhenAuthenticated(view, url)
                }
            }
            loadUrl(DISCORD_LOGIN_URL)
        }
        setContentView(loginWebView)
    }

    override fun onDestroy() {
        if (!completed && isFinishing) {
            DiscordLoginBroker.fail(getString(io.github.aedev.flow.R.string.discord_error_login_cancelled))
        }
        clearLoginSession()
        super.onDestroy()
    }

    private fun handleNavigation(webView: WebView, url: String): Boolean {
        if (!DiscordLoginTokenExtractor.isAllowedNavigation(url)) return true
        requestTokenWhenAuthenticated(webView, url)
        return false
    }

    private fun requestTokenWhenAuthenticated(webView: WebView, url: String) {
        if (!completed && DiscordLoginTokenExtractor.isAuthenticatedAppUrl(url)) {
            readToken(webView)
        }
    }

    private fun readToken(webView: WebView) {
        webView.evaluateJavascript(TOKEN_SCRIPT) { encodedResult ->
            val token = DiscordLoginTokenExtractor.decodeJavascriptValue(encodedResult)

            if (token != null && !completed) {
                completed = true
                DiscordLoginBroker.complete(token)
                clearLoginSession(onComplete = ::finish)
            }
        }
    }

    private fun clearLoginSession(onComplete: (() -> Unit)? = null) {
        if (cleanupStarted) {
            onComplete?.invoke()
            return
        }
        cleanupStarted = true

        WebStorage.getInstance().deleteOrigin(DISCORD_ORIGIN)
        if (::loginWebView.isInitialized) {
            loginWebView.stopLoading()
            loginWebView.clearFormData()
            loginWebView.clearHistory()
        }
        clearDiscordCookies {
            if (::loginWebView.isInitialized) {
                loginWebView.removeAllViews()
                loginWebView.destroy()
            }
            onComplete?.invoke()
        }
    }

    private fun clearDiscordCookies(onComplete: () -> Unit) {
        val cookieManager = CookieManager.getInstance()
        val cookieNames = cookieManager.getCookie(DISCORD_ORIGIN)
            .orEmpty()
            .split(';')
            .mapNotNull { cookie -> cookie.substringBefore('=').trim().takeIf(String::isNotEmpty) }
            .distinct()
        if (cookieNames.isEmpty()) {
            cookieManager.flush()
            onComplete()
            return
        }

        var remaining = cookieNames.size
        cookieNames.forEach { name ->
            cookieManager.setCookie(
                DISCORD_ORIGIN,
                "$name=; Max-Age=0; Path=/; Secure; SameSite=Lax",
            ) {
                remaining -= 1
                if (remaining == 0) {
                    cookieManager.flush()
                    onComplete()
                }
            }
        }
    }

    private companion object {
        const val DISCORD_LOGIN_URL = "https://discord.com/login"
        const val DISCORD_ORIGIN = "https://discord.com"
        const val TOKEN_SCRIPT = "(function(){" +
            "var frame=document.createElement('iframe');" +
            "frame.style.display='none';" +
            "document.body.appendChild(frame);" +
            "var token=frame.contentWindow.localStorage.getItem('token');" +
            "frame.remove();" +
            "return token;" +
            "})()"
    }
}
