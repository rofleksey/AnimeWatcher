package ru.rofleksey.animewatcher.api.provider.template

import ru.rofleksey.animewatcher.api.model.EpisodeInfo

data class PaheEpisodesResponse(val data: List<PaheEpisodeEntry>?) {
    fun toEpisodeInfo(): List<EpisodeInfo> {
        return data?.map { it.toEpisodeInfo() } ?: listOf()
    }
}