package com.example.animewatcher.api.provider

import com.example.animewatcher.api.EpisodeInfo
import com.example.animewatcher.api.TitleInfo

class PaheEpisodeEntry(val id: Int, val episode: String) {
    fun toEpisodeInfo(): EpisodeInfo {
        return EpisodeInfo(id, episode)
    }
}