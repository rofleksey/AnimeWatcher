package ru.rofleksey.animewatcher.api

import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bumptech.glide.load.model.GlideUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import ru.rofleksey.animewatcher.api.model.EpisodeInfo
import ru.rofleksey.animewatcher.api.model.ProviderStats
import ru.rofleksey.animewatcher.api.model.Quality
import ru.rofleksey.animewatcher.api.model.TitleInfo
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.WebViewWrapper
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface AnimeProvider {
    suspend fun search(title: String): List<TitleInfo>
    suspend fun getEpisodeList(titleInfo: TitleInfo, page: Int): List<EpisodeInfo>
    suspend fun getStorageLinks(
        titleInfo: TitleInfo,
        episodeInfo: EpisodeInfo
    ): Map<Quality, String>

    suspend fun getAllTitles(): List<String>?
    suspend fun init(
        context: Context,
        prefs: SharedPreferences,
        updateStatus: (title: String) -> Unit
    )

    fun getGlideUrl(url: String): GlideUrl?
    fun stats(): ProviderStats
    fun clearCache()

    suspend fun getAllEpisodes(titleInfo: TitleInfo): List<EpisodeInfo> {
        val result = mutableListOf<EpisodeInfo>()
        for (i in 0..Int.MAX_VALUE) {
            val page = getEpisodeList(titleInfo, i)
            if (page.isEmpty()) {
                break
            }
            result.addAll(page)
        }
        return result
    }

    suspend fun bypassCloudfare(context: Context, url: HttpUrl) {
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
                            println("title = ${webWrapper.webView.title}")
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