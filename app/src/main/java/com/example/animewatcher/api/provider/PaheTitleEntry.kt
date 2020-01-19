package com.example.animewatcher.api.provider

import com.example.animewatcher.api.model.TitleInfo

data class PaheTitleEntry(val id: Int, val title: String, val episodes: Int, val image: String) {
    fun toTitleInfo(): TitleInfo {
        return TitleInfo(id, title, episodes, image)
    }
}