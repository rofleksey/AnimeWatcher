package ru.rofleksey.animewatcher.api.storage

import android.content.Context
import ru.rofleksey.animewatcher.api.model.ProviderResult
import ru.rofleksey.animewatcher.api.model.StorageResult

interface Storage {
    val score: Int
    val name: String
    suspend fun extract(context: Context, providerResult: ProviderResult): List<StorageResult>
}