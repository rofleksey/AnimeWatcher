package ru.rofleksey.animewatcher.api.storage.russian

import okhttp3.HttpUrl.Companion.toHttpUrl
import ru.rofleksey.animewatcher.api.Storage
import ru.rofleksey.animewatcher.api.model.Quality
import ru.rofleksey.animewatcher.api.model.StorageAction
import ru.rofleksey.animewatcher.api.model.StorageResult
import java.io.IOException

class AnimeDubStorage: Storage {
    companion object {
        private const val TAG = "AnimeDubStorage"
        const val NAME = "animedub.ru"
        const val SCORE = 65
        val instance: AnimeDubStorage by lazy { HOLDER.INSTANCE }
    }

    private object HOLDER {
        val INSTANCE =
            AnimeDubStorage()
    }

    override val score: Int
        get() = SCORE
    override val name: String
        get() = NAME

    override suspend fun extract(url: String, prefQuality: Quality): StorageResult {
        val link: String = url.toHttpUrl().queryParameter("file") ?: throw IOException("invalid playerjs query")
        return StorageResult(link, StorageAction.ANY)
    }
}