package ru.rofleksey.animewatcher.database

import ru.rofleksey.animewatcher.api.model.EpisodeInfo
import ru.rofleksey.animewatcher.api.model.TitleInfo

class TitleStorageEntry(val info: TitleInfo, val provider: String) : Comparable<TitleStorageEntry> {
    val cachedEpisodeList = mutableListOf<EpisodeInfo>()
    val downloads = HashMap<String, EpisodeDownloadStatus>()
    var lastEpisodeNumber: Int = -1
    var lastWatchedEpisode: Int = -1

    override operator fun compareTo(other: TitleStorageEntry): Int {
        return info.compareTo(other.info)
    }

    override fun toString(): String {
        return "TitleStorageEntry(info=$info, provider='$provider', cachedEpisodeList=$cachedEpisodeList, downloads=$downloads, lastEpisodeNumber=$lastEpisodeNumber, lastWatchedEpisode=$lastWatchedEpisode)"
    }


}