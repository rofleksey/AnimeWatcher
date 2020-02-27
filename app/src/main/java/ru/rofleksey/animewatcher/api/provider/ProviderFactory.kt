package ru.rofleksey.animewatcher.api.provider

import android.content.Context
import ru.rofleksey.animewatcher.api.AnimeProvider

class ProviderFactory {
    companion object {
        const val ANIMEPAHE = "animepahe"
        const val ANIMEDUB = "animedub"
        const val GOGOANIME = "gogoanime"
        const val DEFAULT = GOGOANIME

        @Throws(NoSuchElementException::class)
        fun get(context: Context, name: String): AnimeProvider {
            return when (name) {
                ANIMEPAHE -> AnimePahe(context)
                ANIMEDUB -> AnimeDub(context)
                GOGOANIME -> GogoAnime(context)
                else -> throw NoSuchElementException("Invalid anime provider")
            }
        }
    }
}