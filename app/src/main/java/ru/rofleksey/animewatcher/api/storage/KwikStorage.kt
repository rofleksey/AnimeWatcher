package ru.rofleksey.animewatcher.api.storage

import ru.rofleksey.animewatcher.api.Storage
import ru.rofleksey.animewatcher.api.model.StorageType
import ru.rofleksey.animewatcher.api.unpackers.PACKERUnpacker
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody
import okhttp3.HttpUrl
import okhttp3.MultipartBody
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI

class KwikStorage private constructor() : Storage {
    companion object {
        val instance: KwikStorage by lazy { HOLDER.INSTANCE }
        val sourceRegex = Regex("const source='([^']+)'")
        val downloadRegex = Regex("download:'([^']+)'")
    }

    private object HOLDER {
        val INSTANCE = KwikStorage()
    }

    private fun getRegexValue(str: String, regex: Regex): String {
        val match = regex.find(str) ?: throw IOException("can't match regex!")
        return match.groupValues[1]
    }

    override suspend fun extractStream(url: String): String {
        val uri = URI(url)
        return HttpHandler.instance.executeDirect({
            this.scheme(uri.scheme).host(uri.host).addPathSegments(uri.path)
        }, { this.addHeader("Referer", url) }, {
            val doc = Jsoup.parse(this.actualBody())
            val script = doc.selectFirst("body > script:nth-child(6)")
            val scriptText = script.data()
            val unpacked = PACKERUnpacker.unpack(scriptText)
            getRegexValue(unpacked, sourceRegex)
        })
    }

    private data class DownloadExtraction(val link: String, val token: String)

    override suspend fun extractDownload(url: String): String {
        val uri = URI(url)
        // E
        val downloadKwikSite = HttpHandler.instance.executeDirect({
            this.scheme(uri.scheme).host(uri.host).addPathSegments(uri.path)
        }, { this.addHeader("Referer", url) }, {
            val doc = Jsoup.parse(this.actualBody())
            val script = doc.selectFirst("body > script:nth-child(6)")
            val scriptText = script.data()
            val unpacked = PACKERUnpacker.unpack(scriptText)
            println(unpacked)
            getRegexValue(unpacked, downloadRegex)
        })
        // F
        println("downloadKwikSite - $downloadKwikSite")
        val (link, token) = HttpHandler.instance.executeDirect({
            this.scheme(uri.scheme).host(uri.host).addPathSegments(downloadKwikSite)
        }, { this.addHeader("Referer", url) }, {
            val doc = Jsoup.parse(this.actualBody())
            val form = doc.selectFirst("#app > main > div > div > div.columns.is-multiline > div:nth-child(2) > div:nth-child(2) > form")
            val input = doc.selectFirst("#app > main > div > div > div.columns.is-multiline > div:nth-child(2) > div:nth-child(2) > form > input[type=hidden]")
            DownloadExtraction(form.attr("action"), input.attr("value"))
        })
        // D
        val finalUri = URI(link)
        val fUrl = HttpUrl.Builder().scheme(uri.scheme).host(uri.host).addPathSegments(downloadKwikSite).build()
        return HttpHandler.instance.executeDirect({
            this.scheme(finalUri.scheme).host(finalUri.host).addPathSegments(finalUri.path).query(finalUri.query)
        }, {
            this.post(MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("_token", token)
                .build()
            ).addHeader("Referer", fUrl.toString())
        }, {
            this.request.url.toString()
        })
    }

    override fun storageType(): StorageType {
        return StorageType.BOTH
    }
}