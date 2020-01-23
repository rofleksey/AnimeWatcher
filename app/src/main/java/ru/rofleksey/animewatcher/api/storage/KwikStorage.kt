package ru.rofleksey.animewatcher.api.storage

import android.util.Log
import okhttp3.HttpUrl
import okhttp3.MultipartBody
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.Storage
import ru.rofleksey.animewatcher.api.model.StorageAction
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.unpackers.PACKERUnpacker
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody
import java.io.IOException
import java.net.URI

class KwikStorage private constructor() : Storage {
    companion object {
        private const val TAG = "Kwik"
        const val NAME = "kwik"
        const val SCORE = 50
        val instance: KwikStorage by lazy { HOLDER.INSTANCE }
        private val downloadRegex = Regex("download:'([^']+)'")
    }

    private object HOLDER {
        val INSTANCE = KwikStorage()
    }

    private fun getRegexValue(str: String, regex: Regex): String {
        val match = regex.find(str) ?: throw IOException("can't match regex!")
        return match.groupValues[1]
    }

    private data class DownloadExtraction(val link: String, val token: String)

    override suspend fun extract(url: String): StorageResult {
        val uri = URI(url)
        // E
        val downloadKwikSite = HttpHandler.instance.executeDirect({
            this.scheme(uri.scheme).host(uri.host).addPathSegments(uri.path)
        }, { this.addHeader("Referer", url) }, {
            val doc = Jsoup.parse(this.actualBody())
            val script = doc.selectFirst("body > script:nth-child(6)")
            val scriptText = script.data()
            val unpacked = PACKERUnpacker.unpack(scriptText)
            Log.v(TAG, unpacked)
            getRegexValue(unpacked, downloadRegex)
        })
        // F
        Log.v(TAG, "downloadKwikSite - $downloadKwikSite")
        val (link, token) = HttpHandler.instance.executeDirect({
            this.scheme(uri.scheme).host(uri.host).addPathSegments(downloadKwikSite)
        }, { this.addHeader("Referer", url) }, {
            val doc = Jsoup.parse(this.actualBody())
            val form =
                doc.selectFirst("#app > main > div > div > div.columns.is-multiline > div:nth-child(2) > div:nth-child(2) > form")
            val input =
                doc.selectFirst("#app > main > div > div > div.columns.is-multiline > div:nth-child(2) > div:nth-child(2) > form > input[type=hidden]")
            DownloadExtraction(form.attr("action"), input.attr("value"))
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
            StorageResult(result, StorageAction.ANY)
        })
    }

    override fun score(): Int {
        return SCORE
    }

    override fun name(): String {
        return NAME
    }
}