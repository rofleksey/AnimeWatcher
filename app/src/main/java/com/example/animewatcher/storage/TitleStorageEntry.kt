package com.example.animewatcher.storage

import com.example.animewatcher.api.model.EpisodeInfo
import com.example.animewatcher.api.model.TitleInfo

class TitleStorageEntry(val info: TitleInfo, val provider: String): Comparable<TitleStorageEntry> {
    val cachedEpisodeList = mutableListOf<EpisodeInfo>()
    val downloadMap = mutableMapOf<Int, String>()
    val downloadTasks = mutableMapOf<Int, Long>()
    var lastEpisodeNumber: Int = -1
    var lastWatchedEpisode: Int = -1

    override operator fun compareTo(other: TitleStorageEntry): Int {
        return info.compareTo(other.info)
    }
}