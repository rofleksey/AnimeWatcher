package ru.rofleksey.animewatcher.api.provider.template

import ru.rofleksey.animewatcher.api.model.TitleInfo

data class PaheTitleResponse(val data: List<PaheTitleEntry>?) {
    fun toTitleInfo(): List<TitleInfo> {
        return data?.map { it.toTitleInfo() } ?: listOf()
    }
}