package ru.rofleksey.animewatcher.api.storage.english

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.model.ProviderResult
import ru.rofleksey.animewatcher.api.model.Quality
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.storage.Storage
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody

class BitchuteStorage private constructor() : Storage {
    companion object {
        private const val TAG = "Bitchute"
        const val NAME = "bitchute"
        const val SCORE = 40
        val instance: BitchuteStorage by lazy { HOLDER.INSTANCE }
    }

    private object HOLDER {
        val INSTANCE = BitchuteStorage()
    }

    override suspend fun extract(
        context: Context,
        providerResult: ProviderResult
    ): List<StorageResult> {
        return HttpHandler.instance.executeDirect({
            providerResult.link.toHttpUrl().newBuilder()
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            val sources = doc.select("video source")
            sources.map { source ->
                StorageResult(source.attr("src"), Quality.UNKNOWN, false)
            }
        })
    }

    override val score: Int
        get() = SCORE
    override val name: String
        get() = NAME
}