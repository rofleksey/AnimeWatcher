package com.example.animewatcher.api.provider

import com.example.animewatcher.api.TitleInfo

class PaheTitleEntry(val id: Int, val title: String, val episodes: Int) {
    fun toTitleInfo(): TitleInfo {
        return TitleInfo(id, title, episodes)
    }
}