package com.example.animewatcher.api

import java.util.concurrent.CompletableFuture

interface AnimeProvider {
    fun search(title: String) : CompletableFuture<List<TitleInfo>>
    fun getEpisodeList(id: Int, page: Int) : CompletableFuture<List<EpisodeInfo>>
    fun getStorageLink(episodeId: Int) : CompletableFuture<String>
}