package ru.rofleksey.animewatcher.api

import ru.rofleksey.animewatcher.api.model.Quality
import ru.rofleksey.animewatcher.api.model.StorageResult

interface Storage {
    val score: Int
    val name: String
    suspend fun extract(url: String, prefQuality: Quality): StorageResult
}