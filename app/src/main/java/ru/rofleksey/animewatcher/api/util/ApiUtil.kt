package ru.rofleksey.animewatcher.api.util

import android.content.Context
import android.util.Log
import android.util.Patterns
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.HttpUrl
import ru.rofleksey.animewatcher.api.model.Quality
import ru.rofleksey.animewatcher.util.AnimeUtils.Companion.USER_AGENT
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiUtil {
    companion object {

        private val CLOUDFLARE_TAG = "CloudflareBypass"
        private const val BYPASS_TIMEOUT_DEFAULT = 15000L
        private const val CLOUDFLARE_TIME_THRESHOLD = 1000 * 120 * 10
        private val URL_REGEX = Patterns.WEB_URL.toRegex()

        fun sanitizeScheme(url: String): String {
            return if (url.startsWith("//")) {
                "https:$url"
            } else url
        }

        fun strToQuality(str: String): Quality {
            return when (str.replace("p", "").trim()) {
                "360" -> Quality.q360
                "480" -> Quality.q480
                "720" -> Quality.q720
                "1080" -> Quality.q1080
                else -> Quality.UNKNOWN
            }
        }

        fun getRegex(str: String, regex: Regex): String {
            val match = regex.find(str) ?: throw IOException("can't match regex $regex")
            return match.groupValues[1]
        }

        fun getRegexSafe(str: String, regex: Regex): String? {
            val match = regex.find(str) ?: return null
            return match.groupValues[1]
        }

        fun getRegexAll(str: String, regex: Regex): Sequence<String> {
            return regex.findAll(str).map {
                it.groupValues[1]
            }
        }

        @Throws(NoSuchAlgorithmException::class, UnsupportedEncodingException::class)
        fun sha1(text: String): String {
            val digest: MessageDigest = MessageDigest.getInstance("SHA-1")
            val result = digest.digest(text.toByteArray(Charset.forName("UTF-8")))
            val sb = StringBuilder()
            for (b in result) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        }

        fun extractUrls(text: String): List<String> {
            return getRegexAll(text, URL_REGEX).toList()
        }

        fun clearCloudflare(context: Context, cookieHost: String) {
            val prefs = context.getSharedPreferences("cloudflare", Context.MODE_PRIVATE)
            prefs.edit().remove(cookieHost).apply()
        }

        suspend fun bypassCloudflare(
            context: Context,
            url: HttpUrl,
            title: String,
            cookieHost: String,
            timeThreshold: Int = CLOUDFLARE_TIME_THRESHOLD,
            clearCookies: Boolean = true
        ) {
            val prefs = context.getSharedPreferences("cloudflare", Context.MODE_PRIVATE)
            val lastTime = prefs.getLong(cookieHost, 0)
            val curTime = System.currentTimeMillis()
            if (!(curTime - lastTime > timeThreshold || lastTime > curTime)) {
                Log.v(
                    CLOUDFLARE_TAG,
                    "Skipped, ${(timeThreshold - (curTime - lastTime)) / 1000}s till next bypass"
                )
                return
            }
            Log.v(CLOUDFLARE_TAG, "bypassing...")
            val result: MutableList<Cookie> = mutableListOf()
            withContext(Dispatchers.Main) {
                val webWrapper = WebViewWrapper.with(context, clearCookies)
                withTimeout(BYPASS_TIMEOUT_DEFAULT) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        webWrapper.webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                try {
                                    if (webWrapper.webView.title.contains(title) && url != null) {
                                        result.clear()
                                        result.addAll(webWrapper.getCookies(url, cookieHost))
                                        cont.resume(Unit)
                                    } else {
                                        Log.v(
                                            CLOUDFLARE_TAG,
                                            "page title = ${webWrapper.webView.title}"
                                        )
                                    }
                                } catch (e: Throwable) {
                                    cont.resumeWithException(e)
                                }
                            }
                        }
                        val headers = HashMap<String, String>()
                        headers["X-Requested-With"] = ""
                        headers["User-Agent"] = USER_AGENT
                        webWrapper.webView.loadUrl(url.toString(), headers)
                        cont.invokeOnCancellation {
                            webWrapper.destroy()
                        }
                    }
                    webWrapper.destroy()
                }
            }
            HttpHandler.instance.saveCookies(url, result)
            prefs.edit().putLong(cookieHost, curTime).apply()
            Log.v(CLOUDFLARE_TAG, "Bypass OK")
        }
    }
}