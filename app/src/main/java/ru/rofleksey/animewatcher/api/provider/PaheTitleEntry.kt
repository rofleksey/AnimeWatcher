package ru.rofleksey.animewatcher.api.provider

import ru.rofleksey.animewatcher.api.model.TitleInfo

data class PaheTitleEntry(val id: Int, val title: String, val episodes: Int, val image: String,
                          val type: String, val status: String, val season: String) {
    fun toTitleInfo(): TitleInfo {
        val details = "$type, $season, $status ($episodes)"
        return TitleInfo(
            title = title,
            details = details,
            image = image
        ).also {
            it["id"] = id.toString()
        }
    }
}