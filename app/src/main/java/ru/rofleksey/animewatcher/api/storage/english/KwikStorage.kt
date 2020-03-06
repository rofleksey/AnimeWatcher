package ru.rofleksey.animewatcher.api.storage.english

import android.util.Log
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.model.ProviderResult
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.storage.Storage
import ru.rofleksey.animewatcher.api.unpackers.PACKERUnpacker
import ru.rofleksey.animewatcher.api.util.ApiUtil
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody
import java.net.URI

class KwikStorage private constructor() :
    Storage {
    companion object {
        private const val TAG = "Kwik"
        const val NAME = "kwik"
        const val SCORE = 50
        val instance: KwikStorage by lazy { HOLDER.INSTANCE }
//        private val downloadRegex = Regex("download:'([^']+)'")
        private val urlRegex = Regex("/./")
        private val tokenRegex = Regex("var [_a-zA-Z0-9]+=\"([^\"]+)\"")
        private val linkRegex = Regex("action=\"([^\"]+)\"")
    }

    private object HOLDER {
        val INSTANCE = KwikStorage()
    }



    private data class DownloadExtraction(val link: String, val token: String)

    override suspend fun extract(providerResult: ProviderResult): List<StorageResult> {
        val uri = URI(providerResult.link)
        // E
//        val downloadKwikSite = HttpHandler.instance.executeDirect({
//            this.scheme(uri.scheme).host(uri.host).addPathSegments(uri.path)
//        }, { this.addHeader("Referer", url) }, {
//            val doc = Jsoup.parse(this.actualBody())
//            val script = doc.selectFirst("body > script:nth-child(6)")
//            val scriptText = script.data()
//            val unpacked = PACKERUnpacker.unpack(scriptText)
//            Log.v(TAG, unpacked)
//            ApiUtil.getRegex(unpacked,
//                downloadRegex
//            )
//        })
        // F
        val downloadKwikSite = providerResult.link.replace(urlRegex, "/f/")
        Log.v(TAG, "downloadKwikSite - $downloadKwikSite")
        val (link, token) = HttpHandler.instance.executeDirect({
            downloadKwikSite.toHttpUrl().newBuilder()
        }, { this.addHeader("Referer", downloadKwikSite) }, {
            val doc = Jsoup.parse(this.actualBody())
            val scriptTag = doc.selectFirst("body > script:nth-child(6)")
            val scriptText = scriptTag.data()
            Log.v(TAG, "scriptTag.data() = $scriptText")
            val unpacked = PACKERUnpacker.unpack(scriptText)
            Log.v(TAG, "unpacked = $unpacked")
            DownloadExtraction(
                ApiUtil.getRegex(unpacked, linkRegex),
                ApiUtil.getRegex(unpacked, tokenRegex).reversed()
            )
        })
        // D
        val finalUri = URI(link)
        val fUrl =
            HttpUrl.Builder().scheme(uri.scheme).host(uri.host).addPathSegments(downloadKwikSite)
                .build()
        return HttpHandler.instance.executeDirect({
            this.scheme(finalUri.scheme).host(finalUri.host).addPathSegments(finalUri.path)
                .query(finalUri.query)
        }, {
            this.post(
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("_token", token)
                    .build()
            ).addHeader("Referer", fUrl.toString())
        }, {
            val result = this.request.url.toString()
            listOf(StorageResult(result, providerResult.quality))
        })
    }

    override val score: Int
        get() = SCORE
    override val name: String
        get() = NAME
}