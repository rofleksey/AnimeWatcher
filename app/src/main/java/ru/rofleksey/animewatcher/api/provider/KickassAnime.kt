package ru.rofleksey.animewatcher.api.provider

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.model.*
import ru.rofleksey.animewatcher.api.provider.template.KickassEpisodeEntry
import ru.rofleksey.animewatcher.api.provider.template.KickassTitleEntry
import ru.rofleksey.animewatcher.api.util.ApiUtil
import ru.rofleksey.animewatcher.api.util.ApiUtil.Companion.bypassCloudflare
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody


class KickassAnime(context: Context) : AnimeProvider(context) {
    companion object {
        private const val TAG = "KickassAnime"
        const val BASE_URL = "https://www.kickassanime.rs/"
        private const val HOST = "kickassanime.rs"
        private const val HOST_WWW = "www.kickassanime.rs"
        private val gson = Gson()
        private val sigRegex = Regex("\"sig\":\"([^\"]+)\"")
        private val clipRegex = Regex("\"clip\":\"([^\"]+)\"")
        private val vtRegex = Regex("\"vt\":\"([^\"]+)\"")
        private val episodesRegex = Regex("\"episodes\":(\\[[^]]+]),")
        private val linksRegex = Regex("\"link\\d*\":(\"[^\"]*\")")
    }

    private var tokens: KickassTokens? = null

    data class KickassTokens(val sig: String, val vt: String, val clip: String)

    override suspend fun search(title: String): List<TitleInfo> {
        bypass()
        val (sig, vt, clip) = getTokens()
        val mt = (System.currentTimeMillis() / 1000L).toString()
        val stringToHash = "${title}${mt}${sig}${clip}sa"
        val signature = ApiUtil.sha1(stringToHash).toUpperCase()
        return HttpHandler.instance.executeDirect({
            this.scheme("https")
                .host(HOST_WWW)
                .addPathSegments("api/anime_search")
        }, {
            this.post(
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("mt", mt)
                    .addFormDataPart("vt", vt)
                    .addFormDataPart("ek", sig)
                    .addFormDataPart("sig", signature)
                    .addFormDataPart("keyword", title)
                    .build()
            )
        }, {
            val listType = object : TypeToken<List<KickassTitleEntry>>() {}.type
            val array = gson.fromJson<List<KickassTitleEntry>>(this.actualBody(), listType)
            array.map {
                it.toTitleInfo()
            }
        })
    }

    override suspend fun updateTitleMeta(titleInfo: TitleInfo) {
        bypass()
    }

    override suspend fun getEpisodeList(titleInfo: TitleInfo, page: Int): List<EpisodeInfo> {
        if (page != 0) {
            return listOf()
        }
        return HttpHandler.instance.executeDirect({
            this.scheme("https")
                .host(HOST_WWW)
                .addPathSegments(titleInfo["slug"])
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            val scriptTag = doc.selectFirst("body > script:nth-child(6)")
            val scriptText = scriptTag.data()
            val episodeJSON = ApiUtil.getRegex(scriptText, episodesRegex)
            Log.v(TAG, episodeJSON)
            val listType = object : TypeToken<List<KickassEpisodeEntry>>() {}.type
            val array = gson.fromJson<List<KickassEpisodeEntry>>(episodeJSON, listType)
            array.map {
                it.toEpisodeInfo()
            }
        })
    }

    override suspend fun getStorageLinks(
        titleInfo: TitleInfo,
        episodeInfo: EpisodeInfo
    ): List<ProviderResult> {
        bypass()
        return HttpHandler.instance.executeDirect({
            this.scheme("https")
                .host(HOST_WWW)
                .addPathSegments(episodeInfo["slug"])
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            val scriptTag = doc.selectFirst("body > script:nth-child(6)")
            val scriptText = scriptTag.data()
            ApiUtil.getRegexAll(scriptText, linksRegex).map { linkJSON ->
                gson.fromJson<String>(linkJSON, String::class.java)
            }.filter {
                it.isNotBlank()
            }.map {
                val url = it.toHttpUrl()
                val actualLink = url.queryParameter("data")
                if (actualLink != null) {
                    ProviderResult(actualLink, Quality.UNKNOWN)
                } else {
                    ProviderResult(it, Quality.UNKNOWN)
                }
            }.toList()
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

    private suspend fun getTokens(): KickassTokens {
        if (tokens == null) {
            tokens = HttpHandler.instance.executeDirect({
                this.scheme("https")
                    .host(HOST_WWW)
            }, { this }, {
                val doc = Jsoup.parse(this.actualBody())
                val scriptTag = doc.selectFirst("body > script:nth-child(6)")
                val scriptText = scriptTag.data()
                KickassTokens(
                    sig = ApiUtil.getRegex(scriptText, sigRegex),
                    vt = ApiUtil.getRegex(scriptText, vtRegex),
                    clip = ApiUtil.getRegex(scriptText, clipRegex)
                ).apply { Log.v(TAG, this.toString()) }
            })
        }
        return tokens!!
    }

    private suspend fun bypass() {
        bypassCloudflare(
            context,
            url = "https://${HOST_WWW}".toHttpUrl(),
            title = "Anime",
            cookieHost = HOST_WWW
        )
    }

}