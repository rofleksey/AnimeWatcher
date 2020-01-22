package ru.rofleksey.animewatcher.api.provider

import android.content.Context
import android.content.SharedPreferences
import com.bumptech.glide.load.model.GlideUrl
import ru.rofleksey.animewatcher.api.AnimeProvider
import ru.rofleksey.animewatcher.api.model.EpisodeInfo
import ru.rofleksey.animewatcher.api.model.ProviderStats
import ru.rofleksey.animewatcher.api.model.Quality
import ru.rofleksey.animewatcher.api.model.TitleInfo
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody
import com.google.gson.Gson
import okhttp3.HttpUrl
import okhttp3.MultipartBody
import org.jsoup.Jsoup

class AnimeDub : AnimeProvider {
    companion object {
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
            val details = bodies.map {
                val map = mutableMapOf<String, String>()
                it.select(".movie-lines li").forEach { li ->
                    map[li.selectFirst(".ml-label").text()] =
                        li.selectFirst(".ml-desc").text()
                }
                map
            }
            println(details)
            val result = mutableListOf<TitleInfo>()
            for (i in 0 until bodies.size) {
                result.add(
                    TitleInfo(
                        title = titles[i],
                        details = details[i]["Количество серий:"] ?: details[i]["Год выхода:"] ?: "",
                        image = HttpUrl.Builder()
                            .scheme("https")
                            .host(HOST)
                            .addPathSegments(images[i])
                            .build()
                            .toString(),
                        fields = mutableMapOf(links[i] to "link")
                    )
                )
            }
            result
        })
    }

    override suspend fun getEpisodeList(titleInfo: TitleInfo, page: Int): List<EpisodeInfo> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun getStorageLinks(
        titleInfo: TitleInfo,
        episodeInfo: EpisodeInfo
    ): Map<Quality, String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun getAllTitles(): List<String>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun init(context: Context, prefs: SharedPreferences, updateStatus: (title: String) -> Unit) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getGlideUrl(url: String): GlideUrl? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun stats(): ProviderStats {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun clearCache() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}