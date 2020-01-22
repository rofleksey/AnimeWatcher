package ru.rofleksey.animewatcher.api.util

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebView
import ru.rofleksey.animewatcher.util.Util
import okhttp3.Cookie

class WebViewWrapper private constructor(val webView: WebView) {
    companion object {
        fun with(context: Context): WebViewWrapper {
            val webView = WebView(context)
            webView.setWillNotDraw(true)
            CookieManager.getInstance().removeAllCookies(ValueCallback {

            })
            webView.clearCache(false)
            with (webView.settings) {
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                userAgentString = Util.USER_AGENT
            }
            return WebViewWrapper(webView)
        }
        private val cookieRegex = Regex("([^=]+)=([^\\;]*);?\\s?")
    }

    fun getCookies(url: String, domain: String): List<Cookie> {
        val cookieString: String = CookieManager.getInstance().getCookie(url)
        println("cookieString = $cookieString")
        val match = cookieRegex.findAll(cookieString)
        return match.map {
            val cookieKey = it.groupValues[1]
            val cookieValue = it.groupValues[2]
            Cookie.Builder()
                .name(cookieKey)
                .value(cookieValue)
                .domain(domain)
                .build()
        }.toList()
    }

    fun destroy() {
        webView.loadUrl("about:blank")
        webView.stopLoading()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            webView.freeMemory()
        }
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroyDrawingCache()
        webView.destroy()
    }
}