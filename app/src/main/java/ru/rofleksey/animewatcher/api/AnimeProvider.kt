package ru.rofleksey.animewatcher.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import okhttp3.Cookie
import okhttp3.HttpUrl
import ru.rofleksey.animewatcher.api.model.EpisodeInfo
import ru.rofleksey.animewatcher.api.model.ProviderStats
import ru.rofleksey.animewatcher.api.model.Quality
import ru.rofleksey.animewatcher.api.model.TitleInfo
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.WebViewWrapper
import ru.rofleksey.animewatcher.util.Util
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface AnimeProvider {
    suspend fun search(title: String): List<TitleInfo>
    suspend fun getEpisodeList(titleInfo: TitleInfo, page: Int): List<EpisodeInfo>
    suspend fun getStorageLinks(
        titleInfo: TitleInfo,
        episodeInfo: EpisodeInfo,
        prefQuality: Quality
    ): List<String>

    suspend fun init(
        context: Context,
        prefs: SharedPreferences,
        updateStatus: (title: String) -> Unit
    )

    fun stats(): ProviderStats
    fun clearCache()

    suspend fun getAllEpisodes(titleInfo: TitleInfo): List<EpisodeInfo> {
        val result = ArrayList<EpisodeInfo>()
        for (i in 0..Int.MAX_VALUE) {
            val page = getEpisodeList(titleInfo, i)
            Log.v("AnimeProvider", "page - $page")
            if (page.isEmpty()) {
                break
            }
            result.addAll(page)
        }
        val stats = stats()
        return if (stats.episodesDesc) {
            result.reversed()
        } else {
            result
        }
    }

    fun getGlideUrl(url: String): GlideUrl? {
        return try {
            val cookies = HttpHandler.instance.getCookiesString(url)
            Log.v("AnimeProvider", "glide cookies for $url: $cookies")
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

    suspend fun bypassCloudflare(context: Context, url: HttpUrl) {
        val webWrapper = WebViewWrapper.with(context)
        val result: MutableList<Cookie> = mutableListOf()
        suspendCoroutine<Unit> { cont ->
            webWrapper.webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    try {
                        if (webWrapper.webView.title.contains("animepahe") && url != null) {
                            result.clear()
                            result.addAll(webWrapper.getCookies(url, "animepahe.com"))
                            cont.resume(Unit)
                        } else {
                            Log.v("AnimeProvider", "page title = ${webWrapper.webView.title}")
                        }
                    } catch (e: Throwable) {
                        cont.resumeWithException(e)
                    }
                }
            }
            webWrapper.webView.loadUrl(url.toString())
        }
        HttpHandler.instance.saveCookies(url, result)
    }
}