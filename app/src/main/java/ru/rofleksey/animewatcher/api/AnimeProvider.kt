package ru.rofleksey.animewatcher.api

import android.content.Context
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl
import ru.rofleksey.animewatcher.api.model.*
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.WebViewWrapper
import ru.rofleksey.animewatcher.util.Util
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

abstract class AnimeProvider(val context: Context) {
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
            Log.v("AnimeProvider", "page - $page")
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
                    .addHeader("User-Agent", Util.USER_AGENT)
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
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .build()
        val result: MutableList<Cookie> = mutableListOf()
        withContext(Dispatchers.Main) {
            val webWrapper = WebViewWrapper.with(context)
            suspendCoroutine<Unit> { cont ->
                webWrapper.webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        try {
                            if (webWrapper.webView.title.contains(title) && url != null) {
                                result.clear()
                                result.addAll(webWrapper.getCookies(url, cookieHost))
                                cont.resume(Unit)
                            } else {
                                Log.v(
                                    "CloudflareBypass",
                                    "page title = ${webWrapper.webView.title}"
                                )
                            }
                        } catch (e: Throwable) {
                            cont.resumeWithException(e)
                        }
                    }
                }
                webWrapper.webView.loadUrl(url.toString())
            }
            webWrapper.destroy()
        }
        HttpHandler.instance.saveCookies(url, result)
    }
}