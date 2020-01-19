package com.example.animewatcher.api

import com.example.animewatcher.api.model.StorageType
import java.util.concurrent.CompletableFuture

interface Storage {
    suspend fun extractStream(url: String): String
    suspend fun extractDownload(url: String): String
    fun storageType(): StorageType
}