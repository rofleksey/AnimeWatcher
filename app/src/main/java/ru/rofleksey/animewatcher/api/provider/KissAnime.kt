package ru.rofleksey.animewatcher.api.provider

import android.content.Context
import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.model.*
import ru.rofleksey.animewatcher.api.util.ApiUtil
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody

class KissAnime(context: Context) : AnimeProvider(context) {
    companion object {
        private const val TAG = "KissAnime"
        private const val HOST = "kissanime.ru"
        private val gson = Gson()
        private val EPISODE_REGEX_CLEAN = Regex(".*?Episode")
        private val EPISODE_COMMENT_CLEAN = Regex("\\(.*?\\)")
    }

    override suspend fun search(title: String): List<TitleInfo> {
        bypass()
        return HttpHandler.instance.executeDirect({
            this.scheme("https")
                .host(HOST)
                .addPathSegments("/Search/Anime")
                .addQueryParameter("keyword", title)
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            val pageTitle = doc.selectFirst("title").text().toLowerCase()
            if (!pageTitle.contains("find anime")) {
                val titleContainer =
                    doc.selectFirst("#leftside > div:nth-child(1) > div.barContent > div:nth-child(2) > a")
                return@executeDirect listOf(
                    TitleInfo(
                        titleContainer.text(),
                        "",
                        TitleAirStatus.UNKNOWN,
                        null
                    ).also {
                        it["href"] = titleContainer.attr("href")
                    }
                )
            }
            val table =
                doc.selectFirst("#leftside > div > div.barContent > div:nth-child(2) > table > tbody")
            table.select("tr").flatMap {
                val tds = it.select("td")
                if (tds.size != 2) {
                    return@flatMap listOf<TitleInfo>()
                }
                val name = tds[0].text().trim()
                val link = tds[0].selectFirst("a").attr("href")
                val latest = tds[1].text().trim()
                val airStatus = when (latest) {
                    "Completed" -> TitleAirStatus.FINISHED
                    "Not yet aired" -> TitleAirStatus.NOT_YET_AIRED
                    else -> TitleAirStatus.AIRING
                }
                listOf(
                    TitleInfo(name, latest, airStatus, null).also {
                        it["href"] = link
                    }
                )
            }
        })
    }

    override suspend fun updateTitleMeta(titleInfo: TitleInfo) {
        ApiUtil.clearCloudflare(context, HOST)
        bypass()
    }

    override suspend fun getEpisodeList(titleInfo: TitleInfo, page: Int): List<EpisodeInfo> {
        if (page != 0) {
            return listOf()
        }
        return HttpHandler.instance.executeDirect({
            this.scheme("https")
                .host(HOST)
                .addPathSegments(titleInfo["href"])
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            val table =
                doc.selectFirst("#leftside > div:nth-child(4) > div.barContent.episodeList > div:nth-child(2) > table > tbody")
            table.select("tr").flatMap {
                val tds = it.select("td")
                if (tds.size != 2) {
                    return@flatMap listOf<EpisodeInfo>()
                }
                val name = tds[0].text().trim()
                val purifiedName = name
                    .replace(EPISODE_REGEX_CLEAN, "")
                    .replace(EPISODE_COMMENT_CLEAN, "")
                    .trim()
                    .removePrefix("0")
                val link = tds[0].selectFirst("a").attr("href")
                listOf(
                    EpisodeInfo(purifiedName, null).also {
                        it["href"] = link
                    }
                )
            }
        })
    }

    override suspend fun getStorageLinks(
        titleInfo: TitleInfo,
        episodeInfo: EpisodeInfo
    ): List<ProviderResult> {
        return listOf()
    }

    override fun stats(): ProviderStats {
        return ProviderStats(
            needsContext = false,
            hasCloudfare = true,
            episodesDesc = false,
            episodesPerPage = 100
        )
    }

    private suspend fun bypass() {
        ApiUtil.bypassCloudflare(
            context,
            url = "https://${HOST}".toHttpUrl(),
            title = "KissAnime",
            cookieHost = HOST
        )
    }

}