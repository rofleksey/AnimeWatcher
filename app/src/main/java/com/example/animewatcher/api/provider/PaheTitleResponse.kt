package com.example.animewatcher.api.provider

import com.example.animewatcher.api.model.TitleInfo

data class PaheTitleResponse(val data: List<PaheTitleEntry>?) {
    fun toTitleInfo(): List<TitleInfo> {
        return data?.map { it.toTitleInfo() } ?: listOf()
    }
}