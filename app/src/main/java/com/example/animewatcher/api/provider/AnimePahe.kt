package com.example.animewatcher.api.provider

import com.example.animewatcher.api.*
import com.google.gson.Gson
import java.util.concurrent.CompletableFuture

class AnimePahe : AnimeProvider {
    companion object {
        const val HOST = "animepahe.com"
        val gson = Gson()
    }
    val httpHandler = HttpHandler(HOST)

    override fun search(title: String): CompletableFuture<List<TitleInfo>> {
        return httpHandler.execute({
            this.addPathSegment("api")
                .addQueryParameter("m", "search")
                .addQueryParameter("l", "8")
                .addQueryParameter("q", title)
        }, {
            val obj = gson.fromJson(this, PaheTitleResponse::class.java)
            obj.toTitleInfo()
        })
    }

    override fun getEpisodeList(id: Int, page: Int): CompletableFuture<List<EpisodeInfo>> {
        return httpHandler.execute({
            this.addPathSegment("api")
                .addQueryParameter("m", "release")
                .addQueryParameter("id", id.toString())
                .addQueryParameter("l", "30")
                .addQueryParameter("sort", "episode_desc")
                .addQueryParameter("page", (page + 1).toString())
        }, {
            val obj = gson.fromJson(this, PaheEpisodesResponse::class.java)
            obj.toEpisodeInfo()
        })
    }

    override fun getStorageLink(episodeId: Int): CompletableFuture<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}