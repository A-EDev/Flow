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
import org.json.JSONArray

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
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (url.startsWith("https://discord.com/")) {
                        readToken(view)
                    }
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

    private fun readToken(webView: WebView) {
        webView.evaluateJavascript(TOKEN_SCRIPT) { encodedResult ->
            val token = runCatching {
                JSONArray("[$encodedResult]").getString(0)
            }.getOrNull()
                ?.trim()
                ?.removeSurrounding("\"")
                ?.takeIf { it.isNotEmpty() && it != "null" }

            if (token != null) {
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
        const val TOKEN_SCRIPT = "(function(){return window.localStorage.getItem('token');})()"
    }
}
