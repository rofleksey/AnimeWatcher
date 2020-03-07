package ru.rofleksey.animewatcher.api.storage.russian

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.model.ProviderResult
import ru.rofleksey.animewatcher.api.model.Quality
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.storage.Storage
import ru.rofleksey.animewatcher.api.util.ApiUtil
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody
import java.nio.charset.Charset

class HaloAniStorage : Storage {
    companion object {
        private const val TAG = "HaloAni"
        const val NAME = "haloani.ru"
        const val SCORE = 10
        private val gson = Gson()
        val INSTANCE: HaloAniStorage by lazy { HOLDER.INSTANCE }
        private val rootSourcesRegex = Regex("var sources = (\\[\\{.*?\\}\\])")
        private val sourcesRegex = Regex("sources: (\\[\\{.*?\\}\\])")
        private val base64regex = Regex("document.write\\(Base64.decode\\(\"([^\"]+)\"\\)\\)")
        private const val PREFIX = "https://haloani.ru//KickAssAnime//"
    }

    private object HOLDER {
        val INSTANCE =
            HaloAniStorage()
    }

    override val score: Int
        get() = SCORE
    override val name: String
        get() = NAME

    private data class SourceEntry(val file: String, val label: String, val type: String)

    override suspend fun extract(providerResult: ProviderResult): List<StorageResult> {
        val doc = HttpHandler.instance.executeDirect({
            providerResult.link.toHttpUrl().newBuilder()
        }, { this }, {
            Jsoup.parse(this.actualBody())
        })
        val isBase64 = doc.html().contains(base64regex)
        val iframe = doc.selectFirst("iframe")
        val sourcesScriptTag = doc.selectFirst("body > script:nth-child(2)")
        return when {
            iframe != null -> {
                val src = iframe.attr("src")
                listOf(
                    StorageResult(
                        if (src.startsWith("https")) src else "${PREFIX}${src}",
                        Quality.UNKNOWN,
                        true
                    )
                )
            }
            isBase64 -> {
                val base64string = ApiUtil.getRegex(doc.html(), base64regex)
                val decodedBytes: ByteArray = Base64.decode(base64string, Base64.DEFAULT)
                val decodedString = String(decodedBytes, Charset.forName("UTF-8"))
                Log.v(TAG, decodedString)
                val subDoc = Jsoup.parse(decodedString)
                val divDownload = subDoc.selectFirst("#divDownload")
                val sourceArrayJSON = ApiUtil.getRegexSafe(decodedString, sourcesRegex)
                when {
                    divDownload != null -> {
                        val a = divDownload.selectFirst("a")
                        val text = a
                            .text()
                            .replace("Mp4", "")
                            .replace(Regex("\\D"), " ")
                        listOf(
                            StorageResult(
                                a.attr("href"),
                                ApiUtil.strToQuality(text)
                            )
                        )
                    }
                    sourceArrayJSON != null -> {
                        val listType = object : TypeToken<List<SourceEntry>>() {}.type
                        val array = gson.fromJson<List<SourceEntry>>(sourceArrayJSON, listType)
                        array.map {
                            StorageResult(
                                it.file,
                                ApiUtil.strToQuality(it.label)
                            )
                        }
                    }
                    else -> {
                        throw Exception("Unknown schema")
                    }
                }
            }
            else -> {
                val scriptText = sourcesScriptTag.data()
                val sourcesJSON = ApiUtil.getRegex(scriptText, rootSourcesRegex)
                val sources = JsonParser.parseString(sourcesJSON).asJsonArray.map {
                    it.asJsonObject.get("src").asString
                }
                sources.map {
                    StorageResult(it, Quality.UNKNOWN, true)
                }
            }
        }
    }
}