package ru.rofleksey.animewatcher.api.provider

import android.content.Context
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.HttpUrl
import ru.rofleksey.animewatcher.api.model.*
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.WebViewWrapper
import ru.rofleksey.animewatcher.util.AnimeUtils
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

abstract class AnimeProvider(val context: Context) {
    companion object {
        private const val CLOUDFLARE_TIME_THRESHOLD = 1000 * 60 * 5
        private const val TAG = "AnimeProvider"
        private const val CLOUDFLARE_TAG = "CloudflareBypass"
        private const val BYPASS_TIMEOUT = 11000L
    }

    abstract suspend fun search(title: String): List<TitleInfo>
    abstract suspend fun updateTitleMeta(titleInfo: TitleInfo)
    abstract suspend fun getEpisodeList(titleInfo: TitleInfo, page: Int): List<EpisodeInfo>
    abstract suspend fun getStorageLinks(
        titleInfo: TitleInfo,
        episodeInfo: EpisodeInfo
    ): List<ProviderResult>

    abstract fun stats(): ProviderStats

    suspend fun getAllEpisodes(titleInfo: TitleInfo): List<EpisodeInfo> {
        val result = ArrayList<EpisodeInfo>()
        updateTitleMeta(titleInfo)
        for (i in 0..Int.MAX_VALUE) {
            val page = getEpisodeList(titleInfo, i)
            Log.v(TAG, "page - $page")
            if (page.isEmpty()) {
                break
            }
            result.addAll(page)
        }
        val stats = stats()
        val preResult = if (stats.episodesDesc) {
            result
        } else {
            result.reversed()
        }
        return when (titleInfo.airStatus) {
            TitleAirStatus.AIRING -> preResult
            else -> preResult.reversed()
        }
    }

    fun getGlideUrl(url: String): GlideUrl? {
        return try {
            val cookies = HttpHandler.instance.getCookiesString(url)
//            Log.v("AnimeProvider", "glide cookies for $url: $cookies")
            GlideUrl(
                url,
                LazyHeaders.Builder()
                    .addHeader("User-Agent", AnimeUtils.USER_AGENT)
                    .addHeader("Cookie", cookies)
                    .build()
            )
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    protected suspend fun bypassCloudflare(
        context: Context,
        host: String,
        title: String,
        cookieHost: String
    ) {
        val prefs = context.getSharedPreferences("cloudflare", Context.MODE_PRIVATE)
        val lastTime = prefs.getLong(host, 0)
        val curTime = System.currentTimeMillis()
        if (!(curTime - lastTime > CLOUDFLARE_TIME_THRESHOLD || lastTime > curTime)) {
            Log.v(
                CLOUDFLARE_TAG,
                "Skipped, ${(CLOUDFLARE_TIME_THRESHOLD - (curTime - lastTime)) / 1000}s till next bypass"
            )
            return
        }
        Log.v(CLOUDFLARE_TAG, "bypassing...")
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .build()
        val result: MutableList<Cookie> = mutableListOf()
        withContext(Dispatchers.Main) {
            val webWrapper = WebViewWrapper.with(context)
            withTimeout(BYPASS_TIMEOUT) {
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
                    webWrapper.webView.loadUrl(url.toString())
                    cont.invokeOnCancellation {
                        webWrapper.destroy()
                    }
                }
                webWrapper.destroy()
            }
        }
        HttpHandler.instance.saveCookies(url, result)
        prefs.edit().putLong(host, curTime).apply()
        Log.v(CLOUDFLARE_TAG, "Bypass OK")
    }
}