package ru.rofleksey.animewatcher.api.storage.english

import android.util.Base64
import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.model.ProviderResult
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.storage.Storage
import ru.rofleksey.animewatcher.api.util.ApiUtil
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody
import java.nio.charset.Charset

class AnimoPaceStream private constructor() :
    Storage {
    companion object {
        private const val TAG = "AnimoPaceStream"
        const val NAME = "AnimoPaceStream"
        const val SCORE = 25
        const val PREFIX = "https://animo-pace-stream.io/MP4Stream/"
        val instance: AnimoPaceStream by lazy { HOLDER.INSTANCE }
        private val base64regex = Regex("document.write\\(Base64.decode\\(\"([^\"]+)\"\\)\\)")
    }

    private object HOLDER {
        val INSTANCE = AnimoPaceStream()
    }


    override suspend fun extract(providerResult: ProviderResult): List<StorageResult> {
        val iframeSrc = HttpHandler.instance.executeDirect({
            ApiUtil.sanitizeScheme(providerResult.link).toHttpUrl().newBuilder()
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            val iframe = doc.selectFirst("body > iframe")
            "${PREFIX}${iframe.attr("src")}"
        })
        Log.v(TAG, "iframeSrc = $iframeSrc")
        return HttpHandler.instance.executeDirect({
            iframeSrc.toHttpUrl().newBuilder()
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            val scriptTag = doc.selectFirst("body > script:nth-child(2)")
            val scriptText = scriptTag.data()
            val base64string = ApiUtil.getRegex(scriptText, base64regex)
            val decodedBytes: ByteArray = Base64.decode(base64string, Base64.DEFAULT)
            val decodedString = String(decodedBytes, Charset.forName("UTF-8"))
            val subDoc = Jsoup.parse(decodedString)
            val sources = subDoc.select("source")
            sources.map { source ->
                StorageResult(
                    source.attr("src"),
                    ApiUtil.strToQuality(source.attr("label"))
                )
            }
        })
    }

    override val score: Int
        get() = SCORE
    override val name: String
        get() = NAME
}