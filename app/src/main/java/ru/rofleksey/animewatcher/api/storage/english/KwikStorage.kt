package ru.rofleksey.animewatcher.api.storage.english

import android.content.Context
import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.model.ProviderResult
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.storage.Storage
import ru.rofleksey.animewatcher.api.unpackers.PACKERUnpacker
import ru.rofleksey.animewatcher.api.util.ApiUtil
import ru.rofleksey.animewatcher.api.util.ApiUtil.Companion.bypassCloudflare
import ru.rofleksey.animewatcher.api.util.ApiUtil.Companion.puppeteerPage
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody

class KwikStorage private constructor() :
    Storage {
    companion object {
        private const val TAG = "Kwik"
        const val NAME = "kwik"
        const val SCORE = 50
        val instance: KwikStorage by lazy { HOLDER.INSTANCE }
//        private val downloadRegex = Regex("download:'([^']+)'")
        private val urlRegex = Regex("/./")
        private val tokenRegex = Regex("value=\"([^\"]+?)\"")
        private val linkRegex = Regex("action=\"([^\"]+?)\"")
    }

    private object HOLDER {
        val INSTANCE = KwikStorage()
    }



    private data class DownloadExtraction(val link: String, val token: String)

    override suspend fun extract(
        context: Context,
        providerResult: ProviderResult
    ): List<StorageResult> {
        val downloadKwikSite = providerResult.link.replace(urlRegex, "/f/")
        bypassCloudflare(context, downloadKwikSite.toHttpUrl(), "Kwik", "kwik.cx")
        Log.v(TAG, "downloadKwikSite - $downloadKwikSite")
        //bypassCloudflare(context, "https://kwik.cx".toHttpUrl(), "Kwik", "kwik.cx")
        val headers = HashMap<String, String>()
        headers["Referer"] = downloadKwikSite
        val (link, token) = puppeteerPage(context, downloadKwikSite.toHttpUrl(), headers) { body ->
            val doc = Jsoup.parse(body)
            Log.d(TAG, "lmao = ${doc.selectFirst("form")}")
            val form = doc.selectFirst(".download-form > form")
            Log.d(TAG, "form = $form")
            val input = form.selectFirst("input")
            DownloadExtraction(
                form.attr("action"),
                input.attr("value")
            )
        }
        // D
        return HttpHandler.instance.executeDirect({
            link.toHttpUrl().newBuilder()
        }, {
            this.post(
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("_token", token)
                    .build()
            ).addHeader("Referer", downloadKwikSite)
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