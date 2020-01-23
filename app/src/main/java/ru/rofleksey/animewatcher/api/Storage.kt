package ru.rofleksey.animewatcher.api

import ru.rofleksey.animewatcher.api.model.StorageResult

interface Storage {
    suspend fun extract(url: String): StorageResult
    fun score(): Int
    fun name(): String
}