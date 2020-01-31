package ru.rofleksey.animewatcher.api.provider

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.AnimeProvider
import ru.rofleksey.animewatcher.api.model.EpisodeInfo
import ru.rofleksey.animewatcher.api.model.ProviderStats
import ru.rofleksey.animewatcher.api.model.Quality
import ru.rofleksey.animewatcher.api.model.TitleInfo
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody

class GogoAnime : AnimeProvider {
    companion object {
        private const val TAG = "GogoAnime"
        private const val HOST = "www12.gogoanime.io"
        private const val APIMOVIE_HOST = "ajax.apimovie.xyz"
        private val gson = Gson()
    }

    override suspend fun search(title: String): List<TitleInfo> {
        return HttpHandler.instance.executeDirect({
            this.scheme("https")
                .host(HOST)
                .addPathSegment("search.html")
                .addQueryParameter("keyword", title)
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            doc.select("ul.items li").map {
                val img = it.select(".img img").attr("src")
                val partialLink = it.select(".img a").attr("href")
                val link = HttpUrl.Builder()
                    .scheme("https")
                    .host(HOST)
                    .addPathSegments(partialLink)
                    .build()
                    .toString()
                val name = it.select(".name a").text().trim()
                val details = it.select("p.released").text().trim()
                TitleInfo(name, details, img).also {
                    it.fields["link"] = link
                }
            }
        })
    }

    override suspend fun getEpisodeList(titleInfo: TitleInfo, page: Int): List<EpisodeInfo> {
        val stats = stats()
        if (!titleInfo.has("id")) {
            val id = HttpHandler.instance.executeDirect({
                titleInfo["link"].toHttpUrl().newBuilder()
            }, { this }, {
                val doc = Jsoup.parse(this.actualBody())
                doc.selectFirst("input#movie_id").attr("value")
            })
            titleInfo["id"] = id
            Log.v(TAG, "Retreived TitleInfo.id - $id")
        }
        return HttpHandler.instance.executeDirect({
            this.scheme("https")
                .host(APIMOVIE_HOST)
                .addPathSegment("ajax/load-list-episode")
                .addQueryParameter("ep_start", (stats.episodesPerPage * page).toString())
                .addQueryParameter("default_ep", "0")
                .addQueryParameter("ep_end", (stats.episodesPerPage * (page + 1) - 1).toString())
                .addQueryParameter("id", titleInfo["id"])
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            doc.select("li").map {
                val link = it.select("a").attr("href").trim()
                val name = it.select(".name").text().replace("EP", "").trim()
                EpisodeInfo(name, null).also {
                    it.fields["link"] = HttpUrl.Builder()
                        .scheme("https")
                        .host(HOST)
                        .addPathSegments(link)
                        .build()
                        .toString()
                }
            }.reversed()
        })
    }

    override suspend fun getStorageLinks(
        titleInfo: TitleInfo,
        episodeInfo: EpisodeInfo,
        prefQuality: Quality
    ): List<String> {
        return HttpHandler.instance.executeDirect({
            episodeInfo["link"].toHttpUrl().newBuilder()
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            val linkContainer = doc.selectFirst(".anime_muti_link")
            linkContainer.select("a").map {
                it.attr("data-video")
            }
        })
    }

    override suspend fun init(
        context: Context,
        prefs: SharedPreferences,
        updateStatus: (title: String) -> Unit
    ) {

    }

    override fun stats(): ProviderStats {
        return ProviderStats(
            needsContext = false,
            hasCloudfare = false,
            episodesDesc = false,
            episodesPerPage = 100
        )
    }

    override fun clearCache() {

    }

}