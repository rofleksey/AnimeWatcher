package com.example.animewatcher.api.provider

import com.example.animewatcher.api.model.EpisodeInfo
import com.example.animewatcher.util.Util

class PaheEpisodeEntry(val id: Int, val episode: String, val snapshot: String) {
    fun toEpisodeInfo(): EpisodeInfo {
        return EpisodeInfo(id, Util.getEpisodeNumber(episode), snapshot)
    }
}