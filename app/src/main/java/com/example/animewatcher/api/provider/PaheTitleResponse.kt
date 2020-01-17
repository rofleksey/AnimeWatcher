package com.example.animewatcher.api.provider

import com.example.animewatcher.api.TitleInfo

class PaheTitleResponse(val data: List<PaheTitleEntry>) {
    fun toTitleInfo(): List<TitleInfo> {
        return data.map { it.toTitleInfo() }
    }
}