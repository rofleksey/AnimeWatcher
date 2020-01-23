package ru.rofleksey.animewatcher.api.storage

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.Storage
import ru.rofleksey.animewatcher.api.model.StorageAction
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.unpackers.PACKERUnpacker
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody
import java.io.IOException

class Mp4UploadStorage : Storage {
    companion object {
        private const val TAG = "Mp4Upload"
        const val NAME = "mp4upload"
        const val SCORE = 25
        val instance: Mp4UploadStorage by lazy { HOLDER.INSTANCE }
        private val sourceRegex = Regex("player.src\\(\"([^\"]+)\"\\)")
    }

    private object HOLDER {
        val INSTANCE = Mp4UploadStorage()
    }

    override suspend fun extract(url: String): StorageResult {
        return HttpHandler.instance.executeDirect({
            url.toHttpUrl().newBuilder()
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            val scriptTag = doc.selectFirst("body > script:nth-child(10)")
            val scriptText = scriptTag.data()
            Log.v(TAG, "scriptTag.data() = $scriptText")
            val unpacked = PACKERUnpacker.unpack(scriptText)
            Log.v(TAG, "unpacked = $unpacked")
            val match = sourceRegex.find(unpacked) ?: throw IOException("can't match regex!")
            StorageResult(match.groupValues[1], StorageAction.CUSTOM_ONLY)
        })
    }

    override fun score(): Int {
        return SCORE
    }

    override fun name(): String {
        return NAME
    }
}