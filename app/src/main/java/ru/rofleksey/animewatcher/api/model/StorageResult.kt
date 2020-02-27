package ru.rofleksey.animewatcher.api.model

class StorageResult(val link: String, val quality: Quality) {
    val headers = HashMap<String, String>()
}