package ru.rofleksey.animewatcher.api.storage.russian

import android.content.Context
import android.util.Log
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.model.ProviderResult
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.storage.Storage
import ru.rofleksey.animewatcher.api.util.ApiUtil
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody

class SibnetStorage: Storage {
    companion object {
        private const val TAG = "Sibnet"
        const val NAME = "sibnet.ru"
        const val SCORE = 60
        val instance: SibnetStorage by lazy { HOLDER.INSTANCE }
        private val srcRegex = Regex("src: \"([^\"]+)\"")
    }

    private object HOLDER {
        val INSTANCE =
            SibnetStorage()
    }

    override suspend fun extract(
        context: Context,
        providerResult: ProviderResult
    ): List<StorageResult> {
        val redirectUrl = HttpHandler.instance.executeDirect({
            providerResult.link.toHttpUrl().newBuilder()
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            val scriptTag = doc.selectFirst(
                "body > script:nth-child(33)"
            )
            val scriptText = scriptTag.data()
            Log.v(TAG, "scriptText: $scriptText")
            HttpUrl.Builder()
                .scheme("https")
                .host("video.sibnet.ru")
                .addPathSegments(ApiUtil.getRegex(scriptText,
                    srcRegex
                ))
                .build()
                .toString()
        })
        return HttpHandler.instance.executeDirect({
            redirectUrl.toHttpUrl().newBuilder()
        }, { this.addHeader("Referer", providerResult.link) }, {
            listOf(StorageResult(this.request.url.toString(), providerResult.quality))
        })
    }

    override val score: Int
        get() = SCORE
    override val name: String
        get() = NAME
}