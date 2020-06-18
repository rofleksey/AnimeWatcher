package ru.rofleksey.animewatcher.api.provider

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import ru.rofleksey.animewatcher.R
import ru.rofleksey.animewatcher.api.model.*
import ru.rofleksey.animewatcher.api.util.ApiUtil
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody

class YoutubeProvider(context: Context) : AnimeProvider(context) {
    companion object {
        private const val TAG = "YoutubeProvider"
        private const val HOST = "www.googleapis.com"
    }

    private val apiKey: String = context.getString(R.string.youtube_key)
    private val filterLinksArray =
        listOf("patreon.com", "twitter.com", "myanimelist.net", "youtube.com")

    override suspend fun search(title: String): List<TitleInfo> {
        return HttpHandler.instance.executeDirect({
            this.scheme("https")
                .host(HOST)
                .addPathSegments("youtube/v3/search")
                .addQueryParameter("part", "snippet")
                .addQueryParameter("type", "playlist")
                .addQueryParameter("maxResults", "8")
                .addQueryParameter("q", title)
                .addQueryParameter("key", apiKey)
        }, { this }, {
            val body = this.actualBody()
            val results = JsonParser.parseString(body).asJsonObject.get("items").asJsonArray
            results.map {
                val id = it.asJsonObject.get("id").asJsonObject.get("playlistId").asString
                val snippet = it.asJsonObject.get("snippet").asJsonObject
                val name = snippet.get("title").asString
                var image: String? = null
                if (snippet.has("thumbnails")) {
                    val thumbnails = snippet.get("thumbnails").asJsonObject
                    if (thumbnails.has("default")) {
                        image = thumbnails.get("default").asJsonObject.get("url").asString
                    }
                }
                val channelName = snippet.get("channelTitle").asString
                TitleInfo(name, channelName, TitleAirStatus.UNKNOWN, image).also { params ->
                    params["id"] = id
                }
            }
        })
    }

    override suspend fun updateTitleMeta(titleInfo: TitleInfo) {

    }

    override suspend fun getEpisodeList(titleInfo: TitleInfo, page: Int): List<EpisodeInfo> {
        if (page != 0) {
            return listOf()
        }
        Log.v(TAG, "title.id = ${titleInfo["id"]}")
        return HttpHandler.instance.executeDirect({
            this.scheme("https")
                .host(HOST)
                .addPathSegments("youtube/v3/playlistItems")
                .addQueryParameter("part", "snippet")
                .addQueryParameter("playlistId", titleInfo["id"])
                .addQueryParameter("maxResults", "50")
                .addQueryParameter("key", apiKey)
        }, { this }, {
            val body = this.actualBody()
            val results = JsonParser.parseString(body).asJsonObject.get("items").asJsonArray
            results.map {
                val snippet = it.asJsonObject.get("snippet").asJsonObject
                val name = snippet.get("title").asString
                val thumbnails = snippet.get("thumbnails").asJsonObject
                var image: String? = null
                if (thumbnails.has("default")) {
                    image = thumbnails.get("default").asJsonObject.get("url").asString
                }
                val description = snippet.get("description").asString
                Log.v(TAG, description)
                val videoId = snippet.get("resourceId").asJsonObject.get("videoId").asString
                val links = ApiUtil.extractUrls(description)
                val linksArray = JsonArray()
                links.filter { link ->
                    !filterLinksArray.any { filter ->
                        link.contains(filter)
                    }
                }.forEach { link ->
                    linksArray.add(link)
                }
                EpisodeInfo(name, image).also { params ->
                    params["links"] = linksArray.toString()
                    params["videoId"] = videoId
                }
            }
        })
    }

    override suspend fun getStorageLinks(
        titleInfo: TitleInfo,
        episodeInfo: EpisodeInfo
    ): List<ProviderResult> {
        val links = JsonParser.parseString(episodeInfo["links"]).asJsonArray
        return links.map {
            ProviderResult(it.asString, Quality.UNKNOWN)
        }
    }

    override fun stats(): ProviderStats {
        return ProviderStats(
            needsContext = false,
            hasCloudfare = true,
            episodesDesc = false,
            episodesPerPage = 100
        )
    }

}