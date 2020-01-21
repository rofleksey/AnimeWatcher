package com.example.animewatcher.api.provider

import com.example.animewatcher.api.AnimeProvider

class ProviderFactory {
    companion object {
        val ANIMEPAHE = "animepahe"
        @Throws(NoSuchElementException::class)
        fun get(name: String): AnimeProvider {
            return when(name) {
                ANIMEPAHE -> AnimePahe()
                else -> throw NoSuchElementException("Invalid anime provider")
            }
        }
    }
}