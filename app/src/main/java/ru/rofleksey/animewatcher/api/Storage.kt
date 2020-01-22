package ru.rofleksey.animewatcher.api

import ru.rofleksey.animewatcher.api.model.StorageType

interface Storage {
    suspend fun extractStream(url: String): String
    suspend fun extractDownload(url: String): String
    fun storageType(): StorageType
}