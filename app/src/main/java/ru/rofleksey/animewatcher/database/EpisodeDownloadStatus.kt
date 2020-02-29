package ru.rofleksey.animewatcher.database

data class EpisodeDownloadStatus(val id: Long, val file: String, var state: EpisodeDownloadState)