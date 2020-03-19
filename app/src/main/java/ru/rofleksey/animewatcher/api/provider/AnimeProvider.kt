package ru.rofleksey.animewatcher.api.provider

import android.content.Context
import android.util.Log
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import ru.rofleksey.animewatcher.api.model.*
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.util.AnimeUtils

abstract class AnimeProvider(val context: Context) {
    companion object {
        private const val TAG = "AnimeProvider"
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
}