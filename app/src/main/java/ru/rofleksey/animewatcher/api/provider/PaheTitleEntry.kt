package ru.rofleksey.animewatcher.api.provider

import ru.rofleksey.animewatcher.api.model.TitleAirStatus
import ru.rofleksey.animewatcher.api.model.TitleInfo

data class PaheTitleEntry(
    val id: Int, val title: String, val episodes: Int, val image: String,
    val type: String, val status: String, val season: String
) {
    fun toTitleInfo(): TitleInfo {
        val details = "$type, $season, $status ($episodes)"
        val airStatus = when (status.toLowerCase()) {
            "finished airing" -> TitleAirStatus.FINISHED
            "currently airing" -> TitleAirStatus.AIRING
            else -> TitleAirStatus.UNKNOWN
        }
        return TitleInfo(
            title = title,
            details = details,
            airStatus = airStatus,
            image = image
        ).also {
            it["id"] = id.toString()
        }
    }
}