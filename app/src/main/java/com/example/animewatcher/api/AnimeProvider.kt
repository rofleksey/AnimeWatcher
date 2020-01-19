package com.example.animewatcher.api

import com.example.animewatcher.api.model.EpisodeInfo
import com.example.animewatcher.api.model.ProviderStats
import com.example.animewatcher.api.model.Quality
import com.example.animewatcher.api.model.TitleInfo
import java.util.concurrent.*

interface AnimeProvider {
    suspend fun search(title: String) : List<TitleInfo>
    suspend fun getEpisodeList(title: String, page: Int) : List<EpisodeInfo>
    suspend fun getStorageLink(title: String, episode: Int) : Map<Quality, String>
    fun stats(): ProviderStats
    fun clearCache()

    suspend fun searchExact(title: String): TitleInfo? {
        val searchResult = search(title)
        if (searchResult.isEmpty()) {
            return null
        }
        if (searchResult[0].title == title) {
            return searchResult[0]
        }
        return null
    }

    suspend fun getExactEpisode(title: String, number: Int): EpisodeInfo? {
        val searchResult = searchExact(title) ?: return null
        val page = getPage(number, searchResult.episodeCount)
        val numStr = number.toString()
        return getEpisodeList(title, page).find { it.name == numStr }
    }

    suspend fun getPage(episodeNumber: Int, count: Int): Int {
        val stats = stats()
        var num = episodeNumber
        if (stats.episodesDesc) {
            num = count - episodeNumber
        }
        return num / stats.episodesPerPage
    }

    suspend fun getAllEpisodes(title: String): List<EpisodeInfo> {
        val result = mutableListOf<EpisodeInfo>()
        for (i in 0 .. Int.MAX_VALUE) {
            val page = getEpisodeList(title, i)
            if (page.isEmpty()) {
                break
            }
            result.addAll(page)
        }
        return result
    }
}