package ru.rofleksey.animewatcher.api.storage.english

import android.util.Log
import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import ru.rofleksey.animewatcher.api.Storage
import ru.rofleksey.animewatcher.api.model.Quality
import ru.rofleksey.animewatcher.api.model.StorageAction
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.util.ApiUtil
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody
import java.io.IOException

class XStreamCdnStorage : Storage {
    companion object {
        private const val TAG = "XStreamCdn"
        private const val UNAVAILABLE = "Sorry this file is unavailable: DMCA Takedown"
        const val NAME = "XStreamCdn"
        const val SCORE = 60
        val instance: XStreamCdnStorage by lazy { HOLDER.INSTANCE }
        private val postRegex = Regex("\\\$\\.post\\('([^\']+)'")
    }

    private object HOLDER {
        val INSTANCE =
            XStreamCdnStorage()
    }

    override suspend fun extract(url: String, prefQuality: Quality): StorageResult {
        val httpUrl = url.toHttpUrl()
        val segments = ArrayList(httpUrl.pathSegments).map {
            if (it == "v") "f" else it
        }
        val fUrl = HttpUrl.Builder()
            .scheme(httpUrl.scheme)
            .host(httpUrl.host)
            .addPathSegments(segments.joinToString("/"))
        Log.v(TAG, "fUrl - $fUrl")
        val postUrl = HttpHandler.instance.executeDirect({
            fUrl
        }, { this.addHeader("Referer", url) }, {
            val content = this.actualBody()
            if (content.contains(UNAVAILABLE)) {
                throw IOException(UNAVAILABLE)
            }
            HttpUrl.Builder()
                .scheme(httpUrl.scheme)
                .host(httpUrl.host)
                .addPathSegments(ApiUtil.getRegex(content,
                    postRegex
                ).removePrefix("/"))
                .build()
        })
        Log.v(TAG, "postUrl = $postUrl")
        val redirectUrl = HttpHandler.instance.executeDirect({
            postUrl.newBuilder()
        }, { this.addHeader("Referer", fUrl.toString()).post("".toRequestBody()) }, {
            val obj = JsonParser.parseString(this.actualBody()).asJsonObject
            val links = obj.getAsJsonArray("data").map {
                val file = it.asJsonObject.get("file").asString
                val qualityString = it.asJsonObject.get("label").asString
                Pair(ApiUtil.strToQuality(qualityString), file)
            }
            Log.v(TAG, links.toString())
            ApiUtil.pickQuality(links, prefQuality)
        })
        return HttpHandler.instance.executeDirect({
            redirectUrl.toHttpUrl().newBuilder()
        }, { this.addHeader("Referer", postUrl.toString()) }, {
            StorageResult(this.request.url.toString(), StorageAction.ANY)
        })
    }

    override val score: Int
        get() = SCORE
    override val name: String
        get() = NAME
}