package ru.rofleksey.animewatcher.api.model

class StorageResult(val link: String, val action: StorageAction) {
    val headers = HashMap<String, String>()
}