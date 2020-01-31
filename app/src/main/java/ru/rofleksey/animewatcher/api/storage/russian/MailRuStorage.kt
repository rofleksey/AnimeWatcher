package ru.rofleksey.animewatcher.api.storage.russian

import android.util.Log
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import ru.rofleksey.animewatcher.api.Storage
import ru.rofleksey.animewatcher.api.model.Quality
import ru.rofleksey.animewatcher.api.model.StorageAction
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.util.ApiUtil
import ru.rofleksey.animewatcher.api.util.HttpHandler
import ru.rofleksey.animewatcher.api.util.actualBody

class MailRuStorage: Storage {
    companion object {
        private const val TAG = "MailRu"
        const val NAME = "mail.ru"
        const val SCORE = 50
        val instance: MailRuStorage by lazy { HOLDER.INSTANCE }
    }

    private object HOLDER {
        val INSTANCE =
            MailRuStorage()
    }
    override suspend fun extract(url: String, prefQuality: Quality): StorageResult {
        val metadataUrl = HttpHandler.instance.executeDirect({
            url.toHttpUrl().newBuilder()
        }, { this }, {
            val doc = Jsoup.parse(this.actualBody())
            val scriptTag = doc.selectFirst(
                "div[data-mru-app-section=\"body\"] div[data-mru-fragment=\"video/embed/main\"] script"
            )
            val scriptText = scriptTag.data()
            Log.v(TAG, "scriptText: $scriptText")
            val json = JsonParser.parseString(scriptText).asJsonObject
            val flashVars = json.getAsJsonObject("flashVars")
            ApiUtil.sanitizeScheme(flashVars.get("metadataUrl").asString)
        })
        Log.v(TAG, metadataUrl)
        val result = HttpHandler.instance.executeDirect({
            metadataUrl.toHttpUrl().newBuilder()
        }, { this }, {
            val json = JsonParser.parseString(this.actualBody()).asJsonObject
            val links = json.getAsJsonArray("videos").map {
                val curUrl = ApiUtil.sanitizeScheme(it.asJsonObject.get("url").asString)
                val qualityString = it.asJsonObject.get("key").asString
                Pair(ApiUtil.strToQuality(qualityString), curUrl)
            }
            Log.v(TAG, links.toString())
            ApiUtil.pickQuality(links, prefQuality)
        })
        return StorageResult(result, StorageAction.DOWNLOAD_ONLY).also {
            it.headers["Cookie"] = HttpHandler.instance.getCookiesString(metadataUrl)
        }
    }

    override val score: Int
        get() = SCORE
    override val name: String
        get() = NAME
}