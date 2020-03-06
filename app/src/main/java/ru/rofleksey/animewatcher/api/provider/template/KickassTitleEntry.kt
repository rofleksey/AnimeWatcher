package ru.rofleksey.animewatcher.api.provider.template

import ru.rofleksey.animewatcher.api.model.TitleAirStatus
import ru.rofleksey.animewatcher.api.model.TitleInfo
import ru.rofleksey.animewatcher.api.provider.KickassAnime

data class KickassTitleEntry(val name: String, val slug: String, val image: String) {
    fun toTitleInfo(): TitleInfo {
        return TitleInfo(
            title = name,
            details = "",
            airStatus = TitleAirStatus.UNKNOWN,
            image = "${KickassAnime.BASE_URL}uploads/$image"
        ).also {
            it["slug"] = slug
        }
    }
}