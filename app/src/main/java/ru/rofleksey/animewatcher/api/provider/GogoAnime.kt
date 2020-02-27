package ru.rofleksey.animewatcher.api.provider

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.AnimeProvider
import ru.rofleksey.animewatcher.api.model.*
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody

class GogoAnime(context: Context) : AnimeProvider(context) {
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
                TitleInfo(name, details, TitleAirStatus.UNKNOWN, img).also {
                    it.fields["link"] = link
                }
            }
        })
    }

    override suspend fun updateTitleMeta(titleInfo: TitleInfo) {
        val doc = HttpHandler.instance.executeDirect({
            titleInfo["link"].toHttpUrl().newBuilder()
        }, { this }, {
            Jsoup.parse(this.actualBody())

        })
        val id = doc.selectFirst("input#movie_id").attr("value")
        val statusP =
            doc.selectFirst("#wrapper_bg > section > section.content_left > div.main_body > div:nth-child(2) > div.anime_info_body_bg > p:nth-child(8)")
        val statusText = statusP.text().toLowerCase()
        when {
            statusText.contains("ongoing") -> {
                titleInfo.airStatus = TitleAirStatus.AIRING
                Log.v(TAG, "airStatus = AIRING")
            }
            statusText.contains("completed") -> {
                titleInfo.airStatus = TitleAirStatus.FINISHED
                Log.v(TAG, "airStatus = FINISHED")
            }
            else -> {
                titleInfo.airStatus = TitleAirStatus.UNKNOWN
                Log.w(TAG, "airStatus = UNKNOWN")
            }
        }
        titleInfo["id"] = id
        Log.v(TAG, "Retreived TitleInfo.id - $id")
    }

    override suspend fun getEpisodeList(titleInfo: TitleInfo, page: Int): List<EpisodeInfo> {
        val stats = stats()
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
        episodeInfo: EpisodeInfo
    ): List<ProviderResult> {
        return HttpHandler.instance.executeDirect({
            episodeInfo["link"].toHttpUrl().newBuilder()
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            val linkContainer = doc.selectFirst(".anime_muti_link")
            linkContainer.select("a").map {
                ProviderResult(it.attr("data-video"), Quality.UNKNOWN)
            }
        })
    }

    override fun stats(): ProviderStats {
        return ProviderStats(
            needsContext = false,
            hasCloudfare = false,
            episodesDesc = false,
            episodesPerPage = 100
        )
    }

}