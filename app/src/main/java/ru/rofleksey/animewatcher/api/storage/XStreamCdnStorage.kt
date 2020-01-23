package ru.rofleksey.animewatcher.api.storage

import android.util.Log
import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import ru.rofleksey.animewatcher.api.Storage
import ru.rofleksey.animewatcher.api.model.StorageAction
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody
import java.io.IOException

class XStreamCdnStorage : Storage {
    companion object {
        private const val TAG = "XStreamCdn"
        const val NAME = "XStreamCdn"
        const val SCORE = 60
        val instance: XStreamCdnStorage by lazy { HOLDER.INSTANCE }
        private val postRegex = Regex("\\\$\\.post\\('([^\']+)'")
    }

    private object HOLDER {
        val INSTANCE = XStreamCdnStorage()
    }

    override suspend fun extract(url: String): StorageResult {
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
            val match = postRegex.find(content) ?: throw IOException("can't match regex!")
            HttpUrl.Builder()
                .scheme(httpUrl.scheme)
                .host(httpUrl.host)
                .addPathSegments(match.groupValues[1].removePrefix("/"))
                .build()
        })
        Log.v(TAG, "postUrl = $postUrl")
        val redirectUrl = HttpHandler.instance.executeDirect({
            postUrl.newBuilder()
        }, { this.addHeader("Referer", fUrl.toString()).post("".toRequestBody()) }, {
            val obj = JsonParser.parseString(this.actualBody()).asJsonObject
            Log.v(TAG, "Response: $obj")
            val data = obj.getAsJsonArray("data")
            Log.v(TAG, "data = $data")
            val first = data.get(0).asJsonObject
            first.get("file").asString
        })
        return HttpHandler.instance.executeDirect({
            redirectUrl.toHttpUrl().newBuilder()
        }, { this.addHeader("Referer", postUrl.toString()) }, {
            StorageResult(this.request.url.toString(), StorageAction.ANY)
        })
    }

    override fun score(): Int {
        return SCORE
    }

    override fun name(): String {
        return NAME
    }
}