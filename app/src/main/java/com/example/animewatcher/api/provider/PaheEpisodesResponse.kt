package com.example.animewatcher.api.provider

import com.example.animewatcher.api.EpisodeInfo
import com.example.animewatcher.api.TitleInfo

class PaheEpisodesResponse(val data: List<PaheEpisodeEntry>) {
    fun toEpisodeInfo(): List<EpisodeInfo> {
        return data.map { it.toEpisodeInfo() }
    }
}