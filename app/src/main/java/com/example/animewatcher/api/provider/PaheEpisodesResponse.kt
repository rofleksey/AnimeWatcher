package com.example.animewatcher.api.provider

import com.example.animewatcher.api.model.EpisodeInfo

data class PaheEpisodesResponse(val data: List<PaheEpisodeEntry>?) {
    fun toEpisodeInfo(): List<EpisodeInfo> {
        return data?.map { it.toEpisodeInfo() } ?: listOf()
    }
}