package com.example.animewatcher.api.provider

import com.example.animewatcher.api.*
import com.example.animewatcher.api.model.EpisodeInfo
import com.example.animewatcher.api.model.ProviderStats
import com.example.animewatcher.api.model.Quality
import com.example.animewatcher.api.model.TitleInfo
import com.example.animewatcher.api.util.HttpHandler
import com.example.animewatcher.api.util.SimpleCache
import com.example.animewatcher.api.util.actualBody
import com.google.gson.Gson
import com.google.gson.JsonParser

class AnimePahe : AnimeProvider {
    companion object {
        private const val HOST = "animepahe.com"
        private val gson = Gson()
    }

    private val httpHandler = HttpHandler()
    private val searchCache = SimpleCache<List<TitleInfo>>()
    private val episodesCache = SimpleCache<List<EpisodeInfo>>()
    private val storageCache = SimpleCache<Map<Quality, String>>()

    override suspend fun search(title: String): List<TitleInfo> {
        return httpHandler.execute({
            this.scheme("https")
                .host(HOST)
                .addPathSegment("api")
                .addQueryParameter("m", "search")
                .addQueryParameter("l", "8")
                .addQueryParameter("q", title)
        }, { this }, {
            val obj = gson.fromJson(this.actualBody(), PaheTitleResponse::class.java)
            obj.toTitleInfo()
        }, searchCache, title)
    }

    override suspend fun getEpisodeList(title: String, page: Int): List<EpisodeInfo> {
        val searchResult = searchExact(title) ?: return listOf()
        return httpHandler.execute({
            this.scheme("https")
                .host(HOST)
                .addPathSegment("api")
                .addQueryParameter("m", "release")
                .addQueryParameter("id", searchResult.id.toString())
                .addQueryParameter("l", "30")
                .addQueryParameter("sort", "episode_desc")
                .addQueryParameter("page", (page + 1).toString())
        }, { this }, {
            val obj = gson.fromJson(this.actualBody(), PaheEpisodesResponse::class.java)
            obj.toEpisodeInfo()
        }, episodesCache, title, page)
    }

    override suspend fun getStorageLink(title: String, episode: Int): Map<Quality, String> {
        val episodeResult = getExactEpisode(title, episode) ?: return mapOf()
        val episodeId = episodeResult.id.toString()
        return httpHandler.execute({
            this.scheme("https")
                .host(HOST)
                .addPathSegment("api")
                .addQueryParameter("m", "embed")
                .addQueryParameter("id", episodeId)
                .addQueryParameter("p", "kwik")
        }, { this }, {
            println("getStorageLink: $this")
            val obj = JsonParser.parseString(this.actualBody()).asJsonObject
            val data = obj.getAsJsonObject("data")
            val ep = data.getAsJsonObject(episodeId)
            val links = mutableMapOf<Quality, String>()
            for (prop in ep.entrySet()) {
                val key = when (prop.key) {
                    "360p" -> Quality.q360
                    "480p" -> Quality.q480
                    "720p" -> Quality.q720
                    "1080p" -> Quality.q1080
                    else -> Quality.UNKNOWN
                }
                links[key] = prop.value.asJsonObject.get("url").asString
            }
            links
        }, storageCache, title, episode)
    }

    override fun stats(): ProviderStats {
        return ProviderStats(
            episodesPerPage = 30,
            episodesDesc = true
        )
    }

    override fun clearCache() {
        storageCache.clear()
        episodesCache.clear()
        searchCache.clear()
    }

}