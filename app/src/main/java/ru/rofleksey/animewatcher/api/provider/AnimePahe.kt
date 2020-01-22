package ru.rofleksey.animewatcher.api.provider

import android.content.Context
import android.content.SharedPreferences
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import ru.rofleksey.animewatcher.api.AnimeProvider
import ru.rofleksey.animewatcher.api.model.EpisodeInfo
import ru.rofleksey.animewatcher.api.model.ProviderStats
import ru.rofleksey.animewatcher.api.model.Quality
import ru.rofleksey.animewatcher.api.model.TitleInfo
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.SimpleCache
import ru.rofleksey.animewatcher.api.util.actualBody
import ru.rofleksey.animewatcher.util.Util
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import java.lang.IllegalArgumentException

class AnimePahe : AnimeProvider {
    companion object {
        private const val BASE_URL = "https://animepahe.com"
        private const val HOST = "animepahe.com"
        private val gson = Gson()
    }

    private val allAnimeCache = SimpleCache<List<String>>()

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
        return HttpHandler.instance.executeDirect({
            this.scheme("https")
                .host(HOST)
                .addPathSegment("api")
                .addQueryParameter("m", "release")
                .addQueryParameter("id", titleInfo["id"])
                .addQueryParameter("l", "30")
                .addQueryParameter("sort", "episode_desc")
                .addQueryParameter("page", (page + 1).toString())
        }, { this }, {
            val obj = gson.fromJson(this.actualBody(), PaheEpisodesResponse::class.java)
            obj.toEpisodeInfo()
        })
    }

    override suspend fun getStorageLinks(titleInfo: TitleInfo, episodeInfo: EpisodeInfo): Map<Quality, String> {
        return HttpHandler.instance.executeDirect({
            this.scheme("https")
                .host(HOST)
                .addPathSegment("api")
                .addQueryParameter("m", "embed")
                .addQueryParameter("id", episodeInfo["id"])
                .addQueryParameter("p", "kwik")
        }, { this }, {
            println("getStorageLink: $this")
            val obj = JsonParser.parseString(this.actualBody()).asJsonObject
            val data = obj.getAsJsonObject("data")
            val ep = data.getAsJsonObject(episodeInfo["id"])
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
        })
    }

    override suspend fun getAllTitles(): List<String>? {
        return HttpHandler.instance.execute({
            this.scheme("https")
                .host(HOST)
                .addPathSegment("anime")
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            doc.select(".tab-content li").map {
                it.text()
            }
        }, allAnimeCache)
    }

    override suspend fun init(context: Context, prefs: SharedPreferences, updateStatus: (title: String) -> Unit) {
        updateStatus("Initializing AnimePahe...")
        bypassCloudfare(context, HttpUrl.Builder()
            .scheme("https")
            .host(HOST)
            .build())
        updateStatus("Initializing AnimePahe images...")
        bypassCloudfare(context, HttpUrl.Builder()
            .scheme("https")
            .host("i.$HOST")
            .build())
    }

    override fun getGlideUrl(url: String): GlideUrl? {
        try {
            println("getGlideUrl - $url")
            val cookies = HttpHandler.instance.getCookies(url).joinToString("; ") {
                "${it.name}=${it.value}"
            }
            println("glide cookies for $url: $cookies")
            return GlideUrl(
                url,
                LazyHeaders.Builder()
                    .addHeader("User-Agent", Util.USER_AGENT)
                    .addHeader("Cookie", cookies)
                    .build()
            )
        } catch (e: IllegalArgumentException) {
            return null
        }
    }

    override fun stats(): ProviderStats {
        return ProviderStats(
            episodesDesc = true,
            hasCloudfare = true,
            needsContext = true,
            loadingString = "Bypassing cloudflare scrape shield"
        )
    }

    override fun clearCache() {
        allAnimeCache.clear()
    }

}