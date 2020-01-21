package com.example.animewatcher.api.model

data class TitleInfo(val id: Int, val title: String, val episodeCount: Int, val image: String?,
                     val details: String) : Comparable<TitleInfo> {
    override fun compareTo(other: TitleInfo): Int {
        return title.compareTo(other.title)
    }
}