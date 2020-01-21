package com.example.animewatcher.api.provider

import com.example.animewatcher.api.model.TitleInfo

data class PaheTitleEntry(val id: Int, val title: String, val episodes: Int, val image: String,
                          val type: String, val status: String, val season: String) {
    fun toTitleInfo(): TitleInfo {
        val details = "$type, $season, $status ($episodes)"
        return TitleInfo(id, title, episodes, image, details)
    }
}