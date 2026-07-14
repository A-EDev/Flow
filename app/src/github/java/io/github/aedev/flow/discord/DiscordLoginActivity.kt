package io.github.aedev.flow.discord

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

class DiscordLoginActivity : ComponentActivity() {
    private var completed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            webViewClient = object : WebViewClient() {
                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    requestTokenWhenAuthenticated(view, url)
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    requestTokenWhenAuthenticated(view, url)
                }
            }
            loadUrl(DISCORD_LOGIN_URL)
        }
        setContentView(webView)
    }

    override fun onDestroy() {
        if (!completed && isFinishing) {
            DiscordLoginBroker.fail("Discord connection was cancelled.")
        }
        super.onDestroy()
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
                webView.clearHistory()
                webView.clearCache(true)
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies(null)
                finish()
            }
        }
    }

    private companion object {
        const val DISCORD_LOGIN_URL = "https://discord.com/login"
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
