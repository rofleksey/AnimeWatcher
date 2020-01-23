package ru.rofleksey.animewatcher.api.provider

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.HttpUrl
import ru.rofleksey.animewatcher.api.AnimeProvider
import ru.rofleksey.animewatcher.api.model.EpisodeInfo
import ru.rofleksey.animewatcher.api.model.ProviderStats
import ru.rofleksey.animewatcher.api.model.Quality
import ru.rofleksey.animewatcher.api.model.TitleInfo
import ru.rofleksey.animewatcher.api.util.ApiUtil
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody

class AnimePahe : AnimeProvider {
    companion object {
        private const val TAG = "AnimePahe"
        private const val BASE_URL = "https://animepahe.com"
        private const val HOST = "animepahe.com"
        private val gson = Gson()
    }

    override suspend fun search(title: String): List<TitleInfo> {
        return HttpHandler.instance.executeDirect({
            this.scheme("https")
                .host(HOST)
                .addPathSegment("api")
                .addQueryParameter("m", "search")
                .addQueryParameter("l", "8")
                .addQueryParameter("q", title)
        }, { this }, {
            val obj = gson.fromJson(this.actualBody(), PaheTitleResponse::class.java)
            obj.toTitleInfo()
        })
    }

    override suspend fun getEpisodeList(titleInfo: TitleInfo, page: Int): List<EpisodeInfo> {
        val stats = stats()
        return HttpHandler.instance.executeDirect({
            this.scheme("https")
                .host(HOST)
                .addPathSegment("api")
                .addQueryParameter("m", "release")
                .addQueryParameter("id", titleInfo["id"])
                .addQueryParameter("l", stats.episodesPerPage.toString())
                .addQueryParameter("sort", "episode_desc")
                .addQueryParameter("page", (page + 1).toString())
        }, { this }, {
            val obj = gson.fromJson(this.actualBody(), PaheEpisodesResponse::class.java)
            obj.toEpisodeInfo()
        })
    }

    override suspend fun getStorageLinks(
        titleInfo: TitleInfo,
        episodeInfo: EpisodeInfo,
        prefQuality: Quality
    ): List<String> {
        return HttpHandler.instance.executeDirect({
            this.scheme("https")
                .host(HOST)
                .addPathSegment("api")
                .addQueryParameter("m", "embed")
                .addQueryParameter("id", episodeInfo["id"])
                .addQueryParameter("p", "kwik")
        }, { this }, {
            val obj = JsonParser.parseString(this.actualBody()).asJsonObject
            val data = obj.getAsJsonObject("data")
            val ep = data.getAsJsonObject(episodeInfo["id"])
            val links = mutableListOf<Pair<Quality, String>>()
            for (prop in ep.entrySet()) {
                val key = when (prop.key) {
                    "360p" -> Quality.q360
                    "480p" -> Quality.q480
                    "720p" -> Quality.q720
                    "1080p" -> Quality.q1080
                    else -> Quality.UNKNOWN
                }
                links.add(Pair(key, prop.value.asJsonObject.get("url").asString))
            }
            listOf(ApiUtil.pickQuality(links, prefQuality))
        })
    }

    override suspend fun init(
        context: Context,
        prefs: SharedPreferences,
        updateStatus: (title: String) -> Unit
    ) {
        updateStatus("Initializing AnimePahe...")
        bypassCloudfare(
            context, HttpUrl.Builder()
                .scheme("https")
                .host(HOST)
                .build()
        )
        updateStatus("Initializing AnimePahe images...")
        bypassCloudfare(
            context, HttpUrl.Builder()
                .scheme("https")
                .host("i.$HOST")
                .build()
        )
    }

    override fun stats(): ProviderStats {
        return ProviderStats(
            episodesDesc = true,
            hasCloudfare = true,
            needsContext = true,
            loadingString = "Bypassing cloudflare scrape shield",
            episodesPerPage = 30
        )
    }

    override fun clearCache() {

    }

}