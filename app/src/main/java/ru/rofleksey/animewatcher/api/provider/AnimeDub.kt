package ru.rofleksey.animewatcher.api.provider

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.model.*
import ru.rofleksey.animewatcher.api.util.ApiUtil
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody

class AnimeDub(context: Context) : AnimeProvider(context) {
    //TODO: kodik
    companion object {
        private const val TAG = "AnimeDub"
        private const val HOST = "animedub.ru"
        private val gson = Gson()
    }

    override suspend fun search(title: String): List<TitleInfo> {
        return HttpHandler.instance.executeDirect({
            this.scheme("https").host(HOST).query("?do=search")
        }, {
            this.post(
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("titleonly", "3")
                    .addFormDataPart("do", "search")
                    .addFormDataPart("subaction", "search")
                    .addFormDataPart("story", title)
                    .build()
            )
        }, {
            val doc = Jsoup.parse(this.actualBody())

            val headers = doc.select("a.mov-t.nowrap")
            val titles = headers.map {
                val name = it.text()
                if (name.count { it == '/' } == 1) name.split("/")[1].trim() else name
            }
            val links = headers.map { it.attr("href") }

            val bodies = doc.select("a.mov-t.nowrap + .mov.clearfix")
            val images = bodies.map { it.selectFirst("img").attr("src") }
            val details: List<Map<String, String>> = bodies.map {
                val map = mutableMapOf<String, String>()
                try {
                    it.select(".movie-lines li").forEach { li ->
                        map[li.selectFirst(".ml-label").text()] =
                            li.selectFirst(".ml-desc").text()
                    }
                } catch (e: Exception) {
                    //TODO: what happens here?
                }
                map
            }
            Log.v(TAG, "details - $details")
            val result = mutableListOf<TitleInfo>()
            for (i in 0 until bodies.size) {
                result.add(
                    TitleInfo(
                        title = titles[i],
                        details = details[i]["Количество серий:"] ?: details[i]["Год выхода:"]
                        ?: "",
                        airStatus = TitleAirStatus.UNKNOWN,
                        image = HttpUrl.Builder()
                            .scheme("https")
                            .host(HOST)
                            .addPathSegments(images[i])
                            .build()
                            .toString()
                    ).also {
                        it["link"] = links[i]
                    }
                )
            }
            result
        })
    }

    override suspend fun updateTitleMeta(titleInfo: TitleInfo) {
        // no metadata to update
    }

    override suspend fun getEpisodeList(titleInfo: TitleInfo, page: Int): List<EpisodeInfo> {
        //https://animedub.ru/anime/fehntezi/89-hunter-x-hunter-2-sezon-2011.html
        //https://animedub.ru/engine/ajax/controller.php?mod=iframeplayer&post_id=89&action=iframe&select=source=2&dubbing=1&series=1&skin=animedub
        if (page != 0) {
            return listOf()
        }
        val httpUrl = titleInfo["link"].toHttpUrl()
        val sitePostId = httpUrl.pathSegments.last().split("-").first()
        titleInfo["sitePostId"] = sitePostId
        return HttpHandler.instance.executeDirect({
            HttpUrl.Builder()
                .scheme("https")
                .host(HOST)
                .addPathSegments("engine/ajax/controller.php")
                .addQueryParameter("mod", "iframeplayer")
                .addQueryParameter("post_id", sitePostId)
                .addQueryParameter("action", "selectors")
                .addQueryParameter("skin", "animedub")
        }, { this.addHeader("Referer", httpUrl.toString()) }, {
            val doc = Jsoup.parse(this.actualBody())
            val options = doc.select("select[name=\"source\"] option")
            titleInfo["providerArray"] = options.joinToString(",") {
                it.attr("value")
            }
            Log.v(TAG, "Providers: ${titleInfo["providerArray"]}")
            val dubbings = doc.select("select[name=\"dubbing\"] option")
            titleInfo["dubbingArray"] = dubbings.joinToString(",") {
                it.attr("value")
            }
            Log.v(TAG, "Dubbings: ${titleInfo["dubbingArray"]}")
            val episodes = doc.select("select[name=\"series\"] option")
            episodes.map {
                EpisodeInfo(
                    it.attr("value"),
                    null
                )
            }
        })
    }

    override suspend fun getStorageLinks(
        titleInfo: TitleInfo,
        episodeInfo: EpisodeInfo
    ): List<ProviderResult> {
        val httpUrl = titleInfo["link"].toHttpUrl()
        val providerIds = titleInfo["providerArray"].split(",")
        val dubbingIds = titleInfo["dubbingArray"].split(",")
        return providerIds.map { providerId ->
            HttpHandler.instance.executeDirect({
                HttpUrl.Builder()
                    .scheme("https")
                    .host(HOST)
                    .addPathSegments("engine/ajax/controller.php")
                    .addQueryParameter("mod", "iframeplayer")
                    .addQueryParameter("post_id", titleInfo["sitePostId"])
                    .addQueryParameter("action", "iframe")
                    .addQueryParameter("select",
                        "source=$providerId&dubbing=${dubbingIds.first()}&series=${episodeInfo.name}")
                    .addQueryParameter("skin", "animedub")
            }, { this.addHeader("Referer", httpUrl.toString()) }, {
                ProviderResult(
                    ApiUtil.sanitizeScheme(this.actualBody()).also {
                        Log.v(
                            TAG,
                            "storageLink = $it"
                        )
                    },
                    Quality.UNKNOWN
                )
            })
        }
    }

    override fun stats(): ProviderStats {
        return ProviderStats(
            needsContext = false,
            hasCloudfare = false,
            episodesDesc = false,
            episodesPerPage = 99999
        )
    }
}