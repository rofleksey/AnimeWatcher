package ru.rofleksey.animewatcher.api.provider.template

import ru.rofleksey.animewatcher.api.model.EpisodeInfo

class KickassEpisodeEntry(private val num: String, private val slug: String) {
    fun toEpisodeInfo(): EpisodeInfo {
        return EpisodeInfo(
            name = num,
            image = null
        ).also {
            it["slug"] = slug
        }
    }
}