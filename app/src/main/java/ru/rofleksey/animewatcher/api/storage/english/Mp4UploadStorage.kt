package ru.rofleksey.animewatcher.api.storage.english

import android.content.Context
import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.model.ProviderResult
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.storage.Storage
import ru.rofleksey.animewatcher.api.unpackers.PACKERUnpacker
import ru.rofleksey.animewatcher.api.util.ApiUtil
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody

class Mp4UploadStorage : Storage {
    companion object {
        private const val TAG = "Mp4Upload"
        const val NAME = "mp4upload"
        const val SCORE = 25
        val instance: Mp4UploadStorage by lazy { HOLDER.INSTANCE }
        private val sourceRegex = Regex("player.src\\(\"([^\"]+)\"\\)")
    }

    private object HOLDER {
        val INSTANCE =
            Mp4UploadStorage()
    }

    override suspend fun extract(
        context: Context,
        providerResult: ProviderResult
    ): List<StorageResult> {
        return HttpHandler.instance.executeDirect({
            providerResult.link.toHttpUrl().newBuilder()
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            val scriptTag = doc.selectFirst("body > script:nth-child(10)")
            val scriptText = scriptTag.data()
            Log.v(TAG, "scriptTag.data() = $scriptText")
            val unpacked = PACKERUnpacker.unpack(scriptText)
            Log.v(TAG, "unpacked = $unpacked")
            listOf(
                StorageResult(
                ApiUtil.getRegex(unpacked,
                    sourceRegex
                ),
                    providerResult.quality
                )
            )
        })
    }

    override val score: Int
        get() = SCORE
    override val name: String
        get() = NAME
}