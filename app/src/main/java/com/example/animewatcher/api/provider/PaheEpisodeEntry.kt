package com.example.animewatcher.api.provider

import com.example.animewatcher.api.model.EpisodeInfo

class PaheEpisodeEntry(val id: Int, val episode: String, val snapshot: String) {
    fun toEpisodeInfo(): EpisodeInfo {
        return EpisodeInfo(id, episode, snapshot)
    }
}