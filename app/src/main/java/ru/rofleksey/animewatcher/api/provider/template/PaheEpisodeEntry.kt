package ru.rofleksey.animewatcher.api.provider.template

import ru.rofleksey.animewatcher.api.model.EpisodeInfo

class PaheEpisodeEntry(
    val id: Int,
    val episode: String,
    val snapshot: String,
    val session: String
) {
    fun toEpisodeInfo(): EpisodeInfo {
        return EpisodeInfo(
            name = episode,
            image = snapshot
        ).also {
            it["id"] = id.toString()
            it["session"] = session
        }
    }
}