package ru.rofleksey.animewatcher.api.storage.english

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.Storage
import ru.rofleksey.animewatcher.api.model.ProviderResult
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.util.ApiUtil
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody
import java.io.IOException
import java.util.*

class VidStreamingStorage : Storage {
    companion object {
        private const val TAG = "VidStreaming"
        const val NAME = "Vidstreaming"
        const val SCORE = 10
        val instance: VidStreamingStorage by lazy { HOLDER.INSTANCE }
        private val sourceRegex = Regex("\"(https://vidstreaming\\.io/download[^\"]+)\"")
    }

    private object HOLDER {
        val INSTANCE =
            VidStreamingStorage()
    }

    override suspend fun extract(providerResult: ProviderResult): List<StorageResult> {
        val urlArg = ApiUtil.sanitizeScheme(providerResult.link)
        Log.v(TAG, providerResult.link)
        val redirect = HttpHandler.instance.executeDirect({
            urlArg.toHttpUrl().newBuilder()
        }, { this }, {
            val content = this.actualBody()
            ApiUtil.getRegex(content,
                sourceRegex
            )
        })
        return HttpHandler.instance.executeDirect({
            redirect.toHttpUrl().newBuilder()
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            val links = doc.select(".mirror_link a").map {
                Log.v(TAG, "link - ${it.attr("href")}")
                it.attr("href")
            }.filter {
                it.toHttpUrl().pathSegments.last().toLowerCase(Locale.US).trim().endsWith(".mp4")
            }
            if (links.isEmpty()) {
                throw IOException("Can't find link to mp4")
            }
            listOf(StorageResult(links.first(), providerResult.quality))
        })
    }

    override val score: Int
        get() = SCORE
    override val name: String
        get() = NAME
}