package ru.rofleksey.animewatcher.api.storage.russian

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import ru.rofleksey.animewatcher.api.model.ProviderResult
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.storage.Storage
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

    override suspend fun extract(
        context: Context,
        providerResult: ProviderResult
    ): List<StorageResult> {
        val link: String = providerResult.link.toHttpUrl().queryParameter("file")
            ?: throw IOException("invalid playerjs query")
        return listOf(StorageResult(link, providerResult.quality))
    }
}