package com.example.animewatcher.api

import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bumptech.glide.load.model.GlideUrl
import com.example.animewatcher.api.model.EpisodeInfo
import com.example.animewatcher.api.model.ProviderStats
import com.example.animewatcher.api.model.Quality
import com.example.animewatcher.api.model.TitleInfo
import com.example.animewatcher.api.provider.AnimePahe
import com.example.animewatcher.api.util.HttpHandler
import com.example.animewatcher.api.util.WebViewWrapper
import okhttp3.Cookie
import okhttp3.HttpUrl
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface AnimeProvider {
    suspend fun search(title: String) : List<TitleInfo>
    suspend fun getEpisodeList(titleInfo: TitleInfo, page: Int) : List<EpisodeInfo>
    suspend fun getStorageLinks(titleInfo: TitleInfo, episodeInfo: EpisodeInfo) : Map<Quality, String>
    suspend fun getAllTitles(): List<String>?
    suspend fun init(context: Context, prefs: SharedPreferences, updateStatus: (title: String) -> Unit)
    fun getGlideUrl(url: String): GlideUrl?
    fun stats(): ProviderStats
    fun clearCache()

    suspend fun getAllEpisodes(titleInfo: TitleInfo): List<EpisodeInfo> {
        val result = mutableListOf<EpisodeInfo>()
        for (i in 0 .. Int.MAX_VALUE) {
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
            webWrapper.webView.webViewClient = object: WebViewClient() {
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