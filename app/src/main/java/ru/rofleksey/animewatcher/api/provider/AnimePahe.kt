package ru.rofleksey.animewatcher.api.provider

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import ru.rofleksey.animewatcher.api.model.EpisodeInfo
import ru.rofleksey.animewatcher.api.model.ProviderResult
import ru.rofleksey.animewatcher.api.model.ProviderStats
import ru.rofleksey.animewatcher.api.model.TitleInfo
import ru.rofleksey.animewatcher.api.provider.template.PaheEpisodesResponse
import ru.rofleksey.animewatcher.api.provider.template.PaheTitleResponse
import ru.rofleksey.animewatcher.api.util.ApiUtil
import ru.rofleksey.animewatcher.api.util.ApiUtil.Companion.bypassCloudflare
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody

class AnimePahe(context: Context) : AnimeProvider(context) {
    companion object {
        private const val TAG = "AnimePahe"
        private const val BASE_URL = "https://animepahe.com"
        private const val HOST = "animepahe.com"
        private val gson = Gson()
    }

    override suspend fun search(title: String): List<TitleInfo> {
        bypass()
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

    override suspend fun updateTitleMeta(titleInfo: TitleInfo) {
        bypass()
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
        episodeInfo: EpisodeInfo
    ): List<ProviderResult> {
        bypass()
        return HttpHandler.instance.executeDirect({
            this.scheme("https")
                .host(HOST)
                .addPathSegment("api")
                .addQueryParameter("m", "embed")
                .addQueryParameter("id", titleInfo["id"])
                .addQueryParameter("session", episodeInfo["session"])
                .addQueryParameter("p", "kwik")
        }, { this }, {
            val body = this.actualBody()
            Log.v(TAG, body)
            val obj = JsonParser.parseString(body).asJsonObject
            Log.v(TAG, obj.toString())
            val data = obj.getAsJsonArray("data")
            data.map {
                it.asJsonObject.entrySet()
            }.flatMap {
                it.flatMap { entry ->
                    if (entry.value.asJsonObject.has("kwik")) {
                        listOf(ProviderResult(
                            entry.value.asJsonObject.get("kwik").asString,
                            ApiUtil.strToQuality(entry.key)
                        ))
                    } else {
                        listOf()
                    }
                }
            }
        })
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

    private suspend fun bypass() {
        bypassCloudflare(
            context,
            url = "https://$HOST".toHttpUrl(),
            title = "animepahe",
            cookieHost = HOST
        )
        bypassCloudflare(
            context,
            url = "https://i.$HOST".toHttpUrl(),
            title = "animepahe",
            cookieHost = "i.$HOST"
        )
    }

}