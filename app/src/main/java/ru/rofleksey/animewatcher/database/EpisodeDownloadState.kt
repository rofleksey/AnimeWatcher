package ru.rofleksey.animewatcher.database

enum class EpisodeDownloadState(val state: String) {
    FINISHED("finished"),
    PENDING("pending"),
    REJECTED("rejected")
}